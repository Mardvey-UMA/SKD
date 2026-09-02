package com.skd.subscription.job

import com.skd.subscription.configuration.ReconcileProperties
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.service.PaymentReconciler
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
@DisallowConcurrentExecution
class ReconcilePendingPaymentsJob(
    private val paymentRepository: PaymentRepository,
    private val paymentReconciler: PaymentReconciler,
    private val reconcileProperties: ReconcileProperties
) : Job {

    companion object {
        private val log = LoggerFactory.getLogger(ReconcilePendingPaymentsJob::class.java)
    }

    override fun execute(context: JobExecutionContext) {
        val after = Instant.now().minus(reconcileProperties.scheduled.pendingWindowMinutes, ChronoUnit.MINUTES)
        val payments = paymentRepository.findAllPendingCreatedAfter(after)

        if (payments.isEmpty()) {
            log.debug("No pending payments found within the scheduled reconcile window")
            return
        }

        log.info("Scheduled reconcile: found {} pending payments to process", payments.size)

        for (payment in payments) {
            try {
                paymentReconciler.fetchAndApply(payment, PaymentReconciler.Trigger.SCHEDULED)
            } catch (e: Exception) {
                log.warn("Scheduled reconcile failed for paymentId={}: {}", payment.id, e.message)
            }
        }
    }
}
