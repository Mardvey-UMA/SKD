package com.contentagg.config.processor.source

import com.contentagg.config.configuration.properties.SourceLimitProperties
import com.contentagg.config.exception.SourceLimitExceededException
import com.contentagg.config.integration.rest.feed.SourceAdditionsCountClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatNoException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class UserSourceLimitCheckerTest {

    private val client = mockk<SourceAdditionsCountClient>(relaxed = true)
    private val props = SourceLimitProperties(enabled = true, perUser = 20)
    private val checker = UserSourceLimitChecker(client, props)

    @Test
    fun `no-op when disabled`() {
        val disabled = SourceLimitProperties(enabled = false, perUser = 20)
        val c = UserSourceLimitChecker(client, disabled)

        assertThatNoException().isThrownBy { c.ensureUnderLimit("u-1") }

        verify(exactly = 0) { client.tryGetCount(any()) }
    }

    @Test
    fun `no-op when userId is null or blank`() {
        assertThatNoException().isThrownBy { checker.ensureUnderLimit(null) }
        assertThatNoException().isThrownBy { checker.ensureUnderLimit("   ") }

        verify(exactly = 0) { client.tryGetCount(any()) }
    }

    @Test
    fun `fail-open when feed-service returns null`() {
        every { client.tryGetCount("u-1") } returns null

        assertThatNoException().isThrownBy { checker.ensureUnderLimit("u-1") }
    }

    @Test
    fun `passes when count under limit`() {
        every { client.tryGetCount("u-1") } returns
            SourceAdditionsCountClient.CountResponse(userId = "u-1", count = 7, limit = 20, remaining = 13)

        assertThatNoException().isThrownBy { checker.ensureUnderLimit("u-1") }
    }

    @Test
    fun `throws when count at or above limit`() {
        every { client.tryGetCount("u-1") } returns
            SourceAdditionsCountClient.CountResponse(userId = "u-1", count = 20, limit = 20, remaining = 0)

        assertThatThrownBy { checker.ensureUnderLimit("u-1") }
            .isInstanceOf(SourceLimitExceededException::class.java)
            .hasMessageContaining("20 of 20")
    }

    @Test
    fun `uses server-reported limit when non-zero`() {
        every { client.tryGetCount("u-1") } returns
            SourceAdditionsCountClient.CountResponse(userId = "u-1", count = 10, limit = 10, remaining = 0)

        assertThatThrownBy { checker.ensureUnderLimit("u-1") }
            .isInstanceOf(SourceLimitExceededException::class.java)
    }
}
