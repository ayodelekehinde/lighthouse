package com.midstane.lighthouse

import com.midstane.lighthouse.controller.Controller
import com.midstane.lighthouse.controller.LighthouseRouting
import com.midstane.lighthouse.controller.Validatable
import com.midstane.lighthouse.controller.ValidationError
import com.midstane.lighthouse.dependency.RouteGraph
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

class LighthouseTest {
    @Test
    fun `lighthouse installs json and registers controller routes`() = testApplication {
        application {
            lighthouse(
                graph = testGraph(
                    object : Controller {
                        override val baseRoute: String = "/health"

                        override fun registerRoutes(routes: LighthouseRouting) {
                            routes.get<HealthResponse>("/") {
                                HealthResponse(status = "ok")
                            }
                        }
                    },
                ),
            )
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertJsonEquals("""{"status":"ok"}""", response.bodyAsText())
    }

    @Test
    fun `lighthouse installs standard validation error handling`() = testApplication {
        application {
            lighthouse(
                graph = testGraph(
                    object : Controller {
                        override fun registerRoutes(routes: LighthouseRouting) {
                            routes.post<CreateHealthRequest, HealthResponse>("/health") { request ->
                                HealthResponse(status = request.status)
                            }
                        }
                    },
                ),
            )
        }

        val response = client.post("/health") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertJsonEquals(
            """{"success":false,"message":"Request validation failed","errors":[{"field":"status","message":"status is required"}]}""",
            response.bodyAsText(),
        )
    }
}

private fun testGraph(vararg controllers: Controller): RouteGraph {
    return object : RouteGraph {
        override val applicationConfig: ApplicationConfig = MapApplicationConfig()
        override val controllers: Set<Controller> = controllers.toSet()
    }
}

private fun assertJsonEquals(expected: String, actual: String) {
    assertEquals(Json.decodeFromString<JsonElement>(expected), Json.decodeFromString<JsonElement>(actual))
}

@Serializable
private data class HealthResponse(
    val status: String,
)

@Serializable
private data class CreateHealthRequest(
    val status: String,
) : Validatable {
    override fun validate(): List<ValidationError> {
        return buildList {
            if (status.isBlank()) {
                add(ValidationError(field = "status", message = "status is required"))
            }
        }
    }
}
