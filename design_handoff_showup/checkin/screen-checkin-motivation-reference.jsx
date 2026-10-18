// screen-checkin-motivation-reference.jsx — 48hrs Check-In 03 · Motivation
//
// Design reference for [Check-In 03] Motivation. This is the code that
// renders spec-sheets/03-motivation.png. Read values from HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/screens-checkin.jsx:
// CHECKIN_MOTIVATIONS, IntentStep, CoachNote, and step 4 of ScreenCheckIn).
// It is a reference implementation, not production code: recreate it in the
// target codebase's own patterns. Do not ship this file.
//
// CONSUMES, never copies:
//   Check-In 02 (screen-checkin-availability-reference.jsx) — the step shell
//     and CheckInFooter (with its toast and dimmed-but-tappable state).
//   Check-In 01 (screen-checkin-intro-reference.jsx) — CheckInCloseButton,
//     CheckInArrow.
//   components/shared.jsx — StatusBar, AppHeader, StepProgress, Button, Icon,
//     HomeIndicator.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// THREE STATES, ONE SCREEN.
//   A  <MotivationScreen intent={null}/>                 first Check-In, nothing picked
//   B  <MotivationScreen intent={null} toast={TOAST}/>   dimmed Continue tapped
//   C  <MotivationScreen intent="long_term"/>            picked / returning user
//
// SINGLE-SELECT (decided 13 Oct 2026). Exactly one motivation per Check-In.
// A tap selects; a tap on another row replaces; a tap on the selected row is
// a no-op (radio). Once picked it cannot be cleared on this screen.
//
// VALUES ARE enums §30 check_in_motivation, in this order:
//   long_term · non_monogamy · short_term · casual · friends
// Class 1. The same ids are used by Instant and the dating_intent filter.
//
// PRE-SELECTION. A first Check-In starts with intent = null (A). Every later
// Check-In pre-selects the LAST ACCEPTED answer (server-side, written on each
// accepted Continue — not on ✕, not on a tap).
//
// STEP SHELL: StepProgress steps={6} current={3}. Headline 30 (step size).
//
// LAYOUT — the option list is the only flexible element (flex: 1,
// min-height 0, overflow-y auto). Headline + CoachNote above it are flex
// none. Fits at 390 × 844; scrolls inside itself at 375 × 667. Never shrink
// the rows to avoid it. Never hard-code a Y.

const CHECKIN_MOTIVATIONS = [
  { id: 'long_term',    icon: 'heart-filled', t: 'Long-term partner',      s: 'Looking for a meaningful connection' },
  { id: 'non_monogamy', icon: 'compass',      t: 'Long-term Non-Monogamy', s: 'Non-monogamous lifestyle' },
  { id: 'short_term',   icon: 'flame',        t: 'Short-term fun',         s: 'Physical chemistry, no commitment' },
  { id: 'casual',       icon: 'sparkles',     t: 'Casual dating',          s: 'Light, fun, non-committal' },
  { id: 'friends',      icon: 'users',        t: 'New friends',            s: 'Strictly platonic connections' },
];

const MOTIVATION_TOAST = 'Pick your motivation to date.';

// The screen as the step shell renders it. StepShell / CheckInFooter are the
// ones built by Check-In 02 — shown here only to name the props this step passes.
function MotivationScreen({ intent: initial = null, toast = '' }) {
  const [intent, setIntent] = React.useState(initial);
  return (
    <StepShell current={3} onBack={/* → checkin_tomorrow */ () => {}} onClose={/* → gate, nothing saved */ () => {}}>
      <IntentStep intent={intent} setIntent={setIntent}/>
      <CheckInFooter label="Continue" disabled={!intent} toast={toast} onClick={() => {}}/>
    </StepShell>
  );
}

function IntentStep({ intent, setIntent }) {
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 'none' }}>
        <h2 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
          letterSpacing: '-0.015em', lineHeight: 1.15, color: 'var(--liq-fg)',
          margin: '8px 0 0', textWrap: 'pretty',
        }}>What is your <em>motivation</em> to date?</h2>

        <CoachNote color="warm" body={<>This will be highly visible on your profile and a key decision point for someone to date you.</>}/>
      </div>

      <div role="radiogroup" aria-label="Motivation to date" style={{
        flex: 1, minHeight: 0, overflowY: 'auto', marginTop: 14, paddingBottom: 12,
        display: 'flex', flexDirection: 'column', gap: 10,
      }}>
        {CHECKIN_MOTIVATIONS.map(opt => {
          const sel = intent === opt.id;
          return (
            <button key={opt.id} role="radio" aria-checked={sel} onClick={() => setIntent(opt.id)} style={{
              flex: 'none', display: 'flex', alignItems: 'center', gap: 14, padding: '14px 16px',
              borderRadius: 16, background: '#fff', textAlign: 'left',
              border: `1.5px solid ${sel ? 'var(--liq-primary-500)' : 'var(--liq-border)'}`,
              transition: 'all 180ms',
            }}>
              <div style={{
                width: 36, height: 36, borderRadius: 12, flex: 'none',
                background: sel ? 'var(--liq-primary-500)' : 'rgba(167,139,250,0.16)',
                color: sel ? '#fff' : 'var(--liq-primary-500)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon name={opt.icon} size={18} stroke={2}/>
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 700, fontSize: 15, color: 'var(--liq-fg)' }}>{opt.t}</div>
                <div style={{ fontSize: 13, color: 'var(--liq-fg-subtle)' }}>{opt.s}</div>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}

// CoachNote — first built here for the Check-In (Uplift reuses it, lilac).
function CoachNote({ body, color = 'lilac' }) {
  const palette = color === 'warm'
    ? { bg: '#FFF6CF', fg: '#73510B' }
    : { bg: '#EFE6FF', fg: '#4F2B8C' };
  return (
    <div style={{
      marginTop: 14, padding: '10px 14px', borderRadius: 12,
      background: palette.bg, color: palette.fg,
      fontFamily: 'var(--liq-font-sans)', fontSize: 12.5, fontWeight: 500,
      lineHeight: 1.45,
    }}>{body}</div>
  );
}
