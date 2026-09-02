---
name: Test Infrastructure Lessons
description: Critical fixes for Spring Security + Kafka + Quartz integration test setup in this service
type: feedback
---

## Spring Security Filter Auto-Registration

`@Component OncePerRequestFilter` is registered BOTH as a regular servlet filter AND inside Spring Security, causing double execution and unexpected 403 responses.

**Why:** When `GatewaySignatureFilter` runs outside Spring Security first, it sends `sendError(401)` and returns. Then Spring Security runs and denies access with 403.

**How to apply:** Always add `FilterRegistrationBean(filter).apply { isEnabled = false }` bean in SecurityConfiguration to prevent Spring Boot from auto-registering security filters as servlet filters.

## GatewaySignatureFilter Must Set Authentication

After successful HMAC validation, must set `SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(...)`. Otherwise Spring Security's `anyRequest().authenticated()` blocks with 403.

**How to apply:** Always set Authentication after any custom authentication filter that validates requests.

## /error Must Be in permitAll

Spring Boot forwards `sendError()` calls to `/error` endpoint. With `anyRequest().authenticated()`, this endpoint is blocked (→ 403). Add `/error` to `permitAll`.

## Quartz driverDelegateClass Breaks RAMJobStore in Tests

`org.quartz.jobStore.driverDelegateClass` set in main `application.yml` Spring Quartz properties is inherited by tests even when `job-store-type: memory` is configured. `RAMJobStore` doesn't have that property → `NoSuchMethodException`.

**How to apply:** Never put JDBC-specific Quartz properties in main `application.yml`. Use profiles or override in `application-prod.yml`.

## Kafka Consumer Partition Assignment Race Condition

Using `AUTO_OFFSET_RESET_CONFIG = "latest"` requires waiting until partition assignment completes BEFORE the test sends messages. A simple `consumer.poll(500ms)` is not reliable.

**How to apply:** Use this pattern in `@BeforeEach`:
```kotlin
consumer.subscribe(listOf("topic"))
val deadline = System.currentTimeMillis() + 5000L
while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
    consumer.poll(Duration.ofMillis(100))
}
```

## Kafka Consumer: earliest vs latest in Tests

Using `earliest` offset in tests causes cross-test contamination — consumers receive messages from previous tests in the same topic. Use `latest` + partition assignment wait.

**How to apply:** For tests that check specific message keys, always use `latest` + assignment wait. For tests checking presence (contains), can use `earliest` but assertions should use `contains` not `first()`.
