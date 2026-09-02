package com.contentplatform.user.unit.service

import com.contentplatform.user.application.service.OutboxService
import com.contentplatform.user.application.service.ProfileService
import com.contentplatform.user.db.repository.ProfileRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("unit")
class OnboardingUseCaseTest {

    private val profileRepository = mockk<ProfileRepository>()
    private val outboxService = mockk<OutboxService>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()
    private val profileService = ProfileService(profileRepository, outboxService, meterRegistry)

    @Test
    fun `successful onboarding increments user_onboarding_count counter`() {
        val userId = UUID.randomUUID()
        every { profileRepository.updateOnboarding(any(), any(), any()) } returns Unit

        profileService.completeOnboarding(userId, listOf("tech", "science", "art"))

        assertThat(meterRegistry.counter("user_onboarding_count").count()).isEqualTo(1.0)
    }
}
