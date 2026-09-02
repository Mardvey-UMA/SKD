"""Read-only SQLAlchemy model for data_flow.raw_content.

rec-system reads from this table but only writes the
``is_processed_by_rec`` flag (UPDATE only). All DDL and INSERT
is owned by content-parser-service.

T5: Added clean_html, clean_text, preview_text columns populated by
content-parser-service CleanContentJob. rec-system now reads clean_text
directly instead of calling strip_html(raw_data['content']).
"""
from sqlalchemy import Column, String, Boolean, DateTime, Text
from sqlalchemy.dialects.postgresql import UUID, JSONB

from src.infrastructure.persistence.models.base import Base


class RawContentModel(Base):
    """Maps to ``data_flow.raw_content`` (read-only from rec-system perspective).

    Only the columns that rec-system needs to read are declared here.
    """
    __tablename__ = "raw_content"

    id = Column(UUID(as_uuid=True), primary_key=True)
    external_id = Column(String, nullable=True)
    source_id = Column(UUID(as_uuid=True), nullable=True)
    source_type = Column(String, nullable=True)
    raw_data = Column(JSONB, nullable=True)
    processing_status = Column(String, nullable=True)
    is_processed_by_rec = Column(Boolean, default=False, nullable=False)
    received_at = Column(DateTime(timezone=True), nullable=True)

    # T5: Pre-cleaned text columns populated by content-parser-service CleanContentJob.
    # rec-system filters clean_text IS NOT NULL and reads it directly.
    clean_html = Column(Text, nullable=True)
    clean_text = Column(Text, nullable=True)
    preview_text = Column(Text, nullable=True)
