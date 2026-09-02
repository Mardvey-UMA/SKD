package com.contentagg.parser.integration.telegram

import com.contentagg.parser.configuration.properties.TelegramProperties
import com.contentagg.parser.exception.TelegramParseException
import it.tdlight.client.SimpleTelegramClient
import it.tdlight.jni.TdApi
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * TDLib client wrapper providing synchronous, typed methods for Telegram channel operations.
 * All TDLib calls block until completion or REQUEST_TIMEOUT_SECONDS elapses.
 *
 * Loaded only when telegram.enabled=true (matches TelegramConfiguration condition).
 */
@Service
@ConditionalOnProperty(
    prefix = "telegram",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class TelegramClientService(
    private val telegramClient: SimpleTelegramClient,
    private val telegramProperties: TelegramProperties,
) {

    companion object {
        private val log = LoggerFactory.getLogger(TelegramClientService::class.java)
        private const val REQUEST_TIMEOUT_SECONDS = 30L
    }

    /**
     * Search for a public channel/chat by username.
     *
     * @param username Channel username without '@' prefix
     * @return TdApi.Chat object for the channel
     * @throws TelegramParseException if channel not found or request fails
     */
    fun searchChannel(username: String): TdApi.Chat {
        log.debug("Searching for channel: {}", username)
        val request = TdApi.SearchPublicChat(username)
        return sendRequest(request) as TdApi.Chat
    }

    /**
     * Open a chat to allow receiving message updates and history.
     * Should be paired with closeChat() when done.
     *
     * @param chatId Telegram chat ID
     * @throws TelegramParseException if request fails
     */
    fun openChat(chatId: Long) {
        log.debug("Opening chat: {}", chatId)
        val request = TdApi.OpenChat(chatId)
        sendRequest(request)
    }

    /**
     * Close a previously opened chat.
     *
     * @param chatId Telegram chat ID
     * @throws TelegramParseException if request fails
     */
    fun closeChat(chatId: Long) {
        log.debug("Closing chat: {}", chatId)
        val request = TdApi.CloseChat(chatId)
        sendRequest(request)
    }

    /**
     * Retrieve chat message history in reverse chronological order.
     *
     * @param chatId       Telegram chat ID
     * @param fromMessageId Message ID to start from (0 = from latest)
     * @param limit        Maximum number of messages to retrieve
     * @return TdApi.Messages containing the retrieved messages
     * @throws TelegramParseException if request fails
     */
    fun getChatHistory(
        chatId: Long,
        fromMessageId: Long,
        limit: Int,
    ): TdApi.Messages {
        log.debug(
            "Getting chat history: chatId={}, fromMessageId={}, limit={}",
            chatId, fromMessageId, limit,
        )
        // offset=0, onlyLocal=false to fetch from remote if not cached
        val request = TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false)
        return sendRequest(request) as TdApi.Messages
    }

    /**
     * Download a file from Telegram servers synchronously.
     * Blocks until download completes.
     *
     * @param fileId   TDLib file ID
     * @param priority Download priority (1-32, default 32 = highest)
     * @return TdApi.File with local.path set to the downloaded file location
     * @throws TelegramParseException if download fails or times out
     */
    fun downloadFile(fileId: Int, priority: Int = 32): TdApi.File {
        log.debug("Downloading file: fileId={}", fileId)
        // synchronous=true: block until download completes; offset=0, limit=0 = entire file
        val request = TdApi.DownloadFile(fileId, priority, 0, 0, true)
        return sendRequest(request) as TdApi.File
    }

    /**
     * Check whether the TDLib client is currently authorized.
     * Uses getMeAsync with a short timeout to probe the auth state.
     *
     * @return true if authorized, false otherwise
     */
    fun isAuthenticated(): Boolean {
        return try {
            telegramClient.meAsync.get(5, TimeUnit.SECONDS)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ========== Private helpers ==========

    /**
     * Send a TDLib request synchronously and return the result.
     * Converts TdApi.Error responses into TelegramParseException.
     *
     * @param request TDLib function to execute
     * @return Result object; never TdApi.Error (converted to exception)
     * @throws TelegramParseException on TDLib errors or timeout
     */
    private fun sendRequest(request: TdApi.Function<*>): TdApi.Object {
        return try {
            val result = telegramClient.send(request).get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (result is TdApi.Error) {
                throw TelegramParseException(
                    "TDLib error: code=${result.code}, message=${result.message}",
                )
            }
            result
        } catch (e: TelegramParseException) {
            throw e
        } catch (e: Exception) {
            throw TelegramParseException(
                "TDLib request failed: ${request.javaClass.simpleName}: ${e.message}", e,
            )
        }
    }
}
