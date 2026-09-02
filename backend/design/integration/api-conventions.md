# API Conventions — Unified Standards Across All Services

**Date:** 2026-04-04

Standards that all services MUST follow for consistency.

---

## 1. Unified Error Response Format

All services return errors in the same JSON format:

```json
{
  "error": "error_code",
  "message": "Human-readable description in Russian",
  "request_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "timestamp": "2026-04-04T12:00:00Z"
}
```

| Field | Type | Always present | Description |
|-------|------|----------------|-------------|
| `error` | string | yes | Machine-readable error code (snake_case) |
| `message` | string | yes | Human-readable message (for frontend display or logging) |
| `request_id` | string | yes | X-Request-Id from gateway (for tracing) |
| `timestamp` | ISO8601 | yes | Server time of the error |
| `details` | object | no | Optional: validation errors, field-level details |

### Standard Error Codes

| HTTP Status | error code | When |
|------------|------------|------|
| 400 | `bad_request` | Malformed request body or invalid parameters |
| 401 | `unauthorized` | Missing or invalid authentication |
| 401 | `token_expired` | JWT expired |
| 401 | `token_revoked` | JWT revoked |
| 403 | `forbidden` | Authenticated but not authorized |
| 403 | `subscription_required` | Route requires active subscription |
| 404 | `not_found` | Resource not found |
| 409 | `conflict` | Resource already exists (e.g., duplicate email, active subscription) |
| 422 | `validation_error` | Request validation failed (details in `details` field) |
| 429 | `rate_limit_exceeded` | Too many requests (includes `Retry-After` header) |
| 500 | `internal_error` | Unexpected server error |
| 502 | `service_unavailable` | Upstream service did not respond |
| 503 | `temporarily_unavailable` | Service overloaded or in maintenance |
| 504 | `gateway_timeout` | Upstream service timed out |

### Validation Error Example (422)

```json
{
  "error": "validation_error",
  "message": "Validation failed",
  "request_id": "...",
  "timestamp": "...",
  "details": {
    "fields": {
      "email": "Invalid email format",
      "password": "Must be at least 8 characters"
    }
  }
}
```

### Spring Implementation

```kotlin
// Shared library or copy to each service
data class ErrorResponse(
    val error: String,
    val message: String,
    @JsonProperty("request_id") val requestId: String,
    val timestamp: Instant = Instant.now(),
    val details: Any? = null
)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(404).body(ErrorResponse(
            error = "not_found",
            message = ex.message ?: "Resource not found",
            requestId = request.getHeader("X-Request-Id") ?: ""
        ))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "") }
        return ResponseEntity.status(422).body(ErrorResponse(
            error = "validation_error",
            message = "Validation failed",
            requestId = request.getHeader("X-Request-Id") ?: "",
            details = mapOf("fields" to fieldErrors)
        ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", ex)
        return ResponseEntity.status(500).body(ErrorResponse(
            error = "internal_error",
            message = "Internal server error",
            requestId = request.getHeader("X-Request-Id") ?: ""
        ))
    }
}
```

---

## 2. Health Check Standard

All services expose `GET /health` (no authentication required, excluded from gateway filters).

```json
// Healthy
{
  "status": "ok",
  "service": "auth-service",
  "checks": {
    "database": "connected",
    "kafka": "connected",
    "redis": "connected"
  }
}

// Degraded
{
  "status": "degraded",
  "service": "feed-service",
  "checks": {
    "redis": "disconnected"
  }
}
```

| Status | HTTP Code | Meaning |
|--------|-----------|---------|
| `ok` | 200 | All dependencies connected |
| `degraded` | 503 | One or more dependencies unavailable |

### Health checks per service

| Service | DB | Redis | Kafka | Other |
|---------|----|----|-------|-------|
| API Gateway | — | yes | — | JWKS loaded |
| auth-service | yes | yes | yes | — |
| user-service | yes | — | yes | — |
| user-interactions-service | yes | — | yes | — |
| feed-service | — | yes | yes | — |
| subscription-service | yes | — | yes | — |

### Gateway health route

```yaml
gateway:
  routes:
    # ... other routes
    - path-prefix: /health
      target: self                      # gateway's own health
      public: true
```

Internal services' health endpoints are NOT proxied through gateway — they are called directly by Docker/K8s health probes.

---

## 3. OpenAPI / Swagger

All services auto-generate OpenAPI specs using `springdoc-openapi`.

### Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.0")
    // For WebFlux (gateway only):
    // implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.0")
}
```

### Configuration

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true                       # disable in production if needed
  info:
    title: ${spring.application.name}
    version: ${APP_VERSION:0.1.0}
```

### Endpoint Annotations

```kotlin
@Operation(
    summary = "Get paginated feed",
    description = "Returns user's personalized content feed with cursor-based pagination"
)
@ApiResponses(
    ApiResponse(responseCode = "200", description = "Feed page"),
    ApiResponse(responseCode = "401", description = "Unauthorized")
)
@GetMapping("/api/feed")
fun getFeed(
    @RequestHeader("X-User-Id") userId: UUID,
    @Parameter(description = "Opaque cursor from previous response") @RequestParam cursor: String?,
    @Parameter(description = "Force refresh — invalidate cache") @RequestParam refresh: Boolean?
): FeedResponse
```

### Access

| Environment | URL | Access |
|------------|-----|--------|
| Dev | `http://localhost:8080/swagger-ui.html` | Open |
| Production | Disabled or behind auth | — |

**Each service generates its own OpenAPI spec.** No central aggregation needed for MVP. Frontend developers access each service's Swagger UI directly during development.

### Gateway does NOT aggregate specs

Gateway proxies requests but does NOT merge OpenAPI specs from downstream services. For MVP, frontend developers use individual service Swagger UIs:
- auth: `http://localhost:8081/swagger-ui.html` (or mapped port)
- user: `http://localhost:8082/swagger-ui.html`
- feed: `http://localhost:8083/swagger-ui.html`
- etc.

Port mapping defined in Docker Compose (see deployment docs).
