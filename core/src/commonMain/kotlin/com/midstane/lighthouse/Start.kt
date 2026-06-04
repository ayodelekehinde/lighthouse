package com.midstane.lighthouse

import com.midstane.lighthouse.dependency.RouteGraph
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.start(graph: RouteGraph) {
    routing {
        graph.controllers.forEach { it.registerRoutes(this) }
    }
}
