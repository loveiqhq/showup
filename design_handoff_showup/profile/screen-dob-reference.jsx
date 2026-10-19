// screen-dob-reference.jsx — Profile creation 04 · Date of birth
//
// Design reference for [Profile 04] Date of birth. This is the code that
// renders spec-sheets/04-date-of-birth.png. Read values from HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/screens-profile-dob.jsx).
// It is a reference implementation, not production code: recreate it in the
// target codebase's own environment and patterns. Do not ship this file.
//
// NumericKeypad is NOT duplicated here — it lives in
// screen-verify-email-reference.jsx, which is the same mock. Both screens use
// one keypad, and in the app neither builds it: request the platform's
// numeric keyboard.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// STATES — one component, four states, one prop. The prop only SEEDS a value;
// the screen derives its real state from what is typed:
//   A · empty              <ScreenProfileDoB/>
//   B · inline age confirm <ScreenProfileDoB state="confirm"/>    03/22/1998 → 28
//   C · invalid date       <ScreenProfileDoB state="invalid"/>    02/30/1990
//   D · under 18           <ScreenProfileDoB state="underage"/>   06/14/2010 → 15
// A fifth body — the incomplete error — is state A after Continue is pressed
// (`attempted`). It is not a separate screen.
//
// STEP 3 OF 3. StepProgress is steps={3} current={3} — the only screen in the
// group with all three segments filled. Do not animate the fill on arrival.
//
// LAYOUT — flex column, BOTTOM-ANCHORED, with ONE flex: 1 spacer above the
// visibility band:
//   StepProgress steps={3} current={3}, marginBottom 18
//   h1 .su-underlined — Lora 700 / 32 / 1.1, margin 0 0 8, wraps to two lines
//   p — Manrope 500 / 14.5, maxWidth 320
//   DoBField wrapper, marginTop 18, shake-keyed
//   status region — minHeight 84, marginTop 10, aria-live
//   spacer (flex: 1)
//   ProfileVisibility band
//   CTA row — marginTop 26, marginBottom 10
//
// THE STATUS REGION IS RESERVED AT 84 — sized to the age card, the tallest of
// the four bodies — so the band, the CTA and the keyboard sit at the identical
// Y in all four states. Never drop it to fit a failure state. (44 on name,
// 30 on verify email, 84 here.)
//
// THE AGE CARD IS THE CONFIRMATION, AND IT IS INLINE. Age is locked after this
// step, so the date, the age it produces and the warning that it is permanent
// are one glance apart — no confirm screen, no modal, no toast. The card leads
// with the COMPUTED AGE, not the typed date: a user re-reading "03/22/1998"
// checks their own input, a user reading "28" checks what the app will use.
// Lavender, not green — a question to answer, not a success message.
//
// VALIDITY IS A ROUND-TRIP CHECK, NOT A REGEX (see parseDoB). Build the date,
// then confirm it reports back the same month, day and year — otherwise
// 02/30/1990 silently becomes 2 March. Age is whole years since the date,
// decremented if this year's birthday has not passed, computed against a
// single injected "today" in LOCAL time. DOB_TODAY (18 May 2026) exists only so
// the artboards are reproducible; the app passes real time, and the backend
// re-derives age on submit.
//
// THE CTA IS NEVER DISABLED (group rule 2c). Pressing Continue on an empty or
// impossible date produces the inline error plus a one-shot su-shake 480ms and
// submits nothing. Only state B advances. The incomplete error appears only
// after a press — never mid-typing — clears on the eighth digit, and does not
// re-fire until Continue is pressed again.
//
// NEVER CLEAR THE FIELD ON A FAILURE. The fix is usually one digit; backspace
// deletes a digit and re-formats the slashes. Edit (in the age card) IS a
// deliberate clear: a wrong date is nearly always wrong in the year.
//
// NO AGE NUMERAL IN STATE D. Printing "You're 15" back at a 15-year-old adds
// nothing they don't know and hands a rejection a number.
//
// THE VISIBILITY BAND IS FINAL AS DRAWN — decided 7 Sep 2026. Label is the
// component default ("Don't display on my profile"), unchecked by default.
// CHECKED = the age is not shown on the profile; it is STILL USED for the
// matching algorithm. The choice hides a value, it never excludes the user
// from being matched. This is a named exception to flow README rule 8
// (ProfileVisibility otherwise belongs to the detail steps): date of birth is
// the only step whose value is locked, so the one moment the user can decide
// what happens to it is the moment they give it. Note the inverted prop —
// visible={!hideAge}, checked means hidden. The choice is persisted with the
// step and, unlike the age, stays changeable afterwards.
//
// ─────────────────────────────────────────────────────────────

const { useState: useStateDoB } = React;

// "Today" for age math. Defaults to a fixed date so the kit screenshots
// are stable; pass `today={new Date()}` to use wall time in a live flow.
const DOB_TODAY = new Date(2026, 4, 18); // May 18 2026

// ─────────────────────────────────────────────────────────────
// Date input — like FloatingField but auto-formats numeric digits as
// mm/dd/yyyy as the user types, and renders a date placeholder when
// empty. Drives the same focus/error/valid styling as FloatingField so
// the DoB screen reads as a sibling of the name/email screens.
// ─────────────────────────────────────────────────────────────
function DoBField({ value, onChange, focused = true, valid = false, error = false }) {
  const [isFocus, setFocus] = useStateDoB(focused);
  const active = isFocus || !!value;

  const borderColor = error
    ? 'var(--liq-danger)'
    : isFocus
      ? 'var(--liq-primary-500)'
      : valid
        ? 'rgba(0,171,85,0.55)'
        : 'var(--liq-border)';
  const halo = error
    ? '0 0 0 4px rgba(251,50,59,0.10)'
    : isFocus
      ? '0 0 0 4px rgba(129,42,236,0.10)'
      : 'none';
  const labelColor = error
    ? 'var(--liq-danger-fg)'
    : isFocus
      ? 'var(--liq-primary-500)'
      : valid
        ? 'var(--liq-success-fg)'
        : 'var(--liq-fg-faint)';

  return (
    <div style={{ position: 'relative', width: '100%' }}>
      <div style={{
        height: 64,
        borderRadius: 14,
        background: error ? 'rgba(251,50,59,0.04)' : '#fff',
        border: `1.5px solid ${borderColor}`,
        boxShadow: halo,
        padding: '0 18px',
        display: 'flex', alignItems: 'center', gap: 8,
        cursor: 'text',
        transition: 'border-color 180ms cubic-bezier(.22,1,.36,1), box-shadow 180ms, background 180ms',
      }}
        onClick={() => setFocus(true)}
      >
        <div style={{
          flex: 1, minWidth: 0,
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 17,
          color: value
            ? (error ? '#7A1F26' : 'var(--liq-fg)')
            : 'var(--liq-fg-faint)',
          fontVariantNumeric: 'tabular-nums',
          letterSpacing: value ? 0.4 : 0,
        }}>
          {value || (active ? 'mm/dd/yyyy' : '')}
          {active && isFocus && !value && (
            <span style={{
              display: 'inline-block', width: 1.5, height: 20,
              background: 'var(--liq-primary-500)', verticalAlign: 'middle',
              marginLeft: 1,
              animation: 'su-caret-blink 1s steps(2, end) infinite',
            }}/>
          )}
        </div>
        {(valid && !error) && (
          <span style={{
            flex: 'none', width: 22, height: 22, borderRadius: '50%',
            background: 'rgba(0,171,85,0.14)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            marginLeft: 8,
          }}>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
              stroke="#0A7A47" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="5 12 10 17 19 7"/>
            </svg>
          </span>
        )}
        {error && (
          <span style={{
            flex: 'none', width: 22, height: 22, borderRadius: '50%',
            background: 'var(--liq-danger)', color: '#fff',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 13,
            lineHeight: 1, marginLeft: 8,
          }}>!</span>
        )}
      </div>
      {/* Floating label — same notch treatment as FloatingField. */}
      <span aria-hidden style={{
        position: 'absolute',
        top: active ? -8 : 22,
        left: active ? 14 : 18,
        padding: active ? '0 6px' : 0,
        background: active
          ? (error ? 'var(--liq-bg)' : '#fff')
          : 'transparent',
        fontFamily: 'var(--liq-font-sans)',
        fontWeight: active ? 600 : 500,
        fontSize: active ? 12 : 17,
        letterSpacing: 0.01,
        color: active ? labelColor : 'var(--liq-fg-faint)',
        pointerEvents: 'none',
        transition: 'top 180ms cubic-bezier(.22,1,.36,1), font-size 180ms, color 180ms, padding 180ms',
      }}>
        Date of birth
      </span>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// HideAgeOptOut — deprecated alias for <ProfileVisibility> (shared.jsx).
// Kept only so older call sites render the unified switch; note the
// inverted semantics (`checked` meant hidden).
// ─────────────────────────────────────────────────────────────
function HideAgeOptOut({ checked, onToggle }) {
  return <ProfileVisibility visible={!checked} onToggle={onToggle} label="Show my age on my profile"/>;
}

// ─────────────────────────────────────────────────────────────
// Date parsing & age math
//
// parseDoB("03/22/1998") → { date: Date, age: 28, real: true }
// parseDoB("02/30/1990") → { date: null, age: null, real: false } // Feb 30 doesn't exist
// parseDoB("hello")      → null
// ─────────────────────────────────────────────────────────────
function parseDoB(s, today = DOB_TODAY) {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec((s || '').trim());
  if (!m) return null;
  const mo = +m[1], d = +m[2], y = +m[3];
  if (mo < 1 || mo > 12 || d < 1 || d > 31 || y < 1900 || y > today.getFullYear()) {
    return { date: null, age: null, real: false };
  }
  const date = new Date(y, mo - 1, d);
  // Round-trip check: new Date(1990, 1, 30) yields March 2 — if the input
  // doesn't round-trip, the calendar date didn't exist.
  const real = date.getMonth() === mo - 1 && date.getDate() === d && date.getFullYear() === y;
  if (!real) return { date: null, age: null, real: false };
  // Age = whole years since DOB, accounting for whether birthday has passed.
  let age = today.getFullYear() - y;
  const beforeBday =
    today.getMonth() < mo - 1 ||
    (today.getMonth() === mo - 1 && today.getDate() < d);
  if (beforeBday) age -= 1;
  return { date, age, real: true };
}

// Auto-format digits as mm/dd/yyyy as they're typed. Inserts the slashes
// after the user has typed enough digits for them to be unambiguous.
function appendDoBDigit(s, d) {
  const digits = (s || '').replace(/\D/g, '');
  if (digits.length >= 8) return s; // full
  const next = digits + d;
  const mo = next.slice(0, 2);
  const da = next.slice(2, 4);
  const yr = next.slice(4, 8);
  return [mo, da, yr].filter(Boolean).join('/');
}

function backspaceDoB(s) {
  const digits = (s || '').replace(/\D/g, '');
  if (!digits) return '';
  const prev = digits.slice(0, -1);
  const mo = prev.slice(0, 2);
  const da = prev.slice(2, 4);
  const yr = prev.slice(4, 8);
  return [mo, da, yr].filter(Boolean).join('/');
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileDoB — Date of birth, with inline age confirmation
//
// Props:
//   state       'default' | 'confirm' | 'invalid' | 'underage'
//                — sets the initial value + error mode so each artboard
//                  in the design canvas shows a distinct state.
//   initialValue optional override
//   today        Date — defaults to DOB_TODAY (May 18 2026) for stable
//                screenshots; pass new Date() in a live flow.
//
// The screen is fully interactive — typing flips between states live —
// but the `state` prop seeds the right starting value.
// ─────────────────────────────────────────────────────────────
function ScreenProfileDoB({
  state = 'default',
  initialValue,
  today = DOB_TODAY,
  onBack,
  onContinue,
}) {
  // Seed values that put each artboard squarely in its state.
  const seed = initialValue !== undefined ? initialValue
    : state === 'confirm'  ? '03/22/1998'   // 28 yrs on 2026-05-18
    : state === 'invalid'  ? '02/30/1990'   // Feb 30 — doesn't exist
    : state === 'underage' ? '06/14/2010'   // 15 yrs on 2026-05-18
    : '';
  const [value, setValue] = useStateDoB(seed);
  const [hideAge, setHideAge] = useStateDoB(false);
  const [shakeKey, setShakeKey] = useStateDoB(0);
  // Set once the user presses Continue on an incomplete field — that press
  // is what surfaces the "still empty" error, rather than a dead button.
  const [attempted, setAttempted] = useStateDoB(false);

  const digits = value.replace(/\D/g, '');
  const full = digits.length === 8;
  const parsed = full ? parseDoB(value, today) : null;
  // Status derivation:
  //   complete + parses + ≥18 → confirm (inline age card)
  //   complete + doesn't parse OR impossible date → invalid
  //   complete + parses + <18  → underage
  //   incomplete + pressed CTA → incomplete (error, only after a press)
  //   incomplete               → default
  const status =
    !full ? (attempted ? 'incomplete' : 'default')
    : !parsed || parsed.real === false || parsed.date === null ? 'invalid'
    : parsed.age < 18 ? 'underage'
    : 'confirm';

  // Re-trigger the shake animation on every transition into an error
  // state (so a fresh error registers kinetically, but not on every keystroke
  // while already in error).
  React.useEffect(() => {
    if (status === 'invalid' || status === 'underage') setShakeKey(k => k + 1);
  }, [status]);

  // Once they've typed a full date, a later backspace shouldn't re-fire the
  // empty-field error until they press Continue again.
  React.useEffect(() => { if (full) setAttempted(false); }, [full]);

  const isError = status === 'invalid' || status === 'underage' || status === 'incomplete';
  const isValid = status === 'confirm';

  // CTA is always live (no disabled state) — pressing it with an empty or
  // impossible date surfaces the inline error + shake instead of doing
  // nothing, which is what makes the requirement discoverable.
  const handleContinue = () => {
    if (isValid) { onContinue && onContinue(); return; }
    setAttempted(true);
    setShakeKey(k => k + 1);
  };

  const handleKey = (d) => setValue(v => appendDoBDigit(v, d));
  const handleBack = () => setValue(v => backspaceDoB(v));

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Self-contained keyframes so this screen works standalone in the
          design-canvas focus overlay. */}
      <style>{`
        @keyframes su-shake {
          10%, 90% { transform: translateX(-2px); }
          20%, 80% { transform: translateX(3px); }
          30%, 50%, 70% { transform: translateX(-6px); }
          40%, 60% { transform: translateX(6px); }
        }
        @keyframes su-caret-blink {
          0%, 49% { opacity: 1; }
          50%, 100% { opacity: 0; }
        }
        @keyframes su-confirm-in {
          from { opacity: 0; transform: translateY(6px); }
          to   { opacity: 1; transform: translateY(0); }
        }
      `}</style>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
        <AppHeader title="The basics" leading="back" onBack={onBack}/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', overflow: 'hidden',
        padding: '4px 24px 0', display: 'flex', flexDirection: 'column',
      }}>
        {/* 4-step progress; DoB is step 2 within "The basics" (after name,
            before email). Pinning this here means the canvas can show the
            new step in isolation without touching the sibling screens. */}
        <StepProgress steps={3} current={3} style={{ marginBottom: 18 }}/>

        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 32,
          letterSpacing: '-0.015em', lineHeight: 1.1, color: 'var(--liq-fg)',
          margin: '0 0 8px', textWrap: 'balance',
        }}>
          What's your <em>date of birth</em>?
        </h1>

        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
          lineHeight: 1.4, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty', maxWidth: 320,
        }}>
          Be honest — it helps us find the right matches. You must be 18 or older.
        </p>

        <div
          key={shakeKey}
          style={{
            marginTop: 18,
            animation: isError ? 'su-shake 480ms cubic-bezier(.36,.07,.19,.97) both' : undefined,
          }}
        >
          <DoBField
            value={value}
            onChange={setValue}
            focused={!isError}
            valid={isValid}
            error={isError}
          />
        </div>

        {/* Inline status area — morphs by state. Reserved minHeight keeps
            the layout still while the body changes, so the field and
            keyboard don't shift under the user. aria-live=polite means
            screen readers announce the state without stealing focus. */}
        <div
          aria-live="polite"
          style={{ minHeight: 84, marginTop: 10, paddingLeft: 2 }}
        >
          {status === 'default' && (
            <div style={{
              display: 'flex', alignItems: 'flex-start', gap: 8,
              padding: '4px 2px',
            }}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
                stroke="var(--liq-fg-muted)" strokeWidth="1.8"
                strokeLinecap="round" strokeLinejoin="round"
                style={{ flex: 'none', marginTop: 1 }}>
                <rect x="3" y="5" width="18" height="16" rx="2"/>
                <path d="M3 9h18M8 3v4M16 3v4"/>
              </svg>
              <span style={{
                fontFamily: 'var(--liq-font-sans)', fontSize: 13, lineHeight: 1.4,
                color: 'var(--liq-fg-muted)', fontWeight: 500,
              }}>
                Your <span style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>age</span> appears on your profile — not your date of birth.
              </span>
            </div>
          )}

          {status === 'confirm' && parsed && (
            <div
              key="confirm-card"
              style={{
                animation: 'su-confirm-in 240ms cubic-bezier(.22,1,.36,1) both',
                padding: '14px 16px 14px 18px',
                borderRadius: 16,
                background: 'var(--su-grad-lilac, linear-gradient(180deg, #F2EAFB 0%, #F8F2FB 100%))',
                border: '1px solid rgba(129,42,236,0.22)',
                display: 'flex', alignItems: 'center', gap: 14,
              }}
            >
              {/* Big age numeral on the left — Lora 700, primary violet.
                  Matches the score-ring readout style used elsewhere in the
                  kit (large numeric headline, role-specific color). */}
              <div style={{
                flex: 'none',
                fontFamily: 'var(--liq-font-serif)', fontWeight: 700,
                fontSize: 44, lineHeight: 1, letterSpacing: '-0.02em',
                color: 'var(--liq-primary-500)',
                fontVariantNumeric: 'tabular-nums',
              }}>
                {parsed.age}
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  fontFamily: 'var(--liq-font-sans)', fontSize: 14.5, fontWeight: 700,
                  color: 'var(--liq-fg)', lineHeight: 1.25, marginBottom: 4,
                }}>
                  You're {parsed.age}. Look right?
                </div>
                <div style={{
                  display: 'flex', alignItems: 'flex-start', gap: 6,
                  fontFamily: 'var(--liq-font-sans)', fontSize: 12.5, lineHeight: 1.35,
                  color: 'var(--liq-fg-muted)', fontWeight: 500,
                }}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
                    stroke="currentColor" strokeWidth="2"
                    strokeLinecap="round" strokeLinejoin="round"
                    style={{ flex: 'none', marginTop: 2 }}>
                    <rect x="3" y="11" width="18" height="11" rx="2"/>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                  </svg>
                  <span>
                    Locked after this step.{' '}
                    <button
                      onClick={() => setValue('')}
                      style={{
                        color: 'var(--liq-primary-500)', fontWeight: 700,
                        textDecoration: 'underline', textUnderlineOffset: 3,
                        textDecorationThickness: 1.5,
                      }}
                    >
                      Edit
                    </button>
                  </span>
                </div>
              </div>
            </div>
          )}

          {status === 'incomplete' && (
            <div style={{
              display: 'flex', alignItems: 'flex-start', gap: 10,
              padding: '10px 14px',
              borderRadius: 12,
              background: 'rgba(251,50,59,0.07)',
              border: '1px solid rgba(251,50,59,0.18)',
            }}>
              <span style={{
                flex: 'none', width: 18, height: 18, borderRadius: '50%',
                background: 'var(--liq-danger)', color: '#fff',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 12,
                lineHeight: 1, marginTop: 1,
              }}>!</span>
              <div style={{
                fontFamily: 'var(--liq-font-sans)', fontSize: 13.5, lineHeight: 1.4,
                color: 'var(--liq-danger-fg)', fontWeight: 500,
              }}>
                Enter your full date of birth to continue —{' '}
                <span style={{
                  fontFamily: 'ui-monospace, "SF Mono", Menlo, monospace',
                  fontWeight: 700, fontSize: 13,
                  background: 'rgba(251,50,59,0.10)',
                  padding: '1px 5px', borderRadius: 4,
                  margin: '0 1px',
                }}>mm/dd/yyyy</span>.
              </div>
            </div>
          )}

          {status === 'invalid' && (
            <div style={{
              display: 'flex', alignItems: 'flex-start', gap: 10,
              padding: '10px 14px',
              borderRadius: 12,
              background: 'rgba(251,50,59,0.07)',
              border: '1px solid rgba(251,50,59,0.18)',
            }}>
              <span style={{
                flex: 'none', width: 18, height: 18, borderRadius: '50%',
                background: 'var(--liq-danger)', color: '#fff',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 12,
                lineHeight: 1, marginTop: 1,
              }}>!</span>
              <div style={{
                fontFamily: 'var(--liq-font-sans)', fontSize: 13.5, lineHeight: 1.4,
                color: 'var(--liq-danger-fg)', fontWeight: 500,
              }}>
                That date doesn't exist. Please check the format —{' '}
                <span style={{
                  fontFamily: 'ui-monospace, "SF Mono", Menlo, monospace',
                  fontWeight: 700, fontSize: 13,
                  background: 'rgba(251,50,59,0.10)',
                  padding: '1px 5px', borderRadius: 4,
                  margin: '0 1px',
                }}>mm/dd/yyyy</span>.
              </div>
            </div>
          )}

          {status === 'underage' && parsed && (
            <div style={{
              display: 'flex', alignItems: 'flex-start', gap: 10,
              padding: '10px 14px',
              borderRadius: 12,
              background: 'rgba(251,50,59,0.07)',
              border: '1px solid rgba(251,50,59,0.18)',
            }}>
              <span style={{
                flex: 'none', width: 18, height: 18, borderRadius: '50%',
                background: 'var(--liq-danger)', color: '#fff',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 12,
                lineHeight: 1, marginTop: 1,
              }}>!</span>
              <div style={{
                fontFamily: 'var(--liq-font-sans)', fontSize: 13.5, lineHeight: 1.4,
                color: 'var(--liq-danger-fg)', fontWeight: 500,
              }}>
                You must be at least 18 to use Show Up. Please check the date you entered.
              </div>
            </div>
          )}
        </div>

        <div style={{ flex: 1, minHeight: 0 }}/>

        {/* Pinned visibility band — switch on the left, diagonally opposite
            the bottom-right Continue, so neither can be mis-tapped. */}
        <div style={{ flex: 'none' }}>
          <ProfileVisibility
            visible={!hideAge}
            onToggle={() => setHideAge(v => !v)}
            note={null}
          />
        </div>

        {/* Footer — single CTA, right-aligned, same as every other profile
            step. Always rendered active: validation happens on press. */}
        <div style={{
          flex: 'none',
          display: 'flex', justifyContent: 'flex-end', alignItems: 'center',
          marginTop: 26, marginBottom: 10,
        }}>
          <NextButton
            label="Continue"
            color="orange"
            onClick={handleContinue}
          />
        </div>
      </div>

      {/* Numeric keypad pinned bottom — same primitive as the verify-email
          screen, so the two read as siblings. */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <NumericKeypad onKey={handleKey} onBackspace={handleBack}/>
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, { ScreenProfileDoB, DoBField, HideAgeOptOut, parseDoB });
