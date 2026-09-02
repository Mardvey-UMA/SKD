package com.skd.subscription.presentation.controller

import com.skd.subscription.processor.WebhookProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhook")
class YookassaWebhookController(
    private val webhookProcessor: WebhookProcessor
) {

    companion object {
        private val log = LoggerFactory.getLogger(YookassaWebhookController::class.java)
    }

    @PostMapping("/yookassa")
    fun handleWebhook(
        @RequestBody body: Map<String, Any>
    ): ResponseEntity<Map<String, String>> {
        val event = body["event"] as? String ?: ""
        @Suppress("UNCHECKED_CAST")
        val obj = body["object"] as? Map<String, Any> ?: emptyMap()
        val paymentId = obj["id"] as? String ?: ""

        // Derive a deterministic event ID from event type + payment ID for idempotency
        val eventId = "$event:$paymentId"

        log.info("Received YooKassa webhook event={}, paymentId={}", event, paymentId)
        webhookProcessor.processWebhook(eventId, event, paymentId)

        return ResponseEntity.ok(mapOf("status" to "ok"))
    }
}
