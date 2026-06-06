package com.specthinker.auth

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    prefix = "specthinker.auth.mail",
    name = ["mode"],
    havingValue = "logging",
    matchIfMissing = true,
)
class LoggingMailer : MailService {
    private val log = LoggerFactory.getLogger(LoggingMailer::class.java)

    override fun sendMagicLink(to: String, link: String) {
        log.info("Magic link for {}: {}", to, link)
    }
}
