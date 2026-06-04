package com.midstane.lighthouse.service

import dev.zacsweers.metro.Inject
import io.ktor.server.config.ApplicationConfig
import jakarta.mail.MessagingException
import jakarta.mail.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.Properties

@Inject
class SmtpService(
    private val config: ApplicationConfig,
) {
    suspend fun testSmtp(request: SmtpTestRequest = SmtpTestRequest()): SmtpTestResult = withContext(Dispatchers.IO) {
        val smtpConfig = SmtpConfig.from(config, request)
        val properties = Properties().apply {
            put("mail.smtp.host", smtpConfig.host)
            put("mail.smtp.port", smtpConfig.port.toString())
            put("mail.smtp.connectiontimeout", smtpConfig.timeoutMillis.toString())
            put("mail.smtp.timeout", smtpConfig.timeoutMillis.toString())
            put("mail.smtp.writetimeout", smtpConfig.timeoutMillis.toString())
            put("mail.smtp.auth", smtpConfig.hasCredentials.toString())
            put("mail.smtp.starttls.enable", smtpConfig.startTls.toString())
            put("mail.smtp.ssl.enable", smtpConfig.ssl.toString())
        }

        val session = Session.getInstance(properties)
        val transport = session.getTransport("smtp")
        try {
            transport.connect(
                smtpConfig.host,
                smtpConfig.port,
                smtpConfig.username.takeIf { it.isNotBlank() },
                smtpConfig.password.takeIf { it.isNotBlank() },
            )
            SmtpTestResult(
                success = true,
                message = "SMTP connection succeeded",
                host = smtpConfig.host,
                port = smtpConfig.port,
                authenticated = smtpConfig.hasCredentials,
                startTls = smtpConfig.startTls,
                ssl = smtpConfig.ssl,
            )
        } catch (exception: MessagingException) {
            SmtpTestResult(
                success = false,
                message = exception.message ?: "SMTP connection failed",
                host = smtpConfig.host,
                port = smtpConfig.port,
                authenticated = smtpConfig.hasCredentials,
                startTls = smtpConfig.startTls,
                ssl = smtpConfig.ssl,
            )
        } finally {
            runCatching { transport.close() }
        }
    }
}

@Serializable
data class SmtpTestRequest(
    val host: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val password: String? = null,
    val startTls: Boolean? = null,
    val ssl: Boolean? = null,
    val timeoutMillis: Int? = null,
)

@Serializable
data class SmtpTestResult(
    val success: Boolean,
    val message: String,
    val host: String,
    val port: Int,
    val authenticated: Boolean,
    val startTls: Boolean,
    val ssl: Boolean,
)

private data class SmtpConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val startTls: Boolean,
    val ssl: Boolean,
    val timeoutMillis: Int,
) {
    val hasCredentials: Boolean = username.isNotBlank() && password.isNotBlank()

    companion object {
        fun from(config: ApplicationConfig, request: SmtpTestRequest): SmtpConfig {
            return SmtpConfig(
                host = request.host ?: config.requiredString("smtp.host"),
                port = request.port ?: config.requiredInt("smtp.port"),
                username = request.username ?: config.requiredString("smtp.username"),
                password = request.password ?: config.requiredString("smtp.password"),
                startTls = request.startTls ?: config.requiredBoolean("smtp.startTls"),
                ssl = request.ssl ?: config.requiredBoolean("smtp.ssl"),
                timeoutMillis = request.timeoutMillis ?: config.requiredInt("smtp.timeoutMillis"),
            )
        }
    }
}

private fun ApplicationConfig.requiredString(path: String): String = property(path).getString()

private fun ApplicationConfig.requiredInt(path: String): Int = property(path).getString().toInt()

private fun ApplicationConfig.requiredBoolean(path: String): Boolean = property(path).getString().toBoolean()
