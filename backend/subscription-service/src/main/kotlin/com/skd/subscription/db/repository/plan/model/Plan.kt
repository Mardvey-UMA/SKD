package com.skd.subscription.db.repository.plan.model

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("plans")
data class Plan(
    @Id
    @get:JvmName("getPlanId")
    val id: String,
    val name: String,
    @Column("price_kopecks")
    val priceKopecks: Int,
    @Column("duration_days")
    val durationDays: Int,
    val active: Boolean = true
) : Persistable<String> {

    override fun getId(): String = id

    override fun isNew(): Boolean = true
}
