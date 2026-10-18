// screen-checkin-uplift-reference.jsx — 48hrs Check-In 06 · Uplift + commit
//
// Design reference for [Check-In 06] Uplift + commit. This is the code that
// renders spec-sheets/06-uplift.png. Read values from HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/screens-checkin.jsx:
// UPLIFT_* constants, upliftContact, UpliftErrorCard, UpliftStep, the
// CheckInFooter loading / error props, CheckInSpinner, and step 7 of
// ScreenCheckIn). It is a reference implementation, not production code:
// recreate it in the target codebase's own patterns. Do not ship this file.
//
// CONSUMES, never copies:
//   Check-In 02 (screen-checkin-availability-reference.jsx) — the step shell
//     and CheckInFooter. This story ADDS two props to CheckInFooter
//     (loading, error) — a variant on the one component, not a fork.
//   Check-In 03 (screen-checkin-motivation-reference.jsx) — CoachNote (lilac).
//   Check-In 01 (screen-checkin-intro-reference.jsx) — CheckInCloseButton,
//     CheckInArrow.
//   Profile 01 (profile/screen-name-reference.jsx) — the error card pattern
//     (UpliftErrorCard is that card; if the build already has it as a shared
//     component, use it) and the mocked keyboard.
//   components/shared.jsx — StatusBar, AppHeader, StepProgress, Button, Icon,
//     HomeIndicator.
//
// BUILDS: UpliftStep, upliftContact (reference detector — the server owns the
// authoritative rules), UPLIFT_* strings, CheckInFooter loading + error,
// CheckInSpinner, and the Check-In commit (Find my date).
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// FIVE STATES, ONE SCREEN.
//   A  <UpliftScreen/>                                   on entry — always empty
//   B  <UpliftScreen uplift="…" keyboard/>               typing, keyboard open (mock)
//   C  <UpliftScreen uplift="Text me on 0176 …" toast={UPLIFT_CONTACT_ERROR}/>  contact info blocked
//   D  <UpliftScreen uplift="…" commit="saving"/>        Find my date tapped, request in flight
//   E  <UpliftScreen uplift="…" commit="failed"/>        save failed (network / server)
//
// OPTIONAL. Empty commits with no nudge. 100 characters (graphemes), return =
// new line, trimmed on save. Never pre-filled, never kept (epic rule 3).
//
// NO CONTACT INFO. Emails, phone numbers and links are caught live; one string
// for all three. Find my date dims; a dimmed tap raises the same string as the
// toast. The server repeats the check; a server rejection lands in state C.
//
// INSPIRATIONS are plain italic lines — not tappable, never copied into the field.
//
// COMMIT. Find my date saves the whole Check-In. Saving (D): spinner, the
// button / field / back / ✕ inert. Success: straight to Search, no toast.
// Failure (E): everything kept, the card persists until the next tap or edit,
// the same button retries.
//
// STEP SHELL: StepProgress steps={6} current={6}. Headline 30 (step size).
// The only step with the ambient backdrop (see ScreenCheckIn step 7 in the kit).
//
// LAYOUT — the step body (headline → inspirations) is ONE scroll container,
// the only flexible element. Find my date is pinned below it, above the
// keyboard when open. Never hard-code a Y.

// Check-In 06 · decided 13 Oct 2026:
//   - OPTIONAL. Empty commits silently — no nudge, no confirm.
//   - 100 CHARACTERS (grapheme clusters, so an emoji counts as one).
//     Return inserts a NEW LINE. Trimmed on save; whitespace-only = empty.
//   - NO CONTACT INFO. Emails, phone numbers and links are detected live as
//     the user types: the field turns danger, the error card shows
//     UPLIFT_CONTACT_ERROR, and Find my date dims (a tap raises the same
//     string as the toast — never a dead button). The server repeats the
//     check on commit with the same rules.
//   - INSPIRATIONS are ideas only: plain italic lines, NOT tappable, never
//     copied into the field.
//   - LAYOUT: the step body (headline → inspirations) is ONE scroll
//     container — the only flexible element. Find my date is pinned below
//     it, above the keyboard when the keyboard is open. Fits at rest on
//     390 × 844; scrolls at 375 × 667 and whenever the keyboard is open.
const UPLIFT_MAX = 100;
const UPLIFT_CONTACT_ERROR = 'No emails, phone numbers or links allowed.';
// UI string keeps "Check-in" as drawn (epic: copy rule), not the doc term.
const UPLIFT_SAVE_ERROR = "Couldn't save your Check-in. Check your connection and try again.";
const UPLIFT_INSPIRATIONS = [
  'have 2x tickets for Eminem today',
  'will take you out on my boat on Wannsee',
  'need a co-pilot for my flight training today',
  'made fresh brownies today, free to share',
  'still looking for someone to join my dance class',
];
const upliftGraphemes = (t) => {
  if (typeof Intl !== 'undefined' && Intl.Segmenter) return [...new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(t)].map(x => x.segment);
  return [...t];
};
const upliftLength = (t) => upliftGraphemes(t).length;
const upliftClamp = (t) => upliftGraphemes(t).slice(0, UPLIFT_MAX).join('');
// Contact-info detector. Returns 'email' | 'phone' | 'link' | null.
// Reference rules — the server owns the authoritative list and runs the
// same check on commit.
const UPLIFT_LINK_RE = /\b[a-z0-9-]+\.(com|de|net|org|io|me|app|co|info|biz|link|ly|gg|tv|eu|at|ch|uk|xyz|online|site)\b/i;
function upliftContact(t) {
  const s = t || '';
  if (/[^\s@]+@[^\s@]+\.[a-z]{2,}/i.test(s)) return 'email';
  if (/(https?:\/\/|www\.)\S+/i.test(s) || UPLIFT_LINK_RE.test(s)) return 'link';
  const runs = s.match(/\+?\d[\d\s().\/-]{5,}\d/g) || [];
  if (runs.some(r => r.replace(/\D/g, '').length >= 7)) return 'phone';
  return null;
}

function UpliftErrorCard({ children }) {
  return (
    <div role="alert" style={{
      display: 'flex', alignItems: 'flex-start', gap: 10, padding: '10px 14px', borderRadius: 12,
      background: 'rgba(251,50,59,0.07)', border: '1px solid rgba(251,50,59,0.18)',
    }}>
      <span style={{
        flex: 'none', width: 18, height: 18, borderRadius: '50%', background: 'var(--liq-danger)', color: '#fff',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 12, lineHeight: 1, marginTop: 1,
      }}>!</span>
      <div style={{ fontFamily: 'var(--liq-font-sans)', fontSize: 13.5, lineHeight: 1.4, color: 'var(--liq-danger-fg)', fontWeight: 500 }}>{children}</div>
    </div>
  );
}

function UpliftStep({ uplift, setUplift, readOnly = false, focused = false }) {
  const contact = upliftContact(uplift);
  const left = UPLIFT_MAX - upliftLength(uplift);
  const border = contact ? 'var(--liq-danger)' : (focused ? 'var(--liq-primary-500)' : 'var(--liq-border)');
  return (
    <div data-uplift-body className="su-noscroll" style={{ height: '100%', overflowY: 'auto', paddingBottom: 12 }}>
      <h2 className="su-underlined" style={{
        fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
        letterSpacing: '-0.015em', lineHeight: 1.15, color: 'var(--liq-fg)',
        margin: '8px 0 0', textWrap: 'pretty',
      }}>Give people a reason to date <em>you</em> over others!</h2>

      <CoachNote color="lilac" body="Your place to offer something special and get preferred over others! This will be highly visible on your profile."/>

      <div style={{ marginTop: 14, position: 'relative' }}>
        <textarea
          aria-label="Your reason to be picked" aria-invalid={!!contact}
          value={uplift} readOnly={readOnly} onChange={e => setUplift(upliftClamp(e.target.value))}
          placeholder="e.g. If we meet today, I will take you on a rafting tour on Wannsee with Picnic :)"
          style={{
            display: 'block', width: '100%', minHeight: 116, padding: 14, borderRadius: 16,
            border: '1.5px solid ' + border,
            background: contact ? 'rgba(251,50,59,0.04)' : '#fff',
            boxShadow: contact ? '0 0 0 4px rgba(251,50,59,0.10)' : (focused ? '0 0 0 4px rgba(129,42,236,0.10)' : 'none'),
            fontFamily: 'var(--liq-font-sans)', fontSize: 14, color: 'var(--liq-fg)',
            outline: 'none', resize: 'none', lineHeight: 1.5, opacity: readOnly ? 0.7 : 1,
          }}
        />
        <div aria-live="polite" style={{ marginTop: 6, fontSize: 12, color: 'var(--liq-fg-subtle)' }}>
          {left} {left === 1 ? 'character' : 'characters'} left
        </div>
        {contact && <div style={{ marginTop: 10 }}><UpliftErrorCard>{UPLIFT_CONTACT_ERROR}</UpliftErrorCard></div>}
      </div>

      <div style={{ marginTop: 22 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--liq-fg)', marginBottom: 10 }}>
          Some inspirations that matched well
        </div>
        <ul style={{ display: 'flex', flexDirection: 'column', gap: 8, margin: 0, padding: 0, listStyle: 'none' }}>
          {UPLIFT_INSPIRATIONS.map((s, i) => (
            <li key={i} style={{
              fontFamily: 'var(--liq-font-sans)', fontSize: 14, lineHeight: 1.45, fontStyle: 'italic',
              color: 'var(--liq-fg-muted)', textWrap: 'pretty',
            }}>“{s}”</li>
          ))}
        </ul>
      </div>
    </div>
  );
}

// The screen as the step shell renders it. StepShell is the one built by
// Check-In 02 — shown here only to name the props this step passes.
function UpliftScreen({ uplift: initial = '', toast = '', commit: initialCommit = 'idle', keyboard = false }) {
  const [uplift, setUplift] = React.useState(initial);
  const [commit, setCommit] = React.useState(initialCommit);
  const blocked = !!upliftContact(uplift);
  const saving = commit === 'saving';
  return (
    <StepShell current={6} backdrop="ambient" keyboard={keyboard}
      onBack={saving ? undefined : /* → checkin_filters */ () => {}}
      onClose={saving ? undefined : /* → gate, text discarded */ () => {}}>
      <UpliftStep uplift={uplift} readOnly={saving} focused={keyboard}
        setUplift={t => { setUplift(t); if (commit === 'failed') setCommit('idle'); }}/>
      <CheckInFooter label="Find my date" disabled={blocked} toast={toast}
        loading={saving} error={commit === 'failed' ? UPLIFT_SAVE_ERROR : ''}
        onClick={() => { /* blocked → toast; else commit: setCommit('saving') → success: navigate to Search · failure: setCommit('failed') */ }}/>
    </StepShell>
  );
}

// Check-In 06 adds two props (a variant, not a fork): `loading` — spinner in
// place of the arrow, the button inert, aria-busy; `error` — a persistent
// danger card above the button until the next tap or edit.
function CheckInFooter({ label, disabled, onClick, toast, color = 'sunset', loading = false, error = '' }) {
  // The intro screen sets the tone: full-width pill in the kit's sunset
  // gradient (violet → warm). The kit has no orange button outside of
  // small icon CTAs, so we stay on sunset across every check-in step.
  const variant = 'sunset';
  return (
    <div style={{ flex: 'none', marginBottom: 22, position: 'relative' }}>
      {/* Stacked extras above the CTA. Centered to align with the full-width button. */}
      {toast && (
        <div style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
          marginBottom: 12,
        }}>
          {toast && (
            <div style={{
              padding: '8px 14px', borderRadius: 10,
              background: 'rgba(29,17,41,0.92)', color: '#fff',
              fontFamily: 'var(--liq-font-sans)', fontSize: 12.5, fontWeight: 600,
              lineHeight: 1.35, textWrap: 'pretty', maxWidth: 280, textAlign: 'center',
              boxShadow: '0 6px 20px rgba(29,17,41,0.18)',
              animation: 'su-toast-in 200ms cubic-bezier(.22,1,.36,1)',
            }}>{toast}</div>
          )}
        </div>
      )}

      {error && !toast && <div style={{ marginBottom: 12 }}><UpliftErrorCard>{error}</UpliftErrorCard></div>}

      {/* Tap-capture wrapper: lets a disabled tap still surface the toast. */}
      <div onClick={disabled ? onClick : undefined} aria-busy={loading || undefined} style={{ cursor: disabled ? 'not-allowed' : 'default', pointerEvents: loading ? 'none' : 'auto' }}>
        <div style={{
          opacity: disabled ? 0.45 : 1,
          filter: disabled ? 'saturate(0.4)' : 'none',
          pointerEvents: disabled ? 'none' : 'auto',
          transition: 'opacity 180ms, filter 180ms',
        }}>
          <Button variant={variant} size="lg" fullWidth onClick={loading ? undefined : onClick} trailing={loading ? <CheckInSpinner/> : <CheckInArrow/>}>
            {label}
          </Button>
        </div>
      </div>
    </div>
  );
}

function CheckInSpinner() {
  return <span aria-hidden="true" style={{ width: 18, height: 18, borderRadius: '50%', border: '2px solid rgba(255,255,255,0.35)', borderTopColor: '#fff', display: 'inline-block', animation: 'su-checkin-spin 800ms linear infinite' }}/>;
}

// @keyframes su-checkin-spin { to { transform: rotate(360deg); } }
