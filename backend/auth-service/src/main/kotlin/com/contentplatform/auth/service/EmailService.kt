package com.contentplatform.auth.service

import com.contentplatform.auth.configuration.AuthProperties
import org.slf4j.LoggerFactory
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    private val authProperties: AuthProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(EmailService::class.java)
    }

    fun sendVerificationEmail(to: String, token: String) {
        val link = "${authProperties.frontendUrl}/verify?token=$token"
        val message = SimpleMailMessage().apply {
            setTo(to)
            subject = "Verify your email"
            text = "Please verify your email by clicking the link: $link"
        }
        mailSender.send(message)
        log.info("Sent verification email to={}", to)
    }

    fun sendVerificationCode(to: String, code: String) {
        val message = SimpleMailMessage().apply {
            setTo(to)
            subject = "Ваш код подтверждения: $code"
            text = """
                Здравствуйте!

                Ваш код подтверждения электронной почты: $code

                Введите этот код в приложении. Код действителен в течение ${authProperties.verificationCodeTtlSeconds / 60} минут.

                Если вы не регистрировались в нашем сервисе — просто проигнорируйте это письмо.
            """.trimIndent()
        }
        mailSender.send(message)
        log.info("Sent verification code email to={}", to)
    }

    fun sendPasswordResetEmail(to: String, token: String) {
        val link = "${authProperties.frontendUrl}/reset-password?token=$token"
        val message = SimpleMailMessage().apply {
            setTo(to)
            subject = "Password Reset Request"
            text = "Reset your password by clicking the link: $link"
        }
        mailSender.send(message)
        log.info("Sent password reset email to={}", to)
    }
}
