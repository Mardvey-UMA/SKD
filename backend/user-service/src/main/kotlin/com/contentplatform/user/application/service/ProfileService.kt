package com.contentplatform.user.application.service

import com.contentplatform.user.application.exception.NotFoundException
import com.contentplatform.user.db.repository.ProfileRepository
import com.contentplatform.user.db.repository.model.ProfileEntity
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProfileService(
    private val profileRepository: ProfileRepository,
    private val outboxService: OutboxService,
    private val meterRegistry: MeterRegistry
) {

    private val onboardingCounter: Counter = Counter.builder("user_onboarding_count")
        .description("Number of successful onboarding completions")
        .register(meterRegistry)

    companion object {
        private val log = LoggerFactory.getLogger(ProfileService::class.java)
    }

    @Transactional
    fun createProfileFromRegistration(userId: UUID, email: String) {
        log.info("Creating profile from registration for userId={}", userId)
        profileRepository.saveIgnoreConflict(
            id = userId,
            email = email,
            displayName = null,
            avatarUrl = null,
            subscriptionTier = "free",
            onboardingCompleted = false,
            categories = null
        )
        outboxService.createUserCreatedEvent(userId, email)
    }

    fun getProfile(userId: UUID): ProfileEntity {
        return profileRepository.findById(userId).orElseThrow {
            NotFoundException("Profile not found for userId=$userId")
        }
    }

    fun updateProfile(userId: UUID, displayName: String?, avatarUrl: String?): ProfileEntity {
        profileRepository.updateProfile(userId, displayName, avatarUrl)
        return profileRepository.findById(userId).orElseThrow {
            NotFoundException("Profile not found for userId=$userId")
        }
    }

    fun updateSubscriptionTier(userId: UUID, tier: String) {
        log.info("Updating subscription tier for userId={} to tier={}", userId, tier)
        profileRepository.updateSubscriptionTier(userId, tier)
    }

    fun completeOnboarding(userId: UUID, categories: List<String>) {
        val categoriesJson = categories.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        log.info("Completing onboarding for userId={}", userId)
        profileRepository.updateOnboarding(userId, true, categoriesJson)
        onboardingCounter.increment()
    }
}
