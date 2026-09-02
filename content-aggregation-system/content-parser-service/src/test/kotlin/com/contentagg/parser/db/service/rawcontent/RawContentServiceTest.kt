package com.contentagg.parser.db.service.rawcontent

import com.contentagg.parser.db.repository.rawcontent.RawContentRepository
import com.contentagg.parser.db.repository.rawcontent.model.RawContent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RawContentServiceTest {

    private val rawContentRepository: RawContentRepository = mockk(relaxed = true)
    private val duplicateResolver: RawContentDuplicateResolver = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private lateinit var rawContentService: RawContentService

    @BeforeEach
    fun setUp() {
        rawContentService = RawContentService(
            rawContentRepository = rawContentRepository,
            duplicateResolver = duplicateResolver,
            objectMapper = objectMapper,
            maxRawDataBytes = 10_485_760L,
            truncateContentBytes = 2_097_152,
        )
    }

    @Test
    fun testSaveRawContent_DelegatesToResolver() {
        val id = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val savedContent = buildSavedContent(id, sourceId)

        every { duplicateResolver.insertIsolated(any()) } returns savedContent

        val result = rawContentService.saveRawContent(
            externalId = "ext-1",
            sourceId = sourceId,
            sourceType = "HABR",
            rawData = "{}",
            rawMedia = null,
            downloadedMedia = null,
            processingStatus = "PENDING",
            receivedAt = LocalDateTime.now(),
        )

        verify(exactly = 1) { duplicateResolver.insertIsolated(any()) }
        assert(result == id)
    }

    @Test
    fun testSaveRawContent_NewInstanceHasDefaultFlags() {
        val id = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val entitySlot = slot<RawContent>()
        val savedContent = buildSavedContent(id, sourceId)

        every { duplicateResolver.insertIsolated(capture(entitySlot)) } returns savedContent

        rawContentService.saveRawContent(
            externalId = "ext-2",
            sourceId = sourceId,
            sourceType = "VCRU",
            rawData = "{}",
            rawMedia = null,
            downloadedMedia = null,
            processingStatus = "PENDING",
            receivedAt = LocalDateTime.now(),
        )

        val captured = entitySlot.captured
        assertFalse(captured.isProcessedByDedup)
        assertFalse(captured.isPublished)
    }

    private fun buildSavedContent(id: UUID, sourceId: UUID): RawContent = RawContent(
        id = id,
        externalId = "ext-1",
        sourceId = sourceId,
        sourceType = "HABR",
        rawData = "{}",
        rawMedia = null,
        downloadedMedia = null,
        processingStatus = "PENDING",
        isProcessedByDedup = false,
        isPublished = false,
        receivedAt = LocalDateTime.now(),
        processedAt = null,
        errorMessage = null,
        retryCount = 0,
    )
}
