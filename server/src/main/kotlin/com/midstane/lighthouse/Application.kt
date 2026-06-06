package com.midstane.lighthouse

import com.midstane.lighthouse.dependency.AppGraph
import dev.zacsweers.metro.createGraphFactory
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.response.respondText
import io.sentry.Sentry
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.init(){
//    Sentry.init { options ->
//        options.dsn = "https://5ac93e3dd03c4f7fae7e8756bc47cfba@bugsink.onesignaltech.com/1"
//        options.environment = "production"
//        options.tracesSampleRate = 1.0 // Adjust in production to sample traffic
//    }
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Send the server crash details to Sentry
            //Sentry.captureException(cause)

            // Send a safe, clean response back to the API client
            call.respondText(
                text = cause.localizedMessage,
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    val graph = createGraphFactory<AppGraph.Factory>().create(environment.config)
    start(graph)
}
