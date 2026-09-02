# Радар — Design System Tokens

> Справочник design tokens для Flutter Web приложения «Радар».
> Версия: `0.1.0` · Последнее обновление: 2026-04-17
> Платформа: Flutter Web (mobile-first), потенциально Flutter Android
> Тема: Light (Dark предусмотрена архитектурно, но не реализована)

---

## 0. Философия

Приложение следует направлению **мягкого нео-футуризма**: трёхслойная глубина (aurora-фон → непрозрачные карточки → стеклянные плавающие элементы), органические градиенты низкой насыщенности, пастельные акценты для семантического выделения, плавные пружинные переходы. Цель — ощущение «умного, живого, спокойного» интерфейса без визуального шума.

Токены организованы в две группы: **primitive tokens** (сырые значения — цвета, размеры) и **semantic tokens** (осмысленные роли — `surface-card`, `text-primary`). В коде используются только semantic tokens; primitive живут внутри одного файла и не экспортируются наружу. Это даёт возможность безболезненно добавить dark-тему позже — изменится только маппинг semantic → primitive.

Все имена токенов даны на английском по конвенции дизайн-систем. Комментарии и rationale — на русском.

---

## 1. Color Tokens

### 1.1 Primitive палитра

| Токен | HEX | Описание |
|---|---|---|
| `neutral-0` | `#FFFFFF` | Чистый белый |
| `neutral-50` | `#F5F7FC` | Базовый фон (холодный с голубым подтоном) |
| `neutral-100` | `#EEF1F9` | Вторичный фон, чередование секций |
| `neutral-150` | `#E4E8F2` | Разделители на белом |
| `neutral-200` | `#D0D6E3` | Обводки, disabled borders |
| `neutral-300` | `#B4BCCC` | Текст disabled |
| `neutral-400` | `#8792A6` | Текст tertiary |
| `neutral-500` | `#667085` | Текст secondary (альт.) |
| `neutral-600` | `#485063` | Текст secondary |
| `neutral-700` | `#2E3647` | Текст на светлом, не primary |
| `neutral-900` | `#0E1525` | Текст primary (почти чёрный с синевой, НЕ `#000`) |
| `brand-50` | `#EEF1FF` | Primary tint, фон выделения |
| `brand-100` | `#DDE3FF` | Hover-фон для primary-иконок |
| `brand-400` | `#6E84FF` | Акценты, link hover |
| `brand-500` | `#3D5BFF` | **Основной брендовый цвет** |
| `brand-600` | `#2D48E8` | Pressed / hover для CTA |
| `brand-700` | `#1E35C2` | Максимально насыщенный, для текста на светлом |
| `violet-500` | `#7364FF` | Вторая точка в primary-градиенте |
| `success-500` | `#16B67A` | Success семантика |
| `success-50` | `#E3F7EE` | Success фон |
| `warning-500` | `#F5A623` | Warning семантика |
| `warning-50` | `#FFF2D8` | Warning фон |
| `error-500` | `#E54B6B` | Error семантика (мягче классического красного) |
| `error-50` | `#FDEAEE` | Error фон |

### 1.2 Акцентные «пастельные» пары

Каждая пара — это `fg` (для иконки/акцента) и `bg` (для плашки под иконкой). Используются для: иконок в меню Профиля/Настроек, цветов пространств, пустых состояний, категорийных чипов.

| Пара | fg | bg | Назначение по умолчанию |
|---|---|---|---|
| `accent-sky` | `#BCD4FF` | `#E8F0FF` | Закладки, информация |
| `accent-violet` | `#C9BEFF` | `#ECE7FF` | Интересы, кастомизация |
| `accent-rose` | `#FFB8D1` | `#FFE5EF` | Likes, эмоциональные акценты |
| `accent-amber` | `#FFD88A` | `#FFF2D8` | Free-tier badge, warnings |
| `accent-mint` | `#9FE8CB` | `#E0F7EE` | Success состояния |
| `accent-coral` | `#FF9B8A` | `#FFE4DE` | Dislikes, выделение |

**Важно:** цвета пространств (те самые кружки с выбранным orange) должны браться из этого же набора, чтобы ничто в интерфейсе не выбивалось. Текущие 8 цветов на экране редактирования пространства → заменить на набор из 8 акцентных, включая вариации.

### 1.3 Semantic цвета

```dart
// Background layers
surface.background          = neutral-50       // базовый фон приложения
surface.backgroundSubtle    = neutral-100      // альт. фон для чередования
surface.base                = neutral-0        // непрозрачные карточки
surface.raised              = neutral-0        // то же, но с большей тенью
surface.sunken              = neutral-100      // «утопленные» контейнеры (segmented control track)

// Glass layers (используются ТОЛЬКО с BackdropFilter)
surface.glass               = rgba(255,255,255, 0.68)
surface.glassStrong         = rgba(255,255,255, 0.82)
surface.glassHighlight      = rgba(255,255,255, 0.60)  // верхняя грань стекла

// Borders & dividers
border.soft                 = rgba(15,24,40, 0.06)
border.default              = rgba(15,24,40, 0.10)
border.strong               = rgba(15,24,40, 0.16)
border.focus                = brand-500
border.focusHalo            = rgba(61,91,255, 0.16)   // для box-shadow focus-ring
border.error                = error-500
border.errorHalo            = rgba(229,75,107, 0.14)

// Text
text.primary                = neutral-900
text.secondary              = neutral-600
text.tertiary               = neutral-400
text.disabled               = neutral-300
text.onPrimary              = neutral-0           // текст на brand-500
text.onAccent               = neutral-900         // текст на пастельных плашках
text.link                   = brand-500
text.error                  = error-500
text.success                = success-500

// Brand / interactive
interactive.primary         = brand-500
interactive.primaryHover    = brand-600
interactive.primaryPressed  = brand-700
interactive.primarySubtle   = brand-50            // фон для ghost-кнопок, pill-табов
interactive.primaryMuted    = rgba(61,91,255, 0.40)  // disabled primary
```

### 1.4 Правила применения

Фон приложения никогда не бывает плоским `#E5E7EB` (как сейчас) — это **всегда** aurora-градиент (см. §5.1). Белый `neutral-0` используется только для карточек, модалок и поверхностей «выше» фона. Чёрный `#000000` не используется нигде — даже для текста берётся `neutral-900`. Границы всегда берутся из полупрозрачных `border.*`, а не из сплошных нейтралей, чтобы корректно смотреться на любом подложке.

---

## 2. Typography

### 2.1 Семейства шрифтов

Основной стек — **Inter** для тела и **Manrope** для заголовков. Оба покрывают кириллицу полностью, хорошо рендерятся в Flutter Web и имеют variable-версии (экономия на весах).

```dart
fontFamily.display = 'Manrope'    // display-xl, display-l, heading-l
fontFamily.text    = 'Inter'      // всё остальное
fontFamily.mono    = 'JetBrainsMono'  // коды, tabular-nums в таймерах
```

**Подключение в Flutter:** скачать variable-шрифты с [rsms.me/inter](https://rsms.me/inter/) и [fontshare.com/fonts/manrope](https://www.fontshare.com/fonts/manrope), положить в `assets/fonts/`, прописать в `pubspec.yaml`. Общий вес обоих семейств — около 600 KB, для веба приемлемо; если критично — можно загружать `font-display: swap` через CSS, но тогда будет FOUT.

### 2.2 Шкала

Все значения: `размер px / line-height px`, `вес`, `letter-spacing em`. Line-height всегда в абсолютных пикселях, чтобы ритм не плавал.

| Токен | Размер | Line-height | Вес | Letter-spacing | Назначение |
|---|---|---|---|---|---|
| `display-xl` | 32 | 40 | 700 | -0.015 | Hero-заголовки onboarding («Открывайте контент…») |
| `display-l` | 28 | 36 | 700 | -0.013 | Крупные заголовки экранов |
| `heading-l` | 22 | 28 | 600 | -0.008 | Заголовки страниц («Настройки», «Коллекции») |
| `heading-m` | 18 | 24 | 600 | -0.004 | Заголовки карточек, аватар email |
| `heading-s` | 16 | 22 | 600 | 0 | Названия пунктов меню |
| `body-l` | 16 | 24 | 400 | 0 | Основной текст |
| `body-m` | 14 | 20 | 400 | 0 | Описания карточек, вторичный текст |
| `body-s` | 13 | 18 | 400 | 0.002 | Мета-информация (автор · дата) |
| `caption` | 12 | 16 | 500 | 0.025 | Секционные лейблы (UPPERCASE), badges |
| `button` | 15 | 20 | 600 | 0.004 | Текст кнопок |
| `overline` | 11 | 14 | 600 | 0.06 | UPPERCASE лейблы секций |
| `numeric` | 18 | 24 | 600 | 0 | Таймеры, счётчики (tabular-nums) |

### 2.3 Правила

Для UPPERCASE-лейблов всегда добавляется `letter-spacing ≥ 0.06em`, иначе буквы слипаются. Для цифровых таймеров (`03:52` на экране подтверждения) обязательно `font-feature-settings: "tnum"` — иначе цифры «прыгают» при каждой секунде. Негативный letter-spacing применяется только к заголовкам от 18 px и выше; на мелком тексте он ломает читаемость. Максимальная длина строки для основного текста — 64 символа, для этого на мобильных хватает 20 px боковых отступов.

---

## 3. Spacing

### 3.1 Шкала

База — **4 px**. Вся шкала построена на умножениях базы, что гарантирует вертикальный ритм и удобство для разработчика.

| Токен | Значение | Типичное применение |
|---|---|---|
| `space-2xs` | 4 | Зазор между иконкой и текстом |
| `space-xs` | 8 | Внутренний отступ чипа, gap между мета-элементами |
| `space-sm` | 12 | Gap между элементами списка |
| `space-md` | 16 | Padding внутри карточки |
| `space-lg` | 20 | **Боковой отступ экрана** |
| `space-xl` | 24 | Отступ между секциями |
| `space-2xl` | 32 | Крупный отступ между секциями настроек |
| `space-3xl` | 40 | Отступ перед hero-контентом |
| `space-4xl` | 56 | Вертикальный отступ onboarding-блоков |

### 3.2 Обязательные правила

Боковой отступ экрана — всегда `space-lg` (20 px). Это применяется к контейнеру уровня `Scaffold.body`, внутренние элементы (карточки) получают эти 20 px автоматически. Padding внутри карточки — `space-md` (16 px) со всех сторон. Вертикальный gap между карточками в списке — `space-sm` (12 px). Между логически разными секциями (например, «Аккаунт» и «Настройки контента») — `space-2xl` (32 px). Минимальный tap-target — **48 × 48 px**, при необходимости достигается через невидимый padding вокруг иконки.

### 3.3 Safe area

Bottom-navigation высотой 72 px + `MediaQuery.padding.bottom`. Контент ленты получает `padding-bottom = 72 + safe-area + space-lg`, чтобы последняя карточка не пряталась под навигацию. FAB позиционируется `bottom: 72 + safe-area + space-md` (то есть над навигацией с отступом 16 px).

---

## 4. Border Radius

### 4.1 Шкала

| Токен | Значение | Применение |
|---|---|---|
| `radius-xs` | 8 | Мини-badge, чекбоксы |
| `radius-sm` | 12 | Внутренние плашки-иконки внутри крупных карточек |
| `radius-md` | 16 | Input-поля, пункты списков настроек |
| `radius-lg` | 20 | **Карточки в ленте, primary-кнопки, иконки-плашки профиля** |
| `radius-xl` | 28 | Hero-карточки, верх bottom-sheet |
| `radius-2xl` | 36 | Модальные окна, floating containers |
| `radius-full` | 9999 | Чипы, аватары, pill-элементы |

### 4.2 Принцип вложенности

Внутренние элементы должны иметь радиус **меньше или равный** внешнему. Если карточка 20 px, обложка внутри неё — 12–16 px, иконка-плашка внутри строки списка — 12 px. Нарушение этого правила даёт «кривой» визуальный ритм. В Flutter это проверяется на уровне ревью кода: любой вложенный `ClipRRect` или `BorderRadius` не может превышать родительский.

---

## 5. Gradients

### 5.1 Aurora-фон приложения

Главный фоновый градиент, заменяющий текущий плоский серый. Состоит из двух radial-стопов поверх базового `neutral-50`. Не скроллится (фиксирован к viewport).

```
background:
  radial-gradient(1200px 600px at 20% -10%, #E8EEFF 0%, transparent 60%),
  radial-gradient(1000px 500px at 100% 100%, #FCE8F0 0%, transparent 55%),
  #F5F7FC;
```

**Flutter реализация:** через `DecoratedBox` с `BoxDecoration.gradient` невозможна (нужны два слоя), поэтому используется `Stack` с двумя `Container` на `RadialGradient` + базовый фон. Для web это один композитный слой — производительность ок.

### 5.2 Primary CTA градиент

```
gradient.ctaPrimary:
  linear-gradient(135deg, #3D5BFF 0%, #7364FF 100%)
  + inner-highlight: inset 0 1px 0 rgba(255,255,255, 0.25)
```

Применяется на: кнопке «Войти», «Зарегистрироваться», «Продолжить» в onboarding, FAB. Direction 135° (из верхнего левого в нижний правый) даёт ощущение «естественного света» и не конкурирует с aurora-фоном (который движется по другой оси).

### 5.3 Login hero-фон

```
gradient.loginHero:
  linear-gradient(160deg,
    #EEF1FF 0%,
    #F7ECFF 55%,
    #FFE8F1 100%
  );
```

Усиленная версия aurora для эмоциональных экранов (login, register). Применяется как основной фон вместо обычного aurora.

### 5.4 Glass градиент

```
gradient.glass:
  linear-gradient(180deg,
    rgba(255,255,255, 0.78) 0%,
    rgba(255,255,255, 0.55) 100%
  );
  backdrop-filter: blur(32px) saturate(140%);
  border-top: 1px solid rgba(255,255,255, 0.7);
```

Применяется строго на трёх поверхностях: bottom-navigation, sticky-header ленты при прокрутке, bottom-sheets/модалки. Не использовать для обычных карточек — нет смысла без движущегося контента под ними.

### 5.5 Правила

Насыщенность градиентов — всегда **ниже 15%** по HSL. Направления — 135° или 160° для линейных; radial-градиенты позиционируются в углах viewport. Градиенты никогда не применяются на текстовых поверхностях (только за текстом, не сам текст). Анимация градиентов — запрещена (тяжело для web); только статика.

---

## 6. Elevation & Glassmorphism

### 6.1 Уровни

Пять уровней глубины. Реализованы через `BoxShadow` (Flutter) или `box-shadow` (CSS).

| Токен | Описание | Применение |
|---|---|---|
| `elev-0` | Нет тени, только `border-soft` | Пункты сгруппированного списка, flat-контейнеры |
| `elev-1` | Лёгкая касающаяся тень | Обычные карточки, input-поля |
| `elev-2` | Средняя плавающая | Карточки ленты, выбранные свотчи палитры |
| `elev-3` | Заметная floating | FAB, активная карточка, кнопки CTA |
| `elev-4` | Сильная, для overlay | Bottom-sheets, модалки |

### 6.2 Значения

```dart
elev-0: []   // без теней

elev-1: [
  BoxShadow(color: rgba(15,24,40, 0.04), offset: (0, 1), blur: 2),
  BoxShadow(color: rgba(15,24,40, 0.03), offset: (0, 2), blur: 6),
]

elev-2: [
  BoxShadow(color: rgba(15,24,40, 0.06), offset: (0, 4), blur: 12),
  BoxShadow(color: rgba(15,24,40, 0.04), offset: (0, 12), blur: 32),
]

elev-3: [
  BoxShadow(color: rgba(15,24,40, 0.08), offset: (0, 8), blur: 24),
  BoxShadow(color: rgba(61,91,255, 0.08), offset: (0, 20), blur: 48),
  // вторая тень — лёгкий цветной glow в тон бренда
]

elev-4: [
  BoxShadow(color: rgba(15,24,40, 0.12), offset: (0, 16), blur: 40),
  BoxShadow(color: rgba(61,91,255, 0.10), offset: (0, 32), blur: 80),
]
```

### 6.3 Glass depth

Стеклянные поверхности не используют `elev-*` в чистом виде. Вместо этого:

```
glass-nav:
  background: gradient.glass
  backdrop-filter: blur(32px) saturate(140%)
  border-top: 1px solid rgba(255,255,255, 0.7)
  box-shadow: 0 -8px 32px rgba(15,24,40, 0.06)   // тень вверх, от нижней грани

glass-modal:
  background: gradient.glass (strong вариант)
  backdrop-filter: blur(40px) saturate(160%)
  border: 1px solid rgba(255,255,255, 0.8)
  box-shadow: elev-4
```

### 6.4 Производительность

Flutter Web использует CanvasKit, `BackdropFilter` стоит дорого. Ограничения:

- Не более **трёх** одновременно видимых `BackdropFilter` на экране.
- Если стеклянная панель занимает весь viewport (модалка на весь экран) — лучше заменить на `surface-glassStrong` без блюра, разница минимальна.
- Для Android-миграции использовать тот же код — Flutter mobile справляется с блюром быстрее, чем web.

---

## 7. Motion / Animation

### 7.1 Кривые

```dart
curves.standard    = Cubic(0.4, 0.0, 0.2, 1.0)   // большинство переходов
curves.expressive  = Cubic(0.22, 1.0, 0.36, 1.0) // появления hero-элементов
curves.snappy      = Cubic(0.32, 0.72, 0, 1.0)   // быстрые реакции на тап
curves.spring      = Cubic(0.5, 1.6, 0.4, 1.0)   // bottom-sheet с перелётом
```

В Flutter маппятся на `Curves.easeInOut`, `Curves.easeOutCubic`, `Curves.easeOutExpo` соответственно, либо задаются через `Cubic()` напрямую.

### 7.2 Длительности

| Токен | Значение | Применение |
|---|---|---|
| `duration-instant` | 80ms | Hover, focus ring |
| `duration-micro` | 120ms | Ripple, press-scale |
| `duration-fast` | 220ms | Переключения состояний, toggle чипов |
| `duration-base` | 320ms | Появление карточки, раскрытие sheet |
| `duration-emphasized` | 480ms | Page transitions, onboarding переходы |
| `duration-ambient` | 800ms | Фоновая пульсация CTA |

### 7.3 Паттерны

**Press feedback.** При тапе любой интерактивной поверхности — `scale: 1.0 → 0.97` + `opacity: 1.0 → 0.9`, длительность `duration-micro`, кривая `snappy`. Возврат при отпускании — `duration-fast`, кривая `standard`.

**List stagger.** Появление списка карточек в ленте — каждая карточка `fadeIn + translateY(8px → 0)`, задержка между карточками `40ms`, длительность каждой `duration-base`, кривая `expressive`. После шестой карточки stagger обнуляется (пользователь всё равно не смотрит дальше).

**Shared element.** Переход карточка → экран статьи реализуется через `Hero` с `createRectTween` для плавной интерполяции скруглений (20 px → 0).

**Bottom-sheet.** Появление — translateY от `100%` до `0` с кривой `spring` (лёгкий перелёт сверху), длительность `duration-base`. Закрытие — `standard`, `duration-fast`.

**Page transition (для go_router).** Горизонтальный slide + небольшой fade: новая страница приходит справа (`translateX: 40px → 0`), старая уходит с `opacity: 1 → 0.6`, длительность `duration-base`, кривая `standard`.

### 7.4 Запреты

Не анимируем `box-shadow` напрямую (дорого на web) — вместо этого кроссфейдим два одинаковых контейнера с разной тенью. Не анимируем градиенты. Не используем непрерывные loop-анимации нигде, кроме spinner и `ambient`-подсветки CTA (и та — только когда кнопка disabled → enabled, как намёк «готово»).

---

## 8. Breakpoints

Приложение mobile-first, но работает в браузере, поэтому нужна разумная адаптация к планшетам.

| Токен | Ширина | Поведение |
|---|---|---|
| `bp-mobile` | < 600px | Стандартный mobile-layout. Одна колонка, bottom-nav, боковые отступы 20 px. |
| `bp-tablet` | 600–1024px | Контент ограничен шириной 600 px, центрирован. Bottom-nav остаётся. Боковые отступы растут до 32 px. |
| `bp-desktop` | ≥ 1024px | Тот же ограниченный контейнер 600 px по центру. Опционально — добавить левую боковую навигацию вместо bottom-nav (решается позже). |

Ключевое: **не растягивать карточки на всю ширину десктопа**. Приложение должно выглядеть как «мобильное в рамке», а не как неудачный desktop-сайт. Это соответствует тому, как Telegram Web и Claude.ai на широких экранах показывают основной контент.

---

## 9. Component Semantic Tokens

Уровень над базовыми токенами — конкретные компоненты с их собственными семантическими ролями.

### 9.1 Card (стандартная карточка в ленте)

```
Card.background     = surface.base
Card.borderRadius   = radius-lg (20)
Card.padding        = space-md (16) all
Card.elevation      = elev-2
Card.gap            = space-sm (12)   // между карточками в списке
Card.cover.radius   = radius-md (16)
Card.cover.height   = 180
Card.cover.overlay  = linear-gradient(180deg, transparent 60%, rgba(0,0,0,0.08) 100%)
```

### 9.2 Button

```
Button.primary:
  background        = gradient.ctaPrimary
  height            = 56
  borderRadius      = radius-lg (20)
  padding.h         = space-lg (20)
  textStyle         = button + text.onPrimary
  elevation         = elev-2
  press.scale       = 0.97
  disabled.opacity  = 0.4

Button.secondary:
  background        = surface.base
  border            = 1.5px border.default
  height            = 56
  borderRadius      = radius-lg (20)
  padding.h         = space-lg (20)
  textStyle         = button + text.primary
  elevation         = elev-0
  hover.background  = surface.backgroundSubtle

Button.ghost:
  background        = transparent
  height            = 44
  borderRadius      = radius-full
  padding.h         = space-md (16)
  textStyle         = button + text.primary
  hover.background  = interactive.primarySubtle

Button.iconGhost (круглая icon-кнопка):
  size              = 44
  borderRadius      = radius-full
  background        = transparent
  hover.background  = interactive.primarySubtle
  tapTarget         = 48 (через невидимый padding)
```

### 9.3 Input

```
Input:
  height            = 56
  borderRadius      = radius-md (16)
  background        = surface.base
  border            = 1.5px border.default
  padding.h         = space-md (16)
  textStyle         = body-l
  placeholder.color = text.tertiary

Input.focused:
  border            = 1.5px border.focus
  shadow            = 0 0 0 4px border.focusHalo
  transition        = duration-fast, curves.standard

Input.error:
  border            = 1.5px border.error
  shadow            = 0 0 0 4px border.errorHalo

Input.disabled:
  background        = surface.backgroundSubtle
  textStyle.color   = text.disabled
```

### 9.4 Bottom Navigation

```
BottomNav:
  background        = gradient.glass (strong)
  backdropFilter    = blur(32px) saturate(140%)
  height            = 72 + safeArea.bottom
  borderTop         = 1px rgba(255,255,255, 0.7)
  shadow            = 0 -8px 32px rgba(15,24,40, 0.06)

BottomNav.item:
  size              = 48 (tap-target)
  textStyle         = caption
  inactive.color    = text.tertiary
  active.pill:
    background      = interactive.primarySubtle
    borderRadius    = radius-full
    padding         = 8 12
    icon.color      = interactive.primary
    text.color      = interactive.primary
```

### 9.5 Segmented Control (для табов Коллекций, Пространства)

```
SegmentedControl:
  track.background  = surface.sunken (neutral-100)
  track.borderRadius = radius-full
  track.padding     = 4 all
  height            = 44
  
SegmentedControl.thumb (активная вкладка):
  background        = surface.base
  borderRadius      = radius-full
  elevation         = elev-1
  textStyle         = button + interactive.primary
  transition        = duration-base, curves.expressive

SegmentedControl.inactive:
  background        = transparent
  textStyle         = button + text.secondary
```

### 9.6 Chip (категории интересов, фильтры источников)

```
Chip.inactive:
  background        = surface.base
  border            = 1.5px border.default
  borderRadius      = radius-full
  height            = 44
  padding.h         = space-md (16)
  textStyle         = button + text.primary
  icon.color        = text.secondary

Chip.active:
  background        = gradient.ctaPrimary
  border            = none
  borderRadius      = radius-full
  height            = 44
  padding.h         = space-md (16)
  textStyle         = button + text.onPrimary
  icon.color        = text.onPrimary
  elevation         = elev-1
  transition        = duration-fast, curves.snappy
  press.scale       = 0.94
```

### 9.7 Icon Tile (цветная плашка под иконкой в меню)

```
IconTile:
  size              = 40
  borderRadius      = radius-md (12)
  background        = accent-[variant].bg
  icon.color        = accent-[variant].fg (более тёмный оттенок, например neutral-700)
  icon.size         = 20
```

Используется в: Профиле (Закладки/Интересы/Смена пароля), Настройках (Эл. почта / Пароль / Подписка / Каталог и т. д.).

### 9.8 FAB

```
FAB:
  size              = 56
  borderRadius      = radius-full
  background        = gradient.ctaPrimary
  elevation         = elev-3
  icon.size         = 24
  icon.color        = text.onPrimary
  position.bottom   = 72 + safeArea + space-md
  position.right    = space-lg (20)
  press.scale       = 0.94
```

### 9.9 OTP Code Input (4 ячейки кода подтверждения)

```
OTPCell:
  width             = 56
  height            = 64
  borderRadius      = radius-md (16)
  background        = surface.base
  border            = 1.5px border.default
  textStyle         = heading-l + text.primary
  textAlign         = center

OTPCell.active:
  border            = 1.5px border.focus
  shadow            = 0 0 0 4px border.focusHalo
  cursor.blink      = 1s interval

OTPCell.filled:
  border            = 1.5px border.focus (muted, opacity 0.4)
```

Расстояние между ячейками — `space-sm` (12 px). Всего 4 ячейки → общая ширина `4*56 + 3*12 = 260 px`, хорошо вписывается в мобильный viewport.

### 9.10 Bottom Sheet

```
BottomSheet:
  background        = gradient.glass (strong вариант)
  backdropFilter    = blur(40px) saturate(160%)
  borderRadius      = radius-xl (28) top-only
  padding           = space-xl (24)
  elevation         = elev-4
  handle:
    width           = 40
    height          = 4
    borderRadius    = radius-full
    background      = border.default
    marginTop       = 12
  enter.animation:
    translateY      = 100% → 0
    duration        = duration-base
    curve           = curves.spring
```

---

## 10. Flutter Implementation Guide

### 10.1 Структура файлов

```
lib/
  theme/
    tokens/
      app_colors.dart          // semantic color tokens
      app_typography.dart      // text styles
      app_spacing.dart          // spacing constants
      app_radii.dart            // border radii
      app_elevation.dart        // shadows
      app_gradients.dart        // gradient definitions
      app_durations.dart        // animation durations
      app_curves.dart           // animation curves
    app_tokens.dart             // агрегатор: re-export всего
    app_theme.dart              // ThemeData собранная из tokens
    app_theme_extensions.dart   // ThemeExtension для нестандартных полей
  widgets/                      // кастомные компоненты, использующие tokens
```

### 10.2 Паттерн: Tokens как `ThemeExtension`

Material 3 `ThemeData` не покрывает градиенты, glass-слои, кастомные elevation. Решение — использовать `ThemeExtension<T>`:

```dart
// Псевдокод — структура класса
class AppTokens extends ThemeExtension<AppTokens> {
  final AppColors colors;
  final AppGradients gradients;
  final AppElevation elevation;
  // ...

  // copyWith и lerp обязательны
}

// В MaterialApp:
theme: ThemeData.light().copyWith(
  extensions: [AppTokens.light],
  // + стандартные M3 роли из tokens:
  colorScheme: ColorScheme.fromSeed(seedColor: AppColors.brand500, ...),
  textTheme: AppTypography.toTextTheme(),
)

// В виджетах:
final tokens = Theme.of(context).extension<AppTokens>()!;
Container(decoration: BoxDecoration(gradient: tokens.gradients.ctaPrimary))
```

Так Material-виджеты (Button, Card по умолчанию) берут данные из `ColorScheme`/`TextTheme`, а кастомные компоненты — из `AppTokens`. Ничто не дублируется.

### 10.3 Маппинг на Material 3 ColorScheme

```
primary              = brand-500
onPrimary            = neutral-0
primaryContainer     = brand-50
onPrimaryContainer   = brand-700
secondary            = violet-500
onSecondary          = neutral-0
surface              = neutral-0
onSurface            = neutral-900
surfaceContainerLow  = neutral-50
surfaceContainer     = neutral-100
surfaceContainerHigh = neutral-150
outline              = neutral-200
outlineVariant       = border.soft (через opacity неудобно; пропустить или подобрать)
error                = error-500
onError              = neutral-0
errorContainer       = error-50
onErrorContainer     = error-500
```

### 10.4 Cupertino совместимость

Если позже понадобится Cupertino-виджет (например, `CupertinoDatePicker`), токены подставляются через `CupertinoThemeData(primaryColor: tokens.colors.interactive.primary, ...)`. Поскольку tokens — чистые значения, они одинаково работают в обоих фреймворках. Смешивать на одном экране не рекомендуется: либо Material, либо Cupertino.

### 10.5 Responsive layout хелперы

```dart
// Псевдокод
class Breakpoints {
  static const mobile = 600;
  static const tablet = 1024;
}

extension ContextBreakpoints on BuildContext {
  bool get isMobile => MediaQuery.sizeOf(this).width < Breakpoints.mobile;
  bool get isTablet => /* ... */;
}

// Использование:
Widget build(BuildContext context) {
  final maxWidth = context.isMobile ? double.infinity : 600.0;
  return Center(child: ConstrainedBox(
    constraints: BoxConstraints(maxWidth: maxWidth),
    child: content,
  ));
}
```

Обёртку над `Scaffold.body` желательно сделать единожды (например, `AppScreen` widget) и применять на всех экранах, чтобы правило «600 px на десктопе» не забывалось.

### 10.6 Производительность — чеклист

- `BackdropFilter` — только в bottom-nav, sticky-header и bottom-sheet. Нигде больше.
- Aurora-фон реализовать как `RepaintBoundary` с кэшированным рисунком; перерисовывать только при resize окна.
- Shadow на карточках списка — через `const BoxShadow`; Flutter кэширует shadow-layer.
- Press-feedback через `AnimatedScale`, не через `AnimatedContainer` (первое дешевле).
- Для списка ленты использовать `ListView.builder` с `itemExtent` если возможно (фикс. высота карточки), иначе Flutter пересчитывает layout при каждом скролле.

---

## 11. Changelog

- `0.1.0` (2026-04-17) — Первая версия. Базовые tokens после аудита текущего UI.

---

## 12. Open questions (обсудить перед 0.2.0)

Перед следующей итерацией есть несколько развилок, которые стоит закрыть по мере работы:

1. **Dark theme mapping.** Когда будем делать тёмную тему — пересмотреть aurora-фон (насыщенность стопов должна быть ниже, иначе выглядит грязно на тёмном) и стеклянные поверхности (обычно переходят от белого полупрозрачного к чёрному полупрозрачному + чуть больше блюра).
2. **Шрифты.** Проверить рендеринг Manrope на Windows Chrome (там bitmap hinting иногда даёт артефакты на 22 px). Если заметно — заменить на Geist или General Sans.
3. **Иконки.** Текущий набор выглядит как Material Symbols Rounded — это хороший выбор, стоит закрепить его официально и не смешивать с Outlined.
4. **Shared element transitions.** Протестировать на Flutter Web переход карточка → статья на реальном устройстве; если лагает — откатиться на обычный slide + fade.
5. **Мотив звука.** Нео-футуризм часто сопровождается микро-звуками (click, swipe); обсудить, нужны ли они, и если да — где брать (freesound.org, лицензированные пакеты).
