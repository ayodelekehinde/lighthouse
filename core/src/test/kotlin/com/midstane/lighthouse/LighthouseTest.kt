package com.midstane.lighthouse

import com.midstane.lighthouse.controller.*
import com.midstane.lighthouse.dependency.RouteGraph
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.*
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
                            routes.get<HealthResponse>("") {
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

    @Test
    fun `lighthouse wraps controller routes with configured authentication`() = testApplication {
        application {
            install(Authentication) {
                basic("basic") {
                    validate { credentials ->
                        if (credentials.name == "admin" && credentials.password == "secret") {
                            UserIdPrincipal(credentials.name)
                        } else {
                            null
                        }
                    }
                }
            }
            lighthouse(
                graph = testGraph(
                    object : Controller {
                        override val baseRoute: String = "/admin"
                        override val auth: AuthRequirement = AuthRequirement.Required("basic")

                        override fun registerRoutes(routes: LighthouseRouting) {
                            routes.get<HealthResponse>("") {
                                HealthResponse(status = "private")
                            }
                        }
                    },
                ),
            )
        }

        val unauthorizedResponse = client.get("/admin")
        val authorizedResponse = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuth("admin", "secret"))
        }

        assertEquals(HttpStatusCode.Unauthorized, unauthorizedResponse.status)
        assertEquals(HttpStatusCode.OK, authorizedResponse.status)
        assertJsonEquals("""{"status":"private"}""", authorizedResponse.bodyAsText())
    }

    @Test
    fun `lighthouse denies protected route without configured permission authorizer`() = testApplication {
        application {
            lighthouse(
                graph = testGraph(
                    object : Controller {
                        override val baseRoute: String = "/admin"

                        override fun registerRoutes(routes: LighthouseRouting) {
                            routes.get<HealthResponse>("", "admin:read") {
                                HealthResponse(status = "private")
                            }
                        }
                    },
                ),
            )
        }

        val response = client.get("/admin")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertJsonEquals(
            """{"success":false,"message":"Permission denied","errors":[]}""",
            response.bodyAsText(),
        )
    }

    @Test
    fun `lighthouse allows protected route when permission authorizer grants all required permissions`() = testApplication {
        application {
            lighthouse(
                graph = testGraph(
                    object : Controller {
                        override val baseRoute: String = "/reports"
                        override val permissions: Set<String> = setOf("reports:access")

                        override fun registerRoutes(routes: LighthouseRouting) {
                            routes.get<HealthResponse>("", "reports:read") {
                                HealthResponse(status = "visible")
                            }
                        }
                    },
                ),
            ) {
                permissionAuthorizer = PermissionAuthorizer { _, required ->
                    required == setOf("reports:access", "reports:read")
                }
            }
        }

        val response = client.get("/reports")

        assertEquals(HttpStatusCode.OK, response.status)
        assertJsonEquals("""{"status":"visible"}""", response.bodyAsText())
    }

    @Test
    fun `lighthouse denies protected route when permission authorizer rejects required permissions`() = testApplication {
        application {
            lighthouse(
                graph = testGraph(
                    object : Controller {
                        override val baseRoute: String = "/reports"

                        override fun registerRoutes(routes: LighthouseRouting) {
                            routes.get<HealthResponse>("", "reports:delete") {
                                HealthResponse(status = "deleted")
                            }
                        }
                    },
                ),
            ) {
                permissionAuthorizer = PermissionAuthorizer { _, required ->
                    "reports:read" in required
                }
            }
        }

        val response = client.get("/reports")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertJsonEquals(
            """{"success":false,"message":"Permission denied","errors":[]}""",
            response.bodyAsText(),
        )
    }

    @Test
    fun `lighthouse keeps authentication and permissions separate`() = testApplication {
        application {
            install(Authentication) {
                basic("basic") {
                    validate { credentials ->
                        if (credentials.password == "secret") {
                            UserIdPrincipal(credentials.name)
                        } else {
                            null
                        }
                    }
                }
            }
            lighthouse(
                graph = testGraph(
                    object : Controller {
                        override val baseRoute: String = "/admin"
                        override val auth: AuthRequirement = AuthRequirement.Required("basic")

                        override fun registerRoutes(routes: LighthouseRouting) {
                            routes.get<HealthResponse>("", "admin:read") {
                                HealthResponse(status = "private")
                            }
                        }
                    },
                ),
            ) {
                permissionAuthorizer = PermissionAuthorizer { call, required ->
                    call.principal<UserIdPrincipal>()?.name == "admin" && "admin:read" in required
                }
            }
        }

        val unauthenticatedResponse = client.get("/admin")
        val unauthorizedResponse = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuth("user", "secret"))
        }
        val authorizedResponse = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuth("admin", "secret"))
        }

        assertEquals(HttpStatusCode.Unauthorized, unauthenticatedResponse.status)
        assertEquals(HttpStatusCode.Forbidden, unauthorizedResponse.status)
        assertEquals(HttpStatusCode.OK, authorizedResponse.status)
        assertJsonEquals("""{"status":"private"}""", authorizedResponse.bodyAsText())
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

private fun basicAuth(username: String, password: String): String {
    val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    return "Basic $token"
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
