from sqlalchemy import MetaData
from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """SQLAlchemy declarative base for all ORM models.

    All tables belong to the ``data_flow`` schema in ``content_agg_db``.
    """
    metadata = MetaData(schema="data_flow")
