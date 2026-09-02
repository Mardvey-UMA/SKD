package com.contentagg.config.configuration.properties

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "spring.kafka")
@Validated
data class KafkaProperties(
    @field:NotBlank(message = "Kafka bootstrap servers must be configured")
    var bootstrapServers: String = "",

    @field:NotNull(message = "Topics configuration is required")
    @NestedConfigurationProperty
    var topics: Topics = Topics(),

    @field:NotNull(message = "Producer configuration is required")
    @NestedConfigurationProperty
    var producer: Producer = Producer()
) {
    data class Topics(
        @field:NotBlank(message = "Source config updated topic must be configured")
        var sourceConfigUpdated: String = "",

        @field:NotBlank(message = "Source added topic must be configured")
        var sourceAdded: String = "source.added",
    )

    data class Producer(
        @field:Pattern(regexp = "^(all|0|1|-1)$", message = "Acks must be one of: all, 0, 1, -1")
        var acks: String = "all",

        @field:Min(value = 0, message = "Retries must be non-negative")
        var retries: Int = 3
    )
}
