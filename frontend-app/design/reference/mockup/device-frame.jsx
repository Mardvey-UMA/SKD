// DeviceFrame — minimal wrapper that can render our UI inside either an
// iPhone (IOSDevice) or a light Android shell (Pixel / Galaxy).
//
// Keeps our app's own top-bar and bottom-nav intact — the frame only
// provides the bezel, status bar and gesture / home indicator.

function AndroidStatus({ time = '9:41' }) {
  return (
    <div style={{
      position: 'absolute', top: 0, left: 0, right: 0, zIndex: 10,
      height: 32, display: 'flex', alignItems: 'center',
      justifyContent: 'space-between', padding: '0 22px',
      fontFamily: 'Roboto, system-ui, sans-serif',
      color: '#0E0F0D',
    }}>
      <span style={{ fontSize: 14, fontWeight: 500, letterSpacing: 0.1 }}>{time}</span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
        {/* signal bars */}
        <svg width="16" height="12" viewBox="0 0 16 12">
          <rect x="0"  y="8" width="3" height="4"  rx="0.6" fill="currentColor"/>
          <rect x="4.5" y="5" width="3" height="7"  rx="0.6" fill="currentColor"/>
          <rect x="9"  y="2" width="3" height="10" rx="0.6" fill="currentColor"/>
        </svg>
        {/* wifi */}
        <svg width="14" height="12" viewBox="0 0 14 12">
          <path d="M7 3c2 0 3.8 0.8 5.1 2.1l0.9-0.9C11.5 2.6 9.4 1.6 7 1.6 4.6 1.6 2.5 2.6 1 4.2l0.9 0.9C3.2 3.8 5 3 7 3z" fill="currentColor"/>
          <path d="M7 6c1.2 0 2.2 0.5 3 1.2l0.9-0.9C9.8 5.3 8.5 4.7 7 4.7c-1.5 0-2.8 0.6-3.9 1.6l0.9 0.9C4.8 6.5 5.8 6 7 6z" fill="currentColor"/>
          <circle cx="7" cy="9.5" r="1.3" fill="currentColor"/>
        </svg>
        {/* battery */}
        <svg width="22" height="11" viewBox="0 0 22 11">
          <rect x="0.5" y="0.5" width="19" height="10" rx="2.5" fill="none" stroke="currentColor" strokeOpacity="0.45"/>
          <rect x="2"   y="2"   width="16" height="7"  rx="1.4" fill="currentColor"/>
          <rect x="20"  y="3.5" width="1.5" height="4" rx="0.7" fill="currentColor" fillOpacity="0.45"/>
        </svg>
      </div>
    </div>
  );
}

function AndroidGestureBar({ dark = false }) {
  return (
    <div style={{
      position: 'absolute', bottom: 0, left: 0, right: 0, zIndex: 60,
      height: 22, display: 'flex', justifyContent: 'center', alignItems: 'flex-end',
      paddingBottom: 7, pointerEvents: 'none',
    }}>
      <div style={{
        width: 120, height: 4, borderRadius: 100,
        background: dark ? 'rgba(255,255,255,0.75)' : 'rgba(14,15,13,0.55)',
      }}/>
    </div>
  );
}

// Pixel — prouder corner radius, punch-hole camera centered, narrow bezels.
function PixelDevice({ children, width = 402, height = 874 }) {
  return (
    <div style={{
      width, height, borderRadius: 44, overflow: 'hidden', position: 'relative',
      background: '#F7F7F4',
      boxShadow: '0 40px 80px rgba(0,0,0,0.18), 0 0 0 1.5px rgba(0,0,0,0.18)',
      fontFamily: 'Roboto, -apple-system, system-ui, sans-serif',
      WebkitFontSmoothing: 'antialiased',
    }}>
      {/* punch-hole camera (center) */}
      <div style={{
        position: 'absolute', top: 10, left: '50%', transform: 'translateX(-50%)',
        width: 12, height: 12, borderRadius: '50%', background: '#111', zIndex: 50,
      }}/>
      <AndroidStatus/>
      <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column' }}>
        <div style={{ height: 32, flexShrink: 0 }}/>
        <div style={{ flex: 1, overflow: 'auto' }}>{children}</div>
      </div>
      <AndroidGestureBar/>
    </div>
  );
}

// Galaxy — larger corner radius, camera punch-hole slightly offset? center is fine.
// Slightly warmer-white shell, thicker bezel border.
function GalaxyDevice({ children, width = 402, height = 874 }) {
  return (
    <div style={{
      width, height, borderRadius: 52, overflow: 'hidden', position: 'relative',
      background: '#F2F3F5',
      boxShadow: '0 40px 80px rgba(0,0,0,0.2), 0 0 0 2px #111',
      fontFamily: 'Roboto, -apple-system, system-ui, sans-serif',
      WebkitFontSmoothing: 'antialiased',
    }}>
      {/* punch-hole camera (center, slightly smaller + higher) */}
      <div style={{
        position: 'absolute', top: 9, left: '50%', transform: 'translateX(-50%)',
        width: 10, height: 10, borderRadius: '50%', background: '#111', zIndex: 50,
      }}/>
      <AndroidStatus/>
      <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column' }}>
        <div style={{ height: 32, flexShrink: 0 }}/>
        <div style={{ flex: 1, overflow: 'auto' }}>{children}</div>
      </div>
      <AndroidGestureBar/>
    </div>
  );
}

// Unified wrapper — picks device based on prop.
function DeviceFrame({ device = 'iphone', children }) {
  if (device === 'pixel')  return <PixelDevice>{children}</PixelDevice>;
  if (device === 'galaxy') return <GalaxyDevice>{children}</GalaxyDevice>;
  return <IOSDevice>{children}</IOSDevice>;
}

Object.assign(window, { DeviceFrame, PixelDevice, GalaxyDevice });
