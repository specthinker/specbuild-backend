package com.specthinker.auth

interface MailService {
    fun sendMagicLink(to: String, link: String)
}
