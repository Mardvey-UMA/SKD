"""Prometheus metrics definitions for rec-system."""
from __future__ import annotations

from prometheus_client import Counter, Histogram

_LATENCY_BUCKETS_MS = (10, 25, 50, 100, 200, 400, 800, 1600, 3200)

rec_feed_request_latency_ms = Histogram(
    "rec_feed_request_latency_ms",
    "Feed generation latency in milliseconds",
    ["source"],
    buckets=_LATENCY_BUCKETS_MS,
)

rec_feed_requests_total = Counter(
    "rec_feed_requests_total",
    "Total number of feed generation requests",
    ["source"],
)

rec_content_processing_duration_seconds = Histogram(
    "rec_content_processing_duration_seconds",
    "Duration of content processing job iterations in seconds",
    buckets=(1, 5, 10, 30, 60, 120, 300),
)

rec_kafka_events_consumed_total = Counter(
    "rec_kafka_events_consumed_total",
    "Total number of Kafka interaction batches successfully consumed",
)

rec_kafka_events_failed_total = Counter(
    "rec_kafka_events_failed_total",
    "Total number of Kafka interaction batch processing failures",
)
