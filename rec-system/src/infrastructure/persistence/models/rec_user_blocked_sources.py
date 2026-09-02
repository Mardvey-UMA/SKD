"""SQLAlchemy model for data_flow.rec_user_blocked_sources (Phase 1 user-sources)."""
from sqlalchemy import Column, DateTime
from sqlalchemy.dialects.postgresql import UUID

from src.infrastructure.persistence.models.base import Base


class RecUserBlockedSourcesModel(Base):
    """Maps to ``data_flow.rec_user_blocked_sources``. rec-system owns DDL + CRUD."""

    __tablename__ = "rec_user_blocked_sources"

    user_id = Column(UUID(as_uuid=True), primary_key=True)
    source_id = Column(UUID(as_uuid=True), primary_key=True)
    blocked_at = Column(DateTime(timezone=True), nullable=False)
