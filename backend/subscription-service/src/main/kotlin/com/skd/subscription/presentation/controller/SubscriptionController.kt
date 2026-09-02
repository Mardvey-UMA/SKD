package com.skd.subscription.presentation.controller

import com.skd.subscription.processor.SubscriptionProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/subscription")
class SubscriptionController(
    private val subscriptionProcessor: SubscriptionProcessor
) {

    companion object {
        private val log = LoggerFactory.getLogger(SubscriptionController::class.java)
    }

    @GetMapping("/status")
    fun getStatus(
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<Map<String, Any?>> {
        val result = subscriptionProcessor.getStatus(UUID.fromString(userId))
        return ResponseEntity.ok(
            mapOf(
                "tier" to result.tier,
                "status" to result.status,
                "planId" to result.planId,
                "expiresAt" to result.expiresAt,
                "autoRenew" to result.autoRenew
            )
        )
    }

    @PostMapping("/checkout")
    fun checkout(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody body: Map<String, String>
    ): ResponseEntity<Map<String, Any?>> {
        val plan = body["plan"] ?: throw IllegalArgumentException("plan is required")
        val result = subscriptionProcessor.checkout(UUID.fromString(userId), plan)
        return ResponseEntity.ok(
            mapOf(
                "paymentId" to result.paymentId,
                "confirmationUrl" to result.confirmationUrl
            )
        )
    }

    @PostMapping("/cancel")
    fun cancel(
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<Map<String, Any?>> {
        subscriptionProcessor.cancel(UUID.fromString(userId))
        return ResponseEntity.ok(mapOf("status" to "cancelled"))
    }
}
