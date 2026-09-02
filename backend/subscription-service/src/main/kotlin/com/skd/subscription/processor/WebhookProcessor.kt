package com.skd.subscription.processor

import com.skd.subscription.db.service.WebhookService
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WebhookProcessor(
    private val webhookService: WebhookService,
    private val meterRegistry: MeterRegistry
) {

    companion object {
        private val log = LoggerFactory.getLogger(WebhookProcessor::class.java)
        private const val WEBHOOK_METRIC = "yookassa.webhook.count"
        private const val EVENT_TAG = "event"
    }

    init {
        listOf("payment.succeeded", "payment.canceled", "payment.waiting_for_capture", "refund.succeeded", "unknown").forEach { event ->
            meterRegistry.counter(WEBHOOK_METRIC, EVENT_TAG, event)
        }
    }

    fun processWebhook(eventId: String, eventType: String, paymentExternalId: String) {
        log.debug("Processing webhook eventId={}, eventType={}", eventId, eventType)
        meterRegistry.counter(WEBHOOK_METRIC, EVENT_TAG, eventType.ifBlank { "unknown" }).increment()
        webhookService.processWebhook(eventId, eventType, paymentExternalId)
    }
}
