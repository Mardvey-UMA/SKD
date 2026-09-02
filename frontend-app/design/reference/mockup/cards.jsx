// Радар — reusable card atoms

const { useState, useEffect, useRef, useMemo } = React;

// Reactions — LIKE / DISLIKE / BOOKMARK; no counters, no SAVE label.
function ReactionBar({ state, onLike, onDislike, onBookmark, compact = false }) {
  const btn = (active, activeColor, activeInk) => ({
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    width: compact ? 34 : 40, height: compact ? 34 : 40,
    borderRadius: 999,
    background: active ? activeColor : 'transparent',
    border: `1px solid ${active ? activeColor : NF.hairline}`,
    cursor: 'pointer',
    transition: 'background 160ms ease, border-color 160ms ease, transform 160ms ease',
    userSelect: 'none',
  });
  return (
    <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
      <div onClick={(e) => { e.stopPropagation(); onLike(); }}
        style={btn(state.like, NF.accent, NF.accentInk)} aria-label="Нравится">
        <Icon name="thumb-up" size={16} color={state.like ? NF.accentInk : NF.ink2}/>
      </div>
      <div onClick={(e) => { e.stopPropagation(); onDislike(); }}
        style={btn(state.dislike, NF.ink, '#fff')} aria-label="Не нравится">
        <Icon name="thumb-down" size={16} color={state.dislike ? '#fff' : NF.ink2}/>
      </div>
      <div onClick={(e) => { e.stopPropagation(); onBookmark(); }}
        style={btn(state.bookmark, NF.lime, NF.limeInk)} aria-label="В закладки">
        <Icon name="bookmark" size={16}
          color={state.bookmark ? NF.limeInk : NF.ink2}
          fill={state.bookmark ? NF.limeInk : 'none'}/>
      </div>
    </div>
  );
}

// Source chip — platform + name. hasMenu shows a 3-dot affordance anchored right.
function SourceLine({ source, handle, time, read, onMore }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
      <div style={{
        width: 18, height: 18, borderRadius: 4, background: NF.ink,
        display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
      }}>
        <div style={{ width: 8, height: 8, background: NF.lime, transform: 'rotate(45deg)' }}/>
      </div>
      <div style={{
        fontFamily: FONT_SANS, fontSize: 13, fontWeight: 600,
        color: NF.ink, letterSpacing: -0.1,
      }}>{source}</div>
      <span style={{ width: 3, height: 3, borderRadius: 3, background: NF.mute2 }}/>
      <Mono>{time}</Mono>
      {read && <>
        <span style={{ width: 3, height: 3, borderRadius: 3, background: NF.mute2 }}/>
        <Mono>{read}</Mono>
      </>}
      {onMore && (
        <>
          <div style={{ flex: 1 }}/>
          <div
            onClick={(e) => { e.stopPropagation(); onMore(e); }}
            style={{
              width: 28, height: 28, borderRadius: 999,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              cursor: 'pointer', marginRight: -6,
            }}
            aria-label="Ещё">
            <Icon name="more" size={16} color={NF.mute}/>
          </div>
        </>
      )}
    </div>
  );
}

// IMAGE VARIANTS — all render at the same consistent size.

function SingleImage({ item, height = 200 }) {
  return <Stripe height={height} tone={item.tone} seed={item.seed} label="ФОТО" radius={NF.radius}/>;
}

function MultiImage({ item, height = 200 }) {
  const tones = [item.tone, item.toneSecondary || 'accent', item.toneTertiary || 'lime'];
  return (
    <div style={{
      display: 'grid', gridTemplateColumns: '2fr 1fr', gridTemplateRows: '1fr 1fr',
      gap: 4, height, borderRadius: NF.radius, overflow: 'hidden',
    }}>
      <div style={{ gridRow: '1 / span 2', position: 'relative' }}>
        <Stripe height="100%" tone={tones[0]} seed={item.seed}       label="1/3" radius={0}/>
      </div>
      <div style={{ position: 'relative' }}>
        <Stripe height="100%" tone={tones[1]} seed={item.seed + 10}  label="2/3" radius={0}/>
      </div>
      <div style={{ position: 'relative' }}>
        <Stripe height="100%" tone={tones[2]} seed={item.seed + 20}  label="3/3" radius={0}/>
      </div>
    </div>
  );
}

// No-image variant: kept as `null` — a ShortCard/LongCard without an image
// simply skips the image block entirely. The card body handles the title.
function NoImage() { return null; }

// The card body (title + snippet + meta + reactions), same for all image variants.
function CardBody({ item, react, onOpen, onLike, onDislike, onBookmark, onMore, showTitle = true }) {
  return (
    <div style={{ padding: '14px 18px 14px' }}>
      <SourceLine source={item.source} handle={item.sourceHandle} time={item.time} read={item.read} onMore={onMore}/>
      {showTitle && (
        <div onClick={onOpen} style={{
          fontFamily: FONT_SANS, fontSize: 21, lineHeight: 1.15,
          letterSpacing: -0.5, color: NF.ink, fontWeight: 600,
          marginTop: 10, cursor: 'pointer',
        }}>{item.title}</div>
      )}
      <div style={{
        fontFamily: FONT_SANS, fontSize: 14.5, lineHeight: 1.5,
        color: NF.mute, marginTop: 8,
      }}>{item.snippet}</div>

      <div style={{
        marginTop: 14, display: 'flex', alignItems: 'center',
        justifyContent: 'space-between', gap: 8,
      }}>
        <ReactionBar state={react} onLike={onLike} onDislike={onDislike} onBookmark={onBookmark}/>
        <div onClick={onOpen} style={{
          display: 'inline-flex', alignItems: 'center', gap: 6,
          padding: '9px 14px', borderRadius: 999,
          background: NF.ink, color: '#fff', cursor: 'pointer',
          fontFamily: FONT_SANS, fontSize: 13, fontWeight: 600,
          letterSpacing: -0.2,
        }}>
          Читать
          <Icon name="arrow-right" size={13} color="#fff"/>
        </div>
      </div>
    </div>
  );
}

// The short card: handles all three image variants (one/multi/none).
function ShortCard({ item, react, onOpen, onLike, onDislike, onBookmark, onMore }) {
  const variant = item.images || 'one';
  return (
    <div onClick={onOpen}
      style={{
        background: NF.surface, borderRadius: NF.radiusLg, overflow: 'hidden',
        border: `1px solid ${NF.hairline}`,
        boxShadow: '0 1px 0 rgba(0,0,0,0.02), 0 8px 24px -16px rgba(0,0,0,0.12)',
        cursor: 'pointer',
      }}>
      {variant === 'none' ? null : variant === 'multi' ? (
        <div style={{ padding: 8 }}>
          <MultiImage item={item} height={200}/>
        </div>
      ) : (
        <div style={{ padding: 8 }}>
          <SingleImage item={item} height={200}/>
        </div>
      )}
      <CardBody
        item={item} react={react}
        onOpen={onOpen}
        onLike={onLike} onDislike={onDislike} onBookmark={onBookmark}
        onMore={onMore}
      />
    </div>
  );
}

// Long form card — same image system, longer snippet with fade + "Читать далее".
function LongCard({ item, react, onOpen, onLike, onDislike, onBookmark, onMore }) {
  const variant = item.images || 'one';
  return (
    <div style={{
      background: NF.surface, borderRadius: NF.radiusLg, overflow: 'hidden',
      border: `1px solid ${NF.hairline}`,
      boxShadow: '0 1px 0 rgba(0,0,0,0.02), 0 8px 24px -16px rgba(0,0,0,0.12)',
    }}>
      {variant !== 'none' && (
        <div style={{ padding: 8 }}>
          {variant === 'multi'
            ? <MultiImage item={item} height={200}/>
            : <SingleImage item={item} height={200}/>}
        </div>
      )}
      <div style={{ padding: '14px 18px 14px' }}>
        <SourceLine source={item.source} handle={item.sourceHandle} time={item.time} read={item.read} onMore={onMore}/>
        <div onClick={onOpen} style={{
          fontFamily: FONT_SANS, fontSize: 22, lineHeight: 1.14,
          letterSpacing: -0.6, color: NF.ink, fontWeight: 700,
          marginTop: 10, cursor: 'pointer',
        }}>{item.title}</div>
        <div style={{
          position: 'relative', marginTop: 8, maxHeight: 96, overflow: 'hidden',
          fontFamily: FONT_SANS, fontSize: 14.5, lineHeight: 1.5, color: NF.mute,
        }}>
          {item.snippet}
          <div style={{
            position: 'absolute', left: 0, right: 0, bottom: 0, height: 40,
            background: `linear-gradient(to bottom, rgba(255,255,255,0), ${NF.surface})`,
          }}/>
        </div>
        <div style={{
          marginTop: 14, display: 'flex', alignItems: 'center',
          justifyContent: 'space-between', gap: 8,
        }}>
          <ReactionBar state={react} onLike={onLike} onDislike={onDislike} onBookmark={onBookmark}/>
          <div onClick={onOpen} style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: '9px 14px', borderRadius: 999,
            background: NF.ink, color: '#fff', cursor: 'pointer',
            fontFamily: FONT_SANS, fontSize: 13, fontWeight: 600,
            letterSpacing: -0.2,
          }}>
            Читать далее
            <Icon name="arrow-right" size={13} color="#fff"/>
          </div>
        </div>
      </div>
    </div>
  );
}

// Top header for the feed — "Радар" wordmark, clean.
function FeedHeader() {
  return (
    <div style={{
      padding: '62px 18px 14px',
      background: NF.bg, position: 'sticky', top: 0, zIndex: 20,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{
            width: 32, height: 32, borderRadius: 9, background: NF.ink,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            position: 'relative', overflow: 'hidden',
          }}>
            <div style={{
              position: 'absolute', inset: 4, borderRadius: 999,
              border: `1.2px solid ${NF.lime}`,
            }}/>
            <div style={{
              position: 'absolute', left: '50%', top: '50%',
              width: 10, height: 1.2, background: NF.lime,
              transformOrigin: '0 50%', transform: 'translate(0, -50%) rotate(-30deg)',
            }}/>
            <div style={{ width: 3, height: 3, borderRadius: 3, background: NF.lime }}/>
          </div>
          <div style={{
            fontFamily: FONT_SANS, fontWeight: 700, fontSize: 20,
            letterSpacing: -0.8, color: NF.ink,
          }}>Радар</div>
        </div>
        <div style={{
          padding: '6px 12px', borderRadius: 999, background: NF.ink, color: '#fff',
          fontFamily: FONT_MONO, fontSize: 10, letterSpacing: 1.2, textTransform: 'uppercase',
        }}>Для вас</div>
      </div>
    </div>
  );
}

function IconBtn({ name, badge, onClick }) {
  return (
    <div onClick={onClick} style={{
      width: 38, height: 38, borderRadius: 999,
      border: `1px solid ${NF.hairline}`, background: NF.surface,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      position: 'relative', cursor: 'pointer',
    }}>
      <Icon name={name} size={17}/>
      {badge && <div style={{
        position: 'absolute', top: 7, right: 8,
        width: 7, height: 7, borderRadius: 7, background: NF.warn,
        border: `1.5px solid ${NF.surface}`,
      }}/>}
    </div>
  );
}

// --------- CardMenu — bottom sheet from the card 3-dot ----------
// Anchored as full-screen overlay sheet (mobile pattern).
function CardMenu({ open, item, isPremium, onClose, onHideSource, onAddToSpace }) {
  if (!open || !item) return null;
  return (
    <div onClick={onClose} style={{
      position: 'absolute', inset: 0, zIndex: 300,
      background: 'rgba(14,15,13,0.4)',
      display: 'flex', alignItems: 'flex-end',
      animation: 'fade 200ms ease',
    }}>
      <div onClick={e => e.stopPropagation()} style={{
        width: '100%', background: NF.surface,
        borderTopLeftRadius: 24, borderTopRightRadius: 24,
        padding: '12px 12px 20px',
        animation: 'sheetUp 280ms cubic-bezier(.2,.9,.25,1)',
      }}>
        <div style={{
          width: 44, height: 4, borderRadius: 4, background: NF.hairline,
          margin: '4px auto 12px',
        }}/>
        <div style={{
          padding: '4px 12px 12px', display: 'flex', alignItems: 'center', gap: 10,
          borderBottom: `1px solid ${NF.hairline}`, marginBottom: 6,
        }}>
          <div style={{
            width: 36, height: 36, borderRadius: 10, background: NF.chipBg,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <div style={{ width: 14, height: 14, background: NF.ink, borderRadius: 3, transform: 'rotate(45deg)' }}/>
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{
              fontFamily: FONT_SANS, fontWeight: 700, fontSize: 15,
              color: NF.ink, letterSpacing: -0.2,
            }}>{item.source}</div>
            <div style={{
              fontFamily: FONT_MONO, fontSize: 10, letterSpacing: 0.5, color: NF.mute,
            }}>{item.sourceHandle}</div>
          </div>
        </div>

        {/* Add to space — premium gated */}
        <MenuRow
          icon="folder-plus"
          title="Добавить источник в пространство"
          sub={isPremium ? 'Выбрать существующее или создать новое' : 'Доступно в Premium'}
          locked={!isPremium}
          onClick={() => { onAddToSpace(); }}
        />

        {/* Hide source */}
        <MenuRow
          icon="eye-off"
          title="Скрыть источник"
          sub="Материалы этого источника не будут попадать в ленту"
          danger
          onClick={() => { onHideSource(); onClose(); }}
        />

        <div onClick={onClose} style={{
          marginTop: 8, padding: '14px', textAlign: 'center',
          borderRadius: 14, background: NF.surface2,
          fontFamily: FONT_SANS, fontWeight: 600, fontSize: 14, color: NF.ink,
          cursor: 'pointer',
        }}>Отмена</div>
      </div>
      <style>{`
        @keyframes sheetUp {
          from { transform: translateY(100%); }
          to { transform: translateY(0); }
        }
      `}</style>
    </div>
  );
}

function MenuRow({ icon, title, sub, danger, locked, onClick }) {
  return (
    <div onClick={onClick} style={{
      padding: '12px 12px', borderRadius: 14, display: 'flex', gap: 12,
      alignItems: 'center', cursor: 'pointer',
    }}>
      <div style={{
        width: 38, height: 38, borderRadius: 10,
        background: locked ? NF.chipBg : danger ? 'rgba(255,90,31,0.1)' : NF.chipBg,
        display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
      }}>
        <Icon name={icon} size={18} color={locked ? NF.mute : danger ? NF.warn : NF.ink}/>
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 6,
          fontFamily: FONT_SANS, fontWeight: 600, fontSize: 14.5,
          color: danger ? NF.warn : NF.ink,
        }}>
          {title}
          {locked && <span style={{
            fontFamily: FONT_MONO, fontSize: 9, letterSpacing: 0.6,
            padding: '2px 6px', borderRadius: 5,
            background: NF.ink, color: NF.lime,
          }}>PREMIUM</span>}
        </div>
        {sub && <div style={{
          marginTop: 2, fontFamily: FONT_SANS, fontSize: 12.5, color: NF.mute, lineHeight: 1.4,
        }}>{sub}</div>}
      </div>
      <Icon name="chevron" size={14} color={NF.mute}/>
    </div>
  );
}

// --------- AddToSpaceSheet — pick existing or create new ----------
function AddToSpaceSheet({ open, item, userCollections, onClose, onAddExisting, onCreateNew }) {
  if (!open || !item) return null;
  return (
    <div onClick={onClose} style={{
      position: 'absolute', inset: 0, zIndex: 310,
      background: 'rgba(14,15,13,0.4)',
      display: 'flex', alignItems: 'flex-end',
    }}>
      <div onClick={e => e.stopPropagation()} style={{
        width: '100%', background: NF.surface,
        borderTopLeftRadius: 24, borderTopRightRadius: 24,
        padding: '12px 12px 20px', maxHeight: '80vh', overflow: 'auto',
        animation: 'sheetUp 280ms cubic-bezier(.2,.9,.25,1)',
      }}>
        <div style={{
          width: 44, height: 4, borderRadius: 4, background: NF.hairline,
          margin: '4px auto 14px',
        }}/>
        <div style={{ padding: '0 8px 10px' }}>
          <Mono>ДОБАВИТЬ В ПРОСТРАНСТВО</Mono>
          <div style={{
            marginTop: 4, fontFamily: FONT_SANS, fontSize: 20, fontWeight: 700,
            letterSpacing: -0.5, color: NF.ink, lineHeight: 1.15,
          }}>Куда сохранить источник<br/>«{item.source}»?</div>
        </div>

        <div
          onClick={() => { onCreateNew(); }}
          style={{
            margin: '6px 4px 10px', padding: '14px 14px',
            borderRadius: 14, border: `1.5px dashed ${NF.ink}`,
            background: NF.surface, display: 'flex', alignItems: 'center', gap: 12,
            cursor: 'pointer',
          }}>
          <div style={{
            width: 38, height: 38, borderRadius: 10, background: NF.lime,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Icon name="plus" size={18} color={NF.limeInk}/>
          </div>
          <div style={{ flex: 1 }}>
            <div style={{
              fontFamily: FONT_SANS, fontWeight: 700, fontSize: 15, color: NF.ink,
            }}>Создать новое пространство</div>
            <div style={{
              marginTop: 2, fontFamily: FONT_SANS, fontSize: 12.5, color: NF.mute,
            }}>Источник будет добавлен сразу</div>
          </div>
          <Icon name="chevron" size={14} color={NF.mute}/>
        </div>

        <Mono style={{ display: 'block', padding: '8px 12px 6px' }}>ВАШИ ПРОСТРАНСТВА · {userCollections.length}</Mono>
        {userCollections.length === 0 && (
          <div style={{
            padding: '18px', borderRadius: 14, background: NF.surface2,
            textAlign: 'center', margin: '0 4px',
            fontFamily: FONT_SANS, fontSize: 13, color: NF.mute,
          }}>У вас пока нет пространств. Создайте новое выше.</div>
        )}
        {userCollections.map(c => {
          const bg = c.tone === 'lime' ? NF.lime
            : c.tone === 'accent' ? NF.accent
            : c.tone === 'warn' ? NF.warn
            : c.tone === 'violet' ? '#7A4BFF'
            : c.tone === 'teal' ? '#1EC6B6'
            : c.tone === 'rose' ? '#FF7A8A'
            : NF.ink;
          return (
            <div key={c.id} onClick={() => onAddExisting(c.id)} style={{
              margin: '6px 4px', padding: '12px 14px', borderRadius: 14,
              background: NF.surface2, display: 'flex', alignItems: 'center', gap: 12,
              cursor: 'pointer',
            }}>
              <div style={{
                width: 36, height: 36, borderRadius: 10, background: bg,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon name="layers" size={17} color={bg === NF.lime ? NF.limeInk : '#fff'}/>
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  fontFamily: FONT_SANS, fontWeight: 700, fontSize: 14.5, color: NF.ink,
                }}>{c.title}</div>
                <div style={{
                  marginTop: 2, fontFamily: FONT_MONO, fontSize: 10, color: NF.mute, letterSpacing: 0.5,
                }}>{c.count} МАТЕРИАЛОВ · {(c.sources||[]).length} ИСТОЧНИКОВ</div>
              </div>
              <Icon name="plus" size={16} color={NF.mute}/>
            </div>
          );
        })}
        <div onClick={onClose} style={{
          marginTop: 8, padding: '14px', textAlign: 'center',
          borderRadius: 14, background: NF.surface2,
          fontFamily: FONT_SANS, fontWeight: 600, fontSize: 14, color: NF.ink,
          cursor: 'pointer',
        }}>Отмена</div>
      </div>
    </div>
  );
}

// --------- FeedSkeleton — animated placeholders while feed loads ----------
function FeedSkeleton() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{
        textAlign: 'center', padding: '14px 14px 6px',
      }}>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <div className="rdr-pulse" style={{
            width: 10, height: 10, borderRadius: 999, background: NF.lime,
          }}/>
          <Mono style={{ color: NF.ink2 }}>ВАША ЛЕНТА ФОРМИРУЕТСЯ...</Mono>
        </div>
        <div style={{
          marginTop: 6, fontFamily: FONT_SANS, fontSize: 13, color: NF.mute,
        }}>Подбираем свежие материалы по вашим интересам</div>
      </div>
      {[0, 1, 2].map(i => (
        <div key={i} style={{
          background: NF.surface, borderRadius: NF.radiusLg, overflow: 'hidden',
          border: `1px solid ${NF.hairline}`, padding: 8,
        }}>
          <div className="rdr-shimmer" style={{ height: 180, borderRadius: NF.radius }}/>
          <div style={{ padding: '14px 10px 6px' }}>
            <div className="rdr-shimmer" style={{ height: 14, width: '45%', borderRadius: 7, marginBottom: 10 }}/>
            <div className="rdr-shimmer" style={{ height: 20, width: '85%', borderRadius: 8, marginBottom: 8 }}/>
            <div className="rdr-shimmer" style={{ height: 14, width: '70%', borderRadius: 7, marginBottom: 14 }}/>
            <div style={{ display: 'flex', gap: 8 }}>
              <div className="rdr-shimmer" style={{ height: 36, width: 36, borderRadius: 999 }}/>
              <div className="rdr-shimmer" style={{ height: 36, width: 36, borderRadius: 999 }}/>
              <div className="rdr-shimmer" style={{ height: 36, width: 36, borderRadius: 999 }}/>
            </div>
          </div>
        </div>
      ))}
      <style>{`
        @keyframes rdrShimmer {
          0% { background-position: -400px 0; }
          100% { background-position: 400px 0; }
        }
        .rdr-shimmer {
          background: linear-gradient(90deg, ${NF.surface2} 0%, ${NF.chipBg} 20%, ${NF.surface2} 40%, ${NF.surface2} 100%);
          background-size: 800px 100%;
          animation: rdrShimmer 1.4s linear infinite;
        }
        @keyframes rdrPulse {
          0%, 100% { transform: scale(1); opacity: 1; }
          50% { transform: scale(1.6); opacity: 0.5; }
        }
        .rdr-pulse { animation: rdrPulse 1.3s ease-in-out infinite; }
      `}</style>
    </div>
  );
}

// --------- EmptyState — friendly empty for likes/dislikes/bookmarks ----------
function EmptyState({ icon = 'bookmark', title, desc, accent = 'lime', action }) {
  const bg = accent === 'lime' ? NF.lime : accent === 'accent' ? NF.accent : NF.ink;
  const ink = accent === 'lime' ? NF.limeInk : '#fff';
  return (
    <div style={{
      padding: '36px 22px', borderRadius: NF.radiusLg,
      background: NF.surface, border: `1px solid ${NF.hairline}`,
      textAlign: 'center', position: 'relative', overflow: 'hidden',
    }}>
      <div style={{
        position: 'relative', width: 86, height: 86, margin: '0 auto',
      }}>
        <div className="rdr-ring" style={{
          position: 'absolute', inset: 0, borderRadius: 999,
          border: `1.5px dashed ${NF.hairline}`,
        }}/>
        <div className="rdr-ring2" style={{
          position: 'absolute', inset: 12, borderRadius: 999,
          border: `1.5px dashed ${NF.hairline}`,
        }}/>
        <div style={{
          position: 'absolute', inset: 24, borderRadius: 999, background: bg,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          animation: 'rdrFloat 3s ease-in-out infinite',
        }}>
          <Icon name={icon} size={22} color={ink}/>
        </div>
      </div>
      <div style={{
        marginTop: 16, fontFamily: FONT_SANS, fontWeight: 700, fontSize: 18,
        color: NF.ink, letterSpacing: -0.3,
      }}>{title}</div>
      {desc && <div style={{
        marginTop: 6, fontFamily: FONT_SANS, fontSize: 13.5, lineHeight: 1.5,
        color: NF.mute, maxWidth: 260, marginLeft: 'auto', marginRight: 'auto',
      }}>{desc}</div>}
      {action && (
        <div onClick={action.onClick} style={{
          marginTop: 18, display: 'inline-flex', alignItems: 'center', gap: 6,
          padding: '10px 16px', borderRadius: 999, background: NF.ink, color: '#fff',
          fontFamily: FONT_SANS, fontWeight: 600, fontSize: 13.5, cursor: 'pointer',
        }}>
          {action.label}
          <Icon name="arrow-right" size={13} color="#fff"/>
        </div>
      )}
      <style>{`
        @keyframes rdrFloat {
          0%, 100% { transform: translateY(0); }
          50% { transform: translateY(-4px); }
        }
        @keyframes rdrSpin { to { transform: rotate(360deg); } }
        .rdr-ring { animation: rdrSpin 16s linear infinite; }
        .rdr-ring2 { animation: rdrSpin 10s linear reverse infinite; }
      `}</style>
    </div>
  );
}

// --------- AdCard — sponsored content card with 3 visual styles ----------
// Styles: 'subtle' (thin lime accent strip), 'card' (lime outline w/ CTA),
// 'banner' (compact one-row banner). All labeled "Реклама" up top.
function AdCard({ ad, style = 'subtle', onHide, onClick }) {
  // Shared "Реклама · BRAND" label + close button row
  const Label = () => (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      gap: 10,
    }}>
      <div style={{
        display: 'inline-flex', alignItems: 'center', gap: 8,
      }}>
        <div style={{
          width: 5, height: 5, borderRadius: 999, background: NF.lime,
        }}/>
        <Mono style={{ color: NF.ink2 }}>РЕКЛАМА · {ad.brand.toUpperCase()}</Mono>
      </div>
      <div onClick={(e) => { e.stopPropagation(); onHide?.(); }} style={{
        width: 28, height: 28, borderRadius: 999,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        cursor: 'pointer', opacity: 0.55,
      }}>
        <Icon name="close" size={14} color={NF.ink}/>
      </div>
    </div>
  );

  // ─────────────────────── SUBTLE ───────────────────────
  // Regular-looking card with a thin lime accent strip on the left edge.
  if (style === 'subtle') {
    return (
      <div onClick={onClick} style={{
        position: 'relative',
        background: NF.surface, borderRadius: NF.radiusLg,
        border: `1px solid ${NF.hairline}`, overflow: 'hidden',
        padding: '14px 14px 12px 18px', cursor: 'pointer',
      }}>
        {/* left accent strip */}
        <div style={{
          position: 'absolute', left: 0, top: 0, bottom: 0, width: 4,
          background: NF.lime,
        }}/>
        <Label/>
        <div style={{
          marginTop: 8, fontFamily: FONT_SANS, fontWeight: 700,
          fontSize: 18, letterSpacing: -0.4, color: NF.ink,
          lineHeight: 1.25,
        }}>{ad.title}</div>
        <div style={{
          marginTop: 6, fontFamily: FONT_SANS, fontSize: 13.5,
          color: NF.mute, lineHeight: 1.45,
        }}>{ad.tagline}</div>
      </div>
    );
  }

  // ─────────────────────── CARD (emphasized) ───────────────────────
  // Full card with hatched lime header band and an inline CTA button.
  if (style === 'card') {
    return (
      <div onClick={onClick} style={{
        background: NF.surface, borderRadius: NF.radiusLg,
        border: `1.5px solid ${NF.lime}`, overflow: 'hidden',
        cursor: 'pointer', position: 'relative',
      }}>
        {/* Hatched header strip — fills width, slight pattern */}
        <div style={{
          position: 'relative', height: 88, overflow: 'hidden',
          background: NF.lime,
        }}>
          <div style={{
            position: 'absolute', inset: 0,
            backgroundImage:
              `repeating-linear-gradient(135deg, transparent 0 12px, rgba(14,15,13,0.07) 12px 14px)`,
          }}/>
          <div style={{
            position: 'absolute', right: 14, top: 14,
            fontFamily: FONT_SANS, fontWeight: 800, fontSize: 34,
            letterSpacing: -1.2, color: NF.limeInk, lineHeight: 1,
            opacity: 0.85,
          }}>{ad.brand}</div>
        </div>
        <div style={{ padding: '12px 14px 14px' }}>
          <Label/>
          <div style={{
            marginTop: 8, fontFamily: FONT_SANS, fontWeight: 700,
            fontSize: 18, letterSpacing: -0.4, color: NF.ink,
            lineHeight: 1.25,
          }}>{ad.title}</div>
          <div style={{
            marginTop: 6, fontFamily: FONT_SANS, fontSize: 13.5,
            color: NF.mute, lineHeight: 1.45,
          }}>{ad.desc}</div>
          <div style={{
            marginTop: 12, display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: '10px 16px', borderRadius: 999, background: NF.ink, color: '#fff',
            fontFamily: FONT_SANS, fontWeight: 600, fontSize: 13.5,
          }}>
            {ad.cta}
            <Icon name="arrow-right" size={13} color="#fff"/>
          </div>
        </div>
      </div>
    );
  }

  // ─────────────────────── BANNER (compact) ───────────────────────
  // One-row horizontal banner: icon square + title/tagline + chevron.
  return (
    <div onClick={onClick} style={{
      background: NF.surface, borderRadius: NF.radiusLg,
      border: `1px solid ${NF.hairline}`,
      padding: '10px 12px', cursor: 'pointer',
      display: 'flex', alignItems: 'center', gap: 12,
      position: 'relative',
    }}>
      <div style={{
        width: 54, height: 54, borderRadius: 14, flexShrink: 0,
        background: NF.lime, position: 'relative', overflow: 'hidden',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <div style={{
          position: 'absolute', inset: 0,
          backgroundImage:
            `repeating-linear-gradient(135deg, transparent 0 8px, rgba(14,15,13,0.08) 8px 9px)`,
        }}/>
        <div style={{
          position: 'relative',
          fontFamily: FONT_SANS, fontWeight: 800, fontSize: 22,
          letterSpacing: -0.6, color: NF.limeInk,
        }}>{ad.brand[0]}</div>
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <Mono style={{ color: NF.ink2 }}>РЕКЛАМА · {ad.brand.toUpperCase()}</Mono>
        <div style={{
          marginTop: 3, fontFamily: FONT_SANS, fontWeight: 700,
          fontSize: 15, letterSpacing: -0.3, color: NF.ink,
          lineHeight: 1.3, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
        }}>{ad.title}</div>
        <div style={{
          marginTop: 2, fontFamily: FONT_SANS, fontSize: 12.5,
          color: NF.mute, lineHeight: 1.35,
          whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
        }}>{ad.tagline}</div>
      </div>
      <div onClick={(e) => { e.stopPropagation(); onHide?.(); }} style={{
        width: 26, height: 26, borderRadius: 999, flexShrink: 0,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        cursor: 'pointer', opacity: 0.5,
      }}>
        <Icon name="close" size={13} color={NF.ink}/>
      </div>
    </div>
  );
}

Object.assign(window, {
  ReactionBar, SourceLine, SingleImage, MultiImage, NoImage,
  ShortCard, LongCard, FeedHeader, IconBtn,
  CardMenu, AddToSpaceSheet, FeedSkeleton, EmptyState, AdCard,
});
