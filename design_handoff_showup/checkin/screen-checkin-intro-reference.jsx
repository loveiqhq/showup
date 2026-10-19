// screen-checkin-intro-reference.jsx — 48hrs Check-In 01 · Intro + date search not available
//
// Design reference for [Check-In 01] Intro. This is the code that renders
// spec-sheets/01-intro.png. Read values from HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/screens-checkin.jsx,
// CheckInIntro + ScreenCheckInNeeded). It is a reference implementation, not
// production code: recreate it in the target codebase's own patterns. Do not
// ship this file.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// TWO SCREENS, ONE TICKET.
//   A  <CheckInIntro onStart onClose/>        the intro, step 1 of the Check-In
//   B  <ScreenCheckInNeeded onCheckIn/>       the Search tab with no Check-In —
//                                             "date search not available"
//
// REACHING THE INTRO FOR THE FIRST TIME MEANS PROFILE CREATION IS DONE — NOT
// THAT THE USER IS READY TO DATE (decided 12 Oct 2026). So the intro can be
// closed. The ✕ (CheckInCloseButton) lands on B. On B, Settings works as
// normal; the CTA, the Search tab and the Instant tab all reopen the Check-In,
// because availability has to be stated before anyone can be searched for.
//
// THE INTRO IS A BRIDGE, NOT A STEP — same grammar as Profile 05 / 13:
//   - NO AppHeader title, NO back, NO StepProgress. The only chrome is the ✕,
//     in a 52-high row (the AppHeader height) at the AppHeader's leading
//     slot. That row replaces the 52 of top padding the headline had before,
//     so the headline's Y did not move.
//   - Ambient backdrop (two orbs + peach wash), full-bleed, decorative.
//   - Headline Lora 700 / 34 / 1.1 / −0.015em — the bridge group's size.
//   - Full-width sunset CTA with a trailing SVG arrow (CheckInArrow), not a
//     "→" text glyph. Same as Embrace's "Upload my photos".
//
// THE NUMBERED LIST HAS FIVE ROWS — availability, motivation, vibe, filters,
// reasons. It summarises the six steps (today + tomorrow are one row; the
// heads-up time step was removed 13 Oct 2026). Rows are NumberedRow: a 26 orange disc with a white numeral, an
// element, not a list-style.
//
// B IS THE SEARCH TAB, NOT A MODAL. Wordmark row, centred statement, one
// sunset CTA, TabBar with Search active. No profiles, no filters, no likes
// chip. The TabBar's onTab sends search and instant to onCheckIn; settings
// navigates normally. The tabs are NOT disabled — no lock badge, no hint toast.
//
// WHERE B'S CTA GOES depends on whether the user has passed the intro before
// — the caller's decision, not this component's. Never passed → the intro.
// Passed before → the Today step, EMPTY (availability is never kept).
// Superseded 13 Oct 2026: this said "a draft → Today, pre-filled"; there is
// no Check-In draft.
//
// LAYOUT — flex columns, one flex: 1 element per screen, nothing absolute
// except the intro's three backdrop layers.
//   A  StatusBar 54 → close row 52 → headline (pad 0/28) → body (flex: 1,
//      pad 28/28/0: list → closing line mt 22 → spacer flex 1 → CTA mb 22)
//      → HomeIndicator 28
//   B  Phone → wordmark row 52 → statement (flex: 1, centred) → CTA
//      (pad 0/24/22) → TabBar
// Never hard-code a Y.

// NumberedRow — explanatory-screen counterpart to Embrace's BulletRow.
// An orange circle with a white numeral takes the place of the orange dot.
function NumberedRow({ n, children }) {
  return (
    <li style={{
      display: 'flex', alignItems: 'center', gap: 14,
      padding: 0, margin: 0,
    }}>
      <span aria-hidden style={{
        flex: 'none', width: 26, height: 26, borderRadius: '50%',
        background: 'var(--liq-orange-500)',
        boxShadow: '0 2px 6px rgba(254,104,57,0.28)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 13,
        color: '#fff', lineHeight: 1,
      }}>{n}</span>
      <span style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 15,
        lineHeight: 1.4, color: 'var(--liq-fg)',
      }}>
        {children}
      </span>
    </li>
  );
}

// CheckInArrow — the bridges' trailing arrow (same SVG as Embrace's CTA):
// an icon, not a "→" text glyph.
function CheckInArrow() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M5 12h14M13 5l7 7-7 7"/>
    </svg>
  );
}

// CheckInCloseButton — the ✕ every Check-In screen carries. Same 36 target
// and glyph as AppHeader's leading="close"; on the steps it sits trailing
// (back stays leading), on the intro it sits leading (there is no back).
function CheckInCloseButton({ onClick }) {
  return (
    <button onClick={onClick} aria-label="Close Check-In" style={{
      width: 36, height: 36, borderRadius: 18, flex: 'none',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--liq-fg)',
    }}>
      <Icon name="x" size={22} stroke={2}/>
    </button>
  );
}

function CheckInIntro({ onStart, onClose }) {
  const items = [
    'Define your availability',
    'Show your motivation',
    'Define your desired dating vibe',
    'Filter who you wish to date',
    'Give reasons to match you',
  ];
  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Ambient backdrop — same recipe as ScreenProfileEmbrace.
          Orange orb top-right, violet orb bottom-left, peach wash across
          the top third. Sits behind status bar + home indicator for a
          full-bleed warm atmosphere. */}
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
        position: 'absolute', top: 0, left: 0, right: 0,
        background: 'linear-gradient(180deg, rgba(255,229,210,0.55) 0%, rgba(255,251,247,0) 70%)',
        height: 360, pointerEvents: 'none', zIndex: 0,
      }}/>

      {/* Status bar — dark ink on warm paper, matching Embrace. */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20" dark={false}/>
      </div>

      {/* CLOSE ROW — 52 high, the AppHeader height, so the headline keeps
          the Y it had when this was 52 of top padding. No title, no back:
          the ✕ is the only control besides the CTA. */}
      <div style={{
        position: 'relative', zIndex: 2, flex: 'none',
        height: 52, padding: '0 16px', display: 'flex', alignItems: 'center',
      }}>
        <CheckInCloseButton onClick={onClose}/>
      </div>

      {/* HEADLINE — dark Lora, italic-underline flourish on "dated soon".
          34, the bridge group's size (decided 12 Oct 2026, with 05 and 13). */}
      <div style={{
        position: 'relative', zIndex: 1,
        padding: '0 28px',
        flex: 'none',
      }}>
        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 34,
          letterSpacing: '-0.015em', lineHeight: 1.1,
          color: 'var(--liq-fg)', margin: 0, textWrap: 'balance',
        }}>
          Check-in to be <em>dated soon.</em>
        </h1>
      </div>

      {/* BODY — numbered list + closing italic line on the warm canvas. */}
      <div style={{
        position: 'relative', zIndex: 1,
        padding: '28px 28px 0',
        flex: 1,
        display: 'flex', flexDirection: 'column',
      }}>
        <ol style={{
          listStyle: 'none', padding: 0, margin: 0,
          display: 'flex', flexDirection: 'column', gap: 12,
        }}>
          {items.map((t, i) => (
            <NumberedRow key={i} n={i + 1}>{t}</NumberedRow>
          ))}
        </ol>

        <p className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 400,
          fontSize: 17, lineHeight: 1.45,
          color: 'var(--liq-fg-muted)',
          margin: '22px 0 0', textWrap: 'pretty',
        }}>
          The rest is <em>magic</em> to happen.
        </p>

        <div style={{ flex: 1 }}/>

        {/* FOOTER — sunset CTA, full width, white text + arrow.
            Matches ScreenProfileEmbrace's "Upload my photos →" exactly. */}
        <div style={{ marginBottom: 22 }}>
          <Button
            variant="sunset"
            size="lg"
            fullWidth
            onClick={onStart}
            trailing={<CheckInArrow/>}
          >
            Check-in for a date
          </Button>
        </div>
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

// StatusBar / HomeIndicator are exposed on `window` by shared.jsx — usable as
// bare identifiers because they live on the global object.

// ───────────────────────────────────────────────────────────────
// Closed · date search not available
//
// Where the ✕ lands. The Search tab, in the state of a user who has never
// checked in: no profiles, no filters, no likes chip — one statement and one
// way forward. Settings stays reachable. Search, Instant and the CTA all
// reopen the Check-In at the intro (passed in as onCheckIn).
// ───────────────────────────────────────────────────────────────
function ScreenCheckInNeeded({ onCheckIn = () => {} }) {
  return (
    <Phone height={844}>
      <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        <div style={{ padding: '4px 16px 8px', height: 52, display: 'flex', alignItems: 'center', flex: 'none' }}>
          <Wordmark size={20}/>
        </div>

        <div style={{ flex: 1, minHeight: 0, padding: '0 24px', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', textAlign: 'center' }}>
          <div style={{
            width: 64, height: 64, borderRadius: 20, flex: 'none',
            background: 'var(--su-grad-lilac)', color: 'var(--liq-primary-500)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Icon name="calendar" size={28} stroke={1.9}/>
          </div>
          <h1 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
            letterSpacing: '-0.015em', lineHeight: 1.12, color: 'var(--liq-fg)',
            margin: '22px 0 0', textWrap: 'balance',
          }}>Check-in to <em>start dating.</em></h1>
          <p style={{
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
            lineHeight: 1.5, color: 'var(--liq-neutral-200)',
            margin: '12px 0 0', maxWidth: 310, textWrap: 'pretty',
          }}>Date search and Instant open once you've told us when you're free to date.</p>
        </div>

        <div style={{ padding: '0 24px 22px', flex: 'none' }}>
          <Button variant="sunset" size="lg" fullWidth onClick={onCheckIn} trailing={<CheckInArrow/>}>
            Check-in for a date
          </Button>
        </div>

        <TabBar active="search" onTab={(id) => { if (id === 'search' || id === 'instant') onCheckIn(); }}/>
      </div>
    </Phone>
  );
}


Object.assign(window, { CheckInIntro, ScreenCheckInNeeded, CheckInCloseButton, CheckInArrow, NumberedRow });
