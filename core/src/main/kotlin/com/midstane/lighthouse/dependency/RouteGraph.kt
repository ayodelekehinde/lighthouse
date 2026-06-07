package com.midstane.lighthouse.dependency

import com.midstane.lighthouse.controller.Controller
import io.ktor.server.config.ApplicationConfig

interface RouteGraph {
    val applicationConfig: ApplicationConfig
    val controllers: Set<Controller>
}
