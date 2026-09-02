package com.contentplatform.feed.unit.kafka

import org.assertj.core.api.AbstractThrowableAssert

/**
 * Extension function to provide doesNotThrowException() as an alias for doesNotThrowAnyException().
 * Required because assertj-core 3.26.3 only exposes doesNotThrowAnyException().
 */
fun AbstractThrowableAssert<*, *>.doesNotThrowException() = this.doesNotThrowAnyException()
