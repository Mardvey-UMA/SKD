package com.contentagg.parser.integration.s3

import com.contentagg.parser.exception.TelegramParseException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID

/**
 * Service for uploading Telegram media files to S3.
 * Files are downloaded locally by TDLib (DownloadFile) and then uploaded to S3.
 * Local files are always deleted after upload attempt (success or failure).
 */
@Service
class TelegramMediaUploadService(
    private val s3Client: S3Client,
    @Value("\${s3.bucket}") private val s3BucketName: String,
    @Value("\${s3.public-url}") private val s3PublicUrl: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TelegramMediaUploadService::class.java)
    }

    data class UploadResult(
        val s3Url: String,
        val s3Key: String,
        val fileSize: Long,
        val mimeType: String,
    )

    /**
     * Upload a locally downloaded TDLib file to S3.
     *
     * @param localPath Absolute local path to the file downloaded by TDLib
     * @param channelUsername Telegram channel username for S3 key organization
     * @param messageId Telegram message ID
     * @param mediaType Media type label (e.g. "photo", "video", "document")
     * @param fileExtension File extension with or without leading dot (e.g. ".jpg" or "jpg")
     * @return UploadResult containing S3 URL, key, file size, and MIME type
     * @throws TelegramParseException if file not found or upload fails
     */
    fun uploadFromLocalPath(
        localPath: String,
        channelUsername: String,
        messageId: Long,
        mediaType: String,
        fileExtension: String,
    ): UploadResult {
        val path = Path.of(localPath)

        if (!Files.exists(path)) {
            throw TelegramParseException("Local file not found: $localPath")
        }

        val fileBytes = Files.readAllBytes(path)
        val fileSize = fileBytes.size.toLong()
        val mimeType = guessMimeType(fileExtension)
        val s3Key = generateS3Key(channelUsername, messageId, mediaType, fileExtension)

        try {
            val putRequest = PutObjectRequest.builder()
                .bucket(s3BucketName)
                .key(s3Key)
                .contentType(mimeType)
                .build()

            s3Client.putObject(putRequest, RequestBody.fromBytes(fileBytes))

            log.debug("Uploaded telegram media to S3: key={}, size={}", s3Key, fileSize)

            return UploadResult(
                s3Url = buildS3Url(s3Key),
                s3Key = s3Key,
                fileSize = fileSize,
                mimeType = mimeType,
            )
        } catch (e: Exception) {
            throw TelegramParseException("Failed to upload telegram media to S3: ${e.message}", e)
        } finally {
            deleteLocalFile(path)
        }
    }

    // ========== Private methods ==========

    /**
     * Generate S3 key for Telegram media.
     * Pattern: telegram/{date}/{channelUsername}/{messageId}_{mediaType}_{uuid}.{ext}
     * Example: telegram/2026-03-29/durov/123_photo_550e8400-e29b-41d4-a716-446655440000.jpg
     */
    private fun generateS3Key(
        channelUsername: String,
        messageId: Long,
        mediaType: String,
        fileExtension: String,
    ): String {
        val date = LocalDate.now().toString()
        val uuid = UUID.randomUUID()
        val ext = if (fileExtension.startsWith(".")) fileExtension else ".$fileExtension"
        return "telegram/$date/$channelUsername/${messageId}_${mediaType}_${uuid}${ext}"
    }

    private fun guessMimeType(extension: String): String {
        val ext = extension.lowercase().removePrefix(".")
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "ogg" -> "audio/ogg"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    private fun buildS3Url(key: String): String = "/$s3BucketName/$key"

    private fun deleteLocalFile(path: Path) {
        try {
            Files.deleteIfExists(path)
            log.debug("Deleted local telegram media file: {}", path)
        } catch (e: Exception) {
            log.warn("Failed to delete local file {}: {}", path, e.message)
        }
    }
}
