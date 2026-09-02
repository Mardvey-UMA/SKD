package com.contentagg.config.configuration

import com.github.benmanes.caffeine.cache.CaffeineSpec
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@EnableCaching
class CacheConfiguration(
    @Value("\${spring.cache.caffeine.spec:maximumSize=1000,expireAfterWrite=5m}")
    private val caffeineSpec: String
) {

    companion object {
        private val log = LoggerFactory.getLogger(CacheConfiguration::class.java)
    }

    @Bean
    @Primary
    fun cacheManager(): CacheManager {
        log.info("Initializing CacheManager with spec: {}", caffeineSpec)
        return CaffeineCacheManager("sources", "habrSources").apply {
            setCaffeineSpec(CaffeineSpec.parse(caffeineSpec))
        }
    }
}
