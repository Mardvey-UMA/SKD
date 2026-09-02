// Радар — screens

function FeedScreen({ reacts, onOpen, onToggle, onMore, items, isLoading, adStyle = 'subtle', adFrequency = 5, hiddenAds = [], onHideAd }) {
  const list = items || FEED;
  // Build a rendering sequence: content card, then every adFrequency items inject an ad
  // (never first, never last, never two ads adjacent). Ads cycle through ADS pool.
  const sequence = React.useMemo(() => {
    if (!list.length || adStyle === 'off' || adFrequency < 2) {
      return list.map(it => ({ kind: 'item', data: it }));
    }
    const seq = [];
    const available = ADS.filter(a => !hiddenAds.includes(a.id));
    if (!available.length) return list.map(it => ({ kind: 'item', data: it }));
    let adIdx = 0;
    list.forEach((it, i) => {
      seq.push({ kind: 'item', data: it });
      const isLast = i === list.length - 1;
      const canInject = (i + 1) % adFrequency === 0 && i > 0 && !isLast;
      if (canInject) {
        seq.push({ kind: 'ad', data: available[adIdx % available.length], key: `ad-${i}-${adIdx}` });
        adIdx++;
      }
    });
    return seq;
  }, [list, adStyle, adFrequency, hiddenAds]);
  return (
    <div style={{ background: NF.bg, minHeight: '100%' }}>
      <FeedHeader/>
      <div style={{ padding: '4px 14px 120px', display: 'flex', flexDirection: 'column', gap: 14 }}>
        {isLoading ? <FeedSkeleton/> : list.length === 0 ? (
          <div style={{ padding: '20px 0' }}>
            <EmptyState
              icon="radar" accent="accent"
              title="Все источники скрыты"
              desc="Вы скрыли все доступные источники. Откройте Профиль → Источники и верните то, что снова хотите читать."
            />
          </div>
        ) : (
          <>
            {sequence.map((s, idx) => {
              if (s.kind === 'ad') {
                return <AdCard key={s.key} ad={s.data} style={adStyle}
                  onHide={() => onHideAd?.(s.data.id)}
                  onClick={() => {}}/>;
              }
              const item = s.data;
              const r = reacts[item.id];
              const props = {
                item, react: r,
                onOpen: () => onOpen(item),
                onLike: () => onToggle(item.id, 'like'),
                onDislike: () => onToggle(item.id, 'dislike'),
                onBookmark: () => onToggle(item.id, 'bookmark'),
                onMore: () => onMore(item),
              };
              return item.kind === 'short'
                ? <ShortCard key={item.id} {...props}/>
                : <LongCard key={item.id} {...props}/>;
            })}
            <div style={{ textAlign: 'center', padding: '20px 0 10px' }}>
              <Mono>◦ КОНЕЦ ЛЕНТЫ · ПОТЯНИТЕ ДЛЯ ОБНОВЛЕНИЯ ◦</Mono>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

// Related rail — 2.5 visible, "Смотреть все"
function RelatedRail({ current, onOpenAll, onOpenItem }) {
  const items = FEED.filter(f => f.id !== current.id);
  const railRef = useRef(null);
  return (
    <div style={{ marginTop: 22 }}>
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 4px 10px',
      }}>
        <div style={{
          fontFamily: FONT_SANS, fontWeight: 700, fontSize: 17,
          letterSpacing: -0.4, color: NF.ink,
        }}>Похожие материалы</div>
        <div onClick={onOpenAll} style={{
          display: 'inline-flex', alignItems: 'center', gap: 4, cursor: 'pointer',
        }}>
          <span style={{
            fontFamily: FONT_SANS, fontSize: 13, fontWeight: 600,
            color: NF.accent,
          }}>Смотреть все ({items.length})</span>
          <Icon name="arrow-right" size={13} color={NF.accent}/>
        </div>
      </div>
      <div ref={railRef} style={{
        display: 'flex', gap: 10, overflowX: 'auto', paddingBottom: 6,
        scrollSnapType: 'x proximity',
      }}>
        {items.map(r => (
          <div key={r.id} onClick={() => onOpenItem(r)} style={{
            flex: '0 0 66%', background: NF.surface, borderRadius: NF.radius,
            border: `1px solid ${NF.hairline}`, overflow: 'hidden',
            cursor: 'pointer', scrollSnapAlign: 'start',
          }}>
            <div style={{ padding: 6 }}>
              {r.images === 'none'
                ? <div style={{
                    height: 110, borderRadius: 12, background: NF.surface2,
                    display: 'flex', alignItems: 'center', padding: '12px 14px',
                    fontFamily: FONT_SANS, fontWeight: 600, fontSize: 14,
                    lineHeight: 1.2, color: NF.ink, letterSpacing: -0.3,
                    overflow: 'hidden',
                  }}>
                    <span style={{
                      display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                    }}>{r.title}</span>
                  </div>
                : r.images === 'multi'
                  ? <MultiImage item={r} height={110}/>
                  : <SingleImage item={r} height={110}/>}
            </div>
            <div style={{ padding: '4px 12px 12px' }}>
              <Mono style={{ color: NF.ink }}>{r.source}</Mono>
              <div style={{
                marginTop: 4, fontFamily: FONT_SANS, fontWeight: 600, fontSize: 13.5,
                lineHeight: 1.25, color: NF.ink,
                display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}>{r.title}</div>
              <Mono style={{ marginTop: 4, display: 'block' }}>{r.time} · {r.read}</Mono>
            </div>
          </div>
        ))}
        <div style={{ flex: '0 0 16px' }}/>
      </div>
    </div>
  );
}

function DetailScreen({ item, react, onBack, onToggle, onOpenRelated, onOpenRelatedAll }) {
  const originalUrl = (item.source === 'Telegram' ? 't.me/' + (item.sourceHandle || '').replace(/^@/, '')
    : item.source === 'Хабр' ? 'habr.com/ru/article/' + item.seed + '00'
    : item.source === 'VC.RU' ? 'vc.ru/u/' + item.seed + '/original'
    : 'source.example/article/' + item.seed);
  return (
    <div style={{ background: NF.bg, minHeight: '100%' }}>
      <div style={{
        position: 'sticky', top: 0, zIndex: 20, background: NF.bg,
        padding: '62px 14px 8px',
        display: 'flex', alignItems: 'center', gap: 10,
        borderBottom: `1px solid ${NF.hairline}`,
      }}>
        <div onClick={onBack} style={{
          width: 38, height: 38, borderRadius: 999,
          border: `1px solid ${NF.hairline}`, background: NF.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          cursor: 'pointer',
        }}>
          <Icon name="back" size={18}/>
        </div>
        <Mono>СТАТЬЯ · {item.read}</Mono>
        <div style={{ flex: 1 }}/>
      </div>

      <div style={{ padding: '16px 18px 140px' }}>
        <SourceLine source={item.source} handle={item.sourceHandle} time={item.time} read={item.read}/>
        <div style={{
          marginTop: 12, fontSize: 30, lineHeight: 1.08, letterSpacing: -1,
          fontWeight: 700, color: NF.ink, fontFamily: FONT_SANS,
        }}>{item.title}</div>
        <div style={{
          marginTop: 10, fontSize: 14, color: NF.mute, lineHeight: 1.5,
          fontFamily: FONT_SANS,
        }}>Автор: <span style={{ color: NF.ink2, fontWeight: 600 }}>Нур Абеллан</span> · Редакция, Москва + Лиссабон</div>

        {item.images !== 'none' && (
          <div style={{ marginTop: 18 }}>
            {item.images === 'multi'
              ? <MultiImage item={item} height={220}/>
              : <Stripe height={220} tone={item.tone} seed={item.seed} label={item.source} radius={NF.radiusLg}/>}
          </div>
        )}

        {/* Reactions + Original */}
        <div style={{
          marginTop: 16, padding: '10px 14px',
          background: NF.surface, borderRadius: 999,
          border: `1px solid ${NF.hairline}`,
          display: 'flex', alignItems: 'center', gap: 10,
        }}>
          <ReactionBar
            state={react}
            onLike={() => onToggle(item.id, 'like')}
            onDislike={() => onToggle(item.id, 'dislike')}
            onBookmark={() => onToggle(item.id, 'bookmark')}
          />
          <div style={{ flex: 1 }}/>
          <a href={`https://${originalUrl}`} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()}
             style={{
              display: 'inline-flex', alignItems: 'center', gap: 6,
              padding: '8px 12px', borderRadius: 999,
              background: 'transparent', color: NF.ink, textDecoration: 'none',
              border: `1px solid ${NF.hairline}`,
              fontFamily: FONT_SANS, fontSize: 12.5, fontWeight: 600,
            }}>
            <Icon name="external" size={13} color={NF.ink}/>
            Ссылка на оригинал
          </a>
        </div>

        <div style={{
          marginTop: 22, fontSize: 17, lineHeight: 1.65, color: NF.ink2,
          fontFamily: FONT_SANS, whiteSpace: 'pre-line',
        }}>
          <span style={{
            float: 'left', fontSize: 64, lineHeight: 0.9, paddingRight: 10,
            fontWeight: 700, color: NF.ink, letterSpacing: -2,
          }}>{item.snippet.trim()[0]}</span>
          {item.snippet.slice(1)}
          {'\n\n'}
          {FULL_BODY}
        </div>

        {/* Original again near bottom */}
        <a href={`https://${originalUrl}`} target="_blank" rel="noreferrer"
          style={{
            marginTop: 20, padding: '14px 16px', borderRadius: 14,
            background: NF.ink, color: '#fff', textDecoration: 'none',
            display: 'flex', alignItems: 'center', gap: 10,
            fontFamily: FONT_SANS, fontSize: 14, fontWeight: 600,
          }}>
          <Icon name="external" size={16} color="#fff"/>
          <div style={{ flex: 1 }}>Открыть оригинал на {item.source}</div>
          <div style={{
            fontFamily: FONT_MONO, fontSize: 10, letterSpacing: 0.8,
            color: NF.lime,
          }}>{originalUrl.slice(0, 26)}…</div>
        </a>

        <RelatedRail current={item} onOpenAll={onOpenRelatedAll} onOpenItem={onOpenRelated}/>
      </div>
    </div>
  );
}

// Full list of related materials
function RelatedAllScreen({ current, onBack, onOpenItem, reacts, onToggle }) {
  const items = FEED.filter(f => f.id !== current.id);
  return (
    <div style={{ background: NF.bg, minHeight: '100%', paddingBottom: 140 }}>
      <div style={{ padding: '62px 14px 10px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <div onClick={onBack} style={{
          width: 38, height: 38, borderRadius: 999,
          border: `1px solid ${NF.hairline}`, background: NF.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
        }}>
          <Icon name="back" size={18}/>
        </div>
        <Mono>ПОХОЖИЕ · {items.length}</Mono>
      </div>
      <div style={{ padding: '4px 18px 10px' }}>
        <div style={{
          fontSize: 30, lineHeight: 1.05, letterSpacing: -1,
          fontWeight: 700, color: NF.ink, fontFamily: FONT_SANS,
        }}>Похожие<br/>материалы.</div>
      </div>
      <div style={{ padding: '10px 14px', display: 'flex', flexDirection: 'column', gap: 12 }}>
        {items.map(item => {
          const r = reacts[item.id];
          return (
            <ShortCard key={item.id}
              item={item} react={r}
              onOpen={() => onOpenItem(item)}
              onLike={() => onToggle(item.id, 'like')}
              onDislike={() => onToggle(item.id, 'dislike')}
              onBookmark={() => onToggle(item.id, 'bookmark')}
            />
          );
        })}
      </div>
    </div>
  );
}

function CollectionsScreen({ reacts, userCollections, onCreate, onEdit, onOpenSystem }) {
  const saved = FEED.filter(f => reacts[f.id]?.bookmark).length;
  const liked = FEED.filter(f => reacts[f.id]?.like).length;
  const disliked = FEED.filter(f => reacts[f.id]?.dislike).length;
  const systemCollections = [
    { id: 'sys-saved', title: 'Сохранённые', tone: 'lime', desc: 'Всё что в закладках', count: saved, system: true },
    { id: 'sys-liked', title: 'Понравились', tone: 'accent', desc: 'Одобренные статьи', count: liked, system: true },
    { id: 'sys-disliked', title: 'Скрытые', tone: 'ink', desc: 'Не попадают в ленту', count: disliked, system: true },
  ];
  const allCollections = [...systemCollections, ...userCollections];
  return (
    <div style={{ background: NF.bg, minHeight: '100%', paddingBottom: 130 }}>
      <div style={{ padding: '62px 18px 14px' }}>
        <Mono>{allCollections.length} ПРОСТРАНСТВ</Mono>
        <div style={{
          marginTop: 6, fontSize: 40, lineHeight: 0.95, letterSpacing: -1.4,
          fontWeight: 700, color: NF.ink, fontFamily: FONT_SANS,
        }}>Ваши<br/>подборки.</div>
      </div>

      <div style={{ padding: '0 14px 14px' }}>
        <div onClick={onCreate} style={{
          borderRadius: NF.radiusLg, border: `1.5px dashed ${NF.ink}`,
          padding: '18px', display: 'flex', alignItems: 'center', gap: 14,
          background: NF.surface, cursor: 'pointer',
        }}>
          <div style={{
            width: 44, height: 44, borderRadius: 12, background: NF.lime,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Icon name="plus" size={22} color={NF.limeInk}/>
          </div>
          <div style={{ flex: 1 }}>
            <div style={{
              fontFamily: FONT_SANS, fontWeight: 700, fontSize: 17,
              letterSpacing: -0.4, color: NF.ink,
            }}>Создать пространство</div>
            <Mono style={{ marginTop: 3, display: 'block' }}>НАЗВАНИЕ · ИСТОЧНИКИ · ЦВЕТ</Mono>
          </div>
          <Icon name="chevron" size={16} color={NF.mute}/>
        </div>
      </div>

      <div style={{ padding: '0 14px', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        {allCollections.map((c, i) => (
          <div key={c.id} onClick={() => c.system ? (onOpenSystem && onOpenSystem(c.id)) : onEdit(c)} style={{
            background: NF.surface, borderRadius: NF.radiusLg,
            border: `1px solid ${NF.hairline}`, overflow: 'hidden',
            gridColumn: i === 0 ? 'span 2' : 'span 1',
            cursor: 'pointer',
          }}>
            <div style={{ padding: 8 }}>
              <Stripe height={i === 0 ? 120 : 90} tone={c.tone} seed={100 + i} label={c.title.toUpperCase()} radius={NF.radius}/>
            </div>
            <div style={{ padding: '4px 14px 14px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 6 }}>
                <div style={{
                  fontFamily: FONT_SANS, fontWeight: 700,
                  fontSize: i === 0 ? 19 : 15, letterSpacing: -0.4, color: NF.ink,
                  display: 'flex', alignItems: 'center', gap: 6,
                }}>
                  {c.system && <span style={{
                    width: 6, height: 6, borderRadius: 999,
                    background: c.tone === 'accent' ? NF.accent : c.tone === 'lime' ? NF.lime : NF.ink,
                  }}/>}
                  {c.title}
                </div>
                <div style={{
                  background: NF.ink, color: '#fff', borderRadius: 6, padding: '2px 7px',
                  fontFamily: FONT_MONO, fontSize: 10, letterSpacing: 0.5,
                }}>{c.count}</div>
              </div>
              <Mono style={{ marginTop: 4, display: 'block' }}>{c.desc}</Mono>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// Space editor — with source search and Telegram/Habr/VC.RU filter chips
const COLLECTION_COLORS = [
  { id: 'accent', swatch: '#3B2BFF' },
  { id: 'lime', swatch: '#CCFF33' },
  { id: 'ink', swatch: '#0E0F0D' },
  { id: 'warn', swatch: '#FF5A1F' },
  { id: 'violet', swatch: '#7A4BFF' },
  { id: 'teal', swatch: '#1EC6B6' },
  { id: 'rose', swatch: '#FF7A8A' },
];

function CollectionEditorScreen({ onBack, onSave, onDelete, existing, customSources, prefilledSource }) {
  const isNew = !existing;
  const [title, setTitle] = useState(existing?.title ?? '');
  const [desc, setDesc] = useState(existing?.desc ?? '');
  const [tone, setTone] = useState(existing?.tone ?? 'accent');
  const [sources, setSources] = useState(
    existing?.sources ?? (prefilledSource ? [prefilledSource] : [])
  );
  const [q, setQ] = useState('');
  const [platform, setPlatform] = useState('Все'); // Все / Telegram / Habr / VC.RU

  const pool = useMemo(() => [...SYSTEM_SOURCES, ...customSources], [customSources]);
  const filtered = pool.filter(s =>
    (platform === 'Все' || s.platform === platform) &&
    (q.trim() === '' ||
      s.name.toLowerCase().includes(q.toLowerCase()) ||
      s.handle.toLowerCase().includes(q.toLowerCase()))
  );
  const toggleSrc = (id) => setSources(x => x.includes(id) ? x.filter(y => y !== id) : [...x, id]);

  return (
    <div style={{ background: NF.bg, minHeight: '100%', paddingBottom: 140 }}>
      <div style={{
        padding: '56px 14px 10px', display: 'flex', alignItems: 'center', gap: 10,
        background: NF.bg, position: 'sticky', top: 0, zIndex: 30,
      }}>
        <div onClick={onBack} style={{
          width: 38, height: 38, borderRadius: 999,
          border: `1px solid ${NF.hairline}`, background: NF.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
        }}>
          <Icon name="back" size={18}/>
        </div>
        <div style={{
          flex: 1, fontFamily: FONT_SANS, fontWeight: 700, fontSize: 17,
          letterSpacing: -0.3, color: NF.ink,
        }}>{isNew ? 'Новое пространство' : 'Редактировать'}</div>
        <div onClick={() => title.trim() && onSave({ title: title.trim(), desc, tone, sources })} style={{
          padding: '8px 14px', borderRadius: 999,
          background: title.trim() ? NF.ink : 'transparent',
          color: title.trim() ? '#fff' : NF.mute,
          border: `1px solid ${title.trim() ? NF.ink : NF.hairline}`,
          fontFamily: FONT_SANS, fontSize: 13, fontWeight: 600,
          cursor: title.trim() ? 'pointer' : 'not-allowed',
        }}>Сохранить</div>
      </div>

      <div style={{ padding: '14px 14px 0', display: 'flex', flexDirection: 'column', gap: 16 }}>
        {/* Preview */}
        <div style={{ padding: 8, background: NF.surface, borderRadius: NF.radiusLg, border: `1px solid ${NF.hairline}` }}>
          <Stripe height={100} tone={tone} seed={999} label={(title || 'НОВОЕ ПРОСТРАНСТВО').toUpperCase()} radius={NF.radius}/>
        </div>

        <div>
          <Mono style={{ display: 'block', padding: '0 4px 6px' }}>НАЗВАНИЕ</Mono>
          <div style={{ position: 'relative' }}>
            <input value={title} onChange={e => setTitle(e.target.value.slice(0, 50))} placeholder="Например, Выходные чтения"
              style={{
                width: '100%', padding: '14px 62px 14px 16px', borderRadius: 14,
                border: `1px solid ${NF.hairline}`, background: NF.surface,
                fontFamily: FONT_SANS, fontSize: 16, color: NF.ink,
                outline: 'none', boxSizing: 'border-box',
              }}/>
            <div style={{
              position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)',
              fontFamily: FONT_MONO, fontSize: 10, color: NF.mute,
            }}>{title.length}/50</div>
          </div>
        </div>

        <div>
          <Mono style={{ display: 'block', padding: '0 4px 6px' }}>ОПИСАНИЕ</Mono>
          <input value={desc} onChange={e => setDesc(e.target.value)} placeholder="Короткая заметка..."
            style={{
              width: '100%', padding: '14px 16px', borderRadius: 14,
              border: `1px solid ${NF.hairline}`, background: NF.surface,
              fontFamily: FONT_SANS, fontSize: 15, color: NF.ink,
              outline: 'none', boxSizing: 'border-box',
            }}/>
        </div>

        <div>
          <Mono style={{ display: 'block', padding: '0 4px 6px' }}>ЦВЕТ</Mono>
          <div style={{
            padding: 12, borderRadius: 14, background: NF.surface,
            border: `1px solid ${NF.hairline}`, display: 'flex', gap: 10, flexWrap: 'wrap',
          }}>
            {COLLECTION_COLORS.map(c => (
              <div key={c.id} onClick={() => setTone(c.id)} style={{
                width: 38, height: 38, borderRadius: 10, background: c.swatch,
                border: tone === c.id ? `2.5px solid ${NF.ink}` : `1px solid ${NF.hairline}`,
                boxShadow: tone === c.id ? `0 0 0 3px ${NF.bg}, 0 0 0 4.5px ${NF.ink}` : 'none',
                cursor: 'pointer',
              }}/>
            ))}
          </div>
        </div>

        {/* Sources section */}
        <div>
          <Mono style={{ display: 'block', padding: '0 4px 6px' }}>ИСТОЧНИКИ · ВЫБРАНО {sources.length}</Mono>

          {/* Search input */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: 10,
            padding: '12px 14px', borderRadius: 999, background: NF.surface,
            border: `1px solid ${NF.hairline}`,
          }}>
            <Icon name="search" size={16} color={NF.mute}/>
            <input
              value={q} onChange={e => setQ(e.target.value)}
              placeholder="Поиск источника..."
              style={{
                flex: 1, border: 'none', outline: 'none', background: 'transparent',
                fontFamily: FONT_SANS, fontSize: 14, color: NF.ink,
              }}/>
            {q && <Icon name="close" size={14} color={NF.mute} onClick={() => setQ('')}/>}
          </div>

          {/* Platform chips — Все / Telegram / Habr / VC.RU */}
          <div style={{ marginTop: 10, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {['Все', 'Telegram', 'Habr', 'VC.RU'].map(p => {
              const on = platform === p;
              return (
                <div key={p} onClick={() => setPlatform(p)} style={{
                  padding: '7px 14px', borderRadius: 999,
                  background: on ? NF.accent : NF.surface,
                  color: on ? '#fff' : NF.ink2,
                  border: `1px solid ${on ? NF.accent : NF.hairline}`,
                  fontFamily: FONT_SANS, fontSize: 13, fontWeight: 600,
                  cursor: 'pointer',
                }}>{p}</div>
              );
            })}
          </div>

          {/* Source list */}
          <div style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 6 }}>
            {filtered.length === 0 && (
              <div style={{
                padding: '18px', textAlign: 'center', borderRadius: 14,
                background: NF.surface, border: `1px dashed ${NF.hairline}`,
              }}>
                <Mono>НИЧЕГО НЕ НАЙДЕНО</Mono>
                <div style={{
                  marginTop: 6, fontFamily: FONT_SANS, fontSize: 13, color: NF.mute,
                }}>Попробуйте другой запрос или добавьте источник в каталоге.</div>
              </div>
            )}
            {filtered.map(s => {
              const on = sources.includes(s.id);
              return (
                <div key={s.id} onClick={() => toggleSrc(s.id)} style={{
                  padding: '10px 12px', borderRadius: 12,
                  background: on ? NF.ink : NF.surface,
                  border: `1px solid ${on ? NF.ink : NF.hairline}`,
                  display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer',
                }}>
                  <PlatformGlyph platform={s.platform} inverted={on}/>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{
                      fontFamily: FONT_SANS, fontWeight: 600, fontSize: 14,
                      color: on ? '#fff' : NF.ink,
                    }}>{s.name}</div>
                    <div style={{
                      fontFamily: FONT_MONO, fontSize: 10, letterSpacing: 0.5,
                      color: on ? NF.lime : NF.mute,
                    }}>{s.handle}</div>
                  </div>
                  <div style={{
                    width: 22, height: 22, borderRadius: 999,
                    background: on ? NF.lime : 'transparent',
                    border: `1.5px solid ${on ? NF.lime : NF.hairline}`,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    {on && <Icon name="check" size={12} color={NF.limeInk}/>}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {!isNew && (
          <div onClick={onDelete} style={{
            marginTop: 6, padding: '14px 16px', borderRadius: 14,
            border: `1px solid ${NF.hairline}`, background: NF.surface,
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
            cursor: 'pointer',
          }}>
            <Icon name="trash" size={16} color={NF.warn}/>
            <div style={{
              fontFamily: FONT_SANS, fontSize: 14, fontWeight: 600, color: NF.warn,
            }}>Удалить пространство</div>
          </div>
        )}
      </div>
    </div>
  );
}

function PlatformGlyph({ platform, inverted = false }) {
  const fg = inverted ? NF.lime : NF.ink;
  const bg = inverted ? 'transparent' : NF.chipBg;
  const label = platform === 'Telegram' ? 'TG' : platform === 'Habr' ? 'H' : platform === 'VC.RU' ? 'VC' : '?';
  return (
    <div style={{
      width: 34, height: 34, borderRadius: 9, flexShrink: 0,
      background: bg, border: `1px solid ${inverted ? 'rgba(255,255,255,0.15)' : NF.hairline}`,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontFamily: FONT_MONO, fontSize: 11, fontWeight: 700, color: fg, letterSpacing: 0.5,
    }}>{label}</div>
  );
}

// ----------------- Каталог источников (Sources) -----------------

function SourcesScreen({ onBack, customSources, setCustomSources, onAddNew, onOpenMy, hiddenSources = [], setHiddenSources, isPremium = false }) {
  const [q, setQ] = useState('');
  const [platform, setPlatform] = useState('Все');
  const [mode, setMode] = useState('catalog'); // 'catalog' | 'hidden'
  const pool = [...SYSTEM_SOURCES, ...customSources];
  // Pool for current mode
  const activePool = mode === 'hidden'
    ? pool.filter(s => hiddenSources.includes(s.name))
    : pool;
  const filtered = activePool.filter(s =>
    (platform === 'Все' || s.platform === platform) &&
    (q.trim() === '' ||
      s.name.toLowerCase().includes(q.toLowerCase()) ||
      s.handle.toLowerCase().includes(q.toLowerCase())));
  const unhide = (name) => {
    if (setHiddenSources) setHiddenSources(hs => hs.filter(n => n !== name));
  };
  return (
    <div style={{ background: NF.bg, minHeight: '100%', paddingBottom: 130 }}>
      <div style={{ padding: '62px 14px 10px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <div onClick={onBack} style={{
          width: 38, height: 38, borderRadius: 999,
          border: `1px solid ${NF.hairline}`, background: NF.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
        }}>
          <Icon name="back" size={18}/>
        </div>
        <div style={{
          flex: 1, fontFamily: FONT_SANS, fontWeight: 700, fontSize: 17,
          color: NF.ink, letterSpacing: -0.3,
        }}>Источники</div>
        <div onClick={onAddNew} style={{
          minWidth: 38, height: 38,
          padding: isPremium ? 0 : '0 12px',
          borderRadius: 999,
          background: NF.ink,
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
          cursor: 'pointer',
        }}>
          <Icon name="plus" size={18} color="#fff"/>
          {!isPremium && (
            <div style={{
              fontFamily: FONT_SANS, fontWeight: 700, fontSize: 11,
              letterSpacing: 0.4, color: NF.lime, textTransform: 'uppercase',
            }}>Premium</div>
          )}
        </div>
      </div>

      <div style={{ padding: '4px 18px 10px' }}>
        <div style={{
          fontSize: 30, lineHeight: 1.05, letterSpacing: -1,
          fontWeight: 700, color: NF.ink, fontFamily: FONT_SANS,
        }}>{mode === 'hidden' ? <>Скрытые<br/>источники.</> : <>Каталог<br/>источников.</>}</div>
        <div style={{ marginTop: 8, fontSize: 14, color: NF.mute, fontFamily: FONT_SANS }}>
          {mode === 'hidden'
            ? 'Материалы этих источников не попадают в ленту. Верните те, что снова хотите читать.'
            : 'Найдите Telegram-каналы, хабы Habr или разделы VC.RU — или добавьте свой источник.'}
        </div>
      </div>

      {/* Segmented: Каталог / Скрытые */}
      <div style={{ padding: '0 14px 2px' }}>
        <div style={{
          display: 'flex', padding: 4, borderRadius: 999,
          background: NF.surface, border: `1px solid ${NF.hairline}`,
        }}>
          {[
            { id: 'catalog', label: 'Каталог' },
            { id: 'hidden',  label: `Скрытые${hiddenSources.length ? ' · ' + hiddenSources.length : ''}` },
          ].map(t => {
            const on = mode === t.id;
            return (
              <div key={t.id} onClick={() => { setMode(t.id); setPlatform('Все'); setQ(''); }} style={{
                flex: 1, textAlign: 'center', padding: '9px 10px', borderRadius: 999,
                background: on ? NF.ink : 'transparent',
                color: on ? '#fff' : NF.ink2,
                fontFamily: FONT_SANS, fontSize: 13.5, fontWeight: 700,
                cursor: 'pointer', letterSpacing: -0.1,
              }}>{t.label}</div>
            );
          })}
        </div>
      </div>

      {/* Search */}
      <div style={{ padding: '6px 14px 0' }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 10,
          padding: '12px 14px', borderRadius: 999, background: NF.surface,
          border: `1px solid ${NF.hairline}`,
        }}>
          <Icon name="search" size={16} color={NF.mute}/>
          <input value={q} onChange={e => setQ(e.target.value)} placeholder="Поиск по названию или ссылке"
            style={{
              flex: 1, border: 'none', outline: 'none', background: 'transparent',
              fontFamily: FONT_SANS, fontSize: 14, color: NF.ink,
            }}/>
        </div>
      </div>

      {/* Filter chips */}
      <div style={{ padding: '10px 14px 0', display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {['Все', 'Telegram', 'Habr', 'VC.RU'].map(p => {
          const on = platform === p;
          return (
            <div key={p} onClick={() => setPlatform(p)} style={{
              padding: '7px 14px', borderRadius: 999,
              background: on ? NF.accent : NF.surface,
              color: on ? '#fff' : NF.ink2,
              border: `1px solid ${on ? NF.accent : NF.hairline}`,
              fontFamily: FONT_SANS, fontSize: 13, fontWeight: 600,
              cursor: 'pointer',
            }}>{p}</div>
          );
        })}
      </div>

      {/* "My sources" entry row — only in catalog mode */}
      {mode === 'catalog' && (
        <div style={{ padding: '14px 14px 0' }}>
          <div onClick={onOpenMy} style={{
            padding: '14px 16px', borderRadius: 14,
            background: NF.surface, border: `1px solid ${NF.hairline}`,
            display: 'flex', alignItems: 'center', gap: 12, cursor: 'pointer',
          }}>
            <div style={{
              width: 36, height: 36, borderRadius: 10, background: NF.lime,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon name="star" size={18} color={NF.limeInk}/>
            </div>
            <div style={{ flex: 1 }}>
              <div style={{
                fontFamily: FONT_SANS, fontWeight: 600, fontSize: 15, color: NF.ink,
              }}>Мои добавленные</div>
              <Mono style={{ marginTop: 2, display: 'block' }}>{customSources.length} ИЗ 20 · МОЖНО УДАЛЯТЬ</Mono>
            </div>
            <Icon name="chevron" size={16} color={NF.mute}/>
          </div>
        </div>
      )}

      {/* List */}
      <div style={{ padding: '14px 14px 0' }}>
        <Mono style={{ padding: '0 6px 8px', display: 'block' }}>
          {mode === 'hidden'
            ? `СКРЫТЫЕ · ${filtered.length}`
            : `ВСЕ ИСТОЧНИКИ · ${filtered.length}`}
        </Mono>

        {mode === 'hidden' && filtered.length === 0 && (
          <div style={{
            padding: '28px 18px', borderRadius: 14, background: NF.surface,
            border: `1px dashed ${NF.hairline}`, textAlign: 'center',
          }}>
            <div style={{
              width: 44, height: 44, borderRadius: 12, background: NF.chipBg,
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              marginBottom: 10,
            }}>
              <Icon name="eye-off" size={20} color={NF.mute}/>
            </div>
            <div style={{
              fontFamily: FONT_SANS, fontWeight: 700, fontSize: 16, color: NF.ink,
            }}>Ничего не скрыто</div>
            <div style={{
              marginTop: 4, fontFamily: FONT_SANS, fontSize: 13, color: NF.mute,
            }}>Откройте карточку в ленте и выберите «Скрыть источник», чтобы убрать его материалы отсюда.</div>
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {filtered.map(s => (
            <div key={s.id} style={{
              padding: '12px 14px', borderRadius: 12,
              background: NF.surface, border: `1px solid ${NF.hairline}`,
              display: 'flex', alignItems: 'center', gap: 10,
            }}>
              <PlatformGlyph platform={s.platform}/>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  fontFamily: FONT_SANS, fontWeight: 600, fontSize: 14, color: NF.ink,
                }}>{s.name} {s.custom && <span style={{
                  fontFamily: FONT_MONO, fontSize: 9, color: NF.accent,
                  marginLeft: 4, padding: '2px 5px', borderRadius: 4,
                  background: 'rgba(59,43,255,0.08)',
                }}>МОЙ</span>}</div>
                <div style={{
                  fontFamily: FONT_MONO, fontSize: 10, letterSpacing: 0.5,
                  color: NF.mute,
                }}>{s.handle}</div>
                <div style={{
                  marginTop: 3, fontFamily: FONT_SANS, fontSize: 12.5,
                  color: NF.mute,
                }}>{s.desc}</div>
              </div>
              {mode === 'hidden' && (
                <div onClick={() => unhide(s.name)} style={{
                  padding: '8px 12px', borderRadius: 999,
                  background: NF.lime, color: NF.limeInk,
                  fontFamily: FONT_SANS, fontSize: 12.5, fontWeight: 700,
                  cursor: 'pointer', whiteSpace: 'nowrap',
                }}>Вернуть</div>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function AddSourceScreen({ onBack, onAdd }) {
  const [v, setV] = useState('');
  const ok = v.trim().length > 2;
  const detect = () => {
    const s = v.trim();
    if (s.startsWith('t.me/') || s.startsWith('@')) return { platform: 'Telegram', name: s.replace(/^t\.me\//,'').replace(/^@/,''), handle: s };
    if (s.includes('habr.com')) return { platform: 'Habr', name: s.split('/').pop() || 'Хабр', handle: s };
    if (s.includes('vc.ru')) return { platform: 'VC.RU', name: s.split('/').pop() || 'VC', handle: s };
    return { platform: 'Telegram', name: s.replace(/^@/,''), handle: s };
  };
  const det = detect();
  return (
    <div style={{ background: NF.bg, minHeight: '100%', paddingBottom: 140 }}>
      <div style={{ padding: '62px 14px 10px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <div onClick={onBack} style={{
          width: 38, height: 38, borderRadius: 999,
          border: `1px solid ${NF.hairline}`, background: NF.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
        }}>
          <Icon name="back" size={18}/>
        </div>
        <div style={{
          flex: 1, fontFamily: FONT_SANS, fontWeight: 700, fontSize: 17,
          color: NF.ink, letterSpacing: -0.3,
        }}>Добавить источник</div>
      </div>

      <div style={{ padding: '14px 18px' }}>
        <input value={v} onChange={e => setV(e.target.value)} placeholder="Ссылка или @username"
          style={{
            width: '100%', padding: '16px', borderRadius: 14,
            border: `1px solid ${NF.hairline}`, background: NF.surface,
            fontFamily: FONT_SANS, fontSize: 15, color: NF.ink,
            outline: 'none', boxSizing: 'border-box',
          }}/>
        <div style={{
          marginTop: 8, padding: '0 4px', fontFamily: FONT_MONO, fontSize: 10.5,
          letterSpacing: 0.3, color: NF.mute,
        }}>t.me/... · habr.com/hub/... · vc.ru/u/... или vc.ru/&lt;раздел&gt;</div>

        {ok && (
          <div style={{
            marginTop: 18, padding: '14px', borderRadius: 14,
            background: NF.surface, border: `1px solid ${NF.hairline}`,
            display: 'flex', alignItems: 'center', gap: 10,
          }}>
            <PlatformGlyph platform={det.platform}/>
            <div style={{ flex: 1 }}>
              <Mono>ОБНАРУЖЕНО · {det.platform.toUpperCase()}</Mono>
              <div style={{
                marginTop: 3, fontFamily: FONT_SANS, fontWeight: 600,
                fontSize: 15, color: NF.ink,
              }}>{det.name}</div>
            </div>
          </div>
        )}
      </div>

      <div style={{ position: 'absolute', left: 14, right: 14, bottom: 22, display: 'flex', flexDirection: 'column', gap: 8 }}>
        <div onClick={() => ok && onAdd({ ...det, custom: true, id: 'c-' + Date.now() })} style={{
          padding: '16px 18px', borderRadius: 999,
          background: ok ? NF.lime : NF.surface2,
          color: ok ? NF.limeInk : NF.mute,
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          cursor: ok ? 'pointer' : 'not-allowed',
        }}>
          <div style={{ fontFamily: FONT_SANS, fontWeight: 700, fontSize: 15 }}>Добавить</div>
          <Icon name="check" size={15} color={ok ? NF.limeInk : NF.mute}/>
        </div>
        <div onClick={onBack} style={{
          padding: '12px', textAlign: 'center', cursor: 'pointer',
          fontFamily: FONT_SANS, fontSize: 14, fontWeight: 600, color: NF.ink2,
        }}>Отмена</div>
      </div>
    </div>
  );
}

function MySourcesScreen({ onBack, customSources, setCustomSources, onAddNew }) {
  const [q, setQ] = useState('');
  const items = customSources.filter(s =>
    q.trim() === '' || s.name.toLowerCase().includes(q.toLowerCase()));
  const remove = (id) => setCustomSources(cs => cs.filter(s => s.id !== id));
  return (
    <div style={{ background: NF.bg, minHeight: '100%', paddingBottom: 130 }}>
      <div style={{ padding: '62px 14px 10px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <div onClick={onBack} style={{
          width: 38, height: 38, borderRadius: 999,
          border: `1px solid ${NF.hairline}`, background: NF.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
        }}>
          <Icon name="back" size={18}/>
        </div>
        <div style={{
          flex: 1, fontFamily: FONT_SANS, fontWeight: 700, fontSize: 17,
          color: NF.ink, letterSpacing: -0.3,
        }}>Мои добавленные ({customSources.length} из 20)</div>
      </div>

      <div style={{ padding: '10px 14px 0' }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 10,
          padding: '12px 14px', borderRadius: 999, background: NF.surface,
          border: `1px solid ${NF.hairline}`,
        }}>
          <Icon name="search" size={16} color={NF.mute}/>
          <input value={q} onChange={e => setQ(e.target.value)} placeholder="Поиск по названию"
            style={{
              flex: 1, border: 'none', outline: 'none', background: 'transparent',
              fontFamily: FONT_SANS, fontSize: 14, color: NF.ink,
            }}/>
        </div>
      </div>

      {customSources.length === 0 ? (
        <div style={{
          margin: '40px 14px', padding: '40px 20px', textAlign: 'center',
          borderRadius: NF.radiusLg, background: NF.surface,
          border: `1px dashed ${NF.hairline}`,
        }}>
          <Icon name="bookmark" size={32} color={NF.mute2}/>
          <div style={{
            marginTop: 10, fontFamily: FONT_SANS, fontWeight: 600, fontSize: 16, color: NF.ink,
          }}>Вы ещё не добавляли источники</div>
          <div style={{
            marginTop: 6, fontFamily: FONT_SANS, fontSize: 13.5, color: NF.mute,
            lineHeight: 1.5,
          }}>Перейдите в Каталог и добавьте свой первый источник.</div>
          <div onClick={onAddNew} style={{
            marginTop: 16, display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: '10px 16px', borderRadius: 999,
            background: NF.ink, color: '#fff', cursor: 'pointer',
            fontFamily: FONT_SANS, fontSize: 13, fontWeight: 600,
          }}>
            <Icon name="plus" size={14} color="#fff"/>
            Добавить источник
          </div>
        </div>
      ) : (
        <div style={{ padding: '14px', display: 'flex', flexDirection: 'column', gap: 6 }}>
          {items.map(s => (
            <div key={s.id} style={{
              padding: '12px 14px', borderRadius: 12,
              background: NF.surface, border: `1px solid ${NF.hairline}`,
              display: 'flex', alignItems: 'center', gap: 10,
            }}>
              <PlatformGlyph platform={s.platform}/>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  fontFamily: FONT_SANS, fontWeight: 600, fontSize: 14, color: NF.ink,
                }}>{s.name}</div>
                <div style={{
                  fontFamily: FONT_MONO, fontSize: 10, letterSpacing: 0.5, color: NF.mute,
                }}>{s.handle}</div>
              </div>
              <div onClick={() => remove(s.id)} style={{
                width: 34, height: 34, borderRadius: 999,
                border: `1px solid ${NF.hairline}`, cursor: 'pointer',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon name="trash" size={15} color={NF.warn}/>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ----------------- Bookmarks / Profile / Preferences / Settings -----------------

function BookmarksScreen({ reacts, onOpen, onToggle, onBack, kind = 'bookmark', forceEmpty = false }) {
  const KINDS = {
    bookmark: {
      title: 'Закладки', kicker: 'СОХРАНЕНО',
      emptyTitle: 'Тут пока пусто',
      emptyDesc: 'Сохраняйте статьи нажатием на иконку закладки — вернётесь к ним, когда будет время.',
      icon: 'bookmark', accent: 'lime',
    },
    like: {
      title: 'Понравились', kicker: 'ОДОБРЕНО',
      emptyTitle: 'Пока ничего не лайкнули',
      emptyDesc: 'Нажимайте на палец вверх под карточкой — так лента учится подбирать больше того, что вам близко.',
      icon: 'thumb-up', accent: 'accent',
    },
    dislike: {
      title: 'Скрытые', kicker: 'СКРЫТО',
      emptyTitle: 'Ничего не скрыто',
      emptyDesc: 'Дизлайк убирает статью из ленты и учит алгоритм не показывать похожее. Пока вам нравится всё.',
      icon: 'thumb-down', accent: 'ink',
    },
  };
  const meta = KINDS[kind] || KINDS.bookmark;
  const list = forceEmpty ? [] : FEED.filter(f => reacts[f.id]?.[kind]);
  return (
    <div style={{ background: NF.bg, minHeight: '100%', paddingBottom: 130 }}>
      <div style={{ padding: '62px 14px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <div onClick={onBack} style={{
          width: 38, height: 38, borderRadius: 999,
          border: `1px solid ${NF.hairline}`, background: NF.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
        }}>
          <Icon name="back" size={18}/>
        </div>
        <div>
          <Mono>{meta.kicker}: {list.length}</Mono>
          <div style={{
            fontSize: 28, fontWeight: 700, letterSpacing: -0.9, color: NF.ink,
            fontFamily: FONT_SANS, marginTop: 2,
          }}>{meta.title}</div>
        </div>
      </div>
      <div style={{ padding: '0 14px', display: 'flex', flexDirection: 'column', gap: 14 }}>
        {list.length === 0 ? (
          <EmptyState
            icon={meta.icon} accent={meta.accent}
            title={meta.emptyTitle}
            desc={meta.emptyDesc}
          />
        ) : list.map(item => {
          const r = reacts[item.id];
          const props = {
            item, react: r,
            onOpen: () => onOpen(item),
            onLike: () => onToggle(item.id, 'like'),
            onDislike: () => onToggle(item.id, 'dislike'),
            onBookmark: () => onToggle(item.id, 'bookmark'),
          };
          return item.kind === 'short'
            ? <ShortCard key={item.id} {...props}/>
            : <LongCard key={item.id} {...props}/>;
        })}
      </div>
    </div>
  );
}

function ProfileScreen({ reacts, goto, plan }) {
  const liked = Object.values(reacts).filter(r => r.like).length;
  const disliked = Object.values(reacts).filter(r => r.dislike).length;
  const saved = Object.values(reacts).filter(r => r.bookmark).length;
  const isPremium = plan === 'premium';
  // Mock user — users authenticate by email. Until we add profile names,
  // display the local-part (before "@") as the identity.
  const userEmail = 'noor.abellan@fastmail.com';
  const emailLocal = userEmail.split('@')[0];
  const avatarLetter = emailLocal[0].toUpperCase();
  return (
    <div style={{ background: NF.bg, minHeight: '100%', paddingBottom: 130 }}>
      <div style={{ padding: '62px 18px 10px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 4 }}>
          <div style={{
            width: 72, height: 72, borderRadius: 999, background: NF.ink,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            position: 'relative', overflow: 'hidden',
          }}>
            <div style={{
              position: 'absolute', inset: 6, borderRadius: 999,
              background: `conic-gradient(${NF.lime} 0deg 120deg, ${NF.accent} 120deg 260deg, ${NF.surface2} 260deg 360deg)`,
            }}/>
            <div style={{
              position: 'absolute', inset: 14, borderRadius: 999, background: NF.ink,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              color: '#fff', fontWeight: 700, fontSize: 20, fontFamily: FONT_SANS,
            }}>{avatarLetter}</div>
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{
              fontSize: 24, fontWeight: 700, letterSpacing: -0.6, color: NF.ink,
              fontFamily: FONT_SANS,
              whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
            }}>{emailLocal}</div>
            <Mono style={{ marginTop: 2, display: 'block' }}>ПОЧТА · {userEmail.toUpperCase()}</Mono>

            {/* Plan badge */}
            <div style={{ marginTop: 8, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              <div style={{
                display: 'inline-flex', alignItems: 'center', gap: 5,
                padding: '5px 10px', borderRadius: 999,
                background: isPremium ? NF.ink : NF.surface,
                color: isPremium ? NF.lime : NF.ink2,
                border: `1px solid ${isPremium ? NF.ink : NF.hairline}`,
              }}>
                <Icon name="star" size={11} color={isPremium ? NF.lime : NF.mute}/>
                <span style={{
                  fontFamily: FONT_SANS, fontSize: 12, fontWeight: 700,
                  letterSpacing: -0.1,
                }}>{isPremium ? 'Premium' : 'Free'}</span>
              </div>
              <div onClick={() => goto('plan')} style={{
                display: 'inline-flex', alignItems: 'center',
                padding: '5px 10px', borderRadius: 999,
                background: 'transparent', border: `1px solid ${NF.hairline}`,
                fontFamily: FONT_SANS, fontSize: 12, fontWeight: 600, color: NF.accent,
                cursor: 'pointer',
              }}>{isPremium ? 'Управлять' : 'Оформить Premium →'}</div>
            </div>
          </div>
        </div>

        <div style={{
          marginTop: 16, display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8,
        }}>
          {[['ЛАЙКИ', liked, NF.accent], ['СОХР.', saved, NF.lime], ['СКРЫТО', disliked, NF.ink]].map(([l, n, c]) => (
            <div key={l} style={{
              padding: '14px 12px', borderRadius: NF.radius,
              background: NF.surface, border: `1px solid ${NF.hairline}`,
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <div style={{ width: 8, height: 8, borderRadius: 2, background: c }}/>
                <Mono>{l}</Mono>
              </div>
              <div style={{
                fontSize: 26, fontWeight: 700, letterSpacing: -0.8, color: NF.ink,
                fontFamily: FONT_SANS, marginTop: 4,
              }}>{n}</div>
            </div>
          ))}
        </div>
      </div>

      <div style={{ padding: '10px 14px 0', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <ProfileCard title="Закладки" sub={`${saved} сохранено · длинные и короткие`}
          onClick={() => goto('bookmarks')} tone="lime" icon="bookmark"/>
        <ProfileCard title="Источники" sub="Каталог и мои добавленные"
          onClick={() => goto('sources')} tone="ink" icon="radar"/>
      </div>
    </div>
  );
}

function ProfileCard({ title, sub, onClick, tone, icon }) {
  const bg = tone === 'lime' ? NF.lime : tone === 'accent' ? NF.accent : NF.ink;
  const ink = tone === 'lime' ? NF.limeInk : '#fff';
  return (
    <div onClick={onClick} style={{
      padding: 16, borderRadius: NF.radiusLg, background: NF.surface,
      border: `1px solid ${NF.hairline}`, display: 'flex', alignItems: 'center',
      gap: 14, cursor: 'pointer', position: 'relative', overflow: 'hidden',
    }}>
      <div style={{
        width: 48, height: 48, borderRadius: 14, background: bg,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Icon name={icon} size={22} color={ink}/>
      </div>
      <div style={{ flex: 1 }}>
        <div style={{
          fontFamily: FONT_SANS, fontWeight: 700, fontSize: 17,
          letterSpacing: -0.4, color: NF.ink,
        }}>{title}</div>
        <Mono style={{ marginTop: 3, display: 'block' }}>{sub}</Mono>
      </div>
      <Icon name="chevron" size={16} color={NF.mute}/>
    </div>
  );
}

function PreferencesScreen({ onBack, prefs, setPrefs }) {
  const toggle = (t) => { setPrefs(p => p.includes(t) ? p.filter(x => x !== t) : [...p, t]); };
  return (
    <div style={{ background: NF.bg, minHeight: '100%', paddingBottom: 160 }}>
      <div style={{ padding: '62px 14px 10px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <div onClick={onBack} style={{
          width: 38, height: 38, borderRadius: 999,
          border: `1px solid ${NF.hairline}`, background: NF.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
        }}>
          <Icon name="back" size={18}/>
        </div>
        <Mono>ИНТЕРЕСЫ</Mono>
      </div>
      <div style={{ padding: '4px 18px 10px' }}>
        <div style={{
          fontSize: 32, lineHeight: 1.02, letterSpacing: -1.1,
          fontWeight: 700, color: NF.ink, fontFamily: FONT_SANS,
        }}>Ваши<br/>интересы.</div>
        <div style={{ marginTop: 8, fontSize: 14, color: NF.mute, fontFamily: FONT_SANS }}>
          Выберите минимум 3 темы. Лента перестроится в течение цикла.
        </div>
      </div>

      <div style={{
        margin: '16px 14px 0', padding: '10px 14px', borderRadius: 999,
        background: NF.ink, color: '#fff',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <Mono style={{ color: NF.lime }}>ВЫБРАНО</Mono>
        <div style={{
          fontFamily: FONT_MONO, fontSize: 13, letterSpacing: 0.5,
        }}>{prefs.length} / {INTERESTS.length}</div>
      </div>

      <div style={{ padding: '18px 14px', display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {INTERESTS.map(({ t, e }) => {
          const on = prefs.includes(t);
          return (
            <div key={t} onClick={() => toggle(t)} style={{
              padding: '10px 14px', borderRadius: 999,
              background: on ? NF.ink : NF.surface,
              color: on ? '#fff' : NF.ink2,
              border: `1px solid ${on ? NF.ink : NF.hairline}`,
              fontFamily: FONT_SANS, fontSize: 14, fontWeight: 500,
              cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6,
              transition: 'background 160ms',
            }}>
              <span style={{ fontSize: 14 }}>{e}</span>
              <span>{t}</span>
              {on && <Icon name="check" size={13} color={NF.lime}/>}
            </div>
          );
        })}
      </div>

      <div style={{ position: 'absolute', left: 14, right: 14, bottom: 100 }}>
        <div onClick={onBack} style={{
          padding: '16px 18px', borderRadius: 999, background: NF.lime,
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          boxShadow: '0 10px 30px -10px rgba(204,255,51,0.6)', cursor: 'pointer',
        }}>
          <div style={{
            fontFamily: FONT_SANS, fontWeight: 700, fontSize: 15,
            letterSpacing: -0.2, color: NF.limeInk,
          }}>Сохранить</div>
          <Icon name="arrow-up-right" size={15} color={NF.limeInk}/>
        </div>
      </div>
    </div>
  );
}

function PlanScreen({ onBack, plan, setPlan }) {
  const isPremium = plan === 'premium';
  const [cycle, setCycle] = useState('year'); // month | year
  const [confirmed, setConfirmed] = useState(false);

  const plans = {
    month: { price: '299 ₽', per: 'в месяц', note: 'Списание каждый месяц · отмена в любой момент' },
    year:  { price: '2 490 ₽', per: 'в год',  note: '207 ₽/мес · экономия 30% против помесячной оплаты', badge: '−30%' },
  };

  const features = [
    { icon: 'radar', title: 'Собственные источники',
      desc: 'Добавляйте любые Telegram-каналы, хабы Habr и разделы VC.RU. До 100 источников вместо 20.' },
    { icon: 'layers', title: 'Безлимитные пространства',
      desc: 'Собирайте любое число тематических подборок с индивидуальным цветом и набором источников.' },
    { icon: 'spark', title: 'Умная приоритизация',
      desc: 'Алгоритм перестраивает ленту в реальном времени: больше того, что действительно читаете.' },
    { icon: 'bookmark', title: 'История и полнотекстовый поиск',
      desc: 'Всё прочитанное сохраняется навсегда. Ищите по словам внутри статей, а не только по заголовкам.' },
    { icon: 'arrow-up-right', title: 'Ранний доступ к функциям',
      desc: 'Первым получайте дайджесты по расписанию, экспорт в Readwise и бета-интеграции.' },
    { icon: 'check', title: 'Без рекламы и трекеров',
      desc: 'Чистая лента. Никаких промо-карточек между материалами и никакой внешней аналитики.' },
  ];

  return (
    <div style={{ background: NF.bg, minHeight: '100%', paddingBottom: 170 }}>
      <div style={{ padding: '62px 14px 10px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <div onClick={onBack} style={{
          width: 38, height: 38, borderRadius: 999,
          border: `1px solid ${NF.hairline}`, background: NF.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
        }}>
          <Icon name="back" size={18}/>
        </div>
        <Mono>ТАРИФ · {isPremium ? 'PREMIUM' : 'FREE'}</Mono>
      </div>

      {/* Hero */}
      <div style={{ padding: '8px 14px 0' }}>
        <div style={{
          position: 'relative', padding: '22px 20px 20px',
          borderRadius: NF.radiusLg, background: NF.ink, color: '#fff',
          overflow: 'hidden',
        }}>
          <div style={{
            position: 'absolute', top: -30, right: -40, width: 180, height: 180,
            borderRadius: 999,
            background: `radial-gradient(circle, ${NF.lime} 0%, transparent 70%)`,
            opacity: 0.35,
          }}/>
          <div style={{ position: 'relative' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Icon name="star" size={14} color={NF.lime}/>
              <Mono style={{ color: NF.lime }}>RADAR PREMIUM</Mono>
            </div>
            <div style={{
              marginTop: 8, fontFamily: FONT_SANS, fontSize: 32, lineHeight: 1,
              letterSpacing: -1.2, fontWeight: 700,
            }}>Читайте то,<br/>что важно вам.</div>
            <div style={{
              marginTop: 10, fontFamily: FONT_SANS, fontSize: 14, lineHeight: 1.5,
              color: 'rgba(255,255,255,0.7)', maxWidth: 300,
            }}>Premium снимает лимиты Радара: собственные источники,
            неограниченные пространства и инструменты, которые превращают ленту в ваше медиа.</div>
          </div>
        </div>
      </div>

      {/* Cycle toggle */}
      {!isPremium && (
        <div style={{ padding: '18px 14px 0' }}>
          <div style={{
            padding: 4, borderRadius: 999, background: NF.surface2,
            display: 'flex', gap: 4,
          }}>
            {[
              { id: 'month', label: 'Помесячно' },
              { id: 'year',  label: 'Годовой' },
            ].map(c => {
              const on = cycle === c.id;
              return (
                <div key={c.id} onClick={() => setCycle(c.id)} style={{
                  flex: 1, padding: '10px 14px', borderRadius: 999,
                  background: on ? NF.surface : 'transparent',
                  boxShadow: on ? '0 1px 2px rgba(0,0,0,0.06)' : 'none',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
                  fontFamily: FONT_SANS, fontSize: 13, fontWeight: 600,
                  color: on ? NF.ink : NF.mute, cursor: 'pointer',
                }}>
                  {c.label}
                  {c.id === 'year' && (
                    <span style={{
                      padding: '2px 7px', borderRadius: 6, background: NF.lime,
                      color: NF.limeInk, fontFamily: FONT_MONO, fontSize: 9,
                      letterSpacing: 0.5,
                    }}>−30%</span>
                  )}
                </div>
              );
            })}
          </div>

          {/* Price card */}
          <div style={{
            marginTop: 10, padding: '18px 18px', borderRadius: NF.radiusLg,
            background: NF.surface, border: `2px solid ${NF.ink}`,
          }}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
              <div style={{
                fontFamily: FONT_SANS, fontSize: 34, fontWeight: 700,
                letterSpacing: -1.2, color: NF.ink,
              }}>{plans[cycle].price}</div>
              <div style={{
                fontFamily: FONT_SANS, fontSize: 14, color: NF.mute, fontWeight: 500,
              }}>{plans[cycle].per}</div>
            </div>
            <div style={{
              marginTop: 4, fontFamily: FONT_SANS, fontSize: 13, color: NF.mute,
            }}>{plans[cycle].note}</div>
          </div>
        </div>
      )}

      {/* What's included */}
      <div style={{ padding: '22px 14px 0' }}>
        <Mono style={{ padding: '0 6px 10px', display: 'block' }}>ЧТО ВКЛЮЧЕНО · {features.length}</Mono>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {features.map((f, i) => (
            <div key={f.title} style={{
              padding: '14px 14px', borderRadius: NF.radius,
              background: NF.surface, border: `1px solid ${NF.hairline}`,
              display: 'flex', gap: 12, alignItems: 'flex-start',
            }}>
              <div style={{
                width: 38, height: 38, borderRadius: 10, flexShrink: 0,
                background: i % 2 === 0 ? NF.ink : NF.lime,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon name={f.icon} size={18} color={i % 2 === 0 ? NF.lime : NF.limeInk}/>
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  fontFamily: FONT_SANS, fontWeight: 700, fontSize: 15,
                  letterSpacing: -0.3, color: NF.ink,
                }}>{f.title}</div>
                <div style={{
                  marginTop: 3, fontFamily: FONT_SANS, fontSize: 13, lineHeight: 1.45,
                  color: NF.mute,
                }}>{f.desc}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Comparison */}
      <div style={{ padding: '22px 14px 0' }}>
        <Mono style={{ padding: '0 6px 10px', display: 'block' }}>FREE · PREMIUM</Mono>
        <div style={{
          background: NF.surface, borderRadius: NF.radiusLg,
          border: `1px solid ${NF.hairline}`, overflow: 'hidden',
        }}>
          <div style={{
            display: 'grid', gridTemplateColumns: '1.2fr 0.9fr 0.9fr',
            padding: '12px 14px', borderBottom: `1px solid ${NF.hairline}`,
            background: NF.surface2,
          }}>
            <Mono>ФУНКЦИЯ</Mono>
            <Mono style={{ textAlign: 'center' }}>FREE</Mono>
            <Mono style={{ textAlign: 'center', color: NF.accent }}>PREMIUM</Mono>
          </div>
          {[
            ['Пространства', 'до 3', 'без лимита'],
            ['Источники', 'до 20', 'до 100'],
            ['Свои источники', '—', '✓'],
            ['История чтения', '7 дней', 'навсегда'],
            ['Без рекламы', '—', '✓'],
            ['Ранний доступ', '—', '✓'],
          ].map((row, i, arr) => (
            <div key={row[0]} style={{
              display: 'grid', gridTemplateColumns: '1.2fr 0.9fr 0.9fr',
              padding: '12px 14px',
              borderBottom: i === arr.length - 1 ? 'none' : `1px solid ${NF.hairline}`,
              alignItems: 'center',
            }}>
              <div style={{
                fontFamily: FONT_SANS, fontSize: 13.5, color: NF.ink, fontWeight: 500,
              }}>{row[0]}</div>
              <div style={{
                fontFamily: FONT_MONO, fontSize: 11, color: NF.mute,
                textAlign: 'center', letterSpacing: 0.3,
              }}>{row[1]}</div>
              <div style={{
                fontFamily: FONT_MONO, fontSize: 11,
                color: row[2] === '—' ? NF.mute : NF.accent,
                textAlign: 'center', fontWeight: 700, letterSpacing: 0.3,
              }}>{row[2]}</div>
            </div>
          ))}
        </div>
      </div>

      {/* FAQ / fine print */}
      <div style={{ padding: '20px 18px 8px' }}>
        <div style={{
          fontFamily: FONT_SANS, fontSize: 12, lineHeight: 1.55, color: NF.mute,
        }}>
          Оплата в App Store или Google Pay. Подписка продлевается автоматически
          до момента отмены в настройках аккаунта. При отмене вы пользуетесь Premium
          до конца оплаченного периода.
        </div>
      </div>

      {/* CTA — sticky-ish bottom */}
      <div style={{
        position: 'sticky', bottom: 84, marginTop: 12,
        padding: '10px 14px 0',
      }}>
        <div style={{
          padding: 12, borderRadius: 24, background: NF.bg,
          border: `1px solid ${NF.hairline}`,
          boxShadow: '0 -10px 30px -20px rgba(0,0,0,0.15)',
        }}>
          {isPremium ? (
            <>
              <div onClick={() => { setPlan('free'); setConfirmed(false); }} style={{
                padding: '14px 16px', borderRadius: 999,
                background: NF.surface, border: `1px solid ${NF.hairline}`,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontFamily: FONT_SANS, fontWeight: 600, fontSize: 14, color: NF.ink,
                cursor: 'pointer',
              }}>Управлять подпиской</div>
              <div style={{
                marginTop: 6, textAlign: 'center',
                fontFamily: FONT_MONO, fontSize: 10, color: NF.mute, letterSpacing: 0.5,
              }}>СЛЕДУЮЩЕЕ СПИСАНИЕ · 14 МАЯ 2026</div>
            </>
          ) : (
            <>
              <div onClick={() => { setPlan('premium'); setConfirmed(true); }} style={{
                padding: '16px 18px', borderRadius: 999, background: NF.lime,
                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
                boxShadow: `0 10px 30px -10px ${NF.lime}`, cursor: 'pointer',
              }}>
                <div style={{
                  fontFamily: FONT_SANS, fontWeight: 700, fontSize: 15,
                  letterSpacing: -0.2, color: NF.limeInk,
                }}>Оформить · {plans[cycle].price} {cycle === 'year' ? '/ год' : '/ мес'}</div>
                <Icon name="arrow-up-right" size={15} color={NF.limeInk}/>
              </div>
              <div style={{
                marginTop: 6, textAlign: 'center',
                fontFamily: FONT_MONO, fontSize: 10, color: NF.mute, letterSpacing: 0.5,
              }}>7 ДНЕЙ БЕСПЛАТНО · ОТМЕНА В ЛЮБОЙ МОМЕНТ</div>
            </>
          )}
        </div>
      </div>

      {confirmed && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(14,15,13,0.45)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 200, padding: 30,
        }} onClick={() => setConfirmed(false)}>
          <div style={{
            background: NF.surface, borderRadius: NF.radiusLg, padding: 24,
            maxWidth: 300, textAlign: 'center',
          }} onClick={e => e.stopPropagation()}>
            <div style={{
              width: 56, height: 56, borderRadius: 999, background: NF.lime,
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            }}><Icon name="check" size={28} color={NF.limeInk}/></div>
            <div style={{
              marginTop: 12, fontFamily: FONT_SANS, fontWeight: 700, fontSize: 20,
              letterSpacing: -0.5, color: NF.ink,
            }}>Добро пожаловать<br/>в Premium</div>
            <div style={{
              marginTop: 8, fontFamily: FONT_SANS, fontSize: 13, color: NF.mute,
              lineHeight: 1.5,
            }}>Все лимиты сняты. Первые 7 дней бесплатно — отменить можно из профиля.</div>
            <div onClick={() => setConfirmed(false)} style={{
              marginTop: 16, padding: '12px 16px', borderRadius: 999, background: NF.ink,
              color: '#fff', fontFamily: FONT_SANS, fontWeight: 600, fontSize: 14,
              cursor: 'pointer',
            }}>Отлично</div>
          </div>
        </div>
      )}
    </div>
  );
}

function SettingsScreen({ goto, plan }) {
  const isPremium = plan === 'premium';
  const sections = [
    { h: 'АККАУНТ', rows: [
      ['Email', 'noor@radar.app'],
      ['Пароль', 'Сменить'],
      ['Выйти', null, 'warn'],
    ]},
    { h: 'ПОДПИСКА', rows: [
      [isPremium ? 'Управлять Premium' : 'Оформить Premium',
        isPremium ? 'Активно' : 'Free', null, () => goto('plan')],
      ['Тарифы и цены', 'Сравнить', null, () => goto('plan')],
    ]},
    { h: 'ИСТОЧНИКИ', rows: [
      ['Источники', 'Каталог', null, () => goto('sources')],
      ['Мои добавленные', 'Список', null, () => goto('my-sources')],
    ]},
    { h: 'ПРИЛОЖЕНИЕ', rows: [
      ['Версия', '2.4.1'],
    ]},
  ];
  return (
    <div style={{ background: NF.bg, minHeight: '100%', paddingBottom: 130 }}>
      <div style={{ padding: '62px 18px 14px' }}>
        <div style={{
          fontSize: 40, lineHeight: 0.95, letterSpacing: -1.4,
          fontWeight: 700, color: NF.ink, fontFamily: FONT_SANS,
        }}>Настройки</div>
      </div>
      <div style={{ padding: '0 14px', display: 'flex', flexDirection: 'column', gap: 18 }}>
        {sections.map(s => (
          <div key={s.h}>
            <Mono style={{ padding: '0 6px 8px', display: 'block' }}>{s.h}</Mono>
            <div style={{
              background: NF.surface, borderRadius: NF.radiusLg,
              border: `1px solid ${NF.hairline}`, overflow: 'hidden',
            }}>
              {s.rows.map((r, i) => (
                <SettingRow key={r[0]} row={r} isLast={i === s.rows.length - 1}/>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function SettingRow({ row, isLast }) {
  const [title, detail, variant, onClick] = row;
  return (
    <div style={{
      padding: '14px 16px', display: 'flex', alignItems: 'center',
      borderBottom: isLast ? 'none' : `1px solid ${NF.hairline}`,
      cursor: 'pointer',
    }}
    onClick={() => { if (onClick) onClick(); }}
    >
      <div style={{
        fontFamily: FONT_SANS, fontSize: 15, fontWeight: 500,
        color: variant === 'warn' ? NF.warn : NF.ink, flex: 1,
      }}>{title}</div>
      {detail && <Mono style={{ color: NF.ink2 }}>{detail}</Mono>}
      <Icon name="chevron" size={13} color={NF.mute}/>
    </div>
  );
}

Object.assign(window, {
  FeedScreen, DetailScreen, RelatedAllScreen,
  CollectionsScreen, CollectionEditorScreen,
  SourcesScreen, AddSourceScreen, MySourcesScreen,
  BookmarksScreen, ProfileScreen, PreferencesScreen, PlanScreen,
  SettingsScreen,
});
