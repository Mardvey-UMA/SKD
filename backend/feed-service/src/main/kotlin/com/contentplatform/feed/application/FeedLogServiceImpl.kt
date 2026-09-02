package com.contentplatform.feed.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import java.util.concurrent.RejectedExecutionException

@Service
class FeedLogServiceImpl(
    private val feedLogWriter: FeedLogWriter,
    @Qualifier("feedLoggingExecutor") private val loggingExecutor: TaskExecutor
) : FeedLogService {

    companion object {
        private val logger = LoggerFactory.getLogger(FeedLogServiceImpl::class.java)
    }

    override fun logFeedRequest(log: FeedRequestLog, items: List<FeedItemLog>) {
        // Capture values before dispatch — lambdas must not capture Spring-managed mutable state
        val capturedRequest = log
        val capturedItems = items.toList()

        try {
            loggingExecutor.execute {
                try {
                    feedLogWriter.write(capturedRequest, capturedItems)
                } catch (e: Exception) {
                    logger.warn(
                        "Feed logging write failed for requestId={}: {}",
                        capturedRequest.requestId,
                        e.message,
                        e
                    )
                }
            }
        } catch (e: RejectedExecutionException) {
            logger.warn(
                "Feed logging queue full — dropping log for requestId={}: {}",
                capturedRequest.requestId,
                e.message
            )
        }
    }
}
