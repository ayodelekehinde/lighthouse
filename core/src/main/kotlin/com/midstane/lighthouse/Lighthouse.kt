package com.midstane.lighthouse

import com.midstane.lighthouse.controller.RequestValidationException
import com.midstane.lighthouse.controller.ValidationError
import com.midstane.lighthouse.dependency.RouteGraph
import com.midstane.lighthouse.exception.LighthouseException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
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

    start(graph)
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
        exception<LighthouseException> { call, cause ->
            //sentry
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
