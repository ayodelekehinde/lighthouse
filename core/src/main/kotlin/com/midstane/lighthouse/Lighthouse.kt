package com.midstane.lighthouse

import com.midstane.lighthouse.controller.PermissionAuthorizer
import com.midstane.lighthouse.controller.PermissionDeniedException
import com.midstane.lighthouse.controller.RequestValidationException
import com.midstane.lighthouse.controller.ValidationError
import com.midstane.lighthouse.dependency.RouteGraph
import com.midstane.lighthouse.exception.LighthouseException
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LighthouseApplicationConfig {
    var json: Json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var installContentNegotiation: Boolean = true
    var exposeExceptionMessages: Boolean = false
    var permissionAuthorizer: PermissionAuthorizer = PermissionAuthorizer { _, required ->
        required.isEmpty()
    }
}

@Serializable
data class ErrorResponse(
    val success: Boolean = false,
    val message: String,
    val errors: List<ValidationError> = emptyList(),
)

fun Application.lighthouse(
    graph: RouteGraph,
    configure: LighthouseApplicationConfig.() -> Unit = {},
) {
    val config = LighthouseApplicationConfig().apply(configure)

    if (config.installContentNegotiation) {
        install(ContentNegotiation) {
            json(config.json)
        }
    }
    installLighthouseStatusPages(config)

    start(graph, config.permissionAuthorizer)
}

fun Application.installLighthouseStatusPages(config: LighthouseApplicationConfig = LighthouseApplicationConfig()) {
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
        exception<PermissionDeniedException> { call, _ ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(message = "Permission denied"),
            )
        }
        exception<LighthouseException> { call, cause ->
            call.respond(
                status = HttpStatusCode.UnprocessableEntity,
                message = cause.message,
            )
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(message = cause.safeMessage(config)),
            )
        }
    }
}

private fun Throwable.safeMessage(config: LighthouseApplicationConfig): String {
    return if (config.exposeExceptionMessages) {
        localizedMessage ?: "Internal server error"
    } else {
        "Internal server error"
    }
}
