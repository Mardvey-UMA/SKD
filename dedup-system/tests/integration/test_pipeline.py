import pytest


class TestFullPipeline:
    @pytest.mark.integration
    def test_process_new_article_creates_article_record(
        self, pg_connection, mock_encoder
    ):
        from src.application.use_cases.process_batch import ProcessBatchUseCase
        from src.domain.entities.raw_content import RawContent
        from src.infrastructure.persistence.psycopg2_article_repo import (
            Psycopg2ArticleRepository,
        )
        from src.infrastructure.persistence.psycopg2_config_repo import (
            Psycopg2ConfigRepository,
        )
        from src.infrastructure.persistence.psycopg2_raw_content_repo import (
            Psycopg2RawContentRepository,
        )
        from src.infrastructure.persistence.psycopg2_similarity_repo import (
            Psycopg2SimilarityRepository,
        )

        rc_repo = Psycopg2RawContentRepository(conn=pg_connection)
        art_repo = Psycopg2ArticleRepository(conn=pg_connection)
        sim_repo = Psycopg2SimilarityRepository(conn=pg_connection)
        config_repo = Psycopg2ConfigRepository(conn=pg_connection)

        use_case = ProcessBatchUseCase(
            encoder=mock_encoder,
            article_repo=art_repo,
            similarity_repo=sim_repo,
            raw_content_repo=rc_repo,
            config_port=config_repo,
        )

        rc = RawContent(
            id=None, kafka_topic="news", kafka_key=None,
            raw_text="Integration pipeline test article",
            source="test", published_at=None,
            ingested_at=None, status="pending", batch_id=None,
        )
        rc_repo.insert_batch([rc])
        batch = rc_repo.fetch_pending_batch(batch_size=10)

        result = use_case.execute(batch)

        assert result.total_processed == 1
        assert result.new_articles == 1
        assert result.errors == 0

        cur = pg_connection.cursor()
        cur.execute("SELECT COUNT(*) FROM articles")
        assert cur.fetchone()[0] >= 1

    @pytest.mark.integration
    def test_process_exact_duplicate_creates_exact_edge(
        self, pg_connection, mock_encoder
    ):
        from src.application.use_cases.process_batch import ProcessBatchUseCase
        from src.domain.entities.raw_content import RawContent
        from src.infrastructure.persistence.psycopg2_article_repo import (
            Psycopg2ArticleRepository,
        )
        from src.infrastructure.persistence.psycopg2_config_repo import (
            Psycopg2ConfigRepository,
        )
        from src.infrastructure.persistence.psycopg2_raw_content_repo import (
            Psycopg2RawContentRepository,
        )
        from src.infrastructure.persistence.psycopg2_similarity_repo import (
            Psycopg2SimilarityRepository,
        )

        rc_repo = Psycopg2RawContentRepository(conn=pg_connection)
        art_repo = Psycopg2ArticleRepository(conn=pg_connection)
        sim_repo = Psycopg2SimilarityRepository(conn=pg_connection)
        config_repo = Psycopg2ConfigRepository(conn=pg_connection)

        use_case = ProcessBatchUseCase(
            encoder=mock_encoder,
            article_repo=art_repo,
            similarity_repo=sim_repo,
            raw_content_repo=rc_repo,
            config_port=config_repo,
        )

        # First article
        rc1 = RawContent(
            id=None, kafka_topic="news", kafka_key=None,
            raw_text="Exact duplicate test text",
            source="source1", published_at=None,
            ingested_at=None, status="pending", batch_id=None,
        )
        rc_repo.insert_batch([rc1])
        batch1 = rc_repo.fetch_pending_batch(batch_size=10)
        use_case.execute(batch1)

        # Second article (same text)
        rc2 = RawContent(
            id=None, kafka_topic="news", kafka_key=None,
            raw_text="Exact duplicate test text",
            source="source2", published_at=None,
            ingested_at=None, status="pending", batch_id=None,
        )
        rc_repo.insert_batch([rc2])
        batch2 = rc_repo.fetch_pending_batch(batch_size=10)
        result = use_case.execute(batch2)

        assert result.exact_duplicates >= 1

        cur = pg_connection.cursor()
        cur.execute("SELECT COUNT(*) FROM similarities WHERE rel_type = 'EXACT'")
        assert cur.fetchone()[0] >= 1

    @pytest.mark.integration
    def test_process_similar_articles_creates_edges(
        self, pg_connection, mock_encoder_similar
    ):
        from src.application.use_cases.process_batch import ProcessBatchUseCase
        from src.domain.entities.raw_content import RawContent
        from src.infrastructure.persistence.psycopg2_article_repo import (
            Psycopg2ArticleRepository,
        )
        from src.infrastructure.persistence.psycopg2_config_repo import (
            Psycopg2ConfigRepository,
        )
        from src.infrastructure.persistence.psycopg2_raw_content_repo import (
            Psycopg2RawContentRepository,
        )
        from src.infrastructure.persistence.psycopg2_similarity_repo import (
            Psycopg2SimilarityRepository,
        )

        rc_repo = Psycopg2RawContentRepository(conn=pg_connection)
        art_repo = Psycopg2ArticleRepository(conn=pg_connection)
        sim_repo = Psycopg2SimilarityRepository(conn=pg_connection)
        config_repo = Psycopg2ConfigRepository(conn=pg_connection)

        use_case = ProcessBatchUseCase(
            encoder=mock_encoder_similar,
            article_repo=art_repo,
            similarity_repo=sim_repo,
            raw_content_repo=rc_repo,
            config_port=config_repo,
        )

        rc1 = RawContent(
            id=None, kafka_topic="news", kafka_key=None,
            raw_text="Article about central bank rates",
            source="reuters", published_at=None,
            ingested_at=None, status="pending", batch_id=None,
        )
        rc_repo.insert_batch([rc1])
        batch1 = rc_repo.fetch_pending_batch(batch_size=10)
        use_case.execute(batch1)

        rc2 = RawContent(
            id=None, kafka_topic="news", kafka_key=None,
            raw_text="Central bank raises interest rates to 21 percent",
            source="tass", published_at=None,
            ingested_at=None, status="pending", batch_id=None,
        )
        rc_repo.insert_batch([rc2])
        batch2 = rc_repo.fetch_pending_batch(batch_size=10)
        result = use_case.execute(batch2)

        assert result.relationships_created >= 1

        cur = pg_connection.cursor()
        cur.execute(
            "SELECT COUNT(*) FROM similarities WHERE rel_type IN ('DUPLICATE', 'RELATED')"
        )
        assert cur.fetchone()[0] >= 1
