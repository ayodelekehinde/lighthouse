package com.midstane.lighthouse

import com.midstane.lighthouse.dependency.RouteGraph
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import com.midstane.lighthouse.controller.LighthouseRouting

fun Application.start(graph: RouteGraph) {
    routing {
        graph.controllers.forEach { controller ->
            controller.registerRoutes(LighthouseRouting(this, controller.baseRoute))
        }
    }
}
