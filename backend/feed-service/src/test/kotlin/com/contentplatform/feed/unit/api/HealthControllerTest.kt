package com.contentplatform.feed.unit.api

import com.contentplatform.feed.api.controller.HealthController
import com.contentplatform.feed.configuration.GatewayProperties
import com.contentplatform.feed.configuration.InternalSecurityProperties
import com.contentplatform.feed.security.GatewaySignatureFilter
import com.contentplatform.feed.security.InternalSignatureFilter
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

@Tag("unit")
@WebMvcTest(HealthController::class)
@Import(GatewaySignatureFilter::class, InternalSignatureFilter::class, GatewayProperties::class, InternalSecurityProperties::class)
class HealthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var dataSource: DataSource

    @MockkBean
    lateinit var redisConnectionFactory: RedisConnectionFactory

    @Test
    fun `should return 200 with health status without HMAC`() {
        // Health endpoint must be accessible without HMAC signature
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status", `is`("ok")))
            .andExpect(jsonPath("$.service", `is`("feed-service")))
    }

    @Test
    fun `should include checks object in health response`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.checks").exists())
    }
}
