"""RED tests for persona topic coverage — Phase 1 Eval Harness."""
from __future__ import annotations

from pathlib import Path

import pytest

from tests.evaluation.persona_loader import PersonaLoader

PERSONAS_DIR = Path(__file__).parent.parent / "personas"

CANONICAL_TOPICS = {
    "политика", "экономика", "технологии", "наука", "спорт", "культура",
    "общество", "происшествия", "международные новости", "бизнес",
    "финансы", "образование", "здоровье", "развлечения", "криминал",
    "армия", "природа", "транспорт",
}


@pytest.mark.unit
def test_all_18_topics_covered():
    """Union of base_interests keys across 20 personas == 18 canonical topics."""
    loader = PersonaLoader(PERSONAS_DIR, valid_topics=CANONICAL_TOPICS)
    personas = loader.load_all()

    covered = set()
    for persona in personas:
        for topic, weight in persona.base_interests.items():
            if weight > 0:
                covered.add(topic)

    missing = CANONICAL_TOPICS - covered
    assert not missing, f"Topics not covered by any persona: {missing}"
    assert covered == CANONICAL_TOPICS
