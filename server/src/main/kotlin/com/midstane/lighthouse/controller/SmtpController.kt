package com.midstane.lighthouse.controller

import com.midstane.lighthouse.dependency.LightHouseScope
import com.midstane.lighthouse.service.SmtpService
import com.midstane.lighthouse.service.SmtpTestRequest
import com.midstane.lighthouse.service.SmtpTestResult
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

@ContributesIntoSet(LightHouseScope::class, binding<Controller>())
@Inject
class SmtpController(
    private val smtpService: SmtpService,
) : Controller {
    override val baseRoute: String = "/smtp"

    override fun registerRoutes(routes: LighthouseRouting) {
        routes.post<SmtpTestRequest, SmtpTestResult>("/test") { request ->
            smtpService.testSmtp(request)
        }
    }
}
