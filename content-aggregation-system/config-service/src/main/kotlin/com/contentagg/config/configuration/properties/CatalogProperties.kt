package com.contentagg.config.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Properties governing the public source catalog (Phase 2).
 * - cache ttl for Valkey entries
 * - default / max page size
 */
@ConfigurationProperties(prefix = "catalog")
data class CatalogProperties(
    var cacheTtlSeconds: Long = 300L,
    var defaultPageSize: Int = 20,
    var maxPageSize: Int = 50,
    var versionKey: String = "sources:catalog:version",
    var keyPrefix: String = "sources:list",
)
