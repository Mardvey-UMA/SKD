package com.contentplatform.auth.api.exception

class TooManyVerificationAttemptsException(
    val retryAfterSeconds: Long
) : RuntimeException("Too many verification attempts")
