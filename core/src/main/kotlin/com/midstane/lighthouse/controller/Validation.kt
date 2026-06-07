package com.midstane.lighthouse.controller

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.request.header
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

interface Validatable {
    fun validate(): List<ValidationError>
}

@Serializable
data class ValidationError(
    val field: String? = null,
    val message: String,
)

class RequestValidationException(
    val errors: List<ValidationError>,
) : BadRequestException("Request validation failed")

suspend inline fun <reified T : Any> ApplicationCall.body(): T {
    if (!hasRequestBody()) {
        throw BadRequestException("Request body is required")
    }

    val body = try {
        receiveNullable<T>()
    } catch (e: BadRequestException) {
        throw BadRequestException(e.cause?.localizedMessage.orEmpty())
    } ?: throw BadRequestException("Request body is required")

    validateBody(body)
    return body
}

fun ApplicationCall.hasRequestBody(): Boolean {
    val contentLength = request.header(HttpHeaders.ContentLength)?.toLongOrNull()
    return when {
        contentLength == null -> request.header(HttpHeaders.TransferEncoding) != null
        else -> contentLength > 0
    }
}

fun validateBody(body: Any) {
    if (body !is Validatable) return

    val errors = body.validate()
    if (errors.isNotEmpty()) {
        throw RequestValidationException(errors)
    }
}

suspend inline fun <reified T : Any> ApplicationCall.respondResult(result: T) {
    if (result is Unit) {
        respond(HttpStatusCode.NoContent)
    } else {
        ok(result)
    }
}