package com.contentplatform.auth.unit.service

import com.contentplatform.auth.configuration.AuthProperties
import com.contentplatform.auth.service.EmailService
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

@Tag("unit")
class EmailServiceTest {

    private val mailSender = mockk<JavaMailSender>(relaxed = true)
    private val authProperties = AuthProperties().apply {
        frontendUrl = "http://localhost:3000"
    }

    private val emailService = EmailService(mailSender, authProperties)

    @Nested
    inner class SendVerificationEmail {

        @Test
        fun `should send email with correct recipient`() {
            val recipient = "user@example.com"
            val token = "verification-token-123"

            emailService.sendVerificationEmail(recipient, token)

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }
            assertThat(messageSlot.captured.to).containsExactly(recipient)
        }

        @Test
        fun `should send email with verification subject`() {
            emailService.sendVerificationEmail("user@example.com", "token-abc")

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }
            assertThat(messageSlot.captured.subject).isEqualTo("Verify your email")
        }

        @Test
        fun `should include verification link with token in body`() {
            val token = "my-verification-token"

            emailService.sendVerificationEmail("user@example.com", token)

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }
            assertThat(messageSlot.captured.text)
                .contains("http://localhost:3000/verify?token=my-verification-token")
        }

        @Test
        fun `should use frontend URL from properties in verification link`() {
            val customProperties = AuthProperties().apply {
                frontendUrl = "https://app.example.com"
            }
            val service = EmailService(mailSender, customProperties)

            service.sendVerificationEmail("user@example.com", "tok")

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }
            assertThat(messageSlot.captured.text)
                .contains("https://app.example.com/verify?token=tok")
        }
    }

    @Nested
    inner class SendPasswordResetEmail {

        @Test
        fun `should send email with correct recipient`() {
            val recipient = "reset@example.com"
            val token = "reset-token-456"

            emailService.sendPasswordResetEmail(recipient, token)

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }
            assertThat(messageSlot.captured.to).containsExactly(recipient)
        }

        @Test
        fun `should send email with password reset subject`() {
            emailService.sendPasswordResetEmail("user@example.com", "token-xyz")

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }
            assertThat(messageSlot.captured.subject).isEqualTo("Password Reset Request")
        }

        @Test
        fun `should include reset link with token in body`() {
            val token = "my-reset-token"

            emailService.sendPasswordResetEmail("user@example.com", token)

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }
            assertThat(messageSlot.captured.text)
                .contains("http://localhost:3000/reset-password?token=my-reset-token")
        }

        @Test
        fun `should use frontend URL from properties in reset link`() {
            val customProperties = AuthProperties().apply {
                frontendUrl = "https://production.example.com"
            }
            val service = EmailService(mailSender, customProperties)

            service.sendPasswordResetEmail("user@example.com", "tok")

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }
            assertThat(messageSlot.captured.text)
                .contains("https://production.example.com/reset-password?token=tok")
        }
    }
}
