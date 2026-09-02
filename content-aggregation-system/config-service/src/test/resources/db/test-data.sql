DELETE FROM sources;

INSERT INTO sources (id, source_type, name, url, update_frequency_minutes, is_active, parameters, created_at, updated_at)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'HABR', 'Habr Hub Kotlin', 'https://habr.com/hub/kotlin', 30, true, '{"hubId": "kotlin", "subtype": "HUB"}', '2026-01-15 10:00:00', '2026-01-15 10:00:00'),

    ('a0000000-0000-0000-0000-000000000002', 'HABR', 'Habr User JetBrains', 'https://habr.com/users/jetbrains', 60, true, '{"userId": "jetbrains", "subtype": "USER"}', '2026-01-16 12:00:00', '2026-01-16 12:00:00'),

    ('a0000000-0000-0000-0000-000000000003', 'HABR', 'Habr Inactive Source', 'https://habr.com/hub/java', 120, false, '{"hubId": "java", "subtype": "HUB"}', '2026-01-17 08:00:00', '2026-01-17 08:00:00'),

    ('a0000000-0000-0000-0000-000000000004', 'VCRU', 'VC.ru Tech Feed', 'https://vc.ru/tech', 60, true, '{}', '2026-01-18 14:00:00', '2026-01-18 14:00:00'),

    ('a0000000-0000-0000-0000-000000000005', 'TELEGRAM', 'Telegram Dev Channel', 'https://t.me/dev_channel', 15, true, '{"channelId": "dev_channel"}', '2026-01-19 09:00:00', '2026-01-19 09:00:00'),

    ('a0000000-0000-0000-0000-000000000006', 'RSS', 'RSS Blog Feed', 'https://blog.example.com/feed.xml', 120, true, '{"feedUrl": "https://blog.example.com/feed.xml"}', '2026-01-20 16:00:00', '2026-01-20 16:00:00'),

    ('a0000000-0000-0000-0000-000000000007', 'HABR', 'Habr Company Yandex', 'https://habr.com/company/yandex', 45, true, '{"companyId": "yandex", "subtype": "COMPANY"}', '2026-01-10 07:00:00', '2026-01-10 07:00:00');
