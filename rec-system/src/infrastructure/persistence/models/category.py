from sqlalchemy import Column, Integer, String, Boolean, DateTime

from src.infrastructure.persistence.models.base import Base


class CategoryModel(Base):
    __tablename__ = "categories"

    id = Column(String(50), primary_key=True)
    name = Column(String(100), nullable=False)
    icon = Column(String(50), nullable=False)
    sort_order = Column(Integer, nullable=False, default=0)
    is_active = Column(Boolean, nullable=False, default=True)
    created_at = Column(DateTime(timezone=True), nullable=True)
