package com.contentagg.parser.integration

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Base class for integration tests that require a real PostgreSQL database.
 *
 * Uses a singleton Testcontainers PostgreSQL container (pgvector/pgvector:pg16) to match
 * the production image specified in CLAUDE.md. The container is started once per JVM via
 * the companion object and reused across all test classes that extend this base.
 *
 * Using a singleton pattern (not @Testcontainers + @Container) ensures the container
 * stays alive for the entire test suite, even when Spring test context caching creates
 * multiple contexts (e.g., when some tests use @MockkBean).
 *
 * Spring Liquibase is enabled via the `integration-test` profile
 * (application-integration-test.yaml: spring.liquibase.enabled=true).
 * The `data_flow` schema is created by the init-data-flow-schema.sql script run
 * by Testcontainers before Liquibase migrations execute.
 *
 * Quartz auto-startup is set to false in the test profile and QuartzAutoConfiguration
 * is excluded. TestSchedulerConfiguration provides a MockK Scheduler bean so that
 * TaskReclaimer (which depends on Scheduler) can be instantiated without a real Quartz
 * JDBC cluster.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestSchedulerConfiguration::class)
@ActiveProfiles("integration-test")
abstract class AbstractIntegrationTest {

    companion object {
        /**
         * Singleton PostgreSQL container, started once per JVM and shared across all
         * test classes. Using `withReuse(true)` keeps the container alive across contexts.
         */
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("content_agg_db")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("init-data-flow-schema.sql")
            .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun overrideDataSourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                // Append currentSchema=data_flow so Spring Data JDBC resolves tables
                // against data_flow schema by default (matches production JDBC URL).
                "${postgres.jdbcUrl}&currentSchema=data_flow&stringtype=unspecified"
            }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.liquibase.url") { postgres.jdbcUrl }
            registry.add("spring.liquibase.user") { postgres.username }
            registry.add("spring.liquibase.password") { postgres.password }
        }
    }

    /**
     * Truncate parser_tasks table before each test.
     * Subclasses call this to ensure test isolation.
     */
    protected fun truncateParserTasks(jdbcTemplate: JdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE TABLE data_flow.parser_tasks RESTART IDENTITY CASCADE")
    }

    /**
     * Truncate raw_content table before each test.
     */
    protected fun truncateRawContent(jdbcTemplate: JdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE TABLE data_flow.raw_content RESTART IDENTITY CASCADE")
    }
}
