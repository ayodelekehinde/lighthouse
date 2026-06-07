package com.midstane.lighthouse.controller

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LighthouseRoutingTest {
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
