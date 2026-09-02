# Frontend ↔ Backend API Contracts

**Base URL (dev):** `http://localhost:8080`  
**All authenticated requests require:** `Authorization: Bearer {accessToken}`  
**All requests through API Gateway** — never call backend services directly.

---

## Table of Contents

1. [Authentication Flow](#1-authentication-flow)
2. [User Profile & Onboarding](#2-user-profile--onboarding)
3. [Feed](#3-feed)
4. [Collections (Bookmarks / Likes / Dislikes)](#4-collections-bookmarks--likes--dislikes)
5. [User Interaction Events](#5-user-interaction-events)
6. [Subscription](#6-subscription)
7. [Error Format](#7-error-format)
8. [Token Management](#8-token-management)
9. [Content Item Schema](#9-content-item-schema)
10. [Spaces (Phase 3)](#10-spaces-phase-3)
11. [Blocked Sources (Phase 3)](#11-blocked-sources-phase-3)
12. [My Additions (Phase 3)](#12-my-additions-phase-3)

---

## 1. Authentication Flow

### 1.1 Register

```
POST /api/auth/register
Content-Type: application/json
```

**Request:**
```json
{
  "email": "user@example.com",
  "password": "StrongPass1"
}
```

**Password rules:** min 8 chars, at least 1 uppercase, 1 lowercase, 1 digit.

**Response 201:**
```json
{
  "message": "Registration successful. Please check your email to verify your account.",
  "email": "user@example.com"
}
```

**Errors:**
| Status | error | Reason |
|--------|-------|--------|
| 409 | `duplicate_email` | Email already registered |
| 422 | `validation_error` | Invalid email format or weak password |

---

### 1.2 Verify Email (legacy — link-based)

> **LEGACY — for backward compatibility only.** Use the code-based endpoint below for new flows.

```
GET /api/auth/verify?token={verificationToken}
```

**Response 200:**
```json
{
  "message": "Email verified successfully.",
  "email": "user@example.com"
}
```

**Errors:**
| Status | error | Reason |
|--------|-------|--------|
| 400 | `token_expired` | Token expired or already used |
| 404 | `token_not_found` | Token does not exist |

---

### 1.2a Verify Email by Code (POST — active flow)

```
POST /api/auth/verify
Content-Type: application/json
```

**Request:**
```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

**Response 200:**
```json
{
  "message": "Email verified",
  "email": "user@example.com"
}
```

**Errors:**
| Status | error | Reason |
|--------|-------|--------|
| 400 | `INVALID_CODE` | Wrong or expired code |
| 404 | `USER_NOT_FOUND` | No user with this email |
| 429 | `TOO_MANY_ATTEMPTS` | Too many failed attempts; response includes `retryAfterSeconds` |

> **Dev mode** (`AUTH_DEV_MODE=true`): code `000000` is always accepted. No email is sent — the code is logged on the backend (`docker logs auth-service`).

---

### 1.2b Resend Verification Code

```
POST /api/auth/resend-verification
Content-Type: application/json
```

**Request:**
```json
{
  "email": "user@example.com"
}
```

**Response 200:**
```json
{
  "message": "Code sent",
  "cooldownSeconds": 60
}
```

**Errors:**
| Status | error | Reason |
|--------|-------|--------|
| 404 | `USER_NOT_FOUND` | No user with this email |
| 429 | `RESEND_COOLDOWN` | Cooldown active; response includes `retryAfterSeconds` |

---

### 1.3 Login

```
POST /api/auth/login
Content-Type: application/json
```

**Request:**
```json
{
  "email": "user@example.com",
  "password": "StrongPass1"
}
```

**Response 200:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiJ9...",
  "refresh_token": "d4f3a2b1...",
  "token_type": "Bearer",
  "expires_in": 900
}
```

`access_token` TTL: **15 minutes** (900 seconds).  
`refresh_token` TTL: **30 days**.

**Errors:**
| Status | error | Reason |
|--------|-------|--------|
| 401 | `invalid_credentials` | Wrong email or password |
| 403 | `email_not_verified` | Email not yet verified |

---

### 1.4 Refresh Token

```
POST /api/auth/refresh
Content-Type: application/json
```

**Request:**
```json
{
  "refresh_token": "d4f3a2b1..."
}
```

**Response 200:** Same structure as login response.  
Old refresh token is invalidated — store the new pair.

**Errors:**
| Status | error | Reason |
|--------|-------|--------|
| 401 | `invalid_token` | Expired or invalid refresh token |

---

### 1.5 Logout

```
POST /api/auth/logout
Authorization: Bearer {accessToken}
```

**Response 200:**
```json
{ "message": "Logged out successfully." }
```

Revokes access token (via Redis jti blacklist) and deletes all refresh tokens for the user.  
After logout — clear stored tokens locally and redirect to login.

---

### 1.6 Password Reset (Forgot Password)

**Step 1 — Request reset email:**
```
POST /api/auth/password/reset-request
Content-Type: application/json
```
```json
{ "email": "user@example.com" }
```
**Response 200:** `{ "message": "If this email is registered, a reset link has been sent." }`  
Always returns 200 regardless of whether email exists (security).

**Step 2 — Submit new password:**
```
POST /api/auth/password/reset
Content-Type: application/json
```
```json
{
  "token": "reset-token-from-email",
  "new_password": "NewStrongPass1"
}
```
**Response 200:** `{ "message": "Password changed successfully." }`

---

### 1.7 Change Password (Authenticated)

```
POST /api/auth/password/change
Authorization: Bearer {accessToken}
Content-Type: application/json
```
```json
{
  "current_password": "OldPass1",
  "new_password": "NewStrongPass1"
}
```
**Response 200:** `{ "message": "Password changed successfully." }`

**Errors:**
| Status | error | Reason |
|--------|-------|--------|
| 401 | `invalid_credentials` | Current password wrong |
| 422 | `validation_error` | New password too weak |

---

## 2. User Profile & Onboarding

### 2.1 Get Current User Profile

```
GET /api/users/me
Authorization: Bearer {accessToken}
```

**Response 200:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "display_name": "John Doe",
  "avatar_url": "https://example.com/avatar.jpg",
  "subscription_tier": "free",
  "onboarding_completed": false,
  "created_at": "2025-01-15T10:30:00Z"
}
```

`subscription_tier`: `"free"` | `"premium"`  
`display_name` and `avatar_url` can be `null`.

---

### 2.2 Update Profile

```
PUT /api/users/me
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Request (all fields optional):**
```json
{
  "display_name": "Jane Doe",
  "avatar_url": "https://example.com/new-avatar.jpg"
}
```

**Response 200:** Same as GET /api/users/me.

---

### 2.3 Get Available Categories (for Onboarding)

```
GET /api/users/me/categories
Authorization: Bearer {accessToken}
```

**Response 200:**
```json
{
  "categories": [
    { "id": "технологии", "name": "Технологии", "icon": "laptop" },
    { "id": "спорт",      "name": "Спорт",      "icon": "sports" },
    { "id": "наука",      "name": "Наука",       "icon": "science" },
    { "id": "политика",   "name": "Политика",    "icon": "politics" },
    { "id": "экономика",  "name": "Экономика",   "icon": "economics" },
    { "id": "культура",   "name": "Культура",    "icon": "culture" },
    { "id": "общество",   "name": "Общество",    "icon": "society" },
    { "id": "бизнес",     "name": "Бизнес",      "icon": "business" },
    { "id": "финансы",    "name": "Финансы",     "icon": "finance" },
    { "id": "здоровье",   "name": "Здоровье",    "icon": "health" },
    { "id": "развлечения","name": "Развлечения", "icon": "entertainment" },
    { "id": "образование","name": "Образование", "icon": "education" },
    { "id": "международные новости", "name": "Международные новости", "icon": "world" },
    { "id": "происшествия","name": "Происшествия","icon": "alert" },
    { "id": "криминал",   "name": "Криминал",    "icon": "crime" },
    { "id": "армия",      "name": "Армия",       "icon": "military" },
    { "id": "природа",    "name": "Природа",     "icon": "nature" },
    { "id": "транспорт",  "name": "Транспорт",   "icon": "transport" }
  ],
  "min_select": 3,
  "max_select": 5
}
```

User must select **3–5** categories during onboarding.

---

### 2.4 Complete Onboarding

```
POST /api/users/me/onboarding
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Request:**
```json
{
  "categories": ["технологии", "наука", "бизнес"],
  "source_content_ids": []
}
```

`categories`: required, 3–5 items from the list above.  
`source_content_ids`: optional, UUIDs of content the user has already seen/liked (used for cold-start profile seeding, max 10).

**Response 200:**
```json
{
  "onboardingCompleted": true,
  "message": "Onboarding completed successfully."
}
```

**Errors:**
| Status | error | Reason |
|--------|-------|--------|
| 409 | `onboarding_already_completed` | User already onboarded |
| 422 | `validation_error` | < 3 or > 5 categories |

**Side effect:** Creates recommendation profile in rec-system. After this call, `onboarding_completed` in user profile becomes `true`.

---

## 3. Feed

### 3.1 Get Personalized Feed

```
GET /api/feed?cursor={cursor}&refresh={refresh}
Authorization: Bearer {accessToken}
```

**Query parameters:**
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `cursor` | string | — | Opaque pagination cursor from previous response |
| `refresh` | boolean | `false` | `true` = invalidate cache and fetch fresh recommendations |

**Response 200:**
```json
{
  "items": [
    { ...ContentBatchItem... },
    { ...ContentBatchItem... }
  ],
  "cursor": "eyJvIjo0MH0=",
  "hasNext": true
}
```

`cursor` is `null` when there is no more data (`hasNext: false`).  
See [Content Item Schema](#9-content-item-schema) for `ContentBatchItem` structure.

**Response Headers:**
| Header | Value | Description |
|--------|-------|-------------|
| `X-Request-Id` | UUID | Unique request identifier injected by api-gateway. Capture this value and include it as `feed_request_id` in subsequent interaction events to enable feed → interaction attribution. |
| `X-Feed-Source` | `personalized` \| `cold_start` \| `cached` \| `fallback` | Feed generation source determined by feed-service. Useful for debugging recommendation quality. |

> **Important for interaction tracking:** Store the `X-Request-Id` value from each feed response and pass it as `feed_request_id` when sending `POST /api/interactions/batch` events. This links each user action back to the specific feed request that surfaced the content.

**Pagination strategy:**
- On initial load: no `cursor` parameter
- On next page: pass `cursor` from previous response
- On pull-to-refresh: `refresh=true`, no `cursor`

---

### 3.2 Get Single Content Item

```
GET /api/feed/content/{contentId}
Authorization: Bearer {accessToken}
```

**Response 200:** Single `ContentBatchItem` (see schema below).

**Errors:**
| Status | Reason |
|--------|--------|
| 404 | Content not found |

---

### 3.3 Get Content Interaction Status

```
GET /api/feed/content/{contentId}/status
Authorization: Bearer {accessToken}
```

**Response 200:**
```json
{
  "liked": false,
  "disliked": false,
  "bookmarked": true
}
```

Use this to initialize UI state (heart, dislike, bookmark icons) when opening an article.

---

## 4. Collections (Bookmarks / Likes / Dislikes)

> **Note:** Like and Dislike are **mutually exclusive** — adding a like removes any existing dislike, and vice versa. Bookmark is independent.

### 4.1 Bookmarks

```
POST   /api/feed/bookmarks/{contentId}   → 201 { "content_id": "uuid", "action": "bookmarked" }
DELETE /api/feed/bookmarks/{contentId}   → 200 { "content_id": "uuid", "action": "unbookmarked" }
GET    /api/feed/bookmarks?cursor={cur}  → 200 { "items": [...], "cursor": "...", "hasNext": bool }
```

### 4.2 Likes

```
POST   /api/feed/likes/{contentId}   → 201 { "content_id": "uuid", "action": "liked" }
DELETE /api/feed/likes/{contentId}   → 200 { "content_id": "uuid", "action": "unliked" }
GET    /api/feed/likes?cursor={cur}  → 200 { "items": [...], "cursor": "...", "hasNext": bool }
```

### 4.3 Dislikes

```
POST   /api/feed/dislikes/{contentId}   → 201 { "content_id": "uuid", "action": "disliked" }
DELETE /api/feed/dislikes/{contentId}   → 200 { "content_id": "uuid", "action": "undisliked" }
GET    /api/feed/dislikes?cursor={cur}  → 200 { "items": [...], "cursor": "...", "hasNext": bool }
```

**List response structure** (same for all three):
```json
{
  "items": [ { ...ContentBatchItem... } ],
  "cursor": "eyJvIjoyfQ==",
  "hasNext": true
}
```

---

## 5. User Interaction Events

This is the **most important contract** for recommendation quality. Every meaningful user action must be sent to this endpoint so the recommendation system can learn from behavior.

```
POST /api/interactions/batch
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Canonical `action_type` vocabulary (post-MVP hardening 2026-04-20)**: `IMPRESSION`, `OPEN`, `CLOSE`, `LIKE`, `DISLIKE`, `BOOKMARK`. Backend also accepts legacy names (`VIEW→IMPRESSION`, `CLICK→OPEN`, `SCROLL_PAST→CLOSE`, `SAVE→BOOKMARK`, `HIDE→DISLIKE`, `SHARE→BOOKMARK`) and persists the canonical value. New clients should always emit canonical names.

**Request (v1 — minimal, always valid):**
```json
{
  "events": [
    {
      "content_id": "550e8400-e29b-41d4-a716-446655440001",
      "action_type": "IMPRESSION",
      "duration_sec": 45.5,
      "timestamp": "2026-04-20T14:30:00Z"
    },
    {
      "content_id": "550e8400-e29b-41d4-a716-446655440002",
      "action_type": "OPEN",
      "duration_sec": null,
      "timestamp": "2026-04-20T14:30:15Z"
    }
  ]
}
```

**Request (v2 — with optional attribution fields):**
```json
{
  "events": [
    {
      "content_id": "550e8400-e29b-41d4-a716-446655440001",
      "action_type": "LIKE",
      "duration_sec": 45,
      "timestamp": "2026-04-20T12:00:00Z",
      "schema_version": 2,
      "feed_request_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "position_in_feed": 3,
      "device_type": "android",
      "app_version": "1.2.3",
      "ab_bucket": 0,
      "scroll_depth": 0.75,
      "metadata": {"source": "feed_card"}
    }
  ]
}
```

**v2 optional fields per event** (all nullable, omit if unknown):

| Field | Type | Description |
|-------|------|-------------|
| `schema_version` | int | Set to `2` when any v2 fields are present. Absent = v1. |
| `feed_request_id` | string (UUID) | The `X-Request-Id` from the `GET /api/feed` response that surfaced this content. Enables feed → interaction attribution. |
| `position_in_feed` | int | **1-indexed** position of the content item in the feed response (1 = first card). |
| `device_type` | string | Platform identifier: `"android"` or `"web"`. |
| `app_version` | string | App version string (e.g. `"1.2.3"`) from `package_info_plus`. |
| `ab_bucket` | int | A/B experiment bucket number. Default `0` = control group. |
| `scroll_depth` | float | Fraction [0.0, 1.0] of article content scrolled. Relevant for `CLOSE` events. |
| `metadata` | object | Open extension point for future fields. |

**Constraints:**
- Max **100 events per batch**
- `timestamp` must be within **last 24 hours** and **not in the future** (60s tolerance)
- `duration_sec` is optional (float), include when known
- v2 fields are all optional — v1 events (no `schema_version`) remain valid

**Response 202:**
```json
{
  "accepted": 2,
  "rejected": 0
}
```

---

### 5.1 Interaction Event Types — Complete Reference

**Canonical 6** — use these exact strings (uppercase). Backend maps legacy names to canonical for back-compat but new clients should never emit legacy.

| `action_type` | When to send | `duration_sec` / `scroll_depth` | Signal strength | Triggers recs update |
|---------------|-------------|----------------|-----------------|---------------------|
| `IMPRESSION` | Article card appeared in viewport | **Required** — total milliseconds visible (use `duration_sec` × 1000 semantics) | Weak positive if ≥ threshold, weak negative otherwise | No |
| `OPEN` | User tapped/opened the full article | Optional — pair with subsequent `CLOSE` to derive read time | Conditional (pairs with CLOSE for signal) | No |
| `CLOSE` | User closed the article (navigated back) | **Recommended** — `scroll_depth` in [0.0, 1.0]; `duration_sec` = read time | Weak–strong signal depending on scroll + duration (see weights table) | **Yes** (eventual) |
| `LIKE` | User tapped ♡ Like button | — | **Strong positive** | **Yes** |
| `DISLIKE` | User tapped ✕ Dislike button | — | **Strong negative** | **Yes** |
| `BOOKMARK` | User tapped 🔖 Bookmark button | — | **Strongest positive** | **Yes** |

> **Note:** `LIKE`, `DISLIKE`, `BOOKMARK` are additionally sent via the dedicated collection endpoints (`POST /api/feed/likes|dislikes|bookmarks/{id}`) to persist to the user's collections. The batch interaction events drive the recommendation system's signal classifier.

**Legacy names accepted (mapped server-side to canonical)**: `VIEW→IMPRESSION`, `CLICK→OPEN`, `SCROLL_PAST→CLOSE`, `SAVE→BOOKMARK`, `HIDE→DISLIKE`, `SHARE→BOOKMARK`. Existing clients continue to work unchanged. **DB + Kafka persist the canonical value.**

**Rec system signal weights (for understanding importance):**
```
BOOKMARK    → +0.80   (strongest positive)
LIKE        → +0.60
IMPRESSION  → +0.15 (if duration >= threshold) or -0.05 (skim)
OPEN        → conditional: pair with CLOSE within orphan window (weight from pair); orphan → 0.0
CLOSE       → +0.50 (full read: high scroll + long duration)
               +0.40 (half read: moderate scroll + duration)
               +0.10 (other close)
               -0.20 (fast close < threshold)
DISLIKE     → -0.70
```

The CLOSE classification branches on `scroll_depth` and `duration_sec`. `scroll_depth` is now persisted end-to-end (P2) and drives full/half-read signal quality. Omitting `scroll_depth` degrades CLOSE to the `other` bucket (+0.10).

---

### 5.2 Batching Strategy (recommended)

Do **not** send one event per HTTP request. Batch events client-side:

```
Strategy: send batch every 30 seconds OR when batch reaches 50 events
          (whichever comes first)

On app background/close: flush remaining events immediately
On offline: queue events locally (secure storage), send on next connection
```

---

### 5.3 What to Track — Implementation Checklist

| UI Action | action_type | Notes |
|-----------|-------------|-------|
| Article card appears in viewport (any duration) | `IMPRESSION` | `duration_sec` = seconds visible |
| User taps article card to open full article | `OPEN` | Pair with subsequent `CLOSE` |
| User closes article / navigates back | `CLOSE` | `duration_sec` = read time; `scroll_depth` = fraction scrolled [0.0, 1.0] |
| User taps ♡ Like button | `LIKE` | Also call `POST /api/feed/likes/{id}` |
| User taps ✕ Dislike button | `DISLIKE` | Also call `POST /api/feed/dislikes/{id}` |
| User taps 🔖 Bookmark button | `BOOKMARK` | Also call `POST /api/feed/bookmarks/{id}` |

> **Important:** Like/Dislike/Bookmark also require the corresponding REST API calls to `/api/feed/likes|dislikes|bookmarks/{id}` — the interaction event alone does NOT persist these to the user's collections. The batch event drives the recommendation system's signal classifier.

> **Scroll-depth signal:** `scroll_depth` on CLOSE events is the key input for full-read vs half-read classification. Always populate for CLOSE events when article has scrollable content.

---

## 6. Subscription

### 6.1 Get Subscription Status

```
GET /api/subscription/status
Authorization: Bearer {accessToken}
```

**Response 200:**
```json
{
  "tier": "free",
  "status": "active",
  "planId": null,
  "expiresAt": null,
  "autoRenew": false
}
```

For premium users:
```json
{
  "tier": "premium",
  "status": "active",
  "planId": "premium_monthly",
  "expiresAt": "2025-05-09T00:00:00Z",
  "autoRenew": true
}
```

`tier`: `"free"` | `"premium"`  
`status`: `"active"` | `"expired"` | `"pending"`  
`planId`: `"premium_monthly"` | `"premium_yearly"` | `null`

---

### 6.2 Start Checkout (Upgrade to Premium)

```
POST /api/subscription/checkout
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Request:**
```json
{ "plan": "premium_monthly" }
```

Plans:
| planId | Price | Duration |
|--------|-------|----------|
| `premium_monthly` | ₽299/mo | 30 days |
| `premium_yearly` | ₽2990/yr | 365 days |

**Response 200:**
```json
{
  "paymentId": "22e12f66-000f-5000-8000-18db351245c7",
  "confirmationUrl": "https://yookassa.ru/checkout/payments/22e12f66..."
}
```

Open `confirmationUrl` in a WebView or external browser. After payment completes, the subscription status updates automatically via YooKassa webhook.

---

### 6.3 Cancel Auto-Renewal

```
POST /api/subscription/cancel
Authorization: Bearer {accessToken}
```

**Response 200:**
```json
{ "status": "cancelled" }
```

Premium access remains until `expiresAt`, but will not auto-renew.

---

## 7. Error Format

All errors follow this structure:
```json
{
  "error": "error_code",
  "message": "Human-readable description",
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-04-09T14:30:00Z",
  "details": {}
}
```

**Common HTTP status codes:**
| Status | Meaning |
|--------|---------|
| 200 | Success |
| 201 | Created |
| 202 | Accepted (async processing) |
| 400 | Bad request (expired/invalid token) |
| 401 | Unauthorized (missing/invalid JWT or wrong credentials) |
| 403 | Forbidden (email not verified, insufficient role) |
| 404 | Resource not found |
| 409 | Conflict (duplicate email, already onboarded) |
| 422 | Validation error (field rules) |
| 429 | Rate limit exceeded (100 req/60s per user) |
| 502 | Upstream service unavailable |
| 503 | Service unavailable |

---

## 8. Token Management

### Storage
- `access_token` → `FlutterSecureStorage` key: `access_token`
- `refresh_token` → `FlutterSecureStorage` key: `refresh_token`

### Refresh Flow (already implemented in `RefreshTokenInterceptor`)
```
Request → 401 response
  → send POST /api/auth/refresh with stored refresh_token
  → on success: store new access_token + refresh_token, retry original request
  → on failure (401 again): clear all tokens → redirect to /login
```

### Endpoints that DO NOT need Authorization header
```
POST /api/auth/register
GET  /api/auth/verify          (legacy)
POST /api/auth/verify          (code-based)
POST /api/auth/resend-verification
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/password/reset-request
POST /api/auth/password/reset
GET  /health
```

All other `/api/*` endpoints require `Authorization: Bearer {accessToken}`.

---

## 9. Content Item Schema

`ContentBatchItem` — returned by feed, bookmarks, likes, dislikes endpoints.

**New fields added (2026-04):** `content_html`, `content_text`, `preview_text`. Old `content` and `description` fields remain for backward compatibility.

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `id` | UUID string | No | Unique content ID — use as key for interaction events |
| `title` | string | No | Article headline |
| `description` | string | Yes | **Deprecated** — legacy short description (may be raw HTML). Prefer `preview_text`. |
| `content` | string | Yes | **Deprecated** — legacy full body. Prefer `content_html` / `content_text`. |
| `content_html` | string | Yes | **NEW** — Whitelist-sanitized HTML, safe for `HtmlWidget`. Absolute S3 URLs in `<img src>`. Empty string if not applicable. |
| `content_text` | string | Yes | **NEW** — Plain text version (NFC-normalized). Safe for `Text(...)`. Empty string if not applicable. |
| `preview_text` | string | Yes | **NEW** — ≤300 char plain text for cards (sentence-aware truncation). |
| `content_format` | string | Yes | `"HTML"` or `"PLAIN"`. **Note:** changed from `"text/html"` — `isHtml` getter checks `== 'HTML'`. |
| `source_type` | string | Yes | `HABR`, `VCRU`, `VCRU_USER`, `TELEGRAM`, `RSS` |
| `source_subtype` | string | Yes | More specific subtype |
| `url` | string | Yes | Original article URL |
| `published_at` | ISO 8601 | Yes | Publication time |
| `author_name` | string | Yes | Author or channel name |
| `media` | array | Yes | Images/videos — always rendered as a SEPARATE block from body text |
| `metadata` | object | Yes | Extra data (read time, tags, etc.) |
| `related_ids` | UUID array | Yes | Related content IDs (dedup-detected similar articles) |

### Three content variants the frontend MUST handle

The platform produces three patterns. Frontend renders correctly for all three via the **uniform fallback chain** — no `source_type` branching in widgets.

#### V1 — HTML body with embedded images (Habr, VC.RU)

```jsonc
{
  "content_format": "HTML",
  "content_html": "<p>Intro paragraph</p><img src=\"https://media.skd.io/content-media/habr/.../a.jpg\"/><p>More text</p>",
  "content_text": "Intro paragraph\nMore text",
  "preview_text": "Intro paragraph...",
  "source_type": "HABR",
  "media": [
    { "url": "https://media.skd.io/content-media/habr/.../a.jpg", "type": "image", "width": 1200, "height": 630 },
    { "url": "https://media.skd.io/content-media/habr/.../b.jpg", "type": "image" }
  ]
}
```

Render: `HtmlWidget(content_html)` for body — images appear inline. `MediaGalleryWidget(media)` as top-of-detail preview gallery.

#### V2 — Plain text caption + standalone media (Telegram)

```jsonc
{
  "content_format": "PLAIN",
  "content_html": "",
  "content_text": "Заголовок поста, обычный текст без HTML",
  "preview_text": "Заголовок поста, обычный текст без HTML",
  "source_type": "TELEGRAM",
  "media": [
    { "url": "https://media.skd.io/content-media/telegram/.../photo1.jpg", "type": "image" },
    { "url": "https://media.skd.io/content-media/telegram/.../video1.mp4", "type": "video" }
  ]
}
```

Render: `Text(content_text)` for caption + `MediaGalleryWidget(media)` as separate block. No `HtmlWidget` needed, no inline images (cleaner strips `<img>` from Telegram HTML).

#### V3 — HTML body without inline images + standalone media (RSS)

```jsonc
{
  "content_format": "HTML",
  "content_html": "<p>Article body without any inline images</p>",
  "content_text": "Article body without any inline images",
  "preview_text": "Article body without any inline images",
  "source_type": "RSS",
  "media": [
    { "url": "https://media.skd.io/content-media/rss/.../thumb.jpg", "type": "image" }
  ]
}
```

Render: `HtmlWidget(content_html)` for body + `MediaGalleryWidget(media)` as separate block.

### Edge cases

- `content_html` empty AND `content_text` empty → render only `media[]` (or empty if media also empty)
- `media[]` empty → just render the body
- `content_format == "PLAIN"` but `content_html` non-empty → trust `content_format`, use `Text(content_text)`
- `content_format == "HTML"` but `content_html` empty → fall through to `Text(content_text)`

### Rendering rules (uniform for all three variants)

1. **Cards:** use `preview_text` if present, else `description`. Never raw HTML in cards.
2. **Article detail body:**
   - if `content_format == "HTML"` and `content_html` non-empty → `HtmlWidget(content_html)`
   - else if `content_text` non-empty → `Text(content_text)`
   - else if `description` non-empty → `Text(description)` (legacy fallback)
   - else → empty (`SizedBox.shrink()`)
3. **Media gallery:** ALWAYS render `media[]` as a separate block. Same `MediaGalleryWidget` for all source types. Supports both `"image"` and `"video"` types (video shows placeholder with play icon).

### Source type cheat sheet

| `source_type` | Variant | `content_html` has `<img>`? | `media[]` populated? |
|---|---|---|---|
| `HABR` | V1 | Yes | Yes (mirrors + extras) |
| `VCRU`, `VCRU_USER` | V1 | Yes | Yes |
| `TELEGRAM` | V2 | NO (always stripped by cleaner) | Yes |
| `RSS` | V3 | Sometimes | Sometimes |

### Additional fields on every feed item (Phase 3)

Starting with Phase 3, every `ContentItem` returned from `/api/feed`, `/api/feed/spaces/{id}/items`, `/api/feed/bookmarks`, `/api/feed/likes`, `/api/feed/dislikes` additionally includes:

| Field | Type | Description |
|---|---|---|
| `source_id` | UUID (nullable) | ID of the source that produced this post. Used by frontend to render "Hide source" action on each card. |
| `source_type` | string (nullable) | Already part of the schema (see above). Used to render type badge. |

These fields are **additive** — existing fields unchanged.

---

## 10. Spaces (Phase 3)

User-defined named feed collections, each scoped to a chosen subset of sources and
ranked by rec-system NARROW. Max **10 spaces per user**. Colors are constrained
to an 8-value enum: `RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, PURPLE, PINK`.

### 10.1 List spaces

```
GET /api/feed/spaces
Authorization: Bearer {accessToken}
```

**Response 200:**

```json
{
  "items": [
    {
      "id": "b5c0...",
      "name": "Kotlin News",
      "color": "BLUE",
      "source_ids": ["uuid-a", "uuid-b"],
      "source_count": 2,
      "created_at": "2026-04-10T12:00:00Z",
      "updated_at": "2026-04-12T09:30:00Z"
    }
  ],
  "count": 1,
  "limit": 10
}
```

### 10.2 Create space

```
POST /api/feed/spaces
Content-Type: application/json
```

**Body:**

```json
{
  "name": "Kotlin News",
  "color": "BLUE",
  "source_ids": ["uuid-a", "uuid-b"]
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `name` | string | yes | 1–100 chars, trimmed, unique per user |
| `color` | enum string | yes | One of the 8 allowed values |
| `source_ids` | UUID[] | yes | Empty array allowed; duplicates are deduped server-side |

**Response 201:** One `items[]` entry (same shape as list).

**Errors:**

| Status | `error` | Condition |
|---|---|---|
| 400 | `validation_error` | Empty/too-long name; invalid color; malformed UUID |
| 409 | `duplicate_space_name` | Space with same name already exists for this user |
| 409 | `space_limit_reached` | User already has 10 spaces |

### 10.3 Get single space

```
GET /api/feed/spaces/{id}
```

**Response 200:** same shape as `items[]` entry.

**Errors:** `404 space_not_found`.

### 10.4 Update space

```
PUT /api/feed/spaces/{id}
Content-Type: application/json
```

**Body:** (all optional, ≥1 required)

```json
{
  "name": "Kotlin News v2",
  "color": "GREEN",
  "source_ids": ["uuid-a", "uuid-c"]
}
```

`source_ids` = full replace semantics. Name/color update independently.

**Response 200:** updated entry.

**Errors:** `400 validation_error`, `404 space_not_found`, `409 duplicate_space_name`.

### 10.5 Delete space

```
DELETE /api/feed/spaces/{id}
```

**Response 204.** Cascade removes all `space_sources`.

**Errors:** `404 space_not_found`.

### 10.6 Space feed

```
GET /api/feed/spaces/{id}/items?cursor=...&limit=20
```

| Param | Type | Default | Constraints |
|---|---|---|---|
| `cursor` | string | null | Opaque (Base64 JSON `{o:offset}`) |
| `limit` | int | 20 | 1 ≤ limit ≤ 50 |

**Response 200:** same shape as `GET /api/feed`:

```json
{
  "items": [ /* ContentItem[] — see schema §9 and Phase 3 additional fields above */ ],
  "cursor": "eyJvIjozMH0=",
  "has_next": true
}
```

Empty space (0 sources) returns `{items: [], cursor: null, has_next: false}`.

**Errors:**

| Status | `error` | Condition |
|---|---|---|
| 400 | `validation_error` | `invalid_cursor` or `limit` out of range |
| 404 | `space_not_found` | Space missing or not owner |
| 503 | `space_feed_unavailable` | rec-system unreachable |

---

## 11. Blocked Sources (Phase 3)

Proxy endpoints to rec-system blocked-sources CRUD. User id is taken from
`X-User-Id` (injected by gateway), **never from the path/body**.

### 11.1 Block source

```
POST /api/feed/blocked-sources
Content-Type: application/json

{ "source_id": "uuid" }
```

**Response 204.** Idempotent — blocking already-blocked source returns 204.

**Errors:** `400 validation_error` (missing/malformed `source_id`); `503 upstream_unavailable`.

### 11.2 Unblock source

```
DELETE /api/feed/blocked-sources/{source_id}
```

**Response 204.** Idempotent — unblocking unblocked source returns 204.

### 11.3 List blocked sources

```
GET /api/feed/blocked-sources
```

**Response 200:**

```json
{
  "items": [
    {
      "source_id": "uuid",
      "source_type": "TELEGRAM",
      "source_name": "@lovely_news",
      "blocked_at": "2026-04-16T10:00:00Z"
    }
  ],
  "count": 1
}
```

| Field | Type | Nullable | Description |
|---|---|---|---|
| `source_id` | UUID | No | Stable source identifier |
| `source_type` | string | **Yes** | `TELEGRAM` / `HABR` / `VCRU` / `RSS`. Null if source metadata unknown to feed-service (source was never added by this user via `POST /api/config/v1/sources/*` — e.g. blocked directly from a feed card whose source hasn't been registered in `feed.source_additions`). |
| `source_name` | string | **Yes** | Display name (`@channel`, `habr.com/hub/...`, etc.). Null when `source_type` is null (same reason). |
| `blocked_at` | ISO 8601 | No | Server timestamp when the block was inserted |

Frontend MUST handle null `source_type` / `source_name` gracefully — typically
by rendering the `source_id` (or a shortened form) as the label and skipping the
platform badge. Blocks made via the feed card action that have not been
previously "added" will show as UUID-labelled entries until the user views /
interacts with the source catalog.

---

## 12. My Additions (Phase 3)

Paginated list of sources the current user has added (populated from the
`source.added` Kafka topic produced by config-service).

```
GET /api/feed/my-additions?cursor=...&limit=20
```

| Param | Type | Default | Constraints |
|---|---|---|---|
| `cursor` | string | null | Base64 JSON `{o:offset}` |
| `limit` | int | 20 | 1 ≤ limit ≤ 50 |

**Response 200:**

```json
{
  "items": [
    {
      "source_id": "uuid",
      "source_type": "rss",
      "source_name": "Kotlin Weekly",
      "added_at": "2026-04-15T14:20:00Z"
    }
  ],
  "cursor": "eyJvIjoyMH0=",
  "has_next": false
}
```

Ordered by `added_at DESC`.

---

## 13. Sources Catalog (Phase 2)

Public catalog of ingestable sources (Telegram, Habr, VC.RU) plus premium-only
endpoints that let users register new sources. All endpoints sit behind
api-gateway and expect the standard `Authorization: Bearer {accessToken}` header.

### 13.1 List / search catalog

```
GET /api/sources?type=TELEGRAM&q=news&cursor=eyJvIjoyMH0=&limit=20
Authorization: Bearer {accessToken}
```

| Param  | Type   | Default | Constraints                             |
|--------|--------|---------|-----------------------------------------|
| type   | enum   | null    | `TELEGRAM` \| `HABR` \| `VCRU`          |
| q      | string | null    | Free-text, server trims & lowercases    |
| cursor | string | null    | Opaque base64 token (omit for page 1)   |
| limit  | int    | 20      | 1 ≤ limit ≤ 50                          |

**Response 200:**

```json
{
  "items": [
    {
      "id": "d1e2c3...",
      "type": "TELEGRAM",
      "name": "@lovely_news",
      "url": "https://t.me/lovely_news",
      "icon_url": null,
      "created_at": "2026-03-12T10:00:00Z"
    }
  ],
  "cursor": "eyJvIjoyMH0=",
  "has_next": true
}
```

Available to all authenticated users (browsing is NOT premium-gated).

### 13.2 Add source — Telegram

```
POST /api/config/v1/sources/telegram
Authorization: Bearer {accessToken}   # requires subscription tier = premium
Content-Type: application/json

{ "name": "Lovely News", "channel_username": "lovely_news" }
```

Alternate body shape (frontend sends either based on input parsing):

```json
{ "username": "lovely_news" }
```

**Success responses:**

- `201 Created` — the source was added for the first time.
- `200 OK` — the source already existed in the catalog (merge semantics).

Both responses carry:

```json
{
  "source": {
    "id": "uuid",
    "type": "TELEGRAM",
    "name": "@lovely_news",
    "url": "https://t.me/lovely_news",
    "icon_url": null,
    "created_at": "2026-03-12T10:00:00Z"
  },
  "was_existing": false
}
```

### 13.3 Add source — Habr

```
POST /api/config/v1/sources/habr
{ "hub_slug": "flutter" }
```

### 13.4 Add source — VC.RU

```
POST /api/config/v1/sources/vcru
{ "user_id": "123456" }   # or { "blog_slug": "design" }
```

### 13.5 Error codes

| Status | `error`                               | Maps to failure                                         |
|--------|---------------------------------------|---------------------------------------------------------|
| 400    | `source_not_supported` / `source_invalid` | `SourceNotSupportedFailure`                         |
| 403    | `premium_required`                    | `PremiumRequiredFailure`                                |
| 409    | `source_name_duplicate`               | `DuplicateNameFailure` (treated client-side as existing)|
| 429    | `source_limit_reached`                | `LimitReachedFailure(limit=20, kind="source")`          |

### 13.6 Content Item — Phase 4 additions

`ContentItem` (see §9) is extended with two optional fields that feed-service
backfills for every published content object:

| Field         | Type   | Description                                                 |
|---------------|--------|-------------------------------------------------------------|
| `source_id`   | UUID   | ID of the originating source, matches catalog `id`          |
| `source_name` | string | Denormalised display name (`@channel`, `habr.com/hub/...`)  |

These fields drive the "Скрыть источник" action on feed cards and enable
Spaces membership checks.

### 13.7 Error code glossary additions

| Status | `error`                       | Failure subclass                                                |
|--------|-------------------------------|-----------------------------------------------------------------|
| 429    | `source_limit_reached`        | `LimitReachedFailure(limit=20, kind="source")`                  |
| 429    | `space_limit_reached`         | `LimitReachedFailure(limit=10, kind="space")`                   |
| 409    | `duplicate_space_name`        | `DuplicateNameFailure`                                          |
| 409    | `space_name_duplicate`        | `DuplicateNameFailure`                                          |
| 409    | `source_name_duplicate`       | `DuplicateNameFailure`                                          |
| 400    | `source_not_supported`        | `SourceNotSupportedFailure`                                     |
| 400    | `space_requires_sources`      | `ValidationFailure`                                             |
| 403    | `premium_required`            | `PremiumRequiredFailure`                                        |

---

## Appendix: Environment Configuration

```dart
// lib/core/config/api_config.dart

class ApiConfig {
  // Dev: API Gateway URL
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080', // Android emulator → localhost
  );
  
  // For Chrome dev: http://localhost:8080
  // For Android device on local network: http://192.168.X.X:8080
  // For production: https://api.yourdomain.com
}
```

Launch command for Android emulator (gateway on host machine):
```bash
flutter run -d android --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Launch command for Chrome:
```bash
flutter run -d chrome --web-port=3000 --dart-define=API_BASE_URL=http://localhost:8080
```

---

## Changelog

| Date | Version | Change |
|------|---------|--------|
| 2026-04-20 | P9 | `GET /api/feed` — added **Response Headers** section documenting `X-Request-Id` (captured by frontend for interaction attribution) and `X-Feed-Source` |
| 2026-04-20 | P9 | `POST /api/interactions/batch` — added **v2 optional event fields**: `schema_version`, `feed_request_id`, `position_in_feed` (1-indexed), `device_type`, `app_version`, `ab_bucket`, `scroll_depth`, `metadata` |
