package com.midstane.lighthouse.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
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
