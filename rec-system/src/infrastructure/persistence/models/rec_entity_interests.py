from sqlalchemy import Column, String, Float, DateTime
from sqlalchemy.dialects.postgresql import UUID

from src.infrastructure.persistence.models.base import Base


class RecEntityInterestsModel(Base):
    __tablename__ = "rec_entity_interests"

    user_id = Column(UUID(as_uuid=True), primary_key=True)
    entity_type = Column(String, primary_key=True)
    entity_name = Column(String, primary_key=True)
    weight = Column(Float, default=0.0, nullable=True)
    last_seen = Column(DateTime(timezone=True), nullable=True)
