package com.midstane.lighthouse.controller

import io.ktor.server.routing.*

class LighthouseRouting(
    @PublishedApi internal val routing: Route,
    @PublishedApi internal val baseRoute: String = "",
    @PublishedApi internal val defaultPermissions: Set<String> = emptySet(),
    @PublishedApi internal val permissionAuthorizer: PermissionAuthorizer = PermissionAuthorizer { _, required ->
        required.isEmpty()
    },
) {
    fun raw(block: Route.() -> Unit) {
        routeFromBaseUrl {
            block(this)
        }
    }

    inline fun <reified Response : Any> get(
        path: String,
        vararg permissions: String,
        crossinline handler: suspend RoutingCall.() -> Response,
    ) {
        routeFromBaseUrl {
            get(path) {
                call.requirePermissions(permissions)
                call.respondResult(call.handler())
            }
        }

    }

    inline fun <reified Request : Any, reified Response : Any> post(
        path: String,
        vararg permissions: String,
        crossinline handler: suspend RoutingCall.(Request) -> Response,
    ) {
        routeFromBaseUrl {
            post(path) {
                call.requirePermissions(permissions)
                val request = call.body<Request>()
                call.respondResult(call.handler(request))
            }
        }
    }

    inline fun <reified Request : Any, reified Response : Any> put(
        path: String,
        vararg permissions: String,
        crossinline handler: suspend RoutingCall.(Request) -> Response,
    ) {
        routeFromBaseUrl {
            put(path) {
                call.requirePermissions(permissions)
                val request = call.body<Request>()
                call.respondResult(call.handler(request))
            }
        }
    }

    inline fun <reified Request : Any, reified Response : Any> patch(
        path: String,
        vararg permissions: String,
        crossinline handler: suspend RoutingCall.(Request) -> Response,
    ) {
        routeFromBaseUrl {
            patch(path) {
                call.requirePermissions(permissions)
                val request = call.body<Request>()
                call.respondResult(call.handler(request))
            }
        }
    }

    inline fun <reified Response : Any> delete(
        path: String,
        vararg permissions: String,
        crossinline handler: suspend RoutingCall.() -> Response,
    ) {
        routeFromBaseUrl {
            delete(path) {
                call.requirePermissions(permissions)
                call.respondResult(call.handler())
            }
        }
    }

    @PublishedApi
    internal fun routeFromBaseUrl(build: Route.() -> Unit) {
        if (baseRoute.isNotBlank()) {
           routing.route(baseRoute, build)
        } else {
            routing.build()
        }
    }

    @PublishedApi
    internal suspend fun RoutingCall.requirePermissions(routePermissions: Array<out String>) {
        val required = defaultPermissions + routePermissions
        if (required.isEmpty()) {
            return
        }
        if (!permissionAuthorizer.hasPermissions(this, required)) {
            throw PermissionDeniedException(required)
        }
    }
}
