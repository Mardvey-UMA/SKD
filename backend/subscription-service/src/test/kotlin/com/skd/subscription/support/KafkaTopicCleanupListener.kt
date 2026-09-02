package com.skd.subscription.support

import com.skd.subscription.integration.IntegrationTestBase
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.test.context.TestContext
import org.springframework.test.context.support.AbstractTestExecutionListener

/**
 * Cleans up Kafka topics before each test method to ensure test isolation.
 * Registered via META-INF/spring/org.springframework.test.context.TestExecutionListener.
 */
class KafkaTopicCleanupListener : AbstractTestExecutionListener() {

    companion object {
        private val TOPICS_TO_CLEAN = listOf("subscription.changed")
    }

    override fun beforeTestMethod(testContext: TestContext) {
        if (!IntegrationTestBase::class.java.isAssignableFrom(testContext.testClass)) return

        val bootstrapServers = IntegrationTestBase.kafka.bootstrapServers
        val config = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)

        AdminClient.create(config).use { admin ->
            try {
                admin.deleteTopics(TOPICS_TO_CLEAN).all().get()
                // Wait for deletion to complete
                Thread.sleep(500)
            } catch (e: Exception) {
                // Topic may not exist yet, ignore
            }
            // Retry topic creation with backoff in case topic is still being deleted
            var lastException: Exception? = null
            for (attempt in 1..10) {
                try {
                    val newTopics = TOPICS_TO_CLEAN.map { NewTopic(it, 1, 1) }
                    admin.createTopics(newTopics).all().get()
                    return
                } catch (e: Exception) {
                    lastException = e
                    Thread.sleep(200L * attempt)
                }
            }
            // If we still can't create, log and continue (topic might already exist)
        }
    }
}
