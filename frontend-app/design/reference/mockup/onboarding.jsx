// Onboarding — Радар (RU)

function WelcomeScreen({ onContinue, onSignup, onGuest }) {
  const [email, setEmail] = useState('');
  const [pw, setPw] = useState('');
  return (
    <div style={{
      background: NF.bg, minHeight: '100%', position: 'relative', overflow: 'hidden',
      padding: '64px 22px 30px', display: 'flex', flexDirection: 'column',
    }}>
      <div style={{
        position: 'absolute', top: -80, right: -80, width: 260, height: 260,
        borderRadius: 999, background: NF.lime, opacity: 0.45,
      }}/>
      <div style={{
        position: 'absolute', top: 40, left: -60, width: 180, height: 180,
        borderRadius: 999, border: `1px solid ${NF.ink}`,
      }}/>
      <div style={{
        position: 'absolute', top: 160, right: 40, width: 40, height: 40,
        background: NF.accent, transform: 'rotate(45deg)',
      }}/>

      <div style={{ position: 'relative', zIndex: 2, display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{
          width: 32, height: 32, borderRadius: 9, background: NF.ink,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          position: 'relative', overflow: 'hidden',
        }}>
          <div style={{ position: 'absolute', inset: 4, borderRadius: 999, border: `1.2px solid ${NF.lime}` }}/>
          <div style={{ width: 3, height: 3, borderRadius: 3, background: NF.lime }}/>
        </div>
        <div style={{
          fontFamily: FONT_SANS, fontWeight: 700, fontSize: 20,
          letterSpacing: -0.8, color: NF.ink,
        }}>Радар</div>
      </div>

      <div style={{ flex: 1 }}/>

      <div style={{ position: 'relative', zIndex: 2, marginTop: 100 }}>
        <Mono>ГЛАВА 001</Mono>
        <div style={{
          marginTop: 8, fontSize: 42, lineHeight: 0.98, letterSpacing: -1.6,
          fontWeight: 700, color: NF.ink, fontFamily: FONT_SANS,
        }}>Находите<br/>лучшее<br/>из всего.</div>
        <div style={{
          marginTop: 12, fontSize: 15, color: NF.mute, lineHeight: 1.5, maxWidth: 300,
          fontFamily: FONT_SANS,
        }}>Одна тихая лента для Telegram, Хабра и VC — без дофаминовых уловок.</div>
      </div>

      <div style={{ position: 'relative', zIndex: 2, marginTop: 28, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <Input icon="user" placeholder="email@домен.ru" value={email} onChange={setEmail}/>
        <Input icon="gear" placeholder="пароль" type="password" value={pw} onChange={setPw}/>

        <div onClick={onContinue} style={{
          marginTop: 10, padding: '16px 18px', borderRadius: 999, background: NF.accent,
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, cursor: 'pointer',
        }}>
          <div style={{ fontFamily: FONT_SANS, fontWeight: 700, fontSize: 15, color: '#fff' }}>Войти</div>
          <Icon name="arrow-up-right" size={15} color="#fff"/>
        </div>
        <div onClick={onSignup} style={{
          padding: '15px 18px', borderRadius: 999,
          border: `1.5px solid ${NF.ink}`, background: 'transparent',
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
        }}>
          <div style={{ fontFamily: FONT_SANS, fontWeight: 700, fontSize: 15, color: NF.ink }}>Зарегистрироваться — бесплатно</div>
        </div>
        <div onClick={onGuest} style={{ padding: '8px 0 4px', textAlign: 'center', cursor: 'pointer' }}>
          <Mono style={{ color: NF.ink2 }}>◦ ПРОДОЛЖИТЬ КАК ГОСТЬ ◦</Mono>
        </div>
      </div>
    </div>
  );
}

function Input({ icon, placeholder, type = 'text', value, onChange }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10,
      padding: '14px 16px', borderRadius: 14, background: NF.surface,
      border: `1px solid ${NF.hairline}`,
    }}>
      <Icon name={icon} size={17} color={NF.mute}/>
      <input type={type} placeholder={placeholder} value={value}
        onChange={e => onChange(e.target.value)}
        style={{
          flex: 1, border: 'none', outline: 'none', background: 'transparent',
          fontFamily: FONT_SANS, fontSize: 15, color: NF.ink,
        }}/>
    </div>
  );
}

function OnboardingTopics({ step, totalSteps, selected, setSelected, onNext, onBack }) {
  const MIN_TOPICS = 3;
  const MAX_TOPICS = 5;
  const toggle = (t) => setSelected(s => {
    if (s.includes(t)) return s.filter(x => x !== t);
    if (s.length >= MAX_TOPICS) return s; // cap selection
    return [...s, t];
  });
  const canContinue = selected.length >= MIN_TOPICS && selected.length <= MAX_TOPICS;
  const topics = [
    { t: 'Технологии',    e: '💻', c: NF.accent, ink: '#fff' },
    { t: 'Игры',          e: '🎮', c: '#FFE4A8', ink: NF.ink },
    { t: 'Кино и сериалы',e: '🎬', c: '#F6C3D1', ink: NF.ink },
    { t: 'Лонгриды',      e: '📚', c: '#C9E3FF', ink: NF.ink },
    { t: 'ИИ',            e: '🤖', c: NF.lime, ink: NF.limeInk },
    { t: 'Бизнес',        e: '💼', c: '#E0D5FF', ink: NF.ink },
    { t: 'Дизайн',        e: '🎨', c: '#FFD2B8', ink: NF.ink },
    { t: 'Климат',        e: '🌱', c: '#CFF2D8', ink: NF.ink },
    { t: 'Мемы',          e: '😂', c: '#FFE08C', ink: NF.ink },
    { t: 'Наука',         e: '🔬', c: '#C4ECE7', ink: NF.ink },
    { t: 'Фото',          e: '📸', c: NF.ink, ink: '#fff' },
    { t: 'Финансы',       e: '💶', c: '#D7F0A3', ink: NF.ink },
    { t: 'Музыка',        e: '🎧', c: '#E9D5FF', ink: NF.ink },
    { t: 'Спорт',         e: '🏃', c: '#FFC9C2', ink: NF.ink },
  ];
  return (
    <div style={{ background: NF.bg, minHeight: '100%', padding: '62px 20px 140px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div onClick={onBack} style={{
          width: 36, height: 36, borderRadius: 999,
          border: `1px solid ${NF.hairline}`, background: NF.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
        }}>
          <Icon name="back" size={17}/>
        </div>
        <div style={{ display: 'flex', gap: 4, flex: 1 }}>
          {Array.from({ length: totalSteps }).map((_, i) => (
            <div key={i} style={{
              flex: 1, height: 3, borderRadius: 3,
              background: i < step ? NF.ink : NF.surface2,
            }}/>
          ))}
        </div>
        <Mono>{step} / {totalSteps}</Mono>
      </div>

      <div style={{ marginTop: 26 }}>
        <Mono>ПЕРВИЧНАЯ НАСТРОЙКА</Mono>
        <div style={{
          marginTop: 6, fontSize: 36, lineHeight: 1, letterSpacing: -1.3,
          fontWeight: 700, color: NF.ink, fontFamily: FONT_SANS,
        }}>Что вам<br/>интересно?</div>
        <div style={{
          marginTop: 8, fontSize: 14.5, color: NF.mute, lineHeight: 1.5, fontFamily: FONT_SANS,
        }}>Выберите несколько тем — лента подстроится.</div>
      </div>

      <div style={{
        marginTop: 20, padding: '8px 14px', borderRadius: 999, background: NF.ink,
        display: 'inline-flex', alignItems: 'center', gap: 6,
      }}>
        <div style={{ width: 6, height: 6, borderRadius: 6, background: canContinue ? NF.lime : '#FFC9C2' }}/>
        <Mono style={{ color: canContinue ? NF.lime : '#FFC9C2' }}>
          {selected.length} / {MAX_TOPICS} · МИН. {MIN_TOPICS}
        </Mono>
      </div>

      <div style={{ marginTop: 18, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {topics.map(({ t, e, c, ink }) => {
          const on = selected.includes(t);
          const atCap = !on && selected.length >= MAX_TOPICS;
          return (
            <div key={t} onClick={() => toggle(t)} style={{
              padding: '10px 14px', borderRadius: 999,
              background: on ? c : NF.surface,
              color: on ? ink : NF.ink2,
              border: on ? `1.5px solid ${NF.ink}` : `1px solid ${NF.hairline}`,
              fontFamily: FONT_SANS, fontSize: 14.5, fontWeight: 500,
              cursor: atCap ? 'not-allowed' : 'pointer',
              opacity: atCap ? 0.45 : 1,
              display: 'flex', alignItems: 'center', gap: 6,
              boxShadow: on ? '0 6px 14px -8px rgba(0,0,0,0.25)' : 'none',
              transition: 'all 160ms',
            }}>
              <span style={{ fontSize: 15 }}>{e}</span>
              <span>{t}</span>
              {on && <Icon name="check" size={13} color={ink}/>}
            </div>
          );
        })}
      </div>

      <div style={{ position: 'absolute', left: 16, right: 16, bottom: 22, zIndex: 10 }}>
        <div onClick={canContinue ? onNext : undefined} style={{
          padding: '16px 18px', borderRadius: 999,
          background: canContinue ? NF.ink : NF.surface2,
          color: canContinue ? '#fff' : NF.mute,
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          cursor: canContinue ? 'pointer' : 'not-allowed',
          boxShadow: canContinue ? '0 10px 24px -10px rgba(0,0,0,0.35)' : 'none',
        }}>
          <div style={{ fontFamily: FONT_SANS, fontWeight: 700, fontSize: 15 }}>
            {canContinue ? 'Продолжить' : `Выберите ещё ${Math.max(0, MIN_TOPICS - selected.length)}`}
          </div>
          {canContinue && <Icon name="arrow-up-right" size={15} color="#fff"/>}
        </div>
        <div style={{ padding: '10px 0', textAlign: 'center' }}>
          <Mono style={{ color: NF.mute }}>ОТ {MIN_TOPICS} ДО {MAX_TOPICS} ТЕМ</Mono>
        </div>
      </div>
    </div>
  );
}

function OnboardingPreview({ step, totalSteps, selected, onFinish, onBack }) {
  const preview = FEED.slice(0, 2);
  return (
    <div style={{ background: NF.bg, minHeight: '100%', padding: '62px 20px 140px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div onClick={onBack} style={{
          width: 36, height: 36, borderRadius: 999,
          border: `1px solid ${NF.hairline}`, background: NF.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
        }}>
          <Icon name="back" size={17}/>
        </div>
        <div style={{ display: 'flex', gap: 4, flex: 1 }}>
          {Array.from({ length: totalSteps }).map((_, i) => (
            <div key={i} style={{
              flex: 1, height: 3, borderRadius: 3,
              background: i < step ? NF.ink : NF.surface2,
            }}/>
          ))}
        </div>
        <Mono>{step} / {totalSteps}</Mono>
      </div>

      <div style={{ marginTop: 26 }}>
        <Mono style={{ color: NF.accent }}>ПРЕДПРОСМОТР · {selected.length} ТЕМ</Mono>
        <div style={{
          marginTop: 6, fontSize: 36, lineHeight: 1, letterSpacing: -1.3,
          fontWeight: 700, color: NF.ink, fontFamily: FONT_SANS,
        }}>Так будет<br/>выглядеть<br/>ваша лента.</div>
      </div>

      <div style={{ marginTop: 20, display: 'flex', flexDirection: 'column', gap: 12 }}>
        {preview.map(item => (
          <div key={item.id} style={{
            background: NF.surface, borderRadius: NF.radiusLg, overflow: 'hidden',
            border: `1px solid ${NF.hairline}`, padding: 12,
          }}>
            <div style={{ display: 'flex', gap: 12 }}>
              <div style={{ width: 80, flexShrink: 0 }}>
                <Stripe height={80} tone={item.tone} seed={item.seed} label="" radius={12}/>
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <Mono style={{ color: NF.ink }}>{item.source}</Mono>
                <div style={{
                  marginTop: 4, fontFamily: FONT_SANS,
                  fontWeight: 600, fontSize: 15, lineHeight: 1.2, color: NF.ink,
                  display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden',
                }}>{item.title}</div>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div style={{ position: 'absolute', left: 16, right: 16, bottom: 22, zIndex: 10 }}>
        <div onClick={onFinish} style={{
          padding: '16px 18px', borderRadius: 999, background: NF.lime,
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, cursor: 'pointer',
          boxShadow: '0 10px 24px -10px rgba(204,255,51,0.6)',
        }}>
          <div style={{ fontFamily: FONT_SANS, fontWeight: 700, fontSize: 15, color: NF.limeInk }}>В ленту</div>
          <Icon name="arrow-up-right" size={15} color={NF.limeInk}/>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { WelcomeScreen, OnboardingTopics, OnboardingPreview });
