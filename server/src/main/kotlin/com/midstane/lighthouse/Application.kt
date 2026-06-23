package com.midstane.lighthouse

import com.midstane.lighthouse.dependency.AppGraph
import dev.zacsweers.metro.createGraphFactory
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.init(){
    val graph = createGraphFactory<AppGraph.Factory>().create(environment.config)
    lighthouse(graph)
}
