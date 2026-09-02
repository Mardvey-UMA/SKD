"""Read-only SQLAlchemy model for data_flow.articles.

This table is owned by dedup-worker (dedup-system). rec-system reads
from it for dedup clustering purposes (SELECT only). Never write to it.
"""
from sqlalchemy import Column, BigInteger, String, DateTime
from sqlalchemy.dialects.postgresql import UUID

from src.infrastructure.persistence.models.base import Base


class ArticleModel(Base):
    """Maps to ``data_flow.articles`` (read-only from rec-system perspective).

    Owned by dedup-system. Only the columns rec-system needs are declared.
    """
    __tablename__ = "articles"

    id = Column(BigInteger, primary_key=True)
    raw_content_id = Column(UUID(as_uuid=True), nullable=True)
    content_hash = Column(String, nullable=True)
    source = Column(String, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=True)
