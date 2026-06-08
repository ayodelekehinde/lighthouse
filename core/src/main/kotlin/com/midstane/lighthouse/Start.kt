package com.midstane.lighthouse

import com.midstane.lighthouse.controller.AuthRequirement
import com.midstane.lighthouse.controller.Controller
import com.midstane.lighthouse.controller.LighthouseRouting
import com.midstane.lighthouse.dependency.RouteGraph
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun Application.start(graph: RouteGraph) {
    routing {
        graph.controllers.forEach { controller ->
            registerController(controller)
        }
    }
}

private fun Routing.registerController(controller: Controller) {
    when (val auth = controller.auth) {
        AuthRequirement.None -> controller.registerRoutes(LighthouseRouting(this, controller.baseRoute))
        is AuthRequirement.Required -> authenticate(*auth.providers.toTypedArray()) {
            controller.registerRoutes(LighthouseRouting(this, controller.baseRoute))
        }
    }
}
