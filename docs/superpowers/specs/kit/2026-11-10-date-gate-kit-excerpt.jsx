// Kit excerpt -- the location gate's DATE context (frame G of SHOWUP-195).
// Decoded on 10 Nov 2026 from the bundle inside Philipp's date-03-arrival-checkin-spec-sheet.html
// (Jira SHOWUP-195 attachment 12187): ui_kits/show-up/screens-profile-location.jsx, the code that
// renders frame G. The handoff folder has no reference file for this context; this is the kit's own
// source, unedited, lines 230-423 of the decoded file. Read values from here, not from the PNG.
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
  // Date context (5 Nov 2026): the gate over the Date screen while a date is
  // active. Location off → the Date screen is blocked, so Check-in can never
  // be tapped without location. Urgency comes from the date card (time +
  // countdown); the copy says what the user loses: the date screen, directions,
  // the check-in. Functions of the match's name.
  date: {
    default: (n) => ({
      headline: <>Urgent! Allow location to get access to your <em>date information</em>.</>,
      body: <>Your date screen needs your location. It gets you to the meeting place and lets you check in when you arrive, so {n} knows you&rsquo;re there.</>,
      cta: 'Allow location access',
    }),
    denied: (n) => ({
      headline: <>Urgent! Turn on location to get access to your <em>date information</em>.</>,
      body: <>Location is off for Show Up, so your date screen is locked. Turn it on to see your date, get directions and check in when you arrive, so {n} knows you&rsquo;re there.</>,
      cta: 'Open Settings',
    }),
    'services-off': (n) => ({
      headline: <>Urgent! Turn on location to get access to your <em>date information</em>.</>,
      body: <>Location is off for every app on this phone, so your date screen is locked. Turn it on to see your date, get directions and check in when you arrive, so {n} knows you&rsquo;re there.</>,
      cta: 'Open Settings',
    }),
  },
};

// ─────────────────────────────────────────────────────────────
// DateGateCard — the date, on the gate (date context only). Same purple and
// header as the Date screen's time + place card, so the user recognises it.
//   startsIn  '1h 18m' → orange `Starts in 1h 18m`
//   lateMin   10       → red `Started 10 min ago — {name} may be waiting`
//   day       'today' | 'tomorrow' (tomorrow → no countdown pill)
// ─────────────────────────────────────────────────────────────
function DateGateCard({ name = 'Leonie', day = 'today', time = '19:00', place = 'Cafe Krone', startsIn = '1h 18m', lateMin = null }) {
  const late = lateMin != null;
  const showPill = late || day === 'today';
  return (
    <div style={{ padding: '14px', borderRadius: 20, background: '#E8DCF5', textAlign: 'left', display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <Avatar name={name} size={40} rounded={13}/>
        <div style={{ flex: 1, minWidth: 0, fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 19, color: 'var(--liq-fg)', lineHeight: 1.2, textWrap: 'balance' }}>
          You and {name} are meeting <em style={{ color: 'var(--liq-primary-500)' }}>{day}</em>
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--liq-primary-500)' }}>
          <Icon name="clock" size={17} stroke={2.2}/>
          <span style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 19, color: 'var(--liq-fg)' }}>{time}</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0, color: 'var(--liq-primary-500)' }}>
          <Icon name="map-pin" size={17} stroke={2.2}/>
          <span style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 19, color: 'var(--liq-fg)' }}>{place}</span>
        </div>
      </div>
      {showPill && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          <div style={{ flex: 'none', display: 'inline-flex', alignItems: 'center', gap: 6, height: 30, padding: '0 12px', borderRadius: 9999, background: late ? 'var(--liq-danger)' : 'var(--liq-orange-500)', color: '#fff', fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 13, whiteSpace: 'nowrap' }}>
            <Icon name="clock" size={14} stroke={2.4}/>
            {late ? `Started ${lateMin} min ago` : `Starts in ${startsIn}`}
          </div>
          {late && <span style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 13, color: 'var(--liq-danger)' }}>{name} may be waiting</span>}
        </div>
      )}
    </div>
  );
}

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
  date = {},
} = {}) {
  const requesting = context === 'profile' && state === 'requesting';
  const key = state === 'requesting' ? 'default' : state;
  const c = context === 'app'
    ? (LOCATION_COPY.app[key] || LOCATION_COPY.app.default)
    : context === 'date'
      ? (LOCATION_COPY.date[key] || LOCATION_COPY.date.default)(date.name || 'Leonie')
      : (LOCATION_COPY.profile[key] || LOCATION_COPY.profile.default)(platform);
  const blocked = key === 'denied' || key === 'services-off';
  const cta = blocked ? onSettings : onAllow;

  // Date context — the gate over the Date screen (5 Nov 2026). No header (the
  // Date screen has none); the date card replaces the radar; TabBar in date
  // mode (Search, Likes, Instant disabled; Settings open). No Report a problem
  // here (5 Nov 2026): the only way forward is giving location access.
  if (context === 'date') {
    return (
      <Phone time={date.lateMin != null ? '19:10' : '17:42'} height={844} scroll={false}>
        <div style={{ height: '100%', display: 'flex', flexDirection: 'column', position: 'relative', overflow: 'hidden' }}>
          <LocationBackdrop live={!blocked}/>
          <div style={{ position: 'relative', zIndex: 1, flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', padding: '20px 24px 0' }}>
            <DateGateCard {...date}/>
            <h1 className="su-underlined" style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30, letterSpacing: '-0.015em', lineHeight: 1.12, color: 'var(--liq-fg)', margin: '26px 0 0', textWrap: 'balance' }}>{c.headline}</h1>
            <p style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5, lineHeight: 1.5, color: 'var(--liq-neutral-200)', margin: '12px 0 0', textWrap: 'pretty' }}>{c.body}</p>
            {!blocked && <div style={{ marginTop: 18 }}><LocationPrivacyNote/></div>}
            <div style={{ flex: 1, minHeight: 16 }}/>
            <div style={{ paddingBottom: 22 }}>
              <Button variant="sunset" size="lg" fullWidth onClick={cta}
                leading={<Icon name={blocked ? 'settings' : 'map-pin'} size={18} stroke={2.2}/>}>{c.cta}</Button>
            </div>
          </div>
          <div style={{ position: 'relative', zIndex: 2 }}><TabBar active={null} disabled={['search', 'likes', 'instant']} disabledHint="Available again once your date is finished."/></div>
        </div>
      </Phone>
    );
  }
