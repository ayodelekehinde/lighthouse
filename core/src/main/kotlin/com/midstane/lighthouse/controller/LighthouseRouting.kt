package com.midstane.lighthouse.controller

import io.ktor.server.routing.*

class LighthouseRouting(
    @PublishedApi internal val routing: Route,
    @PublishedApi internal val baseRoute: String = "",
) {
    fun raw(block: Route.() -> Unit) {
        routing.block()
    }

    inline fun <reified Response : Any> get(
        path: String,
        crossinline handler: suspend RoutingCall.() -> Response,
    ) {
        routing.get(resolve(path)) {
            call.respondResult(call.handler())
        }
    }

    inline fun <reified Request : Any, reified Response : Any> post(
        path: String,
        crossinline handler: suspend RoutingCall.(Request) -> Response,
    ) {
        routing.post(resolve(path)) {
            val request = call.body<Request>()
            call.respondResult(call.handler(request))
        }
    }

    inline fun <reified Request : Any, reified Response : Any> put(
        path: String,
        crossinline handler: suspend RoutingCall.(Request) -> Response,
    ) {
        routing.put(resolve(path)) {
            val request = call.body<Request>()
            call.respondResult(call.handler(request))
        }
    }

    inline fun <reified Request : Any, reified Response : Any> patch(
        path: String,
        crossinline handler: suspend RoutingCall.(Request) -> Response,
    ) {
        routing.patch(resolve(path)) {
            val request = call.body<Request>()
            call.respondResult(call.handler(request))
        }
    }

    inline fun <reified Response : Any> delete(
        path: String,
        crossinline handler: suspend RoutingCall.() -> Response,
    ) {
        routing.delete(resolve(path)) {
            call.respondResult(call.handler())
        }
    }

    @PublishedApi
    internal fun resolve(path: String): String {
        val normalizedBase = baseRoute.trim().trimEnd('/')
        val normalizedPath = path.trim()

        return when {
            normalizedBase.isBlank() -> normalizedPath.ifBlank { "/" }
            normalizedPath.isBlank() || normalizedPath == "/" -> normalizedBase.ensureLeadingSlash()
            else -> "${normalizedBase.ensureLeadingSlash()}/${normalizedPath.trimStart('/')}"
        }
    }
}

private fun String.ensureLeadingSlash(): String = if (startsWith("/")) this else "/$this"
