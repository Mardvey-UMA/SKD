package com.contentplatform.auth.integration.db

import com.contentplatform.auth.db.repository.UserRepository
import com.contentplatform.auth.db.repository.model.UserEntity
import com.contentplatform.auth.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

@Tag("integration")
class UserRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM email_verification_tokens")
        jdbcTemplate.execute("DELETE FROM password_reset_tokens")
        jdbcTemplate.execute("DELETE FROM refresh_tokens")
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM users")
    }

    @Nested
    inner class `save and find` {

        @Test
        fun `should save user and generate id`() {
            val user = UserEntity(
                email = "test@example.com",
                passwordHash = "hashed-password"
            )

            val saved = userRepository.save(user)

            assertThat(saved.id).isNotNull()
            assertThat(saved.email).isEqualTo("test@example.com")
            assertThat(saved.passwordHash).isEqualTo("hashed-password")
            assertThat(saved.emailVerified).isFalse()
            assertThat(saved.subscriptionTier).isEqualTo("free")
        }

        @Test
        fun `should find user by id`() {
            val saved = userRepository.save(
                UserEntity(email = "findme@example.com", passwordHash = "hash")
            )

            val found = userRepository.findById(saved.id!!)

            assertThat(found).isPresent
            assertThat(found.get().email).isEqualTo("findme@example.com")
        }

        @Test
        fun `should return empty when user not found by id`() {
            val found = userRepository.findById(java.util.UUID.randomUUID())
            assertThat(found).isEmpty()
        }
    }

    @Nested
    inner class `findByEmail` {

        @Test
        fun `should find user by email`() {
            userRepository.save(
                UserEntity(email = "lookup@example.com", passwordHash = "hash")
            )

            val found = userRepository.findByEmail("lookup@example.com")

            assertThat(found).isNotNull
            assertThat(found!!.email).isEqualTo("lookup@example.com")
        }

        @Test
        fun `should return null when email not found`() {
            val found = userRepository.findByEmail("nonexistent@example.com")
            assertThat(found).isNull()
        }
    }

    @Nested
    inner class `unique email constraint` {

        @Test
        fun `should reject duplicate email`() {
            userRepository.save(
                UserEntity(email = "duplicate@example.com", passwordHash = "hash1")
            )

            assertThatThrownBy {
                userRepository.save(
                    UserEntity(email = "duplicate@example.com", passwordHash = "hash2")
                )
            }.hasCauseInstanceOf(DataIntegrityViolationException::class.java)
        }
    }

    @Nested
    inner class `update` {

        @Test
        fun `should update user fields`() {
            val saved = userRepository.save(
                UserEntity(email = "update@example.com", passwordHash = "hash")
            )

            val updated = userRepository.save(
                saved.copy(emailVerified = true, subscriptionTier = "premium")
            )

            val found = userRepository.findById(updated.id!!)
            assertThat(found).isPresent
            assertThat(found.get().emailVerified).isTrue()
            assertThat(found.get().subscriptionTier).isEqualTo("premium")
        }
    }

    @Nested
    inner class `delete` {

        @Test
        fun `should delete user by id`() {
            val saved = userRepository.save(
                UserEntity(email = "delete@example.com", passwordHash = "hash")
            )

            userRepository.deleteById(saved.id!!)

            assertThat(userRepository.findById(saved.id!!)).isEmpty()
        }
    }
}
