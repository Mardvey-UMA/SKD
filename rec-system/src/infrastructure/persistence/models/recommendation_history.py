from sqlalchemy import Column, DateTime
from sqlalchemy.dialects.postgresql import UUID

from src.infrastructure.persistence.models.base import Base


class RecommendationHistoryModel(Base):
    __tablename__ = "recommendation_history"

    user_id = Column(UUID(as_uuid=True), primary_key=True)
    content_id = Column(UUID(as_uuid=True), primary_key=True)
    recommended_at = Column(DateTime(timezone=True), nullable=False)
