from enum import Enum


class EventType(Enum):
    """6 event types representing user interactions."""

    IMPRESSION = "IMPRESSION"
    OPEN = "OPEN"
    CLOSE = "CLOSE"
    LIKE = "LIKE"
    DISLIKE = "DISLIKE"
    BOOKMARK = "BOOKMARK"
