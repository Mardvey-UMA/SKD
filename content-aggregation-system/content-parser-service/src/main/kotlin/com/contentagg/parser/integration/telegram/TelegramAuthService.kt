package com.contentagg.parser.integration.telegram

import com.contentagg.parser.exception.TelegramParseException
import it.tdlight.client.SimpleTelegramClient
import it.tdlight.jni.TdApi
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Service
@ConditionalOnProperty(
    prefix = "telegram",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class TelegramAuthService(
    private val telegramClient: SimpleTelegramClient,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TelegramAuthService::class.java)

        const val STATE_UNKNOWN = "UNKNOWN"
        const val STATE_WAIT_CODE = "WAIT_CODE"
        const val STATE_WAIT_PASSWORD = "WAIT_PASSWORD"
        const val STATE_READY = "READY"
        const val STATE_CLOSING = "CLOSING"
        const val STATE_CLOSED = "CLOSED"
    }

    private val authState = AtomicReference(STATE_UNKNOWN)
    private val passwordHint = AtomicReference("")

    @PostConstruct
    fun init() {
        telegramClient.addUpdateHandler(TdApi.UpdateAuthorizationState::class.java) { update ->
            handleAuthStateUpdate(update.authorizationState)
        }
        log.info("TelegramAuthService initialized, registered auth state handler")
    }

    fun getAuthState(): String = authState.get()

    fun getPasswordHint(): String = passwordHint.get()

    fun isAuthenticated(): Boolean = authState.get() == STATE_READY

    fun submitCode(code: String): Boolean {
        if (authState.get() != STATE_WAIT_CODE) {
            log.warn("Cannot submit code in state: {}", authState.get())
            return false
        }
        return try {
            val request = TdApi.CheckAuthenticationCode(code)
            telegramClient.send(request).get(30, TimeUnit.SECONDS)
            log.info("Auth code accepted")
            true
        } catch (e: Exception) {
            log.error("Failed to submit auth code: {}", e.message)
            false
        }
    }

    fun submitPassword(password: String): Boolean {
        if (authState.get() != STATE_WAIT_PASSWORD) {
            log.warn("Cannot submit password in state: {}", authState.get())
            return false
        }
        return try {
            val request = TdApi.CheckAuthenticationPassword(password)
            telegramClient.send(request).get(30, TimeUnit.SECONDS)
            log.info("Auth password accepted")
            true
        } catch (e: Exception) {
            log.error("Failed to submit auth password: {}", e.message)
            false
        }
    }

    private fun handleAuthStateUpdate(state: TdApi.AuthorizationState) {
        val newState = when (state) {
            is TdApi.AuthorizationStateWaitCode -> {
                log.info("TDLib authorization: waiting for code")
                STATE_WAIT_CODE
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                passwordHint.set(state.passwordHint ?: "")
                log.info("TDLib authorization: waiting for 2FA password")
                STATE_WAIT_PASSWORD
            }
            is TdApi.AuthorizationStateReady -> {
                log.info("TDLib authorization: READY")
                STATE_READY
            }
            is TdApi.AuthorizationStateClosing -> {
                log.info("TDLib authorization: closing")
                STATE_CLOSING
            }
            is TdApi.AuthorizationStateClosed -> {
                log.info("TDLib authorization: closed")
                STATE_CLOSED
            }
            else -> {
                log.debug("TDLib authorization state: {}", state.javaClass.simpleName)
                authState.get() // keep current
            }
        }
        authState.set(newState)
    }
}
