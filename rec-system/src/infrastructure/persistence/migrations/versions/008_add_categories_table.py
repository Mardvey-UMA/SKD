"""Add data_flow.categories reference table with 18 NLP topic seed rows.

Revision ID: 008
Revises: 007
Create Date: 2026-04-03

Single source of truth for:
- GET /categories onboarding endpoint
- POST /onboarding category validation
- TopicVector topic keys
- TorchTopicClassifier NLI labels
"""

from typing import Sequence, Union

from alembic import op

revision: str = "008"
down_revision: Union[str, None] = "007"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("""
        CREATE TABLE data_flow.categories (
            id          VARCHAR(50) PRIMARY KEY,
            name        VARCHAR(100) NOT NULL,
            icon        VARCHAR(50) NOT NULL,
            sort_order  INTEGER NOT NULL DEFAULT 0,
            is_active   BOOLEAN NOT NULL DEFAULT true,
            created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
        )
    """)

    op.execute("""
        INSERT INTO data_flow.categories (id, name, icon, sort_order) VALUES
            ('политика',              'Политика',              'landmark',       1),
            ('экономика',             'Экономика',             'chart-line',     2),
            ('технологии',            'Технологии',            'laptop',         3),
            ('наука',                 'Наука',                 'flask',          4),
            ('спорт',                 'Спорт',                 'trophy',         5),
            ('культура',              'Культура',              'palette',        6),
            ('общество',              'Общество',              'users',          7),
            ('происшествия',          'Происшествия',          'alert-triangle', 8),
            ('международные новости', 'Международные новости', 'globe',          9),
            ('бизнес',                'Бизнес',                'briefcase',      10),
            ('финансы',               'Финансы',               'dollar-sign',    11),
            ('образование',           'Образование',           'book-open',      12),
            ('здоровье',              'Здоровье',              'heart',          13),
            ('развлечения',           'Развлечения',           'film',           14),
            ('криминал',              'Криминал',              'shield',         15),
            ('армия',                 'Армия',                 'shield-alert',   16),
            ('природа',               'Природа',               'leaf',           17),
            ('транспорт',             'Транспорт',             'car',            18)
    """)


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS data_flow.categories CASCADE")
