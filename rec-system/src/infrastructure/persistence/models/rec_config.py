from sqlalchemy import Column, String, DateTime
from sqlalchemy.dialects.postgresql import JSONB

from src.infrastructure.persistence.models.base import Base


class RecConfigModel(Base):
    __tablename__ = "rec_config"

    key = Column(String, primary_key=True)
    value = Column(JSONB, nullable=False)
    description = Column(String, nullable=True)
    updated_at = Column(DateTime, nullable=True)
