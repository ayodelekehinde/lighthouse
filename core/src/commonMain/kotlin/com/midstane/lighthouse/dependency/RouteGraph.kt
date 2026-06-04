package com.midstane.lighthouse.dependency

import com.midstane.lighthouse.controller.Controller

interface RouteGraph {
    val controllers: Set<Controller>
}
