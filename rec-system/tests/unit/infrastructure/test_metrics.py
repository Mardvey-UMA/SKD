"""Unit tests for Prometheus metrics definitions and /metrics endpoint."""
from __future__ import annotations

import pytest
from prometheus_client import REGISTRY
from prometheus_client.parser import text_string_to_metric_families


def _sample_value(sample_name: str, labels: dict | None = None) -> float:
    """Read current sample value from the global registry (0.0 if absent).

    Searches by sample name (not metric family name), so _total/_count/_bucket
    suffixes work correctly across Counter and Histogram types.
    """
    labels = labels or {}
    for metric in REGISTRY.collect():
        for sample in metric.samples:
            if sample.name == sample_name and sample.labels == labels:
                return sample.value
    return 0.0


class TestMetricDefinitions:
    """Verify that all expected metrics are registered and behave correctly."""

    @pytest.mark.unit
    def test_feed_requests_counter_increments(self):
        from src.infrastructure.metrics import rec_feed_requests_total

        before = _sample_value("rec_feed_requests_total", {"source": "personalized"})
        rec_feed_requests_total.labels(source="personalized").inc()
        after = _sample_value("rec_feed_requests_total", {"source": "personalized"})
        assert after - before == pytest.approx(1.0)

    @pytest.mark.unit
    def test_feed_requests_counter_narrow_label(self):
        from src.infrastructure.metrics import rec_feed_requests_total

        before = _sample_value("rec_feed_requests_total", {"source": "narrow"})
        rec_feed_requests_total.labels(source="narrow").inc()
        after = _sample_value("rec_feed_requests_total", {"source": "narrow"})
        assert after - before == pytest.approx(1.0)

    @pytest.mark.unit
    def test_feed_latency_histogram_records_observation(self):
        from src.infrastructure.metrics import rec_feed_request_latency_ms

        before_count = _sample_value("rec_feed_request_latency_ms_count", {"source": "personalized"})
        rec_feed_request_latency_ms.labels(source="personalized").observe(42.0)
        after_count = _sample_value("rec_feed_request_latency_ms_count", {"source": "personalized"})
        assert after_count - before_count == pytest.approx(1.0)

    @pytest.mark.unit
    def test_feed_latency_histogram_bucket_populated(self):
        from src.infrastructure.metrics import rec_feed_request_latency_ms

        # Observe 30ms — should land in the 50ms bucket (<=50)
        rec_feed_request_latency_ms.labels(source="test_bucket").observe(30.0)
        bucket_val = _sample_value(
            "rec_feed_request_latency_ms_bucket",
            {"source": "test_bucket", "le": "50.0"},
        )
        assert bucket_val >= 1.0

    @pytest.mark.unit
    def test_content_processing_histogram_records_observation(self):
        from src.infrastructure.metrics import rec_content_processing_duration_seconds

        before_count = _sample_value("rec_content_processing_duration_seconds_count")
        rec_content_processing_duration_seconds.observe(2.5)
        after_count = _sample_value("rec_content_processing_duration_seconds_count")
        assert after_count - before_count == pytest.approx(1.0)

    @pytest.mark.unit
    def test_kafka_consumed_counter_increments(self):
        from src.infrastructure.metrics import rec_kafka_events_consumed_total

        before = _sample_value("rec_kafka_events_consumed_total")
        rec_kafka_events_consumed_total.inc()
        after = _sample_value("rec_kafka_events_consumed_total")
        assert after - before == pytest.approx(1.0)

    @pytest.mark.unit
    def test_kafka_failed_counter_increments(self):
        from src.infrastructure.metrics import rec_kafka_events_failed_total

        before = _sample_value("rec_kafka_events_failed_total")
        rec_kafka_events_failed_total.inc()
        after = _sample_value("rec_kafka_events_failed_total")
        assert after - before == pytest.approx(1.0)


class TestMetricsEndpoint:
    """Verify that GET /metrics returns valid Prometheus exposition format."""

    def _make_app(self):
        from fastapi import FastAPI
        from src.presentation.api.metrics_router import metrics_router

        app = FastAPI()
        app.include_router(metrics_router)
        return app

    @pytest.mark.unit
    async def test_metrics_returns_200(self):
        from httpx import AsyncClient, ASGITransport

        app = self._make_app()
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/metrics")
        assert response.status_code == 200

    @pytest.mark.unit
    async def test_metrics_content_type_is_prometheus(self):
        from httpx import AsyncClient, ASGITransport

        app = self._make_app()
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/metrics")
        assert "text/plain" in response.headers["content-type"]

    @pytest.mark.unit
    async def test_metrics_output_is_parseable(self):
        from httpx import AsyncClient, ASGITransport

        app = self._make_app()
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/metrics")

        families = list(text_string_to_metric_families(response.text))
        assert len(families) > 0

    @pytest.mark.unit
    async def test_metrics_output_contains_rec_feed_metric(self):
        from httpx import AsyncClient, ASGITransport
        from src.infrastructure.metrics import rec_feed_requests_total

        # Ensure at least one label set exists
        rec_feed_requests_total.labels(source="personalized").inc(0)

        app = self._make_app()
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/metrics")

        # prometheus_client strips _total suffix from Counter family names;
        # check the family name without suffix.
        names = {f.name for f in text_string_to_metric_families(response.text)}
        assert "rec_feed_requests" in names
