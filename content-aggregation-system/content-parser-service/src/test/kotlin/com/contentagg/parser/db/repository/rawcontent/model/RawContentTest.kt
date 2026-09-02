package com.contentagg.parser.db.repository.rawcontent.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RawContentTest {

    private val sourceId = UUID.randomUUID()
    private val receivedAt = LocalDateTime.now()

    @Test
    fun testNewInstance_SetsDefaultFlags() {
        val content = RawContent.newInstance(
            externalId = "ext-1",
            sourceId = sourceId,
            sourceType = "HABR",
            rawData = "{}",
            rawMedia = null,
            downloadedMedia = null,
            processingStatus = "PENDING",
            receivedAt = receivedAt,
        )

        assertFalse(content.isProcessedByDedup)
        assertFalse(content.isPublished)
    }

    @Test
    fun testNewInstance_SetsNullId() {
        val content = RawContent.newInstance(
            externalId = "ext-1",
            sourceId = sourceId,
            sourceType = "HABR",
            rawData = "{}",
            rawMedia = null,
            downloadedMedia = null,
            processingStatus = "PENDING",
            receivedAt = receivedAt,
        )

        assertNull(content.id)
    }

    @Test
    fun testConstructor_AllFields() {
        val id = UUID.randomUUID()
        val content = RawContent(
            id = id,
            externalId = "ext-42",
            sourceId = sourceId,
            sourceType = "VCRU",
            rawData = """{"title":"test"}""",
            rawMedia = null,
            downloadedMedia = null,
            processingStatus = "COMPLETED",
            isProcessedByDedup = true,
            isPublished = false,
            receivedAt = receivedAt,
            processedAt = null,
            errorMessage = null,
            retryCount = 0,
        )

        assertEquals(id, content.id)
        assertEquals("ext-42", content.externalId)
        assertEquals(true, content.isProcessedByDedup)
        assertEquals(false, content.isPublished)
    }

    @Test
    fun testWithProcessingStatus_PreservesFlags() {
        val content = RawContent.newInstance(
            externalId = "ext-1",
            sourceId = sourceId,
            sourceType = "HABR",
            rawData = "{}",
            rawMedia = null,
            downloadedMedia = null,
            processingStatus = "PENDING",
            receivedAt = receivedAt,
        ).copy(isProcessedByDedup = true, isPublished = false)

        val updated = content.withProcessingStatus("COMPLETED")

        assertEquals(true, updated.isProcessedByDedup)
        assertEquals(false, updated.isPublished)
        assertEquals("COMPLETED", updated.processingStatus)
    }

    @Test
    fun testWithFailed_PreservesFlags() {
        val content = RawContent.newInstance(
            externalId = "ext-1",
            sourceId = sourceId,
            sourceType = "HABR",
            rawData = "{}",
            rawMedia = null,
            downloadedMedia = null,
            processingStatus = "PENDING",
            receivedAt = receivedAt,
        ).copy(isProcessedByDedup = true, isPublished = true)

        val failed = content.withFailed("some error")

        assertEquals(true, failed.isProcessedByDedup)
        assertEquals(true, failed.isPublished)
        assertEquals("FAILED", failed.processingStatus)
    }
}
