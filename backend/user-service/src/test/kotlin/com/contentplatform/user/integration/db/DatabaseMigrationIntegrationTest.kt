package com.contentplatform.user.integration.db

import com.contentplatform.user.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

@Tag("integration")
class DatabaseMigrationIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `profiles table exists with correct columns`() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_name = 'profiles'
            ORDER BY ordinal_position
            """.trimIndent()
        )

        val columnNames = columns.map { it["column_name"] as String }
        assertThat(columnNames).containsExactlyInAnyOrder(
            "id", "email", "display_name", "avatar_url",
            "subscription_tier", "onboarding_completed", "categories",
            "created_at", "updated_at"
        )

        // Verify id column is UUID type
        val idColumn = columns.first { it["column_name"] == "id" }
        assertThat(idColumn["data_type"] as String).isEqualTo("uuid")
        assertThat(idColumn["is_nullable"] as String).isEqualTo("NO")

        // Verify email is NOT NULL
        val emailColumn = columns.first { it["column_name"] == "email" }
        assertThat(emailColumn["is_nullable"] as String).isEqualTo("NO")

        // Verify subscription_tier has default 'free'
        val tierColumn = columns.first { it["column_name"] == "subscription_tier" }
        assertThat(tierColumn["column_default"] as String).contains("free")

        // Verify onboarding_completed has default false
        val onboardingColumn = columns.first { it["column_name"] == "onboarding_completed" }
        assertThat(onboardingColumn["column_default"] as String).contains("false")
    }

    @Test
    fun `outbox table exists with correct columns`() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_name = 'outbox'
            ORDER BY ordinal_position
            """.trimIndent()
        )

        val columnNames = columns.map { it["column_name"] as String }
        assertThat(columnNames).containsExactlyInAnyOrder(
            "id", "aggregate_type", "aggregate_id", "event_type",
            "payload", "created_at", "published_at"
        )

        // Verify id is auto-generated (bigint / bigserial)
        val idColumn = columns.first { it["column_name"] == "id" }
        assertThat(idColumn["data_type"] as String).isEqualTo("bigint")

        // Verify published_at is nullable
        val publishedAtColumn = columns.first { it["column_name"] == "published_at" }
        assertThat(publishedAtColumn["is_nullable"] as String).isEqualTo("YES")

        // Verify aggregate_type, aggregate_id, event_type are NOT NULL
        val notNullColumns = listOf("aggregate_type", "aggregate_id", "event_type", "payload")
        for (colName in notNullColumns) {
            val col = columns.first { it["column_name"] == colName }
            assertThat(col["is_nullable"] as String)
                .describedAs("$colName should be NOT NULL")
                .isEqualTo("NO")
        }
    }

    @Test
    fun `outbox has idx_outbox_unpublished partial index`() {
        val indexes = jdbcTemplate.queryForList(
            """
            SELECT indexname, indexdef
            FROM pg_indexes
            WHERE tablename = 'outbox' AND indexname = 'idx_outbox_unpublished'
            """.trimIndent()
        )

        assertThat(indexes).hasSize(1)
        val indexDef = indexes[0]["indexdef"] as String
        // Verify it is a partial index on published_at IS NULL
        assertThat(indexDef.lowercase()).contains("where")
        assertThat(indexDef.lowercase()).contains("published_at is null")
    }

    @Test
    fun `profiles has primary key on id`() {
        val pks = jdbcTemplate.queryForList(
            """
            SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
            WHERE tc.table_name = 'profiles'
              AND tc.constraint_type = 'PRIMARY KEY'
            """.trimIndent()
        )

        assertThat(pks).hasSize(1)
        assertThat(pks[0]["column_name"] as String).isEqualTo("id")
    }
}
