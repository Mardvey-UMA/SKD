package com.contentplatform.feed.api.exception

class InvalidSignatureException(message: String = "Invalid or stale internal signature") : RuntimeException(message)
