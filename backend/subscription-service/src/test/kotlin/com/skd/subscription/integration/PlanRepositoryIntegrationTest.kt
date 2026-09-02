package com.skd.subscription.integration

import com.skd.subscription.db.repository.plan.PlanRepository
import com.skd.subscription.db.repository.plan.model.Plan
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@Tag("integration")
@Transactional
class PlanRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var planRepository: PlanRepository

    @Test
    fun `should find seeded premium_monthly plan by id`() {
        val plan = planRepository.findById("premium_monthly")

        assertThat(plan).isNotNull
        assertThat(plan!!.name).isEqualTo("Premium (month)")
        assertThat(plan.priceKopecks).isEqualTo(29900)
        assertThat(plan.durationDays).isEqualTo(30)
        assertThat(plan.active).isTrue()
    }

    @Test
    fun `should find seeded premium_yearly plan by id`() {
        val plan = planRepository.findById("premium_yearly")

        assertThat(plan).isNotNull
        assertThat(plan!!.name).isEqualTo("Premium (year)")
        assertThat(plan.priceKopecks).isEqualTo(299000)
        assertThat(plan.durationDays).isEqualTo(365)
    }

    @Test
    fun `should return null for non-existent plan`() {
        val plan = planRepository.findById("non_existent")

        assertThat(plan).isNull()
    }

    @Test
    fun `should find active plan by id`() {
        val plan = planRepository.findByIdAndActiveTrue("premium_monthly")

        assertThat(plan).isNotNull
        assertThat(plan!!.active).isTrue()
    }

    @Test
    fun `should not find inactive plan via findByIdAndActiveTrue`() {
        val inactivePlan = Plan(
            id = "inactive_plan",
            name = "Inactive",
            priceKopecks = 100,
            durationDays = 1,
            active = false
        )
        planRepository.save(inactivePlan)

        val result = planRepository.findByIdAndActiveTrue("inactive_plan")

        assertThat(result).isNull()
    }
}
