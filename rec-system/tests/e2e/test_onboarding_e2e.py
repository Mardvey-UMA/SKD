"""E2E tests for POST /onboard endpoint.

Task 51: Full onboarding flow via HTTP with real PostgreSQL.
"""
from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession


@pytest.mark.e2e
class TestOnboardingE2E:
    """End-to-end tests for the onboarding flow."""

    @pytest.mark.asyncio
    async def test_onboard_new_user(self, e2e_client: AsyncClient):
        """POST /onboard with valid payload returns 200 + {"status": "ok"}."""
        user_id = str(uuid.uuid4())
        payload = {
            "user_id": user_id,
            "chosen_topics": ["технологии", "наука", "спорт"],
        }

        response = await e2e_client.post("/onboard", json=payload)

        assert response.status_code == 200
        body = response.json()
        assert body["status"] == "ok"

    @pytest.mark.asyncio
    async def test_profile_created_in_db(
        self,
        e2e_client: AsyncClient,
        e2e_db_session: AsyncSession,
    ):
        """After onboard, rec_profiles has a row for the user."""
        user_id = str(uuid.uuid4())
        payload = {
            "user_id": user_id,
            "chosen_topics": ["культура", "здоровье", "образование"],
        }

        response = await e2e_client.post("/onboard", json=payload)
        assert response.status_code == 200

        result = await e2e_db_session.execute(
            text("SELECT user_id FROM rec_profiles WHERE user_id = cast(:uid as uuid)").bindparams(uid=user_id),
        )
        row = result.fetchone()
        assert row is not None, f"Expected rec_profiles row for user {user_id}"

    @pytest.mark.asyncio
    async def test_topic_vector_matches_chosen_topics(
        self,
        e2e_client: AsyncClient,
        e2e_db_session: AsyncSession,
    ):
        """Profile topic_vector has non-zero weights for the chosen topics."""
        user_id = str(uuid.uuid4())
        chosen = ["наука", "политика", "бизнес"]
        payload = {
            "user_id": user_id,
            "chosen_topics": chosen,
        }

        response = await e2e_client.post("/onboard", json=payload)
        assert response.status_code == 200

        result = await e2e_db_session.execute(
            text("SELECT topic_vector FROM rec_profiles WHERE user_id = cast(:uid as uuid)").bindparams(uid=user_id),
        )
        row = result.fetchone()
        assert row is not None, "Profile not found in DB"

        topic_vector: dict = row[0]
        assert isinstance(topic_vector, dict), "topic_vector must be a JSON object"

        for topic in chosen:
            assert topic in topic_vector, f"Topic '{topic}' not in topic_vector"
            assert topic_vector[topic] > 0.0, (
                f"Topic '{topic}' should have positive weight, got {topic_vector[topic]}"
            )

    @pytest.mark.asyncio
    async def test_invalid_topic_count_returns_422(self, e2e_client: AsyncClient):
        """Fewer than 3 topics must be rejected with HTTP 422."""
        user_id = str(uuid.uuid4())
        payload = {
            "user_id": user_id,
            "chosen_topics": ["технологии", "наука"],  # only 2 — below minimum
        }

        response = await e2e_client.post("/onboard", json=payload)

        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_invalid_user_id_returns_422(self, e2e_client: AsyncClient):
        """Non-UUID user_id must be rejected with HTTP 422."""
        payload = {
            "user_id": "not-a-valid-uuid",
            "chosen_topics": ["технологии", "наука", "спорт"],
        }

        response = await e2e_client.post("/onboard", json=payload)

        assert response.status_code == 422
