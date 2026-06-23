package com.midstane.lighthouse

import com.midstane.lighthouse.controller.AuthRequirement
import com.midstane.lighthouse.controller.Controller
import com.midstane.lighthouse.controller.LighthouseRouting
import com.midstane.lighthouse.controller.PermissionAuthorizer
import com.midstane.lighthouse.dependency.RouteGraph
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun Application.start(
    graph: RouteGraph,
    permissionAuthorizer: PermissionAuthorizer = PermissionAuthorizer { _, required -> required.isEmpty() },
) {
    routing {
        graph.controllers.forEach { controller ->
            registerController(controller, permissionAuthorizer)
        }
    }
}

private fun Routing.registerController(
    controller: Controller,
    permissionAuthorizer: PermissionAuthorizer,
) {
    val routes = LighthouseRouting(
        routing = this,
        baseRoute = controller.baseRoute,
        defaultPermissions = controller.permissions,
        permissionAuthorizer = permissionAuthorizer,
    )
    when (val auth = controller.auth) {
        AuthRequirement.None -> controller.registerRoutes(routes)
        is AuthRequirement.Required -> authenticate(*auth.providers.toTypedArray()) {
            controller.registerRoutes(
                LighthouseRouting(
                    routing = this,
                    baseRoute = controller.baseRoute,
                    defaultPermissions = controller.permissions,
                    permissionAuthorizer = permissionAuthorizer,
                ),
            )
        }
    }
}
