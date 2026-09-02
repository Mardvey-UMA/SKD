package com.contentagg.parser.configuration

import com.contentagg.parser.configuration.properties.TelegramProperties
import com.contentagg.parser.configuration.properties.scheduler.SchedulerProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

/**
 * Runtime filter of SchedulerProperties.enabledSourceTypes.
 *
 * If telegram.enabled=false and enabledSourceTypes contains TELEGRAM, removes TELEGRAM from the list
 * at application-ready time. The mutation is in-place on the @ConfigurationProperties var list —
 * safe under Spring Boot 3.x relaxed binder contract for var fields.
 */
@Component
class EnabledSourceTypesFilter(
    private val schedulerProperties: SchedulerProperties,
    private val telegramProperties: TelegramProperties,
) : ApplicationListener<ApplicationReadyEvent> {

    companion object {
        private val log = LoggerFactory.getLogger(EnabledSourceTypesFilter::class.java)
        private const val TELEGRAM = "TELEGRAM"
    }

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        val beforeList = schedulerProperties.enabledSourceTypes
        val hasTelegram = beforeList.any { it.equals(TELEGRAM, ignoreCase = true) }
        if (!telegramProperties.enabled && hasTelegram) {
            schedulerProperties.enabledSourceTypes =
                beforeList.filterNot { it.equals(TELEGRAM, ignoreCase = true) }
            log.info(
                "Telegram disabled: removed TELEGRAM from enabledSourceTypes; effective={}",
                schedulerProperties.enabledSourceTypes,
            )
        } else {
            log.info("Effective enabledSourceTypes={}", schedulerProperties.enabledSourceTypes)
        }
    }
}
