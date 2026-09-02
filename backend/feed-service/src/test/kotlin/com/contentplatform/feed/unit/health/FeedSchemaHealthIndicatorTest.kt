package com.contentplatform.feed.unit.health

import com.contentplatform.feed.infrastructure.health.FeedSchemaHealthIndicator
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import javax.sql.DataSource

@Tag("unit")
class FeedSchemaHealthIndicatorTest {

    private val dataSource: DataSource = mockk()
    private val indicator = FeedSchemaHealthIndicator(dataSource)

    @Test
    fun `returns UP when query executes successfully`() {
        val connection = mockk<Connection>(relaxed = true)
        val statement = mockk<Statement>(relaxed = true)
        val resultSet = mockk<ResultSet>(relaxed = true)

        every { dataSource.connection } returns connection
        every { connection.createStatement() } returns statement
        every { statement.executeQuery(any()) } returns resultSet

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status.UP)
    }

    @Test
    fun `returns DOWN when query throws exception`() {
        every { dataSource.connection } throws RuntimeException("Connection refused")

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status.DOWN)
        assertThat(health.details["query"]).isEqualTo("SELECT 1 FROM feed.feed_requests LIMIT 1")
    }

    @Test
    fun `returns DOWN when feed schema table is missing`() {
        val connection = mockk<Connection>(relaxed = true)
        val statement = mockk<Statement>(relaxed = true)

        every { dataSource.connection } returns connection
        every { connection.createStatement() } returns statement
        every { statement.executeQuery(any()) } throws RuntimeException("relation \"feed.feed_requests\" does not exist")

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status.DOWN)
    }
}
