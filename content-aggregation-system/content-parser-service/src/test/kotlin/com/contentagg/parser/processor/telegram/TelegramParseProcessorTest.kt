package com.contentagg.parser.processor.telegram

import com.contentagg.parser.configuration.properties.TelegramProperties
import com.contentagg.parser.db.service.rawcontent.RawContentService
import com.contentagg.parser.db.service.util.JsonConversionService
import com.contentagg.parser.exception.TelegramParseException
import com.contentagg.parser.integration.s3.TelegramMediaUploadService
import com.contentagg.parser.integration.telegram.TelegramClientService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class TelegramParseProcessorTest {

    private lateinit var processor: TelegramParseProcessor
    private val telegramClientService: TelegramClientService = mockk()
    private val telegramMediaUploadService: TelegramMediaUploadService = mockk()
    private val rawContentService: RawContentService = mockk(relaxed = true)
    private val jsonConversionService: JsonConversionService = mockk()
    private val objectMapper: ObjectMapper = ObjectMapper()
    private val telegramProperties: TelegramProperties = TelegramProperties().apply { requestDelayMs = 0 }

    @BeforeEach
    fun setUp() {
        processor = TelegramParseProcessor(
            telegramClientService = telegramClientService,
            telegramMediaUploadService = telegramMediaUploadService,
            rawContentService = rawContentService,
            jsonConversionService = jsonConversionService,
            objectMapper = objectMapper,
            telegramProperties = telegramProperties,
        )

        // Default stubs: openChat / closeChat succeed silently
        every { telegramClientService.openChat(any()) } returns Unit
        every { telegramClientService.closeChat(any()) } returns Unit
        every { jsonConversionService.toJson(any()) } returns "{}"
    }

    @Nested
    @DisplayName("parseChannel()")
    inner class ParseChannelTests {

        @Test
        @DisplayName("empty message batch — returns 0 and saves nothing")
        fun testParseChannel_EmptyMessageBatch_Returns0() {
            val chat = buildChat(chatId = 1001L)
            every { telegramClientService.searchChannel("testchannel") } returns chat
            every { telegramClientService.getChatHistory(1001L, 0L, any()) } returns buildMessages(emptyList())

            val result = processor.parseChannel(buildContext())

            assertEquals(0, result)
            verify(exactly = 0) { rawContentService.saveRawContent(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("new message — saves to raw_content and returns count 1")
        fun testParseChannel_NewMessage_SavesAndReturnsCount() {
            val chat = buildChat(chatId = 2002L)
            val message = buildTextMessage(messageId = 100L, chatId = 2002L, text = "Hello channel")

            every { telegramClientService.searchChannel("testchannel") } returns chat
            every { telegramClientService.getChatHistory(2002L, 0L, any()) } returns buildMessages(listOf(message))
            every { telegramClientService.getChatHistory(2002L, 100L, any()) } returns buildMessages(emptyList())
            every { rawContentService.existsBySourceTypeAndExternalId("TELEGRAM_CHANNEL", "2002_100") } returns false

            val result = processor.parseChannel(buildContext(downloadMedia = false))

            assertEquals(1, result)
            verify(exactly = 1) { rawContentService.saveRawContent(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("existing message in DB — stops pagination immediately")
        fun testParseChannel_ExistingMessage_StopsPagination() {
            val chat = buildChat(chatId = 3003L)
            val message = buildTextMessage(messageId = 200L, chatId = 3003L, text = "Old message")

            every { telegramClientService.searchChannel("testchannel") } returns chat
            every { telegramClientService.getChatHistory(3003L, 0L, any()) } returns buildMessages(listOf(message))
            every { rawContentService.existsBySourceTypeAndExternalId("TELEGRAM_CHANNEL", "3003_200") } returns true

            val result = processor.parseChannel(buildContext(downloadMedia = false))

            assertEquals(0, result)
            // Second page must not be fetched after dedup stop
            verify(exactly = 1) { telegramClientService.getChatHistory(any(), any(), any()) }
        }

        @Test
        @DisplayName("forwarded message from a channel that is already saved — message skipped")
        fun testParseChannel_ForwardedDuplicateChannel_Skipped() {
            val chat = buildChat(chatId = 4004L)
            val originalChatId = 9999L
            val originalMessageId = 500L
            val message = buildForwardedChannelMessage(
                messageId = 300L,
                originChatId = originalChatId,
                originMessageId = originalMessageId,
            )

            every { telegramClientService.searchChannel("testchannel") } returns chat
            every { telegramClientService.getChatHistory(4004L, 0L, any()) } returns buildMessages(listOf(message))
            every { telegramClientService.getChatHistory(4004L, 300L, any()) } returns buildMessages(emptyList())
            // The original message IS already saved
            every {
                rawContentService.existsBySourceTypeAndExternalId(
                    "TELEGRAM_CHANNEL",
                    "${originalChatId}_${originalMessageId}"
                )
            } returns true

            val result = processor.parseChannel(buildContext(downloadMedia = false))

            assertEquals(0, result)
            verify(exactly = 0) { rawContentService.saveRawContent(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("forwarded message from user (not channel) — not skipped, saved normally")
        fun testParseChannel_ForwardedFromUser_NotSkipped() {
            val chat = buildChat(chatId = 5005L)
            // A message forwarded from a user (MessageOriginUser) — isForwardedDuplicate should return false
            val message = buildTextMessageForwardedFromUser(messageId = 400L)

            every { telegramClientService.searchChannel("testchannel") } returns chat
            every { telegramClientService.getChatHistory(5005L, 0L, any()) } returns buildMessages(listOf(message))
            every { telegramClientService.getChatHistory(5005L, 400L, any()) } returns buildMessages(emptyList())
            every { rawContentService.existsBySourceTypeAndExternalId("TELEGRAM_CHANNEL", "5005_400") } returns false

            val result = processor.parseChannel(buildContext(downloadMedia = false))

            assertEquals(1, result)
            verify(exactly = 1) { rawContentService.saveRawContent(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("maxMessages reached — stops pagination after limit")
        fun testParseChannel_MaxMessagesReached_StopsPagination() {
            val chat = buildChat(chatId = 6006L)
            // 3 messages but maxMessages = 2
            val messages = (1L..3L).map { id ->
                buildTextMessage(messageId = id, chatId = 6006L, text = "Message $id")
            }

            every { telegramClientService.searchChannel("testchannel") } returns chat
            every { telegramClientService.getChatHistory(6006L, 0L, any()) } returns buildMessages(messages)
            every { rawContentService.existsBySourceTypeAndExternalId(any(), any()) } returns false

            val result = processor.parseChannel(buildContext(maxMessages = 2, downloadMedia = false))

            assertEquals(2, result)
        }

        @Test
        @DisplayName("message text — extracted from MessageText content")
        fun testParseChannel_MessageText_ExtractsText() {
            val chat = buildChat(chatId = 7007L)
            val message = buildTextMessage(messageId = 600L, chatId = 7007L, text = "Expected text content")
            val capturedArg: CapturingSlot<Map<String, String>> = slot()

            every { telegramClientService.searchChannel("testchannel") } returns chat
            every { telegramClientService.getChatHistory(7007L, 0L, any()) } returns buildMessages(listOf(message))
            every { telegramClientService.getChatHistory(7007L, 600L, any()) } returns buildMessages(emptyList())
            every { rawContentService.existsBySourceTypeAndExternalId("TELEGRAM_CHANNEL", "7007_600") } returns false
            every { jsonConversionService.toJson(capture(capturedArg)) } returns "{}"

            processor.parseChannel(buildContext(downloadMedia = false))

            assertEquals("Expected text content", capturedArg.captured["content"])
        }

        @Test
        @DisplayName("message photo — caption extracted as text")
        fun testParseChannel_MessagePhoto_ExtractsCaption() {
            val chat = buildChat(chatId = 8008L)
            val message = buildPhotoMessage(messageId = 700L, caption = "Photo caption text")
            val capturedArg: CapturingSlot<Map<String, String>> = slot()

            every { telegramClientService.searchChannel("testchannel") } returns chat
            every { telegramClientService.getChatHistory(8008L, 0L, any()) } returns buildMessages(listOf(message))
            every { telegramClientService.getChatHistory(8008L, 700L, any()) } returns buildMessages(emptyList())
            every { rawContentService.existsBySourceTypeAndExternalId("TELEGRAM_CHANNEL", "8008_700") } returns false
            every { jsonConversionService.toJson(capture(capturedArg)) } returns "{}"

            processor.parseChannel(buildContext(downloadMedia = false))

            assertEquals("Photo caption text", capturedArg.captured["content"])
        }

        @Test
        @DisplayName("downloadMedia=false — TelegramMediaUploadService never called")
        fun testParseChannel_DownloadMediaFalse_NoMediaDownloaded() {
            val chat = buildChat(chatId = 9009L)
            val message = buildTextMessage(messageId = 800L, chatId = 9009L, text = "text")

            every { telegramClientService.searchChannel("testchannel") } returns chat
            every { telegramClientService.getChatHistory(9009L, 0L, any()) } returns buildMessages(listOf(message))
            every { telegramClientService.getChatHistory(9009L, 800L, any()) } returns buildMessages(emptyList())
            every { rawContentService.existsBySourceTypeAndExternalId("TELEGRAM_CHANNEL", "9009_800") } returns false

            processor.parseChannel(buildContext(downloadMedia = false))

            verify(exactly = 0) { telegramMediaUploadService.uploadFromLocalPath(any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("channel search fails — throws TelegramParseException")
        fun testParseChannel_ChannelSearchFails_ThrowsTelegramParseException() {
            every { telegramClientService.searchChannel("testchannel") } throws
                TelegramParseException("TDLib error: code=400, message=USERNAME_NOT_OCCUPIED")

            assertThrows<TelegramParseException> {
                processor.parseChannel(buildContext())
            }
        }

        @Test
        @DisplayName("chat is always closed in finally block even when exception occurs")
        fun testParseChannel_ExceptionDuringProcessing_ChatClosed() {
            val chat = buildChat(chatId = 1010L)
            every { telegramClientService.searchChannel("testchannel") } returns chat
            every { telegramClientService.getChatHistory(any(), any(), any()) } throws RuntimeException("Unexpected error")

            assertThrows<TelegramParseException> {
                processor.parseChannel(buildContext())
            }

            verify(exactly = 1) { telegramClientService.closeChat(1010L) }
        }
    }

    // -------------------------------------------------------------------------
    // TdApi object builders
    // -------------------------------------------------------------------------

    private fun buildChat(chatId: Long, title: String = "Test Channel"): TdApi.Chat {
        val chat = TdApi.Chat()
        chat.id = chatId
        chat.title = title
        return chat
    }

    private fun buildTextMessage(messageId: Long, chatId: Long, text: String): TdApi.Message {
        val formattedText = TdApi.FormattedText()
        formattedText.text = text
        val content = TdApi.MessageText()
        content.text = formattedText

        val message = TdApi.Message()
        message.id = messageId
        message.chatId = chatId
        message.content = content
        message.date = 1711708800
        message.forwardInfo = null
        return message
    }

    private fun buildPhotoMessage(messageId: Long, caption: String): TdApi.Message {
        val captionText = TdApi.FormattedText()
        captionText.text = caption

        val photoSize = TdApi.PhotoSize()
        val file = TdApi.File()
        file.id = 42
        file.size = 1024
        val localFile = TdApi.LocalFile()
        localFile.path = ""
        localFile.isDownloadingCompleted = false
        file.local = localFile
        photoSize.photo = file

        val photo = TdApi.Photo()
        photo.sizes = arrayOf(photoSize)

        val content = TdApi.MessagePhoto()
        content.photo = photo
        content.caption = captionText

        val message = TdApi.Message()
        message.id = messageId
        message.chatId = 8008L
        message.content = content
        message.date = 1711708800
        message.forwardInfo = null
        return message
    }

    private fun buildForwardedChannelMessage(
        messageId: Long,
        originChatId: Long,
        originMessageId: Long,
    ): TdApi.Message {
        val formattedText = TdApi.FormattedText()
        formattedText.text = "Forwarded message"
        val content = TdApi.MessageText()
        content.text = formattedText

        val origin = TdApi.MessageOriginChannel()
        origin.chatId = originChatId
        origin.messageId = originMessageId

        val forwardInfo = TdApi.MessageForwardInfo()
        forwardInfo.origin = origin

        val message = TdApi.Message()
        message.id = messageId
        message.chatId = 4004L
        message.content = content
        message.date = 1711708800
        message.forwardInfo = forwardInfo
        return message
    }

    private fun buildTextMessageForwardedFromUser(messageId: Long): TdApi.Message {
        val formattedText = TdApi.FormattedText()
        formattedText.text = "User forwarded message"
        val content = TdApi.MessageText()
        content.text = formattedText

        // MessageOriginUser is not MessageOriginChannel — should not be skipped
        val origin = TdApi.MessageOriginUser()
        origin.senderUserId = 12345L

        val forwardInfo = TdApi.MessageForwardInfo()
        forwardInfo.origin = origin

        val message = TdApi.Message()
        message.id = messageId
        message.chatId = 5005L
        message.content = content
        message.date = 1711708800
        message.forwardInfo = forwardInfo
        return message
    }

    private fun buildMessages(messages: List<TdApi.Message>): TdApi.Messages {
        val result = TdApi.Messages()
        result.messages = messages.toTypedArray()
        result.totalCount = messages.size
        return result
    }

    // -------------------------------------------------------------------------
    // Context builders
    // -------------------------------------------------------------------------

    private fun buildContext(
        maxMessages: Int = 100,
        downloadMedia: Boolean = false,
        channelUsername: String = "testchannel",
    ): TelegramParseContext = TelegramParseContext(
        sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
        sourceName = "test-source",
        channelUsername = channelUsername,
        maxMessages = maxMessages,
        downloadMedia = downloadMedia,
        maxMediaSizeMb = 50,
        batchSize = 50,
    )
}
