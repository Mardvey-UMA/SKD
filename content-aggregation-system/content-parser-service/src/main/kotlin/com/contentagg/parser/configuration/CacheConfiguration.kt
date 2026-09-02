package com.contentagg.parser.configuration

import com.contentagg.parser.configuration.properties.CacheProperties
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties::class)
class CacheConfiguration(
    private val cacheProperties: CacheProperties,
) {

    @Bean
    @Primary
    fun sourceConfigsCacheManager(): CacheManager =
        CaffeineCacheManager("sourceConfigs").apply {
            setCaffeine(
                Caffeine.newBuilder()
                    .maximumSize(cacheProperties.sourceConfigs.maximumSize)
                    .expireAfterWrite(cacheProperties.sourceConfigs.expireAfterWrite)
            )
        }

    @Bean
    fun sourceConfigCacheManager(): CacheManager =
        CaffeineCacheManager("sourceConfig").apply {
            setCaffeine(
                Caffeine.newBuilder()
                    .maximumSize(cacheProperties.sourceConfig.maximumSize)
                    .expireAfterWrite(cacheProperties.sourceConfig.expireAfterWrite)
            )
        }
}
