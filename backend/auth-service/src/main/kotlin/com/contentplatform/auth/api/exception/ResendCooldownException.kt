package com.contentplatform.auth.api.exception

class ResendCooldownException(
    val retryAfterSeconds: Long
) : RuntimeException("Resend cooldown active")
