package com.midstane.lighthouse.controller

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.Serializable
import kotlin.runCatching

interface Controller {
    fun registerRoutes(routing: Routing)
}

suspend inline fun <reified T> ApplicationCall.post(block: suspend T.() -> Unit) {
    val body = runCatching { this.receiveNullable<T>() }.getOrThrow()
    if (body == null) {
        throw BadRequestException("Bad Request")
    }
    body.block()
}
@Serializable
data class Success<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null
)
suspend inline fun <reified T: Any> ApplicationCall.ok(data: T) {
    respond(status = HttpStatusCode.OK, message = data)
}

suspend fun ApplicationCall.badRequest(message: String) {
    respond(status = HttpStatusCode.BadRequest, message = message)
}