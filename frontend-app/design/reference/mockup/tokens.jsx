// Радар — neo-futurism light tokens
const NF = {
  bg: '#F4F5F2',
  surface: '#FFFFFF',
  surface2: '#ECEDE8',
  hairline: 'rgba(17,17,17,0.09)',
  ink: '#0E0F0D',
  ink2: '#2A2B28',
  mute: '#6E6F6A',
  mute2: '#9A9B96',
  accent: '#3B2BFF',
  accentInk: '#FFFFFF',
  lime: '#CCFF33',
  limeInk: '#0E0F0D',
  warn: '#FF5A1F',
  chipBg: '#E8E9E3',
  radius: 18,
  radiusLg: 26,
  radiusSm: 10,
};

// Single font family: Nunito (rounded humanist sans) for the entire app.
// "Mono"-styled labels reuse Nunito with uppercase + letter-spacing instead
// of a real monospaced face, so the whole UI reads as one cohesive voice.
const FONT_SANS = '"Nunito", "Inter", -apple-system, system-ui, sans-serif';
const FONT_MONO = '"Nunito", "Inter", -apple-system, system-ui, sans-serif';

const Mono = ({ children, style = {}, ...rest }) => (
  <span style={{
    fontFamily: FONT_MONO,
    fontSize: 10, letterSpacing: 0.8, textTransform: 'uppercase',
    color: NF.mute, ...style,
  }} {...rest}>{children}</span>
);

// Unified image placeholder — single consistent rendering used across cards + detail.
// `label` defaults to a neutral token so all cards look visually consistent.
const Stripe = ({ height = 200, label = 'ФОТО', seed = 0, tone = 'ink', radius = 16, children }) => {
  const hues = {
    ink:    ['#1C1D1B', '#2A2B28'],
    accent: ['#3B2BFF', '#2218C7'],
    lime:   ['#CCFF33', '#B7E620'],
    light:  ['#E8E9E3', '#DADBD4'],
    warn:   ['#FF5A1F', '#E34B13'],
    violet: ['#7A4BFF', '#5A2BDB'],
    teal:   ['#1EC6B6', '#0F9E90'],
    rose:   ['#FF7A8A', '#E0566A'],
  };
  const pair = hues[tone] ?? hues.ink;
  return (
    <div style={{
      position: 'relative', width: '100%', height, borderRadius: radius, overflow: 'hidden',
      background: `repeating-linear-gradient(135deg, ${pair[0]} 0 14px, ${pair[1]} 14px 28px)`,
    }}>
      <div style={{
        position: 'absolute', inset: 0,
        background: 'radial-gradient(120% 80% at 10% 0%, rgba(255,255,255,0.14), rgba(255,255,255,0) 60%)',
      }}/>
      {label && <div style={{
        position: 'absolute', left: 10, top: 10,
        fontFamily: FONT_MONO,
        fontSize: 9, letterSpacing: 1.2, color: 'rgba(255,255,255,0.85)',
        padding: '3px 7px', border: '1px solid rgba(255,255,255,0.35)', borderRadius: 4,
        textTransform: 'uppercase',
      }}>{label}</div>}
      <div style={{
        position: 'absolute', right: 10, bottom: 10,
        fontFamily: FONT_MONO,
        fontSize: 9, letterSpacing: 1.2, color: 'rgba(255,255,255,0.7)',
      }}>#{String(seed).padStart(3, '0')}</div>
      {children}
    </div>
  );
};

const Icon = ({ name, size = 22, color = NF.ink, fill = 'none' }) => {
  const s = { width: size, height: size, display: 'block' };
  const p = { stroke: color, strokeWidth: 1.6, fill, strokeLinecap: 'round', strokeLinejoin: 'round' };
  switch (name) {
    case 'search': return <svg viewBox="0 0 24 24" style={s}><circle cx="11" cy="11" r="7" {...p}/><path d="M20 20l-3.5-3.5" {...p}/></svg>;
    case 'bell': return <svg viewBox="0 0 24 24" style={s}><path d="M6 17h12l-1.5-2V11a4.5 4.5 0 10-9 0v4L6 17z" {...p}/><path d="M10.5 20a1.5 1.5 0 003 0" {...p}/></svg>;
    case 'thumb-up': return <svg viewBox="0 0 24 24" style={s}><path d="M7 10v10H4V10h3zm0 0l4-7 1 1v5h6a2 2 0 012 2l-2 7a2 2 0 01-2 1.5H7" {...p}/></svg>;
    case 'thumb-down': return <svg viewBox="0 0 24 24" style={s}><path d="M17 14V4h3v10h-3zm0 0l-4 7-1-1v-5H6a2 2 0 01-2-2l2-7A2 2 0 018 4.5h9" {...p}/></svg>;
    case 'bookmark': return <svg viewBox="0 0 24 24" style={s}><path d="M6 4h12v17l-6-4-6 4V4z" {...p}/></svg>;
    case 'feed': return <svg viewBox="0 0 24 24" style={s}><path d="M4 6h16M4 12h16M4 18h10" {...p}/></svg>;
    case 'layers': return <svg viewBox="0 0 24 24" style={s}><path d="M12 3l9 5-9 5-9-5 9-5z" {...p}/><path d="M3 13l9 5 9-5M3 17l9 5 9-5" {...p}/></svg>;
    case 'user': return <svg viewBox="0 0 24 24" style={s}><circle cx="12" cy="8" r="4" {...p}/><path d="M4 21a8 8 0 0116 0" {...p}/></svg>;
    case 'gear': return <svg viewBox="0 0 24 24" style={s}><path d="M19.14 12.94c.04-.3.06-.62.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.488.488 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94 0 .31.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 018.4 12c0-1.98 1.62-3.6 3.6-3.6s3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z" {...p}/></svg>;
    case 'back': return <svg viewBox="0 0 24 24" style={s}><path d="M15 5l-7 7 7 7" {...p}/></svg>;
    case 'plus': return <svg viewBox="0 0 24 24" style={s}><path d="M12 5v14M5 12h14" {...p}/></svg>;
    case 'chevron': return <svg viewBox="0 0 24 24" style={s}><path d="M9 6l6 6-6 6" {...p}/></svg>;
    case 'arrow-up-right': return <svg viewBox="0 0 24 24" style={s}><path d="M7 17L17 7M9 7h8v8" {...p}/></svg>;
    case 'arrow-right': return <svg viewBox="0 0 24 24" style={s}><path d="M5 12h14M13 6l6 6-6 6" {...p}/></svg>;
    case 'external': return <svg viewBox="0 0 24 24" style={s}><path d="M14 4h6v6M20 4L10 14M18 14v5a1 1 0 01-1 1H5a1 1 0 01-1-1V7a1 1 0 011-1h5" {...p}/></svg>;
    case 'spark': return <svg viewBox="0 0 24 24" style={s}><path d="M12 3l2 7 7 2-7 2-2 7-2-7-7-2 7-2 2-7z" {...p}/></svg>;
    case 'close': return <svg viewBox="0 0 24 24" style={s}><path d="M6 6l12 12M18 6L6 18" {...p}/></svg>;
    case 'check': return <svg viewBox="0 0 24 24" style={s}><path d="M5 12l4 4 10-10" {...p}/></svg>;
    case 'filter': return <svg viewBox="0 0 24 24" style={s}><path d="M3 5h18M6 12h12M10 19h4" {...p}/></svg>;
    case 'grid': return <svg viewBox="0 0 24 24" style={s}><rect x="3" y="3" width="7" height="7" {...p}/><rect x="14" y="3" width="7" height="7" {...p}/><rect x="3" y="14" width="7" height="7" {...p}/><rect x="14" y="14" width="7" height="7" {...p}/></svg>;
    case 'trash': return <svg viewBox="0 0 24 24" style={s}><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6" {...p}/></svg>;
    case 'link': return <svg viewBox="0 0 24 24" style={s}><path d="M10 14a4 4 0 005.66 0l3-3a4 4 0 00-5.66-5.66l-1 1M14 10a4 4 0 00-5.66 0l-3 3a4 4 0 005.66 5.66l1-1" {...p}/></svg>;
    case 'radar': return <svg viewBox="0 0 24 24" style={s}><circle cx="12" cy="12" r="9" {...p}/><circle cx="12" cy="12" r="5" {...p}/><path d="M12 12l6-4" {...p}/><circle cx="12" cy="12" r="1.5" fill={color} stroke="none"/></svg>;
    case 'star': return <svg viewBox="0 0 24 24" style={s}><path d="M12 3l2.8 6 6.2.8-4.5 4.4 1.1 6.1L12 17.5 6.4 20.3l1.1-6.1L3 9.8 9.2 9 12 3z" {...p}/></svg>;
    case 'more': return <svg viewBox="0 0 24 24" style={s}><circle cx="5" cy="12" r="1.6" fill={color} stroke="none"/><circle cx="12" cy="12" r="1.6" fill={color} stroke="none"/><circle cx="19" cy="12" r="1.6" fill={color} stroke="none"/></svg>;
    case 'eye-off': return <svg viewBox="0 0 24 24" style={s}><path d="M3 3l18 18M10.6 10.6a2 2 0 002.8 2.8M9.9 5.1A10 10 0 0112 5c5 0 9 4 10 7-0.6 1.6-1.8 3.2-3.3 4.4M6.3 6.3C4.1 7.7 2.7 9.7 2 12c1 3 5 7 10 7 1.9 0 3.6-.6 5.1-1.4" {...p}/></svg>;
    case 'folder-plus': return <svg viewBox="0 0 24 24" style={s}><path d="M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V7z" {...p}/><path d="M12 11v6M9 14h6" {...p}/></svg>;
    default: return null;
  }
};

Object.assign(window, { NF, Mono, Stripe, Icon, FONT_SANS, FONT_MONO });
