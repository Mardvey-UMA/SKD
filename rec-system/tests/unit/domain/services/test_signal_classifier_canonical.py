"""Locks the canonical 6 action_type vocabulary into the SignalClassifier contract.

Any regression in recognised event types will be caught here before it reaches
the feed pipeline or Kafka consumers.
"""
from __future__ import annotations

import pytest

from src.domain.services.signal_classifier import SignalClassifier


@pytest.mark.unit
class TestCanonicalActionTypesCoverage:
    """Locks the canonical 6 action type vocabulary into the contract."""

    CANONICAL = ["IMPRESSION", "OPEN", "CLOSE", "LIKE", "DISLIKE", "BOOKMARK"]

    @pytest.mark.parametrize("event_type", CANONICAL)
    def test_canonical_event_types_are_recognized(self, classifier, valid_interaction_for, event_type):
        inter = valid_interaction_for(event_type)
        result = classifier.classify(inter)
        # Signal MAY be 0.0 (e.g. orphan OPEN) but MUST not be None for a recognised event
        assert result is not None, f"{event_type} must be recognized"

    def test_unknown_event_returns_none(self, classifier, valid_interaction_for):
        inter = valid_interaction_for("VIEW")  # legacy name — not canonical for rec-system
        assert classifier.classify(inter) is None

    def test_like_weight_positive(self, classifier, valid_interaction_for):
        inter = valid_interaction_for("LIKE")
        result = classifier.classify(inter)
        assert result > 0

    def test_dislike_weight_negative(self, classifier, valid_interaction_for):
        inter = valid_interaction_for("DISLIKE")
        result = classifier.classify(inter)
        assert result < 0

    def test_bookmark_weight_positive_higher_than_like(self, classifier, valid_interaction_for):
        # Per design: bookmark +0.80 > like +0.60
        like_result = classifier.classify(valid_interaction_for("LIKE"))
        bookmark_result = classifier.classify(valid_interaction_for("BOOKMARK"))
        assert bookmark_result > like_result

    def test_close_variants_distinct(self, classifier, close_interaction_variants):
        # close_fast (negative), close_full (positive), close_half (positive), close_other (small positive)
        # Verify the 4 branches produce 4 different weights given the same event_type=CLOSE.
        results = {
            variant_name: classifier.classify(inter)
            for variant_name, inter in close_interaction_variants.items()
        }
        # All 4 variants non-None
        assert all(r is not None for r in results.values())
        # 4 distinct values
        assert len({round(r, 4) for r in results.values()}) == 4
