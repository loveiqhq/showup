// screen-location-gate-reference.jsx — Search 04 · Location gate
// Reference implementation that renders the attached spec sheet. Read values
// from here rather than measuring the PNG.
//
// The gate is NOT a new screen: it is Profile 12's ScreenProfileLocation in
// context="app". Profile 12 is built — extend that component with the app
// context; do not fork it. Part 1 below is the shared file (both contexts);
// part 2 is the thin ScreenLocationGate wrapper.
//
// States: <ScreenLocationGate state="ask"/> (A) · state="denied" (B) ·
// state="services-off" (C) · state="denied" tab="likes" onBack (D).
// Depends on shared.jsx (Phone, StatusBar, HomeIndicator, Icon, Button,
// TabBar) and Search 01's SearchTopBar (filter / chip / premium props).

// ═════════════ part 1 · screens-profile-location.jsx ═════════════
// screens-profile-location.jsx — Location permission ask
//
// Sits inside Profile creation, alongside the Notifications permission
// screen. Show Up's whole mechanic depends on location: profiles are
// only useful if they're meetable, and "meetable" means within a sane
// halfway-point of where the user actually lives or works. Without
// location we can't search, can't pick a venue, can't route the user
// to the date.
//
// ─────────────────────────────────────────────────────────────
// Visual orientation
// ─────────────────────────────────────────────────────────────
// Same chrome as ScreenProfileNotifications:
//   - warm-paper canvas
//   - orange orb top-right, violet orb bottom-left, peach top wash
//   - dark Lora headline with single italic-underline flourish
//   - Manrope lead body
//   - benefit rows (icon pip + title + line) using the same vocabulary
//   - sunset CTA full-width pinned to the footer
//
// What's different: a small "radar" hero sits above the headline. A
// stack of concentric rings centred on an orange map-pin, with three
// hollow dots scattered on the rings to suggest "people nearby". Pure
// geometry — circles, rings, a stroke-only map-pin glyph — so it stays
// within brand rules (no hand-drawn imagery). The pulse animation is
// declared statically here; the final product can swap to a CSS
// keyframed reveal without changing the JSX.
//
// Voice: one italicized word per headline ("nearby"), the body uses
// the Show Up "30-minute halfway-point" vocabulary directly so the
// user understands *why* this isn't just a generic location ask.
//
// 22 Oct 2026: this file is now the ONE location screen. `context="app"`
// is the Search / Instant location gate (was screens-search-location.jsx).
// See ScreenProfileLocation below for contexts × states.
//
// Variants exposed via `state`:
//   - 'default'     — pre-grant, primary CTA "Allow location access"
//   - 'requesting'  — the iOS system sheet is up (we dim the screen
//                      and show a faint sheet at the bottom)
//   - 'denied'      — the user tapped "Don't Allow" on the system
//                      sheet; we explain the consequence and offer
//                      "Open Settings" as the recovery path
//
// Exposed on window:
//   - ScreenProfileLocation       — main screen
//   - LocationBenefitRow          — primitive row (re-export of the
//                                    notifications row, kept under a
//                                    local name for readability)
//   - LOCATION_BENEFITS           — the three reasons
//   - LocationRadarHero           — the concentric-rings hero

const { useState: useStateLocation } = React;

// ─────────────────────────────────────────────────────────────
// LOCATION_BENEFITS — the three reasons we need location.
// Three rows, not six: location has fewer distinct uses than
// notifications, and a tight list reads more confidently than a
// padded one. Order: discovery → meet point → on-the-day routing.
// ─────────────────────────────────────────────────────────────
const LOCATION_BENEFITS = [
  {
    icon: 'compass',
    title: 'Set a search radius around your location',
  },
  {
    icon: 'map-pin',
    title: 'We pick a fair halfway venue for you both',
    sub: 'No planning, no home advantage \u2014 a busy public spot that\u2019s neutral ground for both of you.',
  },
  {
    icon: 'navigation',
    title: 'Walking directions on the day',
  },
];

// ─────────────────────────────────────────────────────────────
// LocationBenefitRow — icon pip + title + line
//
// Same recipe as NotifyBenefitRow in screens-profile-notifications.jsx.
// Kept as a separate component (rather than re-importing) so this file
// reads end-to-end and the row chrome can drift independently if the
// location screen ever needs a different visual register.
// ─────────────────────────────────────────────────────────────
function LocationBenefitRow({ icon, title, sub }) {
  return (
    <div style={{
      display: 'flex', gap: 14, alignItems: 'center',
    }}>
      <div style={{
        flex: 'none',
        width: 40, height: 40, borderRadius: 12,
        background: 'var(--su-grad-lilac)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--liq-primary-500)',
      }}>
        <Icon name={icon} size={20} stroke={1.8}/>
      </div>
      <div style={{
        flex: 1, minWidth: 0,
        fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 17,
        color: 'var(--liq-fg)', lineHeight: 1.25,
        letterSpacing: '-0.005em',
        textWrap: 'balance',
      }}>
        {title}
        {sub && (
          <div style={{
            marginTop: 3,
            fontFamily: 'var(--liq-font-sans)', fontWeight: 400, fontSize: 13.5,
            lineHeight: 1.35, letterSpacing: 0,
            color: 'rgba(0,0,0,0.62)', textWrap: 'pretty',
          }}>{sub}</div>
        )}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// LocationRadarHero — the ONE location radar (22 Oct 2026: merged with the
// gate's LocationGateRadar so the two can't drift).
//   live=true   violet rings, nearby dots, sunset pin + halo  (ask)
//   live=false  slate rings, no dots, grey pin + slash          (denied / off)
// Drawn on Profile 12's 240×160 viewBox; `height` scales it.
// ─────────────────────────────────────────────────────────────
function LocationRadarHero({ live = true, height = 160 }) {
  // Geometry is Profile 12's, as built (240×160). The gate reuses it and
  // only adds the muted live=false variant.
  const ring = live ? ['rgba(129,42,236,0.45)', 'rgba(129,42,236,0.30)', 'rgba(129,42,236,0.18)']
                    : ['rgba(60,50,70,0.22)', 'rgba(60,50,70,0.14)', 'rgba(60,50,70,0.08)'];
  return (
    <div style={{ position: 'relative', width: '100%', height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <svg width={height * 1.5} height={height} viewBox="0 0 240 160" fill="none" style={{ overflow: 'visible' }}>
        <defs>
          <linearGradient id="su-loc-pin" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#FE6839"/>
            <stop offset="100%" stopColor="#D05976"/>
          </linearGradient>
        </defs>
        <circle cx="120" cy="80" r="24" stroke={ring[0]} strokeWidth="1.25"/>
        <circle cx="120" cy="80" r="56" stroke={ring[1]} strokeWidth="1.25" strokeDasharray="2 4"/>
        <circle cx="120" cy="80" r="96" stroke={ring[2]} strokeWidth="1.25" strokeDasharray="2 4"/>
        {live && (
          <>
            <circle cx="148" cy="62"  r="4.5" fill="var(--liq-bg)" stroke="var(--liq-primary-500)" strokeWidth="1.5"/>
            <circle cx="74"  cy="108" r="4.5" fill="var(--liq-bg)" stroke="var(--liq-primary-500)" strokeWidth="1.5"/>
            <circle cx="198" cy="118" r="4.5" fill="var(--liq-bg)" stroke="var(--liq-primary-500)" strokeWidth="1.5"/>
            <circle cx="44"  cy="50"  r="3.5" fill="var(--liq-bg)" stroke="rgba(167,139,250,0.7)" strokeWidth="1.25"/>
          </>
        )}
        <g transform="translate(120, 80)">
          {live && <circle cx="0" cy="0" r="18" fill="rgba(254,104,57,0.18)"/>}
          <g transform="translate(-12, -16) scale(1.25)" stroke={live ? '#fff' : 'rgba(60,50,70,0.65)'} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 10c0 7-9 13-9 13S3 17 3 10a9 9 0 0 1 18 0z" fill={live ? 'url(#su-loc-pin)' : 'rgba(60,50,70,0.55)'}/>
            <circle cx="12" cy="10" r="3" fill={live ? '#fff' : 'rgba(245,243,240,0.85)'} stroke="none"/>
          </g>
          {!live && <line x1="-24" y1="18" x2="21" y2="-21" stroke="rgba(60,50,70,0.55)" strokeWidth="1.6" strokeLinecap="round"/>}
        </g>
      </svg>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// SystemSheetMock — a thin static rendition of the iOS "Allow
// location" sheet, only used in the `state="requesting"` variant.
// Not pixel-accurate Apple chrome (would be off-brand to reproduce
// it exactly); just enough silhouette to communicate "the system
// dialog is up" in the design review. The real product hands off
// to the OS via the geolocation API and never paints this surface.
// ─────────────────────────────────────────────────────────────
function SystemSheetMock() {
  return (
    <div style={{
      position: 'absolute', left: 16, right: 16, bottom: 18,
      borderRadius: 14, background: 'rgba(245, 243, 240, 0.96)',
      backdropFilter: 'blur(20px)',
      boxShadow: '0 18px 40px rgba(46, 1, 71, 0.18)',
      padding: '18px 20px 0',
      fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
      color: '#000', zIndex: 5,
    }}>
      <div style={{ textAlign: 'center', fontWeight: 600, fontSize: 16, lineHeight: 1.25 }}>
        Allow &ldquo;Show Up&rdquo; to use<br/>your location?
      </div>
      <div style={{
        marginTop: 8, textAlign: 'center',
        fontSize: 13, lineHeight: 1.35, color: 'rgba(0,0,0,0.7)',
      }}>
        We use your location to find people nearby and choose a
        halfway meeting spot for your date.
      </div>
      <div style={{
        marginTop: 14,
        borderTop: '0.5px solid rgba(0,0,0,0.12)',
      }}>
        {[
          'Allow Once',
          'Allow While Using App',
          'Don\u2019t Allow',
        ].map((label, i) => (
          <div key={label} style={{
            padding: '12px 0', textAlign: 'center',
            fontSize: 17, color: '#007AFF',
            fontWeight: i === 1 ? 600 : 400,
            borderTop: i === 0 ? 'none' : '0.5px solid rgba(0,0,0,0.12)',
          }}>
            {label}
          </div>
        ))}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// LOCATION_COPY — one table for both contexts × three states.
//
// Profile context: per-platform copy, as built (ticket 12).
// App context (the gate, 22 Oct 2026): NO platform-specific copy. The text
// never names a Settings path; it says "Open Settings" and the CTA routes:
//   denied        iOS → our app's page in Settings
//                 Android → our app's settings page (user taps Permissions › Location)
//   services-off  iOS → our app's page in Settings (iOS can't deep-link further)
//                 Android → the system Location page
// The states map to enums.json §26 location_unavailable_reason:
//   default = not_determined · denied = denied · services-off = services_off
// ─────────────────────────────────────────────────────────────
const LOCATION_PRIVACY = <>Your exact location is never shown on your profile. Other people only see the city you&rsquo;re in and an approximate distance from themselves e.g. 1.5km.</>;

const LOCATION_COPY = {
  // Profile 12 is BUILT with per-platform copy (ticket 12, 5 Oct 2026) —
  // kept as shipped. Functions of platform; only B and C differ.
  profile: {
    default: () => ({ headline: <>Find people <em>nearby</em>.</>, body: null, cta: 'Allow location access' }),
    denied: (p) => ({
      headline: <>We can&rsquo;t find dates <em>without</em> location.</>,
      body: p === 'android'
        ? <>Location access is off for Show Up. Without it we can&rsquo;t show you anyone nearby — every date happens in the real world. Turn it on in <strong>Permissions &rsaquo; Location</strong>, or finish your profile first and we&rsquo;ll ask again when you start searching.</>
        : <>Location access is off for Show Up. Without it we can&rsquo;t show you anyone nearby — every date happens in the real world. Turn it on in <strong>Settings &rsaquo; Show Up &rsaquo; Location</strong>, or finish your profile first and we&rsquo;ll ask again when you start searching.</>,
      cta: 'Open Settings',
    }),
    'services-off': (p) => ({
      headline: <>Your phone has location <em>switched off</em>.</>,
      body: p === 'android'
        ? <>Location is off for every app on this phone, not just Show Up. Turn it on in Settings now, or finish your profile first and we&rsquo;ll ask again when you start searching.</>
        : <>Location Services is off for every app on this phone, not just Show Up. Turn it on in <strong>Settings &rsaquo; Privacy &amp; Security &rsaquo; Location Services</strong>, or finish your profile first and we&rsquo;ll ask again when you start searching.</>,
      cta: 'Open Settings',
    }),
  },
  app: {
    default: {
      headline: <>Allow location to see people <em>nearby</em>.</>,
      body: <>We use your location to show you people within your distance, pick a fair halfway venue and guide you there on the day.</>,
      cta: 'Allow location access',
    },
    denied: {
      headline: <>Location is off for <em>Show Up</em>.</>,
      body: <>You turned off location access for Show Up, so we can&rsquo;t show you anyone nearby or plan a date. Open Settings and allow location &mdash; you&rsquo;ll pick up right where you left off.</>,
      cta: 'Open Settings',
    },
    'services-off': {
      headline: <>Location is off on your <em>phone</em>.</>,
      body: <>Location is switched off for every app on this phone, not just Show Up. Open Settings and turn it on &mdash; you&rsquo;ll pick up right where you left off.</>,
      cta: 'Open Settings',
    },
  },
};

function LocationBackdrop({ live = true }) {
  return (
    <>
      <div style={{ position: 'absolute', top: '-15%', right: '-25%', width: 520, height: 520, background: `radial-gradient(circle, rgba(254,104,57,${live ? 0.32 : 0.16}) 0%, rgba(254,104,57,0) 65%)`, filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none' }}/>
      <div style={{ position: 'absolute', bottom: '-20%', left: '-30%', width: 600, height: 600, background: `radial-gradient(circle, rgba(129,42,236,${live ? 0.28 : 0.14}) 0%, rgba(129,42,236,0) 65%)`, filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none' }}/>
      <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 360, background: `linear-gradient(180deg, rgba(255,229,210,${live ? 0.55 : 0.28}) 0%, rgba(255,251,247,0) 70%)`, zIndex: 0, pointerEvents: 'none' }}/>
    </>
  );
}

function LocationPrivacyNote() {
  return (
    <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start', padding: '12px 14px', borderRadius: 12, background: 'rgba(167,139,250,0.10)', textAlign: 'left' }}>
      <div style={{ flex: 'none', marginTop: 1, color: 'var(--liq-primary-500)' }}><Icon name="lock" size={16} stroke={1.8}/></div>
      <div style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 12.5, lineHeight: 1.45, color: 'var(--liq-neutral-200)', textWrap: 'pretty' }}>{LOCATION_PRIVACY}</div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileLocation — the ONE location screen, two contexts
//
// Props:
//   context     'profile' — Profile 12, inside sign-up (status bar only,
//                           continues to Embrace 2)
//               'app'     — the location gate on Search / Instant (Search
//                           top bar + TabBar; Likes and Settings stay
//                           reachable). Stays on Search once location is on.
//   state       'default' (never granted) | 'denied' (off for Show Up) |
//               'services-off' (off for the whole phone) |
//               'requesting' (profile only — do-not-build design mock)
//   tab         'search' | 'instant' | 'likes' — app context only, the active tab.
//               'likes' (22 Oct 2026): Confirm date on a profile opened from the
//               Likes tab, with location unavailable, pushes the gate over that
//               profile — Premium top bar without filter or chip, plus a back
//               pill (onBack / backLabel) that returns to the profile.
//   platform    'ios' | 'android' — profile context only (B / C copy)
//   onAllow     primary CTA on 'default' → raises the OS permission dialog
//   onSettings  primary CTA on 'denied' / 'services-off' → platform route
//               (see LOCATION_COPY)
//   onLater     profile only, denied + services-off: "Not now — ask me when
//               I search". Advances sign-up; the app context asks again.
//
// App context hides the filter button (22 Oct 2026): with location off
// the only job is turning it on. Logo + likes chip stay.
//
// No "I just enabled it" link in the app context: the app re-reads the
// permission whenever it returns to the foreground and the gate lifts
// on its own.
// ─────────────────────────────────────────────────────────────
function ScreenProfileLocation({
  context = 'profile', state = 'default', tab = 'search', platform = 'ios',
  onAllow, onSettings, onLater, onTab, onBack, backLabel = 'Back',
} = {}) {
  const requesting = context === 'profile' && state === 'requesting';
  const key = state === 'requesting' ? 'default' : state;
  const c = context === 'app'
    ? (LOCATION_COPY.app[key] || LOCATION_COPY.app.default)
    : (LOCATION_COPY.profile[key] || LOCATION_COPY.profile.default)(platform);
  const blocked = key === 'denied' || key === 'services-off';
  const cta = blocked ? onSettings : onAllow;

  if (context === 'app') {
    return (
      <Phone height={844} scroll={false}>
        <div style={{ height: '100%', display: 'flex', flexDirection: 'column', position: 'relative', overflow: 'hidden' }}>
          <LocationBackdrop live={!blocked}/>
          <div style={{ position: 'relative', zIndex: 2 }}><SearchTopBar filter={false} premium={tab === 'likes'} chip={tab !== 'likes'}/></div>
          {onBack && (
            <div style={{ position: 'relative', zIndex: 2, padding: '0 16px 10px' }}>
              <button onClick={onBack} style={{ display: 'inline-flex', alignItems: 'center', gap: 2, height: 36, padding: '0 14px 0 8px', borderRadius: 9999, border: '1px solid var(--liq-border)', background: '#fff', fontFamily: 'var(--liq-font-sans)', fontSize: 13, fontWeight: 700, color: 'var(--liq-fg)', cursor: 'pointer', whiteSpace: 'nowrap' }}>
                <Icon name="chevron-left" size={18} stroke={2.2}/>{backLabel}
              </button>
            </div>
          )}
          <div style={{ position: 'relative', zIndex: 1, flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', padding: '4px 24px 0', textAlign: 'center' }}>
            <LocationRadarHero live={!blocked} height={onBack ? 140 : 170}/>
            <h1 className="su-underlined" style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30, letterSpacing: '-0.015em', lineHeight: 1.12, color: 'var(--liq-fg)', margin: '10px 0 0', textWrap: 'balance' }}>{c.headline}</h1>
            <p style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5, lineHeight: 1.5, color: 'var(--liq-neutral-200)', margin: '12px auto 0', maxWidth: 330, textWrap: 'pretty' }}>{c.body}</p>
            {!blocked && <div style={{ marginTop: 18 }}><LocationPrivacyNote/></div>}
            <div style={{ flex: 1, minHeight: 16 }}/>
            <div style={{ paddingBottom: 22 }}>
              <Button variant="sunset" size="lg" fullWidth onClick={cta}
                leading={<Icon name={blocked ? 'settings' : 'map-pin'} size={18} stroke={2.2}/>}>{c.cta}</Button>
            </div>
          </div>
          <div style={{ position: 'relative', zIndex: 2 }}><TabBar active={tab} onTab={onTab}/></div>
        </div>
      </Phone>
    );
  }

  return (
    <div className="su-app" style={{ width: 390, height: 844, position: 'relative', overflow: 'hidden', background: 'var(--liq-bg)', display: 'flex', flexDirection: 'column' }}>
      <LocationBackdrop live/>
      {requesting && <div style={{ position: 'absolute', inset: 0, background: 'rgba(20, 10, 30, 0.32)', zIndex: 3, pointerEvents: 'none' }}/>}
      <div style={{ position: 'relative', zIndex: 2 }}><StatusBar time="4:20" dark={false}/></div>

      {/* Radar drops on the blocked states — recovery, not pitch. */}
      {!blocked && (
        <div style={{ position: 'relative', zIndex: 1, padding: '24px 28px 0' }}>
          <LocationRadarHero live height={160}/>
        </div>
      )}

      <div style={{ position: 'relative', zIndex: 1, padding: blocked ? '56px 28px 0' : '12px 28px 0', flex: 'none' }}>
        <h1 className="su-underlined" style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 32, letterSpacing: '-0.015em', lineHeight: 1.1, color: 'var(--liq-fg)', margin: 0, textWrap: 'balance' }}>{c.headline}</h1>
      </div>

      <div style={{ position: 'relative', zIndex: 1, padding: blocked ? '16px 28px 0' : '4px 28px 0', flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        {c.body && <p style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15, lineHeight: 1.55, color: 'var(--liq-neutral-200)', margin: 0, textWrap: 'pretty' }}>{c.body}</p>}

        {!blocked && (
          <div style={{ marginTop: 22, display: 'flex', flexDirection: 'column', gap: 14 }}>
            {LOCATION_BENEFITS.map((b) => <LocationBenefitRow key={b.title} icon={b.icon} title={b.title} sub={b.sub}/>)}
          </div>
        )}
        {!blocked && <div style={{ marginTop: 22 }}><LocationPrivacyNote/></div>}

        <div style={{ flex: 1, minHeight: 12 }}/>

        <div style={{ marginBottom: 6, display: 'flex', flexDirection: 'column', gap: 10 }}>
          <Button variant="sunset" size="lg" fullWidth onClick={cta}>{c.cta}</Button>
          {blocked && <Button variant="ghost" size="lg" fullWidth onClick={onLater}>Not now &mdash; ask me when I search</Button>}
        </div>
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}><HomeIndicator/></div>
      {requesting && <SystemSheetMock/>}
    </div>
  );
}

Object.assign(window, {
  ScreenProfileLocation,
  LocationBenefitRow,
  LOCATION_BENEFITS,
  LOCATION_COPY,
  LocationRadarHero,
});

// ═════════════ part 2 · screens-search-location.jsx ═════════════
// screens-search-location.jsx — the app-wide location gate
//
// 22 Oct 2026: no longer its own screen. The gate is Profile 12's
// location screen in its 'app' context (screens-profile-location.jsx →
// ScreenProfileLocation context="app"). This file only keeps the old
// ScreenLocationGate name working for existing artboards.
//
//   state 'ask'          → never granted        (§26 not_determined)
//   state 'denied'       → off for Show Up      (§26 denied)
//   state 'services-off' → off for the phone    (§26 services_off)
//
// Exposed on window:
//   - ScreenLocationGate

function ScreenLocationGate({ state = 'denied', tab = 'search', onAllow, onSettings, onTab, onBack, backLabel } = {}) {
  return (
    <ScreenProfileLocation context="app" state={state === 'ask' ? 'default' : state}
      tab={tab} onAllow={onAllow} onSettings={onSettings} onTab={onTab} onBack={onBack} backLabel={backLabel}/>
  );
}

Object.assign(window, { ScreenLocationGate });
