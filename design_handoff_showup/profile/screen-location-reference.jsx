// screen-location-reference.jsx — Profile 12 · Location permission ask
// Extracted from ui_kits/show-up/screens-profile-location.jsx on 5 Oct 2026.
// Ticket: profile/tickets/12-location.md · Sheet: profile/spec-sheets/12-location.png
//
// WHAT MATTERS
//   Build three states:  state="default"  (A · ask)
//                        state="denied"   (B · denied / restricted)
//                        state="services-off" (C · phone's Location switch off)
//   platform="ios" | "android" changes ONE sentence in B and C — nothing else.
//
// DO NOT BUILD
//   - state="requesting" and SystemSheetMock — a design-review mock of the OS
//     dialog. The real dialog is the platform's. Never draw a look-alike.
//   - onApprox — there is no approximate button. The OS dialog owns precision.
//   - LOCATION_BENEFITS[].line — never rendered; only title (+ sub on row 2) ship.
//
// Not production code. Recreate in the app's own patterns; read values from here.
// Order of authority: this file wins on numbers · ticket on behaviour, scope, copy · PNG on nothing.

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
    line: 'Set a max distance and we only surface profiles inside it — no long-distance pen pals.',
  },
  {
    icon: 'map-pin',
    title: 'We pick a fair halfway venue for you both',
    sub: 'No planning, no home advantage \u2014 a busy public spot that\u2019s neutral ground for both of you.',
    line: 'A safe public spot halfway between you — no back-and-forth, no home advantage. You can still swap it together.',
  },
  {
    icon: 'navigation',
    title: 'Walking directions on the day',
    line: 'Fifteen minutes before, we route you to the door. Never arrive late.',
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
// LocationRadarHero — concentric rings + map-pin + scattered dots
//
// The visual statement at the top of the screen. Three concentric
// rings (24 / 56 / 96 px radii on a 240px canvas) drawn with a
// faint violet stroke, fading outward to suggest a soft "ping"
// radius. A solid orange map-pin sits at centre. Three small
// open-circle dots — one per ring — are positioned at deliberate
// asymmetric angles so the composition doesn't read as a perfect
// target. Designed to be replaced by an animated SVG later; for
// the design review pass it sits as a static frame.
//
// Sized to ~150px tall so it carries weight without crowding the
// headline. No drop-shadow, no fill on the rings — the brand
// avoids decorative chrome and the orbs behind the canvas already
// supply atmosphere.
// ─────────────────────────────────────────────────────────────
function LocationRadarHero() {
  return (
    <div style={{
      position: 'relative',
      width: '100%', height: 160,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      marginTop: 8,
    }}>
      <svg width="240" height="160" viewBox="0 0 240 160" fill="none"
        style={{ overflow: 'visible' }}>
        {/* concentric rings — violet stroke, decreasing opacity outward */}
        <circle cx="120" cy="80" r="24" stroke="rgba(129,42,236,0.45)" strokeWidth="1.25"/>
        <circle cx="120" cy="80" r="56" stroke="rgba(129,42,236,0.30)" strokeWidth="1.25" strokeDasharray="2 4"/>
        <circle cx="120" cy="80" r="96" stroke="rgba(129,42,236,0.18)" strokeWidth="1.25" strokeDasharray="2 4"/>

        {/* nearby-profile dots: one per ring at varied angles so the
            composition reads as scattered, not symmetric. Open circle
            with a violet stroke + paper-white fill so they sit "on
            top of" the rings rather than inside them. */}
        <circle cx="148" cy="62" r="4.5" fill="var(--liq-bg)" stroke="var(--liq-primary-500)" strokeWidth="1.5"/>
        <circle cx="74"  cy="108" r="4.5" fill="var(--liq-bg)" stroke="var(--liq-primary-500)" strokeWidth="1.5"/>
        <circle cx="198" cy="118" r="4.5" fill="var(--liq-bg)" stroke="var(--liq-primary-500)" strokeWidth="1.5"/>
        {/* one extra dot on the outer ring, soft lavender so it reads
            as further-away */}
        <circle cx="44"  cy="50"  r="3.5" fill="var(--liq-bg)" stroke="rgba(167,139,250,0.7)" strokeWidth="1.25"/>

        {/* centre orange map-pin — slightly larger than a row icon so
            it carries the composition. Filled with the sunset gradient
            via a clipPath on a rect; the stroke detail keeps it
            stroke-line consistent with the rest of the icon set. */}
        <defs>
          <linearGradient id="su-loc-pin" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#FE6839"/>
            <stop offset="100%" stopColor="#D05976"/>
          </linearGradient>
        </defs>
        <g transform="translate(120, 80)">
          {/* soft orange halo behind the pin */}
          <circle cx="0" cy="0" r="18" fill="rgba(254,104,57,0.18)"/>
          {/* pin glyph — 24×24 Lucide geometry, scaled 1.25x and
              centred. Drop body at y=-1 so the optical centre of the
              teardrop sits on the radar centre. */}
          <g transform="translate(-12, -16) scale(1.25)" stroke="#fff" strokeWidth="1.6"
             strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 10c0 7-9 13-9 13S3 17 3 10a9 9 0 0 1 18 0z" fill="url(#su-loc-pin)"/>
            <circle cx="12" cy="10" r="3" fill="#fff" stroke="none"/>
          </g>
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
// ScreenProfileLocation — main screen
//
// Props:
//   state       'default' | 'requesting' | 'denied' — visual variant
//   onAllow     fired on the primary "Allow location access" tap
//   onApprox    fired on the secondary "Use approximate location" tap
//   onSettings  fired on "Open Settings" when in 'denied' state
//   onLater     fired on "Not now — ask me when I search" ('denied' only).
//               Advances the flow without location; the app-wide
//               ScreenLocationGate asks again when Search or Instant opens.
//               Added 27 Sep 2026.
// ─────────────────────────────────────────────────────────────
function ScreenProfileLocation({
  state = 'default',
  onAllow, onApprox, onSettings, onLater,
  platform = 'ios',
} = {}) {
  // 'services-off' (added 27 Sep 2026): the phone's master Location switch
  // is off, so the OS will not raise the permission dialog at all. Same
  // layout and buttons as 'denied'; only the copy differs. iOS can only
  // open our own app page in Settings, so the iOS copy names the path;
  // Android opens the Location page directly and needs no path.
  const servicesOff = state === 'services-off';
  const denied = state === 'denied' || servicesOff;
  const requesting = state === 'requesting';

  // Headline copy varies by state — the italic flourish moves to the
  // word that carries the emotional weight of that state.
  const headline = servicesOff
    ? <>Your phone has location <em>switched off</em>.</>
    : denied
    ? <>We can&rsquo;t find dates <em>without</em> location.</>
    : <>Find people <em>nearby</em>.</>;

  // On the default variant the headline carries the screen — the
  // three benefit rows do the explanatory work, so we drop the lead
  // paragraph entirely. Keep the recovery copy for the 'denied'
  // variant where the user needs to be told what just happened.
  const body = servicesOff
    ? (platform === 'android'
      ? <>Location is off for every app on this phone, not just Show Up.
         Turn it on in Settings now, or finish your profile first and
         we&rsquo;ll ask again when you start searching.</>
      : <>Location Services is off for every app on this phone, not just
         Show Up. Turn it on in <strong>Settings &rsaquo; Privacy &amp;
         Security &rsaquo; Location Services</strong>, or finish your profile
         first and we&rsquo;ll ask again when you start searching.</>)
    : denied
    ? (platform === 'android'
      ? <>Location access is off for Show Up. Without it we can&rsquo;t
         show you anyone nearby — every date happens in the real world.
         Turn it on in <strong>Permissions &rsaquo; Location</strong>, or
         finish your profile first and we&rsquo;ll ask again when you
         start searching.</>
      : <>Location access is off for Show Up. Without it we can&rsquo;t
         show you anyone nearby — every date happens in the real world.
         Turn it on in <strong>Settings &rsaquo; Show Up &rsaquo;
         Location</strong>, or finish your profile first and we&rsquo;ll
         ask again when you start searching.</>)
    : null;

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Ambient backdrop — same recipe as ScreenProfileNotifications. */}
      <div style={{
        position: 'absolute', top: '-15%', right: '-25%', width: 520, height: 520,
        background: 'radial-gradient(circle, rgba(254,104,57,0.32) 0%, rgba(254,104,57,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>
      <div style={{
        position: 'absolute', bottom: '-20%', left: '-30%', width: 600, height: 600,
        background: 'radial-gradient(circle, rgba(129,42,236,0.28) 0%, rgba(129,42,236,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, height: 360,
        background: 'linear-gradient(180deg, rgba(255,229,210,0.55) 0%, rgba(255,251,247,0) 70%)',
        zIndex: 0, pointerEvents: 'none',
      }}/>

      {/* When the system sheet is "up" we dim the whole canvas. */}
      {requesting && (
        <div style={{
          position: 'absolute', inset: 0,
          background: 'rgba(20, 10, 30, 0.32)',
          zIndex: 3, pointerEvents: 'none',
        }}/>
      )}

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20" dark={false}/>
      </div>

      {/* RADAR HERO — sits between status bar and headline. Drops on
          the 'denied' variant: we don't want to celebrate the radar
          when the user has just blocked us; the explanation does the
          work instead. */}
      {!denied && (
        <div style={{ position: 'relative', zIndex: 1, padding: '16px 28px 0' }}>
          <LocationRadarHero/>
        </div>
      )}

      {/* HEADLINE — dark Lora on the warm-paper canvas with the
          italic-underline flourish on a single word. Top padding
          tightens when the radar isn't shown (denied variant) so the
          headline still reads "anchored" rather than floating. */}
      <div style={{
        position: 'relative', zIndex: 1,
        padding: denied ? '56px 28px 0' : '12px 28px 0',
        flex: 'none',
      }}>
        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 32,
          letterSpacing: '-0.015em', lineHeight: 1.1,
          color: 'var(--liq-fg)', margin: 0, textWrap: 'balance',
        }}>
          {headline}
        </h1>
      </div>

      <div style={{
        position: 'relative', zIndex: 1,
        padding: denied ? '16px 28px 0' : '4px 28px 0',
        flex: 1,
        display: 'flex', flexDirection: 'column',
        minHeight: 0,
      }}>
        {body && (
          <p style={{
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15,
            lineHeight: 1.55, color: 'var(--liq-neutral-200)',
            margin: 0, textWrap: 'pretty',
          }}>
            {body}
          </p>
        )}

        {/* The three benefit rows. Drop them on the 'denied' variant —
            the user has already seen them and we shouldn't lecture
            twice; the denied screen is recovery, not pitch. */}
        {!denied && (
          <div style={{
            marginTop: 22,
            display: 'flex', flexDirection: 'column', gap: 14,
          }}>
            {LOCATION_BENEFITS.map((b) => (
              <LocationBenefitRow key={b.title} icon={b.icon} title={b.title} sub={b.sub}/>
            ))}
          </div>
        )}

        {/* PRIVACY REASSURANCE — a short, calm line. Sits in the muted
            neutral so it doesn't compete with the headline or CTA;
            framed with a small lock icon so the user clocks the
            register at a glance. Hidden on the denied variant for the
            same reason as the benefits — recovery, not pitch. */}
        {!denied && (
          <div style={{
            marginTop: 22,
            display: 'flex', gap: 10, alignItems: 'flex-start',
            padding: '12px 14px',
            borderRadius: 12,
            background: 'rgba(167,139,250,0.10)',
          }}>
            <div style={{
              flex: 'none', marginTop: 1,
              color: 'var(--liq-primary-500)',
            }}>
              <Icon name="lock" size={16} stroke={1.8}/>
            </div>
            <div style={{
              fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 12.5,
              lineHeight: 1.45, color: 'var(--liq-neutral-200)',
              textWrap: 'pretty',
            }}>
              Your exact location is never shown on your profile.
              Other people only see the city you&rsquo;re in and an approximate
              distance from themselves e.g. 1.5km.
            </div>
          </div>
        )}

        <div style={{ flex: 1, minHeight: 12 }}/>

        {/* FOOTER — sunset CTA + secondary text link beneath.
            On 'denied' the primary action becomes "Open Settings"
            (the only path back) and we drop the secondary link. */}
        <div style={{ marginBottom: 6 }}>
          {denied ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <Button
                variant="sunset"
                size="lg"
                fullWidth
                onClick={onSettings}
              >
                Open Settings
              </Button>
              <Button
                variant="ghost"
                size="lg"
                fullWidth
                onClick={onLater}
              >
                Not now &mdash; ask me when I search
              </Button>
            </div>
          ) : (
            <Button
              variant="sunset"
              size="lg"
              fullWidth
              onClick={onAllow}
            >
              Allow location access
            </Button>
          )}
        </div>

      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>

      {/* Static iOS system sheet for the 'requesting' variant. Sits
          above the dim layer; pointer-events none in the mock so
          design review can still inspect the layers behind. */}
      {requesting && <SystemSheetMock/>}
    </div>
  );
}

Object.assign(window, {
  ScreenProfileLocation,
  LocationBenefitRow,
  LOCATION_BENEFITS,
  LocationRadarHero,
});
