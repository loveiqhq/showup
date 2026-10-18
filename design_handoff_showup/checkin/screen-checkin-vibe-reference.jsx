// screen-checkin-vibe-reference.jsx — 48hrs Check-In 04 · Vibe
//
// Design reference for [Check-In 04] Vibe. This is the code that renders
// spec-sheets/04-vibe.png. Read values from HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/screens-checkin.jsx:
// CHECKIN_VIBE_GROUPS, CHECKIN_VIBE_LABEL, VibeStep, and step 5 of ScreenCheckIn).
// It is a reference implementation, not production code: recreate it in the
// target codebase's own patterns. Do not ship this file.
//
// CONSUMES, never copies:
//   Check-In 02 (screen-checkin-availability-reference.jsx) — the step shell
//     and CheckInFooter (with its toast and dimmed-but-tappable state).
//   Check-In 03 (screen-checkin-motivation-reference.jsx) — CoachNote.
//   Check-In 01 (screen-checkin-intro-reference.jsx) — CheckInCloseButton,
//     CheckInArrow.
//   Profile 22 (profile/screen-life-reference.jsx) — LifePill, with multi.
//     As built: no new props, no locked or dimmed state.
//   components/shared.jsx — StatusBar, AppHeader, StepProgress, Button, Icon,
//     HomeIndicator.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// FOUR STATES, ONE SCREEN.
//   A  <VibeScreen vibes={[]}/>                          first Check-In, nothing picked
//   B  <VibeScreen vibes={[]} toast={VIBE_TOAST_EMPTY}/> dimmed Continue tapped
//   C  <VibeScreen vibes={['coffee_chat','playful','no_rush']}/>  picked / returning user
//   D  <VibeScreen vibes={[five]} toast={VIBE_TOAST_MAX}/>        a sixth chip tapped
//
// MULTI-SELECT, 1–5 (decided 13 Oct 2026), across all three groups.
// A tap on an idle chip selects it; a tap on a selected chip deselects it.
// At 5 a tap on an idle chip SELECTS NOTHING and raises VIBE_TOAST_MAX.
// Chips are never locked, dimmed or disabled — the counter turning violet
// at 5/5 is the only visual change.
//
// VALUES ARE enums §31 check_in_vibe, in this order (the group is a property
// of the value, never sent separately). Class 0. ONE vocabulary: Search's
// "vibe today" card and Instant show CHECKIN_VIBE_LABEL, nothing else.
//
// PRE-SELECTION. A first Check-In starts empty (A). Every later Check-In
// pre-selects the LAST ACCEPTED set (server-side, written on each accepted
// Continue — not on ✕, not on a tap).
//
// STEP SHELL: StepProgress steps={6} current={4}. Headline 30 (step size).
//
// LAYOUT — the chip groups are the only flexible element (flex: 1,
// min-height 0, overflow-y auto). Headline, CoachNote and the counter row
// are flex none. The groups scroll on EVERY device (about twice the space
// at 390 × 844). Never shrink the chips to avoid it. Never hard-code a Y.

const CHECKIN_VIBE_MAX = 5;
const VIBE_TOAST_EMPTY = 'Pick at least one dating vibe (up to 5).';
const VIBE_TOAST_MAX = 'You can pick up to 5 vibes.';

const CHECKIN_VIBE_GROUPS = [
  { id: 'activity', label: 'What you want to do', hint: 'Activities', opts: [
    { id: 'coffee_chat',      t: 'Coffee & a chat' },
    { id: 'casual_drink',     t: 'Kiosk beer / Casual drink' },
    { id: 'grab_food',        t: 'Grabbing food' },
    { id: 'nature_walk',      t: 'Park or nature walk' },
    { id: 'bouldering_table_tennis', t: 'Bouldering / Table tennis' },
    { id: 'workout',          t: 'Run or active workout' },
    { id: 'sunset',           t: 'Watching the sunset' },
    { id: 'drinks_music',     t: 'Drinks & music' },
  ]},
  { id: 'headspace', label: 'How you feel right now', hint: 'Headspace', opts: [
    { id: 'calm',             t: 'Calm & peaceful' },
    { id: 'deep_talk',        t: 'Deep conversation' },
    { id: 'playful',          t: 'Fun & playful' },
    { id: 'cozy',             t: 'Cozy & comfortable' },
    { id: 'spontaneous',      t: 'Spontaneous / Up for anything' },
    { id: 'curious',          t: 'Curious / Open-minded' },
  ]},
  { id: 'pacing', label: 'Your energy & pacing', hint: 'Boundaries', opts: [
    { id: 'quick_hello',      t: 'Quick 30-min hello' },
    { id: 'low_key',          t: 'Low-key energy' },
    { id: 'high_energy',      t: 'High social energy' },
    { id: 'no_rush',          t: 'Free evening (no rush)' },
    { id: 'early_night',      t: 'Early night (home early)' },
    { id: 'meet_now',         t: 'Ready to meet now' },
  ]},
];

// Label lookup for every display surface (Search "vibe today", Instant).
const CHECKIN_VIBE_LABEL = Object.fromEntries(CHECKIN_VIBE_GROUPS.flatMap(g => g.opts.map(o => [o.id, o.t])));

// §31 values in LIST order — the order vibe_ids is sent in, never tap order.
const CHECKIN_VIBE_ORDER = CHECKIN_VIBE_GROUPS.flatMap(g => g.opts.map(o => o.id));
const vibeIdsForTracking = set => CHECKIN_VIBE_ORDER.filter(id => set.has(id)).join(',');

// The screen as the step shell renders it. StepShell / CheckInFooter are the
// ones built by Check-In 02 — shown here only to name the props this step passes.
function VibeScreen({ vibes: initial = [], toast: initialToast = '' }) {
  const [vibes, setVibes] = React.useState(new Set(initial));
  const [toast, setToast] = React.useState(initialToast);
  return (
    <StepShell current={4} onBack={/* → checkin_motivation */ () => {}} onClose={/* → gate, nothing saved */ () => {}}>
      <VibeStep vibes={vibes} setVibes={setVibes} onLimit={() => setToast(VIBE_TOAST_MAX) /* 2600ms, as 02 */}/>
      <CheckInFooter label="Continue" disabled={vibes.size === 0} toast={toast} onClick={() => {}}/>
    </StepShell>
  );
}

function VibeStep({ vibes, setVibes, onLimit }) {
  const count = vibes.size;
  const toggle = id => {
    if (vibes.has(id)) { const n = new Set(vibes); n.delete(id); setVibes(n); return; }
    if (count >= CHECKIN_VIBE_MAX) { onLimit && onLimit(); return; }
    const n = new Set(vibes); n.add(id); setVibes(n);
  };
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 'none' }}>
        <h2 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
          letterSpacing: '-0.015em', lineHeight: 1.15, color: 'var(--liq-fg)',
          margin: '8px 0 0', textWrap: 'pretty',
        }}>What best describes your <em>dating vibe</em> today?</h2>

        <CoachNote color="warm" body="Your vibes tell people how you feel, what you long for, and how you'd like to meet — a quick read on where your head is at today."/>

        <div style={{ marginTop: 14, display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10 }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--liq-fg)' }}>Pick up to 5 — mix and match</span>
          <span aria-live="polite" style={{ fontSize: 12, fontWeight: 600, color: count >= CHECKIN_VIBE_MAX ? 'var(--liq-primary-500)' : 'var(--liq-fg-subtle)' }}>{count}/{CHECKIN_VIBE_MAX}</span>
        </div>
      </div>

      <div style={{
        flex: 1, minHeight: 0, overflowY: 'auto', marginTop: 12, paddingBottom: 12,
        display: 'flex', flexDirection: 'column', gap: 18,
      }}>
        {CHECKIN_VIBE_GROUPS.map(g => (
          <div key={g.id} role="group" aria-label={g.label} style={{ flex: 'none' }}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, margin: '0 0 10px' }}>
              <span style={{ fontFamily: 'var(--liq-font-sans)', fontSize: 13, fontWeight: 700, color: 'var(--liq-fg)' }}>{g.label}</span>
              <span style={{ fontFamily: 'var(--liq-font-sans)', fontSize: 10.5, fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase', color: 'var(--liq-fg-subtle)' }}>{g.hint}</span>
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {g.opts.map(o => (
                <LifePill key={o.id} label={o.t} multi selected={vibes.has(o.id)} onClick={() => toggle(o.id)}/>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
