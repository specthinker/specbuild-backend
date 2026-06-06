package com.specthinker.auth

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Service
@ConditionalOnProperty(prefix = "specthinker.auth.mail", name = ["mode"], havingValue = "resend")
class ResendMailer(
    private val props: AuthProperties,
    private val http: HttpClient,
) : MailService {
    private val log = LoggerFactory.getLogger(ResendMailer::class.java)

    override fun sendMagicLink(to: String, link: String) {
        if (props.mail.resendApiKey.isBlank()) {
            throw IllegalStateException("RESEND_API_KEY is not configured")
        }
        val body = buildJson(to, link)
        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.resend.com/emails"))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer ${props.mail.resendApiKey}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) {
            log.warn("Resend send failed: {} {}", res.statusCode(), res.body())
            throw IllegalStateException("Resend send failed: ${res.statusCode()}")
        }
    }

    private fun buildJson(to: String, link: String): String {
        val from = props.mail.from
        val subject = "Your Spec Builder sign-in link"
        val html = "<p>Click to sign in to Spec Builder:</p>" +
            "<p><a href=\"$link\">Sign in</a></p>" +
            "<p>This link expires in 15 minutes. If you didn't ask for it, ignore this email.</p>"
        val text = "Sign in to Spec Builder: $link\n\nExpires in 15 minutes."
        return """{"from":${jsonString(from)},"to":[${jsonString(to)}],"subject":${jsonString(subject)},"html":${jsonString(html)},"text":${jsonString(text)}}"""
    }

    private fun jsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                else -> if (c.code < 0x20) {
                    sb.append("\\u%04x".format(c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}
