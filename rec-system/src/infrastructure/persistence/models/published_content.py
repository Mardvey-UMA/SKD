"""Read-only SQLAlchemy model for data_flow.published_content.

This table is owned by content-parser-service (Kotlin). rec-system reads
from it to map raw_content.id to published_content.id (SELECT only). Never write to it.
"""
from sqlalchemy import Column, DateTime
from sqlalchemy.dialects.postgresql import UUID

from src.infrastructure.persistence.models.base import Base


class PublishedContentModel(Base):
    """Maps to ``data_flow.published_content`` (read-only from rec-system perspective).

    Owned by content-parser-service. Only columns needed by rec-system are mapped.
    """
    __tablename__ = "published_content"

    id = Column(UUID(as_uuid=True), primary_key=True)
    content_id = Column(UUID(as_uuid=True), nullable=True)
    published_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=True)
