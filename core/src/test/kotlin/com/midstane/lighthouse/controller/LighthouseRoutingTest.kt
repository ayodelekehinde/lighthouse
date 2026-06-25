package com.midstane.lighthouse.controller

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LighthouseRoutingTest {
    @Test
    fun `get route resolves base route and responds with handler result`() = testApplication {
        application {
            configureTestApplication(baseRoute = "api") {
                get<ThingResponse>("things") {
                    ThingResponse(name = "Ada")
                }
            }
        }

        val response = client.get("/api/things")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"name":"Ada"}""", response.bodyAsText())
    }

    @Test
    fun `post route receives valid body and responds with handler result`() = testApplication {
        application {
            configureTestApplication {
                post<CreateThingRequest, ThingResponse>("/things") { request ->
                    ThingResponse(name = request.name.trim())
                }
            }
        }

        val response = client.post("/things") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":" Ada "}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"name":"Ada"}""", response.bodyAsText())
    }

    @Test
    fun `post route automatically rejects invalid validatable body`() = testApplication {
        var handlerCalled = false
        application {
            configureTestApplication {
                post<CreateThingRequest, ThingResponse>("/things") { request ->
                    handlerCalled = true
                    ThingResponse(name = request.name)
                }
            }
        }

        val response = client.post("/things") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":" "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            """{"message":"Request validation failed","errors":[{"field":"name","message":"name is required"}]}""",
            response.bodyAsText(),
        )
        assertFalse(handlerCalled)
    }

    @Test
    fun `post route rejects missing body before handler runs`() = testApplication {
        var handlerCalled = false
        application {
            configureTestApplication {
                post<CreateThingRequest, ThingResponse>("/things") { request ->
                    handlerCalled = true
                    ThingResponse(name = request.name)
                }
            }
        }

        val response = client.post("/things")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"message":"Request body is required","errors":[]}""", response.bodyAsText())
        assertFalse(handlerCalled)
    }

    @Test
    fun `post route checks permissions before parsing body`() = testApplication {
        var handlerCalled = false
        application {
            configureTestApplication {
                post<CreateThingRequest, ThingResponse>("/things", "things:create") { request ->
                    handlerCalled = true
                    ThingResponse(name = request.name)
                }
            }
        }

        val response = client.post("/things")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("""{"message":"Permission denied. Required permissions: things:create","errors":[]}""", response.bodyAsText())
        assertFalse(handlerCalled)
    }

    @Test
    fun `put route receives body and responds with handler result`() = testApplication {
        application {
            configureTestApplication {
                put<CreateThingRequest, ThingResponse>("/things/1") { request ->
                    ThingResponse(name = request.name.uppercase())
                }
            }
        }

        val response = client.put("/things/1") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"ada"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"name":"ADA"}""", response.bodyAsText())
    }

    @Test
    fun `patch route receives body and responds with handler result`() = testApplication {
        application {
            configureTestApplication {
                patch<CreateThingRequest, ThingResponse>("/things/1") { request ->
                    ThingResponse(name = request.name.trim())
                }
            }
        }

        val response = client.patch("/things/1") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":" Grace "}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"name":"Grace"}""", response.bodyAsText())
    }

    @Test
    fun `delete route can respond with no content`() = testApplication {
        application {
            configureTestApplication {
                delete<Unit>("/things/1") {}
            }
        }

        val response = client.delete("/things/1")

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `raw route registers directly on underlying ktor route`() = testApplication {
        application {
            configureTestApplication("/api") {
                raw {
                    get("/raw") {
                        call.respond(ThingResponse(name = "raw"))
                    }
                }
            }
        }

        val response = client.get("/api/raw")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"name":"raw"}""", response.bodyAsText())
    }
}

private fun Application.configureTestApplication(
    baseRoute: String = "",
    routes: LighthouseRouting.() -> Unit,
) {
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
        })
    }
    install(StatusPages) {
        exception<RequestValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    message = cause.message ?: "Request validation failed",
                    errors = cause.errors,
                ),
            )
        }
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = cause.message ?: "Bad request"),
            )
        }
        exception<PermissionDeniedException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(message = cause.message ?: "Permission denied"),
            )
        }
    }
    routing {
        LighthouseRouting(this, baseRoute).routes()
    }
}

@Serializable
private data class CreateThingRequest(
    val name: String,
) : Validatable {
    override fun validate(): List<ValidationError> {
        return buildList {
            if (name.isBlank()) {
                add(ValidationError(field = "name", message = "name is required"))
            }
        }
    }
}

@Serializable
private data class ThingResponse(
    val name: String,
)

@Serializable
private data class ErrorResponse(
    val message: String,
    val errors: List<ValidationError> = emptyList(),
)
