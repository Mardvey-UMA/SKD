package com.contentplatform.auth.api.exception

class InvalidVerificationCodeException(
    message: String = "Code is invalid or expired"
) : RuntimeException(message)
