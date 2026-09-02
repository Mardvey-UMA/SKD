package com.contentagg.parser.integration.s3

import com.contentagg.parser.exception.TelegramParseException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.nio.file.Files
import java.nio.file.Path

class TelegramMediaUploadServiceTest {

    private lateinit var service: TelegramMediaUploadService
    private val s3Client: S3Client = mockk()
    private val s3BucketName = "test-bucket"
    private val s3PublicUrl = "https://minio.example.com"

    private val tempFiles = mutableListOf<Path>()

    @BeforeEach
    fun setUp() {
        service = TelegramMediaUploadService(
            s3Client = s3Client,
            s3BucketName = s3BucketName,
            s3PublicUrl = s3PublicUrl,
        )
    }

    @AfterEach
    fun tearDown() {
        tempFiles.forEach { Files.deleteIfExists(it) }
        tempFiles.clear()
    }

    @Nested
    @DisplayName("uploadFromLocalPath()")
    inner class UploadFromLocalPathTests {

        @Test
        @DisplayName("valid file — uploads to S3 and returns correct UploadResult")
        fun testUploadFromLocalPath_ValidFile_UploadsAndReturnsResult() {
            val tempFile = createTempFileWithContent("image content bytes")
            every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } returns PutObjectResponse.builder().build()

            val result = service.uploadFromLocalPath(
                localPath = tempFile.toString(),
                channelUsername = "durov",
                messageId = 123L,
                mediaType = "IMAGE",
                fileExtension = ".jpg",
            )

            assertTrue(result.s3Url.startsWith("/$s3BucketName/"), "s3Url should be a relative path starting with /$s3BucketName/")
            assertTrue(result.s3Key.contains("telegram/"))
            assertTrue(result.s3Key.contains("durov"))
            assertTrue(result.s3Key.contains("123_IMAGE"))
            assertEquals("image/jpeg", result.mimeType)
            assertTrue(result.fileSize > 0)
        }

        @Test
        @DisplayName("file not found — throws TelegramParseException without calling S3")
        fun testUploadFromLocalPath_FileNotFound_ThrowsTelegramParseException() {
            assertThrows<TelegramParseException> {
                service.uploadFromLocalPath(
                    localPath = "/nonexistent/path/file.jpg",
                    channelUsername = "channel",
                    messageId = 1L,
                    mediaType = "IMAGE",
                    fileExtension = ".jpg",
                )
            }

            verify(exactly = 0) { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) }
        }

        @Test
        @DisplayName("S3 putObject fails — throws TelegramParseException")
        fun testUploadFromLocalPath_S3Fails_ThrowsTelegramParseException() {
            val tempFile = createTempFileWithContent("video content")
            every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } throws
                RuntimeException("S3 connection refused")

            assertThrows<TelegramParseException> {
                service.uploadFromLocalPath(
                    localPath = tempFile.toString(),
                    channelUsername = "channel",
                    messageId = 456L,
                    mediaType = "VIDEO",
                    fileExtension = ".mp4",
                )
            }
        }

        @Test
        @DisplayName("upload succeeds — local file is deleted after upload")
        fun testUploadFromLocalPath_DeletesLocalFileAfterSuccess() {
            val tempFile = createTempFileWithContent("photo content")
            every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } returns PutObjectResponse.builder().build()

            service.uploadFromLocalPath(
                localPath = tempFile.toString(),
                channelUsername = "channel",
                messageId = 789L,
                mediaType = "IMAGE",
                fileExtension = ".png",
            )

            // Local file must be deleted after successful upload
            assertFalse(Files.exists(tempFile), "Local file should be deleted after successful upload")
            // Remove from cleanup list since it's already deleted
            tempFiles.remove(tempFile)
        }

        @Test
        @DisplayName("S3 upload fails — local file is still deleted in finally block")
        fun testUploadFromLocalPath_DeletesLocalFileAfterFailure() {
            val tempFile = createTempFileWithContent("document content")
            every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } throws
                RuntimeException("Network error")

            assertThrows<TelegramParseException> {
                service.uploadFromLocalPath(
                    localPath = tempFile.toString(),
                    channelUsername = "channel",
                    messageId = 111L,
                    mediaType = "DOCUMENT",
                    fileExtension = ".pdf",
                )
            }

            // Local file must be deleted even on failure
            assertFalse(Files.exists(tempFile), "Local file should be deleted even after upload failure")
            tempFiles.remove(tempFile)
        }

        @Test
        @DisplayName("uploads with correct bucket and content type set in PutObjectRequest")
        fun testUploadFromLocalPath_SetsCorrectBucketAndContentType() {
            val tempFile = createTempFileWithContent("audio content")
            val requestSlot = slot<PutObjectRequest>()
            every { s3Client.putObject(capture(requestSlot), any<RequestBody>()) } returns PutObjectResponse.builder().build()

            service.uploadFromLocalPath(
                localPath = tempFile.toString(),
                channelUsername = "channel",
                messageId = 222L,
                mediaType = "AUDIO",
                fileExtension = ".mp3",
            )

            assertEquals(s3BucketName, requestSlot.captured.bucket())
            assertEquals("audio/mpeg", requestSlot.captured.contentType())
            // Cleanup
            tempFiles.remove(tempFile)
        }
    }

    @Nested
    @DisplayName("guessMimeType()")
    inner class GuessMimeTypeTests {

        // guessMimeType is private — we test it indirectly through UploadResult.mimeType

        @Test
        @DisplayName("jpg extension — returns image/jpeg")
        fun testGuessMimeType_JpgExtension_ReturnsImageJpeg() {
            val tempFile = createTempFileWithContent("jpg content")
            every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } returns PutObjectResponse.builder().build()

            val result = service.uploadFromLocalPath(
                localPath = tempFile.toString(),
                channelUsername = "c",
                messageId = 1L,
                mediaType = "IMAGE",
                fileExtension = "jpg",
            )

            assertEquals("image/jpeg", result.mimeType)
            tempFiles.remove(tempFile)
        }

        @Test
        @DisplayName("mp4 extension — returns video/mp4")
        fun testGuessMimeType_Mp4Extension_ReturnsVideoMp4() {
            val tempFile = createTempFileWithContent("mp4 content")
            every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } returns PutObjectResponse.builder().build()

            val result = service.uploadFromLocalPath(
                localPath = tempFile.toString(),
                channelUsername = "c",
                messageId = 2L,
                mediaType = "VIDEO",
                fileExtension = ".mp4",
            )

            assertEquals("video/mp4", result.mimeType)
            tempFiles.remove(tempFile)
        }

        @Test
        @DisplayName("unknown extension — returns application/octet-stream")
        fun testGuessMimeType_UnknownExtension_ReturnsOctetStream() {
            val tempFile = createTempFileWithContent("binary content")
            every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } returns PutObjectResponse.builder().build()

            val result = service.uploadFromLocalPath(
                localPath = tempFile.toString(),
                channelUsername = "c",
                messageId = 3L,
                mediaType = "DOCUMENT",
                fileExtension = ".xyz",
            )

            assertEquals("application/octet-stream", result.mimeType)
            tempFiles.remove(tempFile)
        }
    }

    @Nested
    @DisplayName("generateS3Key()")
    inner class GenerateS3KeyTests {

        @Test
        @DisplayName("S3 key contains expected path segments: telegram/{date}/{channel}/{msgId}_{type}")
        fun testGenerateS3Key_ContainsExpectedSegments() {
            val tempFile = createTempFileWithContent("some content")
            every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } returns PutObjectResponse.builder().build()

            val result = service.uploadFromLocalPath(
                localPath = tempFile.toString(),
                channelUsername = "testchannel",
                messageId = 999L,
                mediaType = "IMAGE",
                fileExtension = ".jpg",
            )

            val key = result.s3Key
            assertTrue(key.startsWith("telegram/"), "Key should start with 'telegram/'")
            assertTrue(key.contains("/testchannel/"), "Key should contain channel username segment")
            assertTrue(key.contains("999_IMAGE"), "Key should contain messageId and mediaType")
            assertTrue(key.endsWith(".jpg"), "Key should end with file extension")
            tempFiles.remove(tempFile)
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private fun createTempFileWithContent(content: String): Path {
        val file = Files.createTempFile("telegram-test-", ".bin")
        Files.writeString(file, content)
        tempFiles.add(file)
        return file
    }
}
