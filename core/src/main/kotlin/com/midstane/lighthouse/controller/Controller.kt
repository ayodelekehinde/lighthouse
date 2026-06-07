package com.midstane.lighthouse.controller

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.request.header
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable

interface Controller {
    val baseRoute: String
        get() = ""

    fun registerRoutes(routes: LighthouseRouting)
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
