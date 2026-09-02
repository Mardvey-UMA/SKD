package com.contentplatform.feed.application

interface FeedLogService {
    fun logFeedRequest(log: FeedRequestLog, items: List<FeedItemLog>)
}
