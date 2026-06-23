package com.midstane.lighthouse.controller

import io.ktor.server.routing.*

class LighthouseRouting(
    @PublishedApi internal val routing: Route,
    @PublishedApi internal val baseRoute: String = "",
) {
    fun raw(block: Route.() -> Unit) {
        routeFromBaseUrl {
            block(this)
        }
    }

    inline fun <reified Response : Any> get(
        path: String,
        crossinline handler: suspend RoutingCall.() -> Response,
    ) {
        routeFromBaseUrl {
            get(path) {
                call.respondResult(call.handler())
            }
        }

    }

    inline fun <reified Request : Any, reified Response : Any> post(
        path: String,
        crossinline handler: suspend RoutingCall.(Request) -> Response,
    ) {
        routeFromBaseUrl {
            post(path) {
                val request = call.body<Request>()
                call.respondResult(call.handler(request))
            }
        }
    }

    inline fun <reified Request : Any, reified Response : Any> put(
        path: String,
        crossinline handler: suspend RoutingCall.(Request) -> Response,
    ) {
        routeFromBaseUrl {
            put(path) {
                val request = call.body<Request>()
                call.respondResult(call.handler(request))
            }
        }
    }

    inline fun <reified Request : Any, reified Response : Any> patch(
        path: String,
        crossinline handler: suspend RoutingCall.(Request) -> Response,
    ) {
        routeFromBaseUrl {
            patch(path) {
                val request = call.body<Request>()
                call.respondResult(call.handler(request))
            }
        }
    }

    inline fun <reified Response : Any> delete(
        path: String,
        crossinline handler: suspend RoutingCall.() -> Response,
    ) {
        routeFromBaseUrl {
            delete(path) {
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
}
