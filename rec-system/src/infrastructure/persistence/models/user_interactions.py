from sqlalchemy import Column, BigInteger, String, Integer, Float, Boolean, DateTime
from sqlalchemy.dialects.postgresql import UUID

from src.infrastructure.persistence.models.base import Base


class UserInteractionsModel(Base):
    __tablename__ = "user_interactions"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    event_id = Column(UUID(as_uuid=True), nullable=False, unique=True)
    user_id = Column(UUID(as_uuid=True), nullable=True)
    post_id = Column(UUID(as_uuid=True), nullable=True)
    event_type = Column(String, nullable=True)
    duration_ms = Column(Integer, nullable=True)
    scroll_pct = Column(Float, nullable=True)
    max_scroll_pct = Column(Float, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=True)
    processed = Column(Boolean, default=False, nullable=True)
