package com.midstane.lighthouse

import com.midstane.lighthouse.dependency.AppGraph
import dev.zacsweers.metro.createGraphFactory
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.init(){
//    Sentry.init { options ->
//        options.dsn = "https://5ac93e3dd03c4f7fae7e8756bc47cfba@bugsink.onesignaltech.com/1"
//        options.environment = "production"
//        options.tracesSampleRate = 1.0 // Adjust in production to sample traffic
//    }
    val graph = createGraphFactory<AppGraph.Factory>().create(environment.config)
    lighthouse(graph)
}
