package com.contentplatform.feed.api.exception

class UpstreamUnavailableException(message: String = "Upstream service unavailable", cause: Throwable? = null) : RuntimeException(message, cause)
