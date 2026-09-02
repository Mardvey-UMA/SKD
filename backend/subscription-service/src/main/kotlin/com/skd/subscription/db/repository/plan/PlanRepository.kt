package com.skd.subscription.db.repository.plan

import com.skd.subscription.db.repository.plan.model.Plan
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.Repository
import org.springframework.stereotype.Repository as RepositoryAnnotation

@RepositoryAnnotation
interface PlanRepository : Repository<Plan, String> {

    @Query("SELECT * FROM plans WHERE id = :id")
    fun findById(id: String): Plan?

    @Query("SELECT * FROM plans WHERE id = :id AND active = TRUE")
    fun findByIdAndActiveTrue(id: String): Plan?

    fun save(plan: Plan): Plan
}
