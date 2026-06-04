package com.midstane.lighthouse.controller

import com.midstane.lighthouse.dependency.LightHouseScope
import com.midstane.lighthouse.service.SmtpService
import com.midstane.lighthouse.service.SmtpTestRequest
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveNullable
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post

@ContributesIntoSet(LightHouseScope::class, binding<Controller>())
@Inject
class SmtpController(
    private val smtpService: SmtpService,
) : Controller {
    override fun registerRoutes(routing: Routing) {
        routing.post("/smtp/test") {
            handleTestSmtp(call)
        }
    }

    private suspend fun handleTestSmtp(call: ApplicationCall) {
        val request = call.receiveNullable<SmtpTestRequest>() ?: SmtpTestRequest()
        call.ok(smtpService.testSmtp(request))
    }
}
