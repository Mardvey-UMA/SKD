package com.skd.subscription.unit

import com.skd.subscription.db.repository.plan.model.Plan
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PlanEntityTest {

    @Test
    fun `should create plan with all fields`() {
        val plan = Plan(
            id = "premium_monthly",
            name = "Premium (month)",
            priceKopecks = 29900,
            durationDays = 30,
            active = true
        )

        assertThat(plan.id).isEqualTo("premium_monthly")
        assertThat(plan.name).isEqualTo("Premium (month)")
        assertThat(plan.priceKopecks).isEqualTo(29900)
        assertThat(plan.durationDays).isEqualTo(30)
        assertThat(plan.active).isTrue()
    }

    @Test
    fun `should default active to true`() {
        val plan = Plan(
            id = "test_plan",
            name = "Test Plan",
            priceKopecks = 10000,
            durationDays = 7
        )

        assertThat(plan.active).isTrue()
    }

    @Test
    fun `should support inactive plans`() {
        val plan = Plan(
            id = "deprecated_plan",
            name = "Old Plan",
            priceKopecks = 5000,
            durationDays = 14,
            active = false
        )

        assertThat(plan.active).isFalse()
    }
}
