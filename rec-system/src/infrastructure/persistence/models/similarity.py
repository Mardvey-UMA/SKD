"""Read-only SQLAlchemy model for data_flow.similarities.

This table is owned by dedup-worker (dedup-system). rec-system reads
from it for EXACT/DUPLICATE/RELATED detection (SELECT only). Never write to it.
"""
from sqlalchemy import Column, BigInteger, Float, String

from src.infrastructure.persistence.models.base import Base


class SimilarityModel(Base):
    """Maps to ``data_flow.similarities`` (read-only from rec-system perspective).

    Owned by dedup-system. Composite PK on (article_a, article_b).
    """
    __tablename__ = "similarities"

    article_a = Column(BigInteger, primary_key=True)
    article_b = Column(BigInteger, primary_key=True)
    score = Column(Float, nullable=True)
    rel_type = Column(String, nullable=True)
