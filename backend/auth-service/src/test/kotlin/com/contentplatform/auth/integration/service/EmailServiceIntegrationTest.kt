package com.contentplatform.auth.integration.service

import com.contentplatform.auth.integration.IntegrationTestBase
import com.contentplatform.auth.service.EmailService
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

import com.ninjasquad.springmockk.MockkBean

@Tag("integration")
class EmailServiceIntegrationTest : IntegrationTestBase() {

    @MockkBean(relaxed = true)
    lateinit var mailSender: JavaMailSender

    @Autowired
    lateinit var emailService: EmailService

    @Nested
    inner class SendVerificationEmail {

        @Test
        fun `should send verification email with correct recipient and subject`() {
            val recipient = "integration-test@example.com"
            val token = "verify-integration-token"

            emailService.sendVerificationEmail(recipient, token)

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }

            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(messageSlot.captured.to).containsExactly(recipient)
                softly.assertThat(messageSlot.captured.subject).isEqualTo("Verify your email")
            }
        }

        @Test
        fun `should include verification link using configured frontend URL`() {
            emailService.sendVerificationEmail("user@example.com", "abc-token")

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }

            assertThat(messageSlot.captured.text)
                .contains("/verify?token=abc-token")
        }

        @Test
        fun `should construct full verification URL from frontend-url property`() {
            emailService.sendVerificationEmail("user@example.com", "full-url-token")

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }

            // application-test.yml sets auth.frontend-url=http://localhost:3000
            assertThat(messageSlot.captured.text)
                .contains("http://localhost:3000/verify?token=full-url-token")
        }
    }

    @Nested
    inner class SendPasswordResetEmail {

        @Test
        fun `should send reset email with correct recipient and subject`() {
            val recipient = "reset-integration@example.com"
            val token = "reset-integration-token"

            emailService.sendPasswordResetEmail(recipient, token)

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }

            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(messageSlot.captured.to).containsExactly(recipient)
                softly.assertThat(messageSlot.captured.subject).isEqualTo("Password Reset Request")
            }
        }

        @Test
        fun `should include reset link using configured frontend URL`() {
            emailService.sendPasswordResetEmail("user@example.com", "reset-abc")

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }

            assertThat(messageSlot.captured.text)
                .contains("/reset-password?token=reset-abc")
        }

        @Test
        fun `should construct full reset URL from frontend-url property`() {
            emailService.sendPasswordResetEmail("user@example.com", "full-reset-token")

            val messageSlot = slot<SimpleMailMessage>()
            verify(exactly = 1) { mailSender.send(capture(messageSlot)) }

            // application-test.yml sets auth.frontend-url=http://localhost:3000
            assertThat(messageSlot.captured.text)
                .contains("http://localhost:3000/reset-password?token=full-reset-token")
        }
    }
}
