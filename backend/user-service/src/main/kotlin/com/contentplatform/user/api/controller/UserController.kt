package com.contentplatform.user.api.controller

import com.contentplatform.user.api.dto.OnboardingRequest
import com.contentplatform.user.api.dto.OnboardingResponse
import com.contentplatform.user.api.dto.ProfileResponse
import com.contentplatform.user.api.dto.UpdateProfileRequest
import com.contentplatform.user.application.exception.NotFoundException
import com.contentplatform.user.application.service.ProfileService
import com.contentplatform.user.db.repository.model.ProfileEntity
import com.contentplatform.user.infrastructure.client.RecSystemClientPort
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/users")
class UserController(
    private val profileService: ProfileService,
    @Lazy private val recSystemClient: RecSystemClientPort,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private val log = LoggerFactory.getLogger(UserController::class.java)
    }

    @GetMapping("/me")
    fun getProfile(request: HttpServletRequest): ProfileResponse {
        val userId = extractUserId(request)
        log.info("GET /api/users/me userId={}", userId)
        val profile = waitForProfile(userId)
        return profile.toResponse()
    }

    @PutMapping("/me")
    fun updateProfile(
        @RequestBody updateRequest: UpdateProfileRequest,
        request: HttpServletRequest
    ): ProfileResponse {
        val userId = extractUserId(request)
        log.info("PUT /api/users/me userId={}", userId)
        val updated = profileService.updateProfile(userId, updateRequest.displayName, updateRequest.avatarUrl)
        return updated.toResponse()
    }

    @PostMapping("/me/onboarding")
    fun completeOnboarding(
        @RequestBody onboardingRequest: OnboardingRequest,
        request: HttpServletRequest
    ): OnboardingResponse {
        val userId = extractUserId(request)
        if (onboardingRequest.categories.size < 3) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "At least 3 categories must be selected")
        }
        log.info("POST /api/users/me/onboarding userId={}", userId)
        val profile = waitForProfile(userId)

        // Idempotent: return current state if already onboarded (not 409)
        if (profile.onboardingCompleted) {
            return OnboardingResponse(
                onboardingCompleted = true,
                message = "Already onboarded",
                categories = parseCategories(profile.categories),
            )
        }

        // Call rec-system FIRST — only update DB after success
        recSystemClient.postOnboarding(userId, onboardingRequest.categories, onboardingRequest.sourceContentIds)
        profileService.completeOnboarding(userId, onboardingRequest.categories)

        return OnboardingResponse(
            onboardingCompleted = true,
            message = "Preferences saved. Your feed is being personalized.",
            categories = onboardingRequest.categories,
        )
    }

    private fun parseCategories(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            objectMapper.readValue(json, object : TypeReference<List<String>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }

    @GetMapping("/me/categories", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCategories(request: HttpServletRequest): ResponseEntity<String> {
        val userId = extractUserId(request)
        log.info("GET /api/users/me/categories userId={}", userId)
        val body = recSystemClient.getCategories("ru")
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
    }

    /**
     * Wait up to 10 seconds for the user profile to be materialised by the
     * `user.registered` Kafka consumer after registration. Mirrors the
     * `ensure_user_profile_exists` safety-net pattern used by rec-system: any
     * authenticated request for a user is guaranteed to either find the profile
     * or time out with 404 (never race against the async Kafka propagation chain
     * that can take up to ~10s on a fresh registration).
     */
    private fun waitForProfile(userId: UUID): ProfileEntity {
        val deadline = System.currentTimeMillis() + 10_000L
        var lastException: NotFoundException? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                return profileService.getProfile(userId)
            } catch (e: NotFoundException) {
                lastException = e
                try {
                    Thread.sleep(300L)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
            }
        }
        log.warn("Profile not materialised within 10s for userId={} — Kafka consumer lag suspected", userId)
        throw lastException ?: NotFoundException("Profile not found for userId=$userId")
    }

    private fun extractUserId(request: HttpServletRequest): UUID {
        val raw = request.getAttribute("X-User-Id") as String
        return UUID.fromString(raw)
    }

    private fun ProfileEntity.toResponse(): ProfileResponse = ProfileResponse(
        id = id,
        email = email,
        displayName = displayName,
        avatarUrl = avatarUrl,
        subscriptionTier = subscriptionTier,
        onboardingCompleted = onboardingCompleted,
        createdAt = createdAt
    )
}
