package com.skd.subscription.unit

import com.skd.subscription.configuration.ReconcileProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@Tag("unit")
class ReconcilePropertiesTest {

    @Test
    fun `default lazy enabled is true`() {
        val properties = ReconcileProperties()

        assertThat(properties.lazy.enabled).isTrue()
    }

    @Test
    fun `default lazy pending window minutes is 15`() {
        val properties = ReconcileProperties()

        assertThat(properties.lazy.pendingWindowMinutes).isEqualTo(15)
    }

    @Test
    fun `default scheduled enabled is true`() {
        val properties = ReconcileProperties()

        assertThat(properties.scheduled.enabled).isTrue()
    }

    @Test
    fun `default scheduled cron is every minute`() {
        val properties = ReconcileProperties()

        assertThat(properties.scheduled.cron).isEqualTo("0 * * * * ?")
    }

    @Test
    fun `default scheduled pending window minutes is 30`() {
        val properties = ReconcileProperties()

        assertThat(properties.scheduled.pendingWindowMinutes).isEqualTo(30)
    }

    @Test
    fun `lazy sub-properties can be overridden`() {
        val properties = ReconcileProperties(
            lazy = ReconcileProperties.Lazy(
                enabled = false,
                pendingWindowMinutes = 45
            )
        )

        assertThat(properties.lazy.enabled).isFalse()
        assertThat(properties.lazy.pendingWindowMinutes).isEqualTo(45)
    }

    @Test
    fun `scheduled sub-properties can be overridden`() {
        val properties = ReconcileProperties(
            scheduled = ReconcileProperties.Scheduled(
                enabled = false,
                cron = "0 0 * * * ?",
                pendingWindowMinutes = 120
            )
        )

        assertThat(properties.scheduled.enabled).isFalse()
        assertThat(properties.scheduled.cron).isEqualTo("0 0 * * * ?")
        assertThat(properties.scheduled.pendingWindowMinutes).isEqualTo(120)
    }
}

@SpringBootTest(
    classes = [ReconcilePropertiesBindingTest.TestConfig::class]
)
@TestPropertySource(
    properties = [
        "subscription.reconcile.lazy.enabled=false",
        "subscription.reconcile.lazy.pending-window-minutes=17",
        "subscription.reconcile.scheduled.enabled=false",
        "subscription.reconcile.scheduled.cron=0 0 * * * ?",
        "subscription.reconcile.scheduled.pending-window-minutes=123"
    ]
)
@Tag("unit")
class ReconcilePropertiesBindingTest {

    @EnableConfigurationProperties(ReconcileProperties::class)
    class TestConfig

    @Autowired
    lateinit var reconcileProperties: ReconcileProperties

    @Test
    fun `binds subscription reconcile prefix properties from Spring Environment`() {
        assertThat(reconcileProperties.lazy.enabled).isFalse()
        assertThat(reconcileProperties.lazy.pendingWindowMinutes).isEqualTo(17)
        assertThat(reconcileProperties.scheduled.enabled).isFalse()
        assertThat(reconcileProperties.scheduled.cron).isEqualTo("0 0 * * * ?")
        assertThat(reconcileProperties.scheduled.pendingWindowMinutes).isEqualTo(123)
    }
}
