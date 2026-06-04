package com.midstane.lighthouse

import com.midstane.lighthouse.dependency.AppGraph
import dev.zacsweers.metro.createGraphFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.init(){
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    val graph = createGraphFactory<AppGraph.Factory>().create(environment.config)
    start(graph)
}
