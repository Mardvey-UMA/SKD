package com.contentplatform.feed.api.exception

class SpaceLimitReachedException(message: String = "Space limit reached (max 10 per user)") : RuntimeException(message)
