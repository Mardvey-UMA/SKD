package com.contentplatform.feed.unit.service

import com.contentplatform.feed.application.FeedItemLog
import com.contentplatform.feed.application.FeedLogService
import com.contentplatform.feed.application.FeedLogServiceImpl
import com.contentplatform.feed.application.FeedLoggingMapper
import com.contentplatform.feed.application.FeedLogWriter
import com.contentplatform.feed.application.FeedRequestLog
import com.contentplatform.feed.db.entity.FeedItemEntity
import com.contentplatform.feed.db.entity.FeedRequestEntity
import com.contentplatform.feed.db.repository.FeedItemRepository
import com.contentplatform.feed.db.repository.FeedRequestRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.TaskExecutor
import java.time.Instant
import java.util.UUID
import java.util.concurrent.RejectedExecutionException

@Tag("unit")
class FeedLogServiceImplTest {

    private val feedRequestRepository = mockk<FeedRequestRepository>()
    private val feedItemRepository = mockk<FeedItemRepository>()
    private val feedLoggingMapper = mockk<FeedLoggingMapper>()
    private val syncExecutor: TaskExecutor = SyncTaskExecutor()

    private val feedLogWriter = FeedLogWriter(
        feedRequestRepository = feedRequestRepository,
        feedItemRepository = feedItemRepository,
        feedLoggingMapper = feedLoggingMapper
    )

    private val feedLogService = FeedLogServiceImpl(
        feedLogWriter = feedLogWriter,
        loggingExecutor = syncExecutor
    )

    private fun baseRequestLog() = FeedRequestLog(
        requestId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        requestedAt = Instant.now(),
        pageNumber = 0,
        source = "personalized",
        countRequested = 20,
        countReturned = 20,
        latencyMs = 150,
        latencyBreakdown = mapOf("rec_system_ms" to 95L),
        featureFlags = null,
        abBucket = 0,
        appVersion = "1.0.0",
        deviceType = "android"
    )

    private fun baseItemLog(requestId: UUID) = FeedItemLog(
        requestId = requestId,
        position = 0,
        contentId = UUID.randomUUID(),
        rawContentId = null,
        finalScore = 0.75f,
        scoringComponents = null,
        rerankScore = null,
        filteredOutBy = null
    )

    private fun makeFeedRequestEntity(log: FeedRequestLog) = FeedRequestEntity(
        requestId = log.requestId,
        userId = log.userId,
        requestedAt = log.requestedAt,
        pageNumber = log.pageNumber,
        source = log.source,
        countRequested = log.countRequested,
        countReturned = log.countReturned,
        latencyMs = log.latencyMs,
        latencyBreakdown = null,
        featureFlags = null,
        abBucket = log.abBucket,
        appVersion = log.appVersion,
        deviceType = log.deviceType
    )

    private fun makeFeedItemEntity(log: FeedItemLog) = FeedItemEntity(
        requestId = log.requestId,
        position = log.position.toShort(),
        contentId = log.contentId,
        rawContentId = null,
        finalScore = log.finalScore,
        scoringComponents = null,
        rerankScore = null,
        filteredOutBy = null
    )

    @Test
    fun `logFeedRequest calls feedRequestRepository save and feedItemRepository saveAll`() {
        val log = baseRequestLog()
        val itemLog = baseItemLog(log.requestId)
        val requestEntity = makeFeedRequestEntity(log)
        val itemEntity = makeFeedItemEntity(itemLog)

        every { feedLoggingMapper.toEntity(log) } returns requestEntity
        every { feedLoggingMapper.toEntity(itemLog) } returns itemEntity
        every { feedRequestRepository.save(requestEntity) } just runs
        every { feedItemRepository.saveAll(listOf(itemEntity)) } just runs

        feedLogService.logFeedRequest(log, listOf(itemLog))

        verify { feedRequestRepository.save(requestEntity) }
        verify { feedItemRepository.saveAll(listOf(itemEntity)) }
    }

    @Test
    fun `logFeedRequest swallows exception from repository - does not propagate to caller`() {
        val log = baseRequestLog()
        val itemLog = baseItemLog(log.requestId)
        val requestEntity = makeFeedRequestEntity(log)

        every { feedLoggingMapper.toEntity(log) } returns requestEntity
        every { feedLoggingMapper.toEntity(itemLog) } returns makeFeedItemEntity(itemLog)
        every { feedRequestRepository.save(any()) } throws RuntimeException("DB is down")

        // Must not throw
        feedLogService.logFeedRequest(log, listOf(itemLog))
    }

    @Test
    fun `logFeedRequest handles empty items list`() {
        val log = baseRequestLog()
        val requestEntity = makeFeedRequestEntity(log)

        every { feedLoggingMapper.toEntity(log) } returns requestEntity
        every { feedRequestRepository.save(requestEntity) } just runs
        every { feedItemRepository.saveAll(emptyList()) } just runs

        feedLogService.logFeedRequest(log, emptyList())

        verify { feedRequestRepository.save(requestEntity) }
        verify { feedItemRepository.saveAll(emptyList()) }
    }

    @Test
    fun `logFeedRequest with queue overflow does not throw`() {
        val rejectingExecutor = TaskExecutor { throw RejectedExecutionException("Queue full") }
        val serviceWithFullQueue = FeedLogServiceImpl(
            feedLogWriter = feedLogWriter,
            loggingExecutor = rejectingExecutor
        )

        val log = baseRequestLog()
        val itemLog = baseItemLog(log.requestId)

        // Must not throw even when executor rejects
        serviceWithFullQueue.logFeedRequest(log, listOf(itemLog))
    }
}
