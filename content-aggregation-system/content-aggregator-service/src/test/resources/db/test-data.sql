DELETE FROM data_flow.similarities;
DELETE FROM data_flow.articles;
DELETE FROM data_flow.published_content;

INSERT INTO data_flow.published_content (id, content_id, external_id, title, description, source_id, source_type, url, published_at, author_name, media, metadata, content_format, dedup_article_id, created_at, updated_at)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'habr-001', 'Kotlin coroutines tutorial', 'Learn how to use Kotlin coroutines effectively', 'c0000000-0000-0000-0000-000000000001', 'HABR', 'https://habr.com/article/1', '2026-01-15 10:00:00+00', 'Author One', '[{"url": "https://s3.example.com/img1.jpg", "type": "image/jpeg"}]', '{"sourceSubtype": "HUB"}', 'HTML', 1, '2026-01-15 10:00:00+00', '2026-01-15 10:00:00+00'),

    ('a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', 'habr-002', 'Spring Boot 3 migration guide', 'Step by step guide for migrating to Spring Boot 3', 'c0000000-0000-0000-0000-000000000001', 'HABR', 'https://habr.com/article/2', '2026-01-16 12:00:00+00', 'Author Two', NULL, '{"habrSubtype": "HABR_USER"}', 'HTML', 2, '2026-01-16 12:00:00+00', '2026-01-16 12:00:00+00'),

    ('a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000003', 'habr-003', 'PostgreSQL performance tips', 'Advanced PostgreSQL optimization techniques', 'c0000000-0000-0000-0000-000000000002', 'HABR', 'https://habr.com/article/3', '2026-01-17 08:00:00+00', 'Author Three', NULL, NULL, 'HTML', 3, '2026-01-17 08:00:00+00', '2026-01-17 08:00:00+00'),

    ('a0000000-0000-0000-0000-000000000004', 'b0000000-0000-0000-0000-000000000004', 'vcru-001', 'Startup funding trends 2026', 'Analysis of venture capital in 2026', 'c0000000-0000-0000-0000-000000000003', 'VCRU', 'https://vc.ru/article/1', '2026-01-18 14:00:00+00', 'Author Four', NULL, NULL, 'HTML', NULL, '2026-01-18 14:00:00+00', '2026-01-18 14:00:00+00'),

    ('a0000000-0000-0000-0000-000000000005', 'b0000000-0000-0000-0000-000000000005', 'tg-001', 'Telegram channel update', 'Daily digest from tech telegram channel', 'c0000000-0000-0000-0000-000000000004', 'TELEGRAM', 'https://t.me/channel/123', '2026-01-19 09:00:00+00', 'Channel Bot', NULL, NULL, 'HTML', NULL, '2026-01-19 09:00:00+00', '2026-01-19 09:00:00+00'),

    ('a0000000-0000-0000-0000-000000000006', 'b0000000-0000-0000-0000-000000000006', 'rss-001', 'RSS feed article about Kotlin', 'Kotlin tips and tricks from RSS feed', 'c0000000-0000-0000-0000-000000000005', 'RSS', 'https://blog.example.com/kotlin', '2026-01-20 16:00:00+00', 'RSS Author', NULL, NULL, 'HTML', NULL, '2026-01-20 16:00:00+00', '2026-01-20 16:00:00+00'),

    ('a0000000-0000-0000-0000-000000000007', 'b0000000-0000-0000-0000-000000000007', 'habr-004', 'Docker best practices', 'Container orchestration with Docker', 'c0000000-0000-0000-0000-000000000001', 'HABR', 'https://habr.com/article/4', '2026-01-10 07:00:00+00', 'Author Five', NULL, NULL, 'HTML', NULL, '2026-01-10 07:00:00+00', '2026-01-10 07:00:00+00');

-- articles linked to the first 3 published_content rows
INSERT INTO data_flow.articles (id, raw_content_id, content_hash, source)
OVERRIDING SYSTEM VALUE
VALUES
    (1, 'b0000000-0000-0000-0000-000000000001', 'hash1', 'HABR'),
    (2, 'b0000000-0000-0000-0000-000000000002', 'hash2', 'HABR'),
    (3, 'b0000000-0000-0000-0000-000000000003', 'hash3', 'HABR');

-- article 1 is RELATED to article 2 (score 0.9), and RELATED to article 3 (score 0.7)
INSERT INTO data_flow.similarities (article_a, article_b, score, rel_type)
VALUES
    (1, 2, 0.9, 'RELATED'),
    (1, 3, 0.7, 'RELATED'),
    (2, 3, 0.5, 'DUPLICATE');
