from sqlalchemy import Column, Integer, Float, Boolean, String, DateTime
from sqlalchemy.dialects.postgresql import JSONB, UUID
from pgvector.sqlalchemy import Vector

from src.infrastructure.persistence.models.base import Base


class PostsFeaturesModel(Base):
    __tablename__ = "posts_features"

    post_id = Column(UUID(as_uuid=True), primary_key=True)
    source_id = Column(UUID(as_uuid=True), nullable=False)
    source_type = Column(String(50), nullable=False)
    text_length = Column(Integer, nullable=True)
    word_count = Column(Integer, nullable=True)
    reading_time = Column(Float, nullable=True)
    complexity = Column(Float, nullable=True)
    is_short_form = Column(Boolean, nullable=True)
    is_long_form = Column(Boolean, nullable=True)
    topic_1 = Column(String, nullable=True)
    topic_1_score = Column(Float, nullable=True)
    topic_2 = Column(String, nullable=True)
    topic_2_score = Column(Float, nullable=True)
    topic_3 = Column(String, nullable=True)
    topic_3_score = Column(Float, nullable=True)
    sentiment = Column(String, nullable=True)
    sentiment_score = Column(Float, nullable=True)
    entities_persons = Column(JSONB, nullable=True)
    entities_organizations = Column(JSONB, nullable=True)
    entities_locations = Column(JSONB, nullable=True)
    embedding = Column(Vector(312), nullable=True)
    processed_at = Column(DateTime(timezone=True), nullable=True)
