package com.contentagg.parser.processor.telegram

import com.contentagg.parser.configuration.properties.TelegramProperties
import com.contentagg.parser.db.service.rawcontent.RawContentService
import com.contentagg.parser.db.service.util.JsonConversionService
import com.contentagg.parser.exception.TelegramParseException
import com.contentagg.parser.integration.s3.TelegramMediaUploadService
import com.contentagg.parser.integration.telegram.TelegramClientService
import com.fasterxml.jackson.databind.ObjectMapper
import it.tdlight.jni.TdApi
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId

@Component
@ConditionalOnProperty(
    prefix = "telegram",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class TelegramParseProcessor(
    private val telegramClientService: TelegramClientService,
    private val telegramMediaUploadService: TelegramMediaUploadService,
    private val rawContentService: RawContentService,
    private val jsonConversionService: JsonConversionService,
    private val objectMapper: ObjectMapper,
    private val telegramProperties: TelegramProperties,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TelegramParseProcessor::class.java)
        private const val SOURCE_TYPE = "TELEGRAM_CHANNEL"
    }

    /**
     * Parse messages from a Telegram channel.
     * Fetches message history with cursor-based pagination, deduplicates by externalId,
     * downloads media to S3 if enabled, and saves to raw_content.
     *
     * @param context Parsed configuration for this Telegram channel source
     * @return Count of newly saved messages
     */
    fun parseChannel(context: TelegramParseContext): Int {
        MDC.put("channelUsername", context.channelUsername)
        MDC.put("sourceId", context.sourceId.toString())

        try {
            val chat = telegramClientService.searchChannel(context.channelUsername)
            val chatId = chat.id
            val channelTitle = chat.title ?: context.channelUsername

            log.info("Found channel: title={}, chatId={}", channelTitle, chatId)

            telegramClientService.openChat(chatId)

            try {
                return fetchAndSaveMessages(context, chatId, channelTitle)
            } finally {
                telegramClientService.closeChat(chatId)
            }
        } catch (e: TelegramParseException) {
            throw e
        } catch (e: Exception) {
            throw TelegramParseException(
                "Failed to parse channel ${context.channelUsername}: ${e.message}", e
            )
        } finally {
            MDC.remove("channelUsername")
            MDC.remove("sourceId")
        }
    }

    private fun fetchAndSaveMessages(
        context: TelegramParseContext,
        chatId: Long,
        channelTitle: String,
    ): Int {
        var savedCount = 0
        var fromMessageId = 0L
        var hasMore = true

        while (hasMore && savedCount < context.maxMessages) {
            val messages = telegramClientService.getChatHistory(
                chatId = chatId,
                fromMessageId = fromMessageId,
                limit = context.batchSize,
            )

            if (messages.messages == null || messages.messages.isEmpty()) {
                log.debug("No more messages in channel: {}", context.channelUsername)
                break
            }

            for (message in messages.messages) {
                if (savedCount >= context.maxMessages) break

                val externalId = "${chatId}_${message.id}"

                // Forward dedup check: skip forwarded messages whose originals are already saved
                if (isForwardedDuplicate(message)) {
                    log.debug("Skipping forwarded duplicate: messageId={}", message.id)
                    continue
                }

                // Existing content dedup check: stop pagination when we reach already-saved content
                if (rawContentService.existsBySourceTypeAndExternalId(SOURCE_TYPE, externalId)) {
                    log.debug("Reached known message: externalId={}, stopping pagination", externalId)
                    hasMore = false
                    break
                }

                try {
                    saveMessage(context, message, chatId, channelTitle)
                    savedCount++
                } catch (e: Exception) {
                    log.error("Error processing message {}: {}", message.id, e.message, e)
                }
            }

            // Update cursor for next batch
            fromMessageId = messages.messages.last().id

            // Respect rate limit between batches
            if (hasMore && telegramProperties.requestDelayMs > 0) {
                Thread.sleep(telegramProperties.requestDelayMs)
            }
        }

        log.info("Saved {} messages from channel: {}", savedCount, context.channelUsername)
        return savedCount
    }

    /**
     * Check if message is a forward whose original has already been saved.
     * Only checks MessageOriginChannel forwards (cross-channel deduplication).
     */
    private fun isForwardedDuplicate(message: TdApi.Message): Boolean {
        val forwardInfo = message.forwardInfo ?: return false
        val origin = forwardInfo.origin

        if (origin is TdApi.MessageOriginChannel) {
            val originalExternalId = "${origin.chatId}_${origin.messageId}"
            if (rawContentService.existsBySourceTypeAndExternalId(SOURCE_TYPE, originalExternalId)) {
                return true
            }
        }
        return false
    }

    private fun saveMessage(
        context: TelegramParseContext,
        message: TdApi.Message,
        chatId: Long,
        channelTitle: String,
    ) {
        val textContent = extractTextContent(message)
        val url = "https://t.me/${context.channelUsername}/${message.id}"
        val publishedAtMillis = message.date.toLong() * 1000

        var downloadedMediaJson: String? = null
        if (context.downloadMedia) {
            downloadedMediaJson = processMedia(context, message)
        }

        val rawData = jsonConversionService.toJson(
            mapOf(
                "title" to "",
                "content" to textContent,
                "contentFormat" to "TEXT",
                "lead" to "",
                "authorId" to chatId.toString(),
                "authorName" to channelTitle,
                "url" to url,
                "sourceSubtype" to "CHANNEL",
                "publishedAt" to publishedAtMillis.toString(),
            )
        )

        rawContentService.saveRawContent(
            externalId = "${chatId}_${message.id}",
            sourceId = context.sourceId,
            sourceType = SOURCE_TYPE,
            rawData = rawData ?: "{}",
            rawMedia = null,
            downloadedMedia = downloadedMediaJson,
            processingStatus = "COMPLETED",
            receivedAt = LocalDateTime.now(ZoneId.of("UTC")),
        )
        log.debug("Saved raw content for messageId={}, channelUsername={}", message.id, context.channelUsername)
    }

    /**
     * Extract text content from a TDLib message.
     * Handles MessageText, MessagePhoto/Video/Document/Audio captions.
     */
    private fun extractTextContent(message: TdApi.Message): String {
        return when (val content = message.content) {
            is TdApi.MessageText -> content.text?.text ?: ""
            is TdApi.MessagePhoto -> content.caption?.text ?: ""
            is TdApi.MessageVideo -> content.caption?.text ?: ""
            is TdApi.MessageDocument -> content.caption?.text ?: ""
            is TdApi.MessageAudio -> content.caption?.text ?: ""
            is TdApi.MessageAnimation -> content.caption?.text ?: ""
            else -> ""
        }
    }

    /**
     * Download media from message and upload to S3.
     * For photos: uses last (largest) PhotoSize element.
     * Checks maxMediaSizeMb before downloading.
     *
     * @return JSON string of uploaded media items, or null if no media uploaded
     */
    private fun processMedia(
        context: TelegramParseContext,
        message: TdApi.Message,
    ): String? {
        val mediaItems = mutableListOf<Map<String, String>>()

        when (val content = message.content) {
            is TdApi.MessagePhoto -> {
                val photo = content.photo?.sizes?.lastOrNull() ?: return null
                val fileId = photo.photo?.id ?: return null
                val fileSize = photo.photo?.size?.toLong() ?: 0
                if (isMediaTooLarge(fileSize, context.maxMediaSizeMb)) return null
                val result = downloadAndUpload(fileId, context.channelUsername, message.id, "IMAGE", ".jpg")
                if (result != null) mediaItems.add(result)
            }
            is TdApi.MessageVideo -> {
                val video = content.video ?: return null
                val fileId = video.video?.id ?: return null
                val fileSize = video.video?.size?.toLong() ?: 0
                if (isMediaTooLarge(fileSize, context.maxMediaSizeMb)) return null
                val ext = video.fileName?.substringAfterLast('.', "mp4") ?: "mp4"
                val result = downloadAndUpload(fileId, context.channelUsername, message.id, "VIDEO", ".$ext")
                if (result != null) mediaItems.add(result)
            }
            is TdApi.MessageDocument -> {
                val doc = content.document ?: return null
                val fileId = doc.document?.id ?: return null
                val fileSize = doc.document?.size?.toLong() ?: 0
                if (isMediaTooLarge(fileSize, context.maxMediaSizeMb)) return null
                val ext = doc.fileName?.substringAfterLast('.', "bin") ?: "bin"
                val result = downloadAndUpload(fileId, context.channelUsername, message.id, "DOCUMENT", ".$ext")
                if (result != null) mediaItems.add(result)
            }
            is TdApi.MessageAudio -> {
                val audio = content.audio ?: return null
                val fileId = audio.audio?.id ?: return null
                val fileSize = audio.audio?.size?.toLong() ?: 0
                if (isMediaTooLarge(fileSize, context.maxMediaSizeMb)) return null
                val ext = audio.fileName?.substringAfterLast('.', "mp3") ?: "mp3"
                val result = downloadAndUpload(fileId, context.channelUsername, message.id, "AUDIO", ".$ext")
                if (result != null) mediaItems.add(result)
            }
            else -> return null
        }

        if (mediaItems.isEmpty()) return null
        // Build flat map format compatible with RawContentEventBuilder: media_0, media_1, etc.
        val flatMap = mutableMapOf<String, String>()
        mediaItems.forEachIndexed { index, item ->
            flatMap["media_$index"] = objectMapper.writeValueAsString(item)
        }
        return jsonConversionService.toJson(flatMap)
    }

    private fun isMediaTooLarge(fileSize: Long, maxMb: Int): Boolean {
        if (fileSize <= 0) return false // unknown size, try anyway
        return fileSize > maxMb.toLong() * 1024 * 1024
    }

    private fun downloadAndUpload(
        fileId: Int,
        channelUsername: String,
        messageId: Long,
        mediaType: String,
        fileExtension: String,
    ): Map<String, String>? {
        return try {
            val downloadedFile = telegramClientService.downloadFile(fileId)
            val localPath = downloadedFile.local?.path
            if (localPath.isNullOrBlank() || !downloadedFile.local.isDownloadingCompleted) {
                log.warn("File download incomplete: fileId={}", fileId)
                return null
            }

            val uploadResult = telegramMediaUploadService.uploadFromLocalPath(
                localPath = localPath,
                channelUsername = channelUsername,
                messageId = messageId,
                mediaType = mediaType,
                fileExtension = fileExtension,
            )

            mapOf(
                "s3Url" to uploadResult.s3Url,
                "s3Key" to uploadResult.s3Key,
                "mediaType" to mediaType,
                "mimeType" to uploadResult.mimeType,
                "fileSize" to uploadResult.fileSize.toString(),
            )
        } catch (e: Exception) {
            log.warn("Failed to download/upload media: fileId={}, error={}", fileId, e.message)
            null
        }
    }
}
