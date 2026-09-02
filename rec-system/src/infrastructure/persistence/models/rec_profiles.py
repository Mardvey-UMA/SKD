from sqlalchemy import Column, Integer, Boolean, DateTime
from sqlalchemy.dialects.postgresql import UUID, JSONB
from pgvector.sqlalchemy import Vector

from src.infrastructure.persistence.models.base import Base


class RecProfilesModel(Base):
    __tablename__ = "rec_profiles"

    user_id = Column(UUID(as_uuid=True), primary_key=True)
    topic_vector = Column(JSONB, nullable=False)
    embedding = Column(Vector(312), nullable=True)
    sentiment_prefs = Column(JSONB, nullable=False)
    format_prefs = Column(JSONB, nullable=False)
    interaction_count = Column(Integer, default=0, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=True)
    last_updated = Column(DateTime(timezone=True), nullable=True)
    cold_start = Column(Boolean, nullable=False, server_default="true")
