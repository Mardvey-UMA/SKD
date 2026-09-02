package com.contentagg.parser.configuration

import com.contentagg.parser.configuration.properties.TelegramProperties
import it.tdlight.Init
import it.tdlight.Log
import it.tdlight.client.APIToken
import it.tdlight.client.AuthenticationSupplier
import it.tdlight.client.ClientInteraction
import it.tdlight.client.SimpleTelegramClient
import it.tdlight.client.SimpleTelegramClientFactory
import it.tdlight.client.TDLibSettings
import it.tdlight.jni.TdApi
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Configuration for TDLib (TDLight) client beans.
 * Loaded only when telegram.enabled=true.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "telegram",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class TelegramConfiguration(
    private val telegramProperties: TelegramProperties,
) {

    companion object {
        private val log = LoggerFactory.getLogger(TelegramConfiguration::class.java)
    }

    private var client: SimpleTelegramClient? = null
    private var factory: SimpleTelegramClientFactory? = null

    @Bean
    fun telegramClientFactory(): SimpleTelegramClientFactory {
        Init.init()
        Log.setLogMessageHandler(1) { verbosityLevel, message ->
            log.debug("TDLib [{}]: {}", verbosityLevel, message)
        }
        val f = SimpleTelegramClientFactory()
        this.factory = f
        log.info("TDLight library initialized")
        return f
    }

    @Bean(destroyMethod = "")
    fun telegramClient(factory: SimpleTelegramClientFactory): SimpleTelegramClient {
        val apiToken = APIToken(telegramProperties.apiId, telegramProperties.apiHash)
        val settings = TDLibSettings.create(apiToken)
        settings.databaseDirectoryPath = Path.of(telegramProperties.sessionPath)
        settings.downloadedFilesDirectoryPath = Path.of(telegramProperties.downloadPath)

        val builder = factory.builder(settings)

        // Auth code/password are submitted via REST (TelegramAuthService.submitCode/submitPassword).
        // The default ScannerClientInteraction busy-polls stdin (ScannerUtils.interruptibleReadLine),
        // burning a whole CPU core in containers where stdin is /dev/null. A no-op interaction
        // that returns a never-completing future replaces that polling loop.
        builder.setClientInteraction(ClientInteraction { _, _ -> CompletableFuture<String>() })

        // Log authorization state transitions for observability
        builder.addUpdateHandler(TdApi.UpdateAuthorizationState::class.java) { update ->
            when (val state = update.authorizationState) {
                is TdApi.AuthorizationStateReady -> log.info("Telegram authorization successful")
                is TdApi.AuthorizationStateClosing -> log.info("Telegram client closing")
                is TdApi.AuthorizationStateClosed -> log.info("Telegram client closed")
                is TdApi.AuthorizationStateWaitPhoneNumber -> log.info("Waiting for phone number")
                is TdApi.AuthorizationStateWaitCode -> log.info("Waiting for authorization code")
                is TdApi.AuthorizationStateWaitPassword -> log.info("Waiting for 2FA password")
                else -> log.debug("Authorization state changed: {}", state::class.java.simpleName)
            }
        }

        val c = builder.build(AuthenticationSupplier.user(telegramProperties.phoneNumber))
        this.client = c

        log.info(
            "TDLib client initialized for phone: {}****",
            telegramProperties.phoneNumber.take(6),
        )
        return c
    }

    @PreDestroy
    fun shutdown() {
        try {
            log.info("Shutting down TDLib client...")
            client?.sendClose()
            factory?.close()
            log.info("TDLib client shut down successfully")
        } catch (e: Exception) {
            log.error("Error shutting down TDLib client: {}", e.message)
        }
    }
}
