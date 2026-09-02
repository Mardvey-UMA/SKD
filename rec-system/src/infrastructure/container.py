"""Dependency-injector DI container for the recommendation service."""
from __future__ import annotations

from dependency_injector import containers, providers
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker

from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository
from src.infrastructure.persistence.pg_content_repository import PgContentRepository
from src.infrastructure.persistence.pg_config_repository import PgConfigRepository
from src.infrastructure.config.rec_config_loader import RecConfigLoader
from src.infrastructure.persistence.pg_entity_interest_repository import PgEntityInterestRepository
from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository
from src.infrastructure.persistence.pg_category_repository import PgCategoryRepository
from src.infrastructure.persistence.pg_recommendation_history_repository import PgRecommendationHistoryRepository
from src.infrastructure.persistence.pg_blocked_sources_repository import PgBlockedSourcesRepository

from src.infrastructure.nlp.torch_topic_classifier import TorchTopicClassifier
from src.infrastructure.nlp.torch_sentiment_analyzer import TorchSentimentAnalyzer
from src.infrastructure.nlp.spacy_entity_extractor import SpacyEntityExtractor
from src.infrastructure.nlp.torch_content_encoder import TorchContentEncoder
from src.infrastructure.nlp.torch_cross_encoder import TorchCrossEncoder
from src.infrastructure.nlp.text_analyzer_service import TextAnalyzerService

from src.infrastructure.scheduling.job_scheduler import JobScheduler

from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper
from src.infrastructure.messaging.kafka_consumer import KafkaConsumerWrapper
from src.infrastructure.messaging.kafka_event_publisher import KafkaEventPublisher
from src.infrastructure.cache.redis_cache_client import RedisCacheClient

from src.domain.services.scorer import Scorer
from src.domain.services.diversity_filter import DiversityFilter
from src.domain.services.signal_classifier import SignalClassifier
from src.domain.services.profile_updater import ProfileUpdater
from src.domain.services.onboarding_service import OnboardingService
from src.domain.services.narrow_scorer import NarrowScorer

from src.application.services.live_profile_service import LiveProfileService
from src.application.services.hot_content_encoder import HotContentEncoder
from src.application.services.user_context_builder import UserContextBuilder
from src.application.use_cases.generate_feed import GenerateFeedUseCase
from src.application.use_cases.onboard_user import OnboardUserUseCase
from src.application.use_cases.process_content import ProcessContentUseCase
from src.application.use_cases.update_profile import UpdateProfileUseCase
from src.application.use_cases.get_categories import GetCategoriesUseCase
from src.application.use_cases.get_cold_start import GetColdStartUseCase
from src.application.use_cases.get_recommendations import GetRecommendationsUseCase
from src.application.use_cases.handle_user_created import HandleUserCreatedUseCase
from src.application.use_cases.handle_interactions_batch import HandleInteractionsBatchUseCase

from src.presentation.consumers.user_created_consumer import UserCreatedConsumer
from src.presentation.consumers.interactions_batch_consumer import InteractionsBatchConsumer
from src.presentation.consumers.content_published_consumer import ContentPublishedConsumer


def _make_redis_client(redis_url: str):
    """Factory function to create a redis.asyncio.Redis client from a URL."""
    import redis.asyncio as aioredis
    return aioredis.from_url(redis_url, decode_responses=False)


def _make_aiokafka_producer(bootstrap_servers: str):
    """Factory function to create an AIOKafkaProducer."""
    from aiokafka import AIOKafkaProducer
    return AIOKafkaProducer(bootstrap_servers=bootstrap_servers)


def _make_aiokafka_consumer(bootstrap_servers: str, topic: str, group_id: str):
    """Factory function to create an AIOKafkaConsumer."""
    from aiokafka import AIOKafkaConsumer
    return AIOKafkaConsumer(
        topic,
        bootstrap_servers=bootstrap_servers,
        group_id=group_id,
        auto_offset_reset="earliest",
        enable_auto_commit=True,
        session_timeout_ms=60000,       # 60s (default 10s too aggressive)
        heartbeat_interval_ms=10000,    # 10s (must be < session_timeout / 3)
        request_timeout_ms=70000,       # 70s (must be > session_timeout)
        max_poll_interval_ms=600000,    # 10 min (for long NLP processing batches)
    )


class ApplicationContainer(containers.DeclarativeContainer):
    """Main DI container wiring all application dependencies."""

    config = providers.Configuration()

    # --- Database ---

    async_engine = providers.Singleton(
        create_async_engine,
        config.db_url,
        echo=False,
        connect_args={"server_settings": {"search_path": "data_flow,public"}},
    )

    session_factory = providers.Singleton(
        async_sessionmaker,
        async_engine,
        class_=AsyncSession,
        expire_on_commit=False,
    )

    # --- Redis ---

    redis_client = providers.Singleton(
        _make_redis_client,
        redis_url=config.redis_url,
    )

    redis_cache_client = providers.Singleton(
        RedisCacheClient,
        redis_client=redis_client,
    )

    # --- Kafka Producer ---

    _aiokafka_producer = providers.Singleton(
        _make_aiokafka_producer,
        bootstrap_servers=config.kafka_bootstrap_servers,
    )

    kafka_producer_wrapper = providers.Singleton(
        KafkaProducerWrapper,
        producer=_aiokafka_producer,
    )

    kafka_event_publisher = providers.Singleton(
        KafkaEventPublisher,
        producer=kafka_producer_wrapper,
    )

    # --- Kafka Consumers ---

    _aiokafka_consumer_user_created = providers.Singleton(
        _make_aiokafka_consumer,
        bootstrap_servers=config.kafka_bootstrap_servers,
        topic="user.created",
        group_id="rec-system-user-events",
    )

    _aiokafka_consumer_interactions_batch = providers.Singleton(
        _make_aiokafka_consumer,
        bootstrap_servers=config.kafka_bootstrap_servers,
        topic="user.interactions.batch",
        group_id="rec-system-interactions",
    )

    _aiokafka_consumer_content_published = providers.Singleton(
        _make_aiokafka_consumer,
        bootstrap_servers=config.kafka_bootstrap_servers,
        topic="content.published",
        group_id="rec-system-hot-content",
    )

    _kafka_consumer_wrapper_user_created = providers.Singleton(
        KafkaConsumerWrapper,
        consumer=_aiokafka_consumer_user_created,
    )

    _kafka_consumer_wrapper_interactions_batch = providers.Singleton(
        KafkaConsumerWrapper,
        consumer=_aiokafka_consumer_interactions_batch,
    )

    _kafka_consumer_wrapper_content_published = providers.Singleton(
        KafkaConsumerWrapper,
        consumer=_aiokafka_consumer_content_published,
    )

    # --- Repositories ---

    user_profile_repo = providers.Factory(
        PgUserProfileRepository,
        session_factory=session_factory,
    )

    content_repo = providers.Factory(
        PgContentRepository,
        session_factory=session_factory,
    )

    config_repo = providers.Factory(
        PgConfigRepository,
        session_factory=session_factory,
    )

    rec_config_loader = providers.Singleton(
        RecConfigLoader,
        config_repo=config_repo,
        cache_ttl_seconds=config.rec_config_cache_ttl,
    )

    entity_interest_repo = providers.Factory(
        PgEntityInterestRepository,
        session_factory=session_factory,
    )

    interaction_repo = providers.Factory(
        PgInteractionRepository,
        session_factory=session_factory,
    )

    category_repo = providers.Factory(
        PgCategoryRepository,
        session_factory=session_factory,
    )

    recommendation_history_repo = providers.Factory(
        PgRecommendationHistoryRepository,
        session_factory=session_factory,
    )

    blocked_sources_repo = providers.Factory(
        PgBlockedSourcesRepository,
        session_factory=session_factory,
    )

    # --- NLP Adapters (singletons — models loaded once at startup) ---

    topic_classifier = providers.Singleton(TorchTopicClassifier, device="cuda")

    sentiment_analyzer = providers.Singleton(TorchSentimentAnalyzer, device="cuda")

    entity_extractor = providers.Singleton(SpacyEntityExtractor)

    content_encoder = providers.Singleton(TorchContentEncoder, device="cuda")

    text_analyzer = providers.Singleton(TextAnalyzerService)

    # Cross-encoder reranker (Phase C) — lazy-loaded singleton; model loaded on first use
    cross_encoder = providers.Singleton(TorchCrossEncoder)

    # User context builder (Phase C) — stateless, Factory is fine but Singleton is lighter
    user_context_builder = providers.Singleton(UserContextBuilder)

    # --- Domain Services ---

    scorer = providers.Singleton(Scorer)

    narrow_scorer = providers.Singleton(NarrowScorer)

    diversity_filter = providers.Singleton(DiversityFilter)

    signal_classifier = providers.Factory(
        SignalClassifier,
        signal_weights={
            "impression_read": {"threshold_duration_ms": 2000, "weight": 0.15},
            "impression_skip": {"threshold_duration_ms": 2000, "weight": -0.05},
            "open": {"weight": 0.1},
            "close_fast": {"threshold_duration_ms": 3000, "weight": -0.2},
            "close_full": {"threshold_scroll_pct": 0.85, "threshold_duration_ms": 15000, "weight": 0.5},
            "close_half": {"threshold_scroll_pct": 0.5, "threshold_duration_ms": 10000, "weight": 0.4},
            "close_other": {"weight": 0.1},
            "like": {"weight": 0.6},
            "dislike": {"weight": -0.7},
            "bookmark": {"weight": 0.8},
        },
    )

    profile_updater = providers.Singleton(ProfileUpdater)

    onboarding_service = providers.Singleton(OnboardingService)

    live_profile_service = providers.Factory(
        LiveProfileService,
        interaction_repo=interaction_repo,
        content_repo=content_repo,
        signal_classifier=signal_classifier,
    )

    # --- Use Cases ---

    generate_feed_use_case = providers.Factory(
        GenerateFeedUseCase,
        profile_repo=user_profile_repo,
        content_repo=content_repo,
        entity_interest_repo=entity_interest_repo,
        interaction_repo=interaction_repo,
        config_repo=rec_config_loader,
        scorer=scorer,
        diversity_filter=diversity_filter,
        signal_classifier=signal_classifier,
        profile_updater=profile_updater,
        blocked_sources_repo=blocked_sources_repo,
        narrow_scorer=narrow_scorer,
        live_profile_service=live_profile_service,
        reranker=cross_encoder,
        context_builder=user_context_builder,
    )

    onboard_user_use_case = providers.Factory(
        OnboardUserUseCase,
        profile_repo=user_profile_repo,
        config_repo=rec_config_loader,
        onboarding_service=onboarding_service,
        category_repo=category_repo,
        content_repo=content_repo,
        event_publisher=kafka_event_publisher,
    )

    process_content_use_case = providers.Factory(
        ProcessContentUseCase,
        content_repo=content_repo,
        topic_classifier=topic_classifier,
        sentiment_analyzer=sentiment_analyzer,
        entity_extractor=entity_extractor,
        content_encoder=content_encoder,
        text_analyzer=text_analyzer,
        max_nlp_tokens=config.rec_max_nlp_tokens,
        max_text_bytes=config.rec_max_clean_text_bytes,
        truncate_text_bytes=config.rec_truncate_text_bytes,
    )

    update_profile_use_case = providers.Factory(
        UpdateProfileUseCase,
        interaction_repo=interaction_repo,
        profile_repo=user_profile_repo,
        entity_interest_repo=entity_interest_repo,
        config_repo=rec_config_loader,
        signal_classifier=signal_classifier,
        profile_updater=profile_updater,
        content_repo=content_repo,
    )

    get_categories_use_case = providers.Factory(
        GetCategoriesUseCase,
        category_repo=category_repo,
        config_repo=rec_config_loader,
    )

    get_cold_start_use_case = providers.Factory(
        GetColdStartUseCase,
        cache_client=redis_cache_client,
        content_repo=content_repo,
    )

    get_recommendations_use_case = providers.Factory(
        GetRecommendationsUseCase,
        generate_feed_uc=generate_feed_use_case,
        history_repo=recommendation_history_repo,
        profile_repo=user_profile_repo,
    )

    handle_user_created_use_case = providers.Factory(
        HandleUserCreatedUseCase,
        profile_repo=user_profile_repo,
    )

    handle_interactions_batch_use_case = providers.Factory(
        HandleInteractionsBatchUseCase,
        profile_repo=user_profile_repo,
        content_repo=content_repo,
        signal_classifier=signal_classifier,
        profile_updater=profile_updater,
        event_publisher=kafka_event_publisher,
        cache_client=redis_cache_client,
        entity_interest_repo=entity_interest_repo,
        interaction_repo=interaction_repo,
        history_repo=recommendation_history_repo,
    )

    # --- Kafka Consumers ---

    user_created_consumer = providers.Singleton(
        UserCreatedConsumer,
        consumer=_kafka_consumer_wrapper_user_created,
        use_case=handle_user_created_use_case,
    )

    interactions_batch_consumer = providers.Singleton(
        InteractionsBatchConsumer,
        consumer=_kafka_consumer_wrapper_interactions_batch,
        use_case=handle_interactions_batch_use_case,
    )

    hot_content_encoder = providers.Factory(
        HotContentEncoder,
        process_content_use_case=process_content_use_case,
    )

    content_published_consumer = providers.Singleton(
        ContentPublishedConsumer,
        consumer=_kafka_consumer_wrapper_content_published,
        encoder=hot_content_encoder,
        enabled=True,
    )

    # --- Scheduler ---

    job_scheduler = providers.Singleton(
        JobScheduler,
        process_content_use_case=process_content_use_case,
        update_profile_use_case=update_profile_use_case,
    )
