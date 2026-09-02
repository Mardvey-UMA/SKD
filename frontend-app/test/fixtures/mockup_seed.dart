/// Seed fixtures ported verbatim from `design/reference/mockup/data.jsx`
/// (Radar web mockup). Used by golden tests under `test/goldens/` so
/// visual regressions are anchored to the canonical mockup content.
///
/// Source of truth — `design/reference/mockup/data.jsx`. Any update there
/// MUST be mirrored here (and vice versa).
library;

import 'package:frontend_app/models/ad.dart';
import 'package:frontend_app/ui/atoms/stripe_placeholder.dart';
import 'package:frontend_app/ui/cards/card_item.dart';

/// Kind tag — mirrors `FEED[i].kind` from `data.jsx`.
enum MockKind { short, long }

/// Image-mode tag — mirrors `FEED[i].images` from `data.jsx`.
enum MockImages { one, multi, none }

/// Entry as it appears in the JS mockup, retaining every original field.
class MockFeedEntry {
  const MockFeedEntry({
    required this.id,
    required this.kind,
    required this.images,
    required this.source,
    required this.sourceHandle,
    required this.time,
    required this.read,
    required this.tone,
    required this.seed,
    required this.title,
    required this.snippet,
    this.toneSecondary,
    this.toneTertiary,
  });

  final String id;
  final MockKind kind;
  final MockImages images;
  final String source;
  final String sourceHandle;
  final String time;
  final String read;
  final StripeTone tone;
  final int seed;
  final String title;
  final String snippet;
  final StripeTone? toneSecondary;
  final StripeTone? toneTertiary;

  CardImages get cardImages => switch (images) {
        MockImages.one => CardImages.one,
        MockImages.multi => CardImages.multi,
        MockImages.none => CardImages.none,
      };
}

/// Interest chip — mirrors `INTERESTS[i]` (`t`, `e`).
class MockInterest {
  const MockInterest({required this.label, required this.emoji});

  final String label;
  final String emoji;
}

/// Advertisements — mirrors `ADS` in `data.jsx`. `hue` is retained for
/// parity with the JSX mockup even though the Dart [Ad] model does not
/// consume it.
class MockAd {
  const MockAd({
    required this.id,
    required this.brand,
    required this.tagline,
    required this.title,
    required this.desc,
    required this.cta,
    required this.hue,
  });

  final String id;
  final String brand;
  final String tagline;
  final String title;
  final String desc;
  final String cta;
  final int hue;

  Ad toAd() => Ad(
        brand: brand,
        tagline: tagline,
        title: title,
        desc: desc,
        cta: cta,
      );
}

/// Sponsored content — verbatim port of `ADS`.
const List<MockAd> kMockAds = <MockAd>[
  MockAd(
    id: 'ad1',
    brand: 'Skillbox',
    tagline: 'Скидка 45% на курсы до конца недели',
    title: 'Стать data-аналитиком за 6 месяцев',
    desc:
        'Онлайн-обучение с проектами в портфолио и наставником. Первые две недели бесплатно.',
    cta: 'Узнать подробнее',
    hue: 250,
  ),
  MockAd(
    id: 'ad2',
    brand: 'Т-Банк',
    tagline: 'Бизнес-карта · обслуживание 0 ₽',
    title: 'Откройте счёт для бизнеса онлайн',
    desc:
        'Без визита в банк, за 10 минут. Кэшбэк до 7% на B2B-сервисы.',
    cta: 'Открыть счёт',
    hue: 28,
  ),
  MockAd(
    id: 'ad3',
    brand: 'Notion AI',
    tagline: 'Попробуй бесплатно',
    title: 'Все заметки, задачи и доки в одном рабочем пространстве',
    desc:
        'Автоматическое саммари встреч, поиск по базе знаний и шаблоны под любую команду.',
    cta: 'Начать',
    hue: 0,
  ),
];

/// Feed seed — verbatim port of `FEED`.
const List<MockFeedEntry> kMockFeed = <MockFeedEntry>[
  MockFeedEntry(
    id: 'a1',
    kind: MockKind.short,
    images: MockImages.one,
    source: 'Хабр',
    sourceHandle: '@habr',
    time: '12 мин',
    read: '2 мин чтения',
    tone: StripeTone.ink,
    seed: 1,
    title: 'Фотонные вычислители достигли стабильности при комнатной температуре',
    snippet:
        'Решётка кремниевых резонаторов удержала когерентность 18 часов без криогенной поддержки — первый серьёзный порог на пути к коммерческому оптическому инференсу.',
  ),
  MockFeedEntry(
    id: 'a2',
    kind: MockKind.long,
    images: MockImages.one,
    source: 'VC.RU',
    sourceHandle: '@vcru',
    time: '1 ч',
    read: '12 мин чтения',
    tone: StripeTone.accent,
    seed: 2,
    title: 'Тихий захват: почему все переходят на векторные базы данных',
    snippet:
        'Каждую неделю очередной enterprise незаметно переводит поиск с ключевых слов на векторные хранилища. Это один из крупнейших инфраструктурных сдвигов десятилетия — и кроме инженерных команд его почти никто не замечает. Вот что меняется, когда смысл заменяет совпадение.',
  ),
  MockFeedEntry(
    id: 'a3',
    kind: MockKind.short,
    images: MockImages.multi,
    source: 'Telegram',
    sourceHandle: '@designpub',
    time: '3 ч',
    read: '3 мин чтения',
    tone: StripeTone.lime,
    seed: 3,
    title:
        'Как дизайнеры из Лагоса переизобретают интерфейсы мобильных платежей',
    snippet:
        'Движение сначала, данные потом. Шесть команд. Одна валюта. Совершенно другое представление о том, как должен выглядеть экран баланса.',
    toneSecondary: StripeTone.violet,
    toneTertiary: StripeTone.teal,
  ),
  MockFeedEntry(
    id: 'a4',
    kind: MockKind.short,
    images: MockImages.none,
    source: 'Хабр',
    sourceHandle: '@habr',
    time: '6 ч',
    read: '4 мин чтения',
    tone: StripeTone.ink,
    seed: 4,
    title: 'Что Арктическая энергосеть рассказывает нам про устойчивость систем',
    snippet:
        'Посёлки выше 70-й параллели за десять лет перестраивали электроинфраструктуру четыре раза. Их режимы отказов — это наш 2035-й. Коллеги из Лонгйира, Тромсё и Мурманска описывают культуру ремонта, которую уже изучают коммунальные службы от Техаса до Сеула.',
  ),
  MockFeedEntry(
    id: 'a5',
    kind: MockKind.short,
    images: MockImages.one,
    source: 'VC.RU',
    sourceHandle: '@vcru',
    time: 'Вчера',
    read: '4 мин чтения',
    tone: StripeTone.rose,
    seed: 5,
    title: 'Criterion тихо выпустили 4K-реставрацию «Зеркала» Тарковского',
    snippet:
        'Без пресс-релиза, без трейлера — просто новый мастер загружен на канал в 03:00 UTC. Цветокор заметно холоднее.',
  ),
  MockFeedEntry(
    id: 'a6',
    kind: MockKind.long,
    images: MockImages.none,
    source: 'Telegram',
    sourceHandle: '@founderstalk',
    time: '2 д',
    read: '9 мин чтения',
    tone: StripeTone.ink,
    seed: 6,
    title: 'Почему основатели снова и снова пересобирают один и тот же CRM',
    snippet:
        'Восемь компаний, все решают управление отношениями с клиентами, и все убеждены, что именно их продукт станет последним. Мы поговорили со всеми основателями текущего когорта и нашли удивительно единое непонимание того, чем отделы продаж вообще занимаются весь день.',
  ),
];

/// Full long-form body for the detail screen — verbatim port of `FULL_BODY`.
const String kMockFullBody = '''История начинается, как это часто бывает, с разногласия о том, для чего вообще нужна база данных.

Сорок лет консенсус был комфортным: база хранит факты, а вы просите у неё совпадений. Даёшь слово — получаешь строки, в которых это слово встречается. Допущение под этим — что смысл можно свести к пересечению — никто не ставил под сомнение просто потому, что на масштабе ничего другого не работало.

Векторные базы ломают это допущение. Они хранят не факты, а позиции — координаты в многомерном пространстве, где близость означает сходство смысла. Слово «база» оказывается ближе к «каталогу», чем к «данным», хотя строковое совпадение сказало бы обратное.

Что это делает с компанией? Тихо, почти незаметно это перепрошивает то, как работает каждый внутренний инструмент. Тикеты поддержки кластеризуются по намерению, а не по ключевому слову. Поиск по документам подсказывает то, что вы почти забыли. Дедупликация наконец становится решаемой. Паттерн, который мы видим в миграциях: новая система ведёт себя меньше как инструмент и больше как коллега, который всё прочитал.

Это не хайп. Это инфраструктура. Тот же паттерн, который десять лет назад гнал компании с on-prem в облако, теперь переводит их с ключевых слов на векторы — только в этот раз пользователь слой не видит. Он просто замечает, что поиск вдруг стал подозрительно хорошим.''';

/// Interest catalogue — verbatim port of `INTERESTS`.
const List<MockInterest> kMockInterests = <MockInterest>[
  MockInterest(label: 'Технологии', emoji: '💻'),
  MockInterest(label: 'Игры', emoji: '🎮'),
  MockInterest(label: 'Кино и сериалы', emoji: '🎬'),
  MockInterest(label: 'Лонгриды', emoji: '📚'),
  MockInterest(label: 'ИИ', emoji: '🤖'),
  MockInterest(label: 'Бизнес', emoji: '💼'),
  MockInterest(label: 'Дизайн', emoji: '🎨'),
  MockInterest(label: 'Климат', emoji: '🌱'),
  MockInterest(label: 'Мемы', emoji: '😂'),
  MockInterest(label: 'Наука', emoji: '🔬'),
  MockInterest(label: 'Фото', emoji: '📸'),
  MockInterest(label: 'Финансы', emoji: '💶'),
  MockInterest(label: 'Музыка', emoji: '🎧'),
  MockInterest(label: 'Спорт', emoji: '🏃'),
  MockInterest(label: 'Архитектура', emoji: '🏛'),
  MockInterest(label: 'Город', emoji: '🌆'),
];

/// Deterministic `DateTime` for use as `CardItem.time` in goldens. Goldens
/// must be deterministic — never use `DateTime.now()`. 2026-04-20 12:00 UTC.
final DateTime kMockNow = DateTime.utc(2026, 4, 20, 12, 0, 0);

/// Maps a [MockFeedEntry] to the in-app [CardItem] consumed by
/// `ShortCard` / `LongCard`.
CardItem mockEntryToCardItem(MockFeedEntry entry) {
  return CardItem(
    id: entry.id,
    source: entry.source,
    time: kMockNow,
    readTime: entry.read,
    title: entry.title,
    snippet: entry.snippet,
    images: entry.cardImages,
    tone: entry.tone,
    seed: entry.seed,
    toneSecondary: entry.toneSecondary,
    toneTertiary: entry.toneTertiary,
  );
}
