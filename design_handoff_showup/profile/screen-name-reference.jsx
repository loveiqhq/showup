// screen-name-reference.jsx — Profile creation 01 · Name
//
// Design reference for [Profile 01] Name. This is the code that renders
// spec-sheets/01-name.png. Read values from HERE, not from the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/screens-profile.jsx).
// It is a reference implementation, not production code: recreate it in the
// target codebase's own environment and patterns. Do not ship this file.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// STATES — one component, three states, two props:
//   A · empty, field focused   <ScreenProfileName/>
//   B · value entered          <ScreenProfileName initialValue="Leo"/>
//   C · empty submit (error)   <ScreenProfileName state="empty-submit"/>
//   All three share one layout column. Nothing moves between them.
//
// LAYOUT — flex column, no absolute Y anywhere:
//   StatusBar 54 (fixed)
//   AppHeader 52 (fixed) — title "The basics", BOTH slots empty (see below)
//   content flex: 1, padding 4 / 24 / 0, overflow hidden
//     StepProgress steps={3} current={1}, marginBottom 28
//     h1 .su-underlined — Lora 700 / 34 / 1.1 / -0.015em, margin 0 0 10
//     p  — Manrope 500 / 15 / 1.45 / --liq-neutral-200, maxWidth 320
//     FloatingField wrapper, marginTop 28
//     status region — minHeight 44, marginTop 10, paddingLeft 2, aria-live
//     <div style={{ flex: 1, minHeight: 0 }}/>   <-- the ONE spacer
//     NextButton row, justify flex-end, marginBottom 18
//   QwertyKeyboard (fixed) + HomeIndicator 28 (fixed)
//
// NO BACK BUTTON ON THIS SCREEN. The user arrives from the app tutorial and
// profile creation is mandatory once entered — there is nothing behind this
// screen to return to, so AppHeader gets leading="none" and the component
// takes no onBack prop at all. The empty 36 slot still occupies its width so
// the centred title does not shift. Steps 2 and 3 DO have a back button:
// build the group's header shell with the leading slot as a variant, not a
// constant. Also suppress the iOS swipe-back gesture and the Android
// hardware / gesture back here — a header with no chevron that the OS can
// still dismiss is worse than no rule at all.
//
// THE FLOW RESUMES WHERE THE USER LEFT OFF. Progress is persisted per
// completed step, so a user who quits mid-flow and relaunches lands on their
// last incomplete step — not here. This screen is the entry point ONLY when
// no saved progress exists. Nothing about that is visible in this file; it is
// a flow-level behaviour and it is specced in the ticket and the flow README.
// What this screen owns: persist the name on Continue, before navigating.
//
// THE STATUS REGION IS RESERVED, NOT CONDITIONAL. minHeight 44 holds the
// space in A and B so the CTA and the keyboard do not move when the error
// card appears in C. Render it conditionally and the three states stop
// reading as one screen. Same rule as the phone and DoB screens.
//
// EMPTY SUBMIT — the whole of state C:
//   The CTA is NEVER disabled. Pressing Continue (or the keyboard's Go)
//   with an empty field sets the "attempted" flag, which surfaces the error
//   card and fires a one-shot su-shake on the field wrapper. A disabled
//   button cannot explain itself; the press is what teaches the requirement.
//   This is the pattern already settled on the DoB screen (status
//   "incomplete") — the error card is the SAME card, and it should be one
//   component across name, email and DoB, not three copies.
//   Recovery: the error clears on the first character typed (the "filled"
//   effect resets "attempted"), not on blur and not on re-submit. Clearing
//   a typed name does not re-fire it until Continue is pressed again.
//   FOCUS STAYS IN THE FIELD in the error state (focused={true}); only the
//   error styling changes. Do not blur the user out of the input.
//
// THE KEYBOARD IS A MOCK. QwertyKeyboard is drawn only so the artboard shows
// the true content height above it (286 + 28 home indicator, leaving 424 of
// content at 390 x 844). Ship the real iOS / Android keyboard. Do not build,
// restyle or assume the height of this component — it varies by OS, language
// and prediction settings, and it is why the flex: 1 spacer exists.
//
// FIELD — FloatingField carries valid / error affordances shared with the
// SIBLING screens in this flow (email, DoB). On the name screen "valid" is
// never set: no tick, no counter. "error" is set only by an empty submit.
//
// CTA — NextButton color="orange" (--liq-orange-500 + --liq-shadow-cta),
// NOT the sunset gradient. Sunset is reserved for commitment screens.
// The label and the circle are one hit area.
//
// MOCK-ONLY BEHAVIOUR, do not port as-is:
//   - onKey force-capitalises the first letter and slices at 30 chars.
//     The 30 is an unconfirmed cap and the force-capitalisation is a demo
//     convenience — see the ticket's Open section.
//   - state="empty-submit" seeds "attempted" so the artboard renders the
//     error statically. In the app the only way into it is a real press.
//
// DEPENDENCIES from ../components/shared.jsx:
//   StatusBar · AppHeader · StepProgress · NextButton · HomeIndicator · Icon
// TOKENS from ../tokens/colors_and_type.css, incl. .su-underlined em
// (the italic emphasis + orange radial wash). Never re-derive that rule.
// The su-shake keyframes are inlined in the screen so it runs standalone.
//
// EXPORTS: FloatingField, QwertyKeyboard, ScreenProfileName
// ─────────────────────────────────────────────────────────────

const { useState: useStateProfile } = React;

function FloatingField({
  label, value, onChange, placeholder, focused = true,
  type = 'text', valid = false, error = false, trailing,
}) {
  const [isFocus, setFocus] = useStateProfile(focused);
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

  // Default trailing affordance: success tick or error glyph. Callers
  // can pass `trailing` to override (e.g. an explicit clear button).
  const trailingNode = trailing !== undefined ? trailing
    : error ? (
      <span style={{
        flex: 'none', width: 22, height: 22, borderRadius: '50%',
        background: 'var(--liq-danger)', color: '#fff',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 13,
        lineHeight: 1, marginLeft: 8,
      }}>!</span>
    ) : valid ? (
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
    ) : null;

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
        transition: 'border-color 180ms cubic-bezier(.22,1,.36,1), box-shadow 180ms, background 180ms',
      }}>
        <input
          value={value || ''}
          onChange={e => onChange && onChange(e.target.value)}
          onFocus={() => setFocus(true)}
          onBlur={() => setFocus(false)}
          placeholder={active ? placeholder : ''}
          type={type}
          autoCapitalize={type === 'email' ? 'none' : undefined}
          spellCheck={type === 'email' ? false : undefined}
          style={{
            flex: 1, minWidth: 0, border: 'none', outline: 'none', background: 'transparent',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 17,
            color: 'var(--liq-fg)',
          }}
        />
        {trailingNode}
      </div>
      {/* Floating label — sits on top of the border with a small white
          patch behind it so it visually notches the stroke. */}
      <span
        aria-hidden
        style={{
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
        }}
      >
        {label}
      </span>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// iOS-style QWERTY keyboard
//
// Matches the Show Up numeric keypad's bg/key colors so the two
// keyboards feel like the same OS surface. Keys are presentational
// in static mocks — taps update internal demo state.
//
// Layout:
//   suggestion strip (3 cells, divider lines)
//   row 1: Q W E R T Y U I O P            (10 keys)
//   row 2: A S D F G H J K L              (9 keys, centred)
//   row 3: ⇧ Z X C V B N M ⌫              (shift + 7 letters + backspace)
//   row 4: 123 · 🌐 · space · Go          (4 cells, space wide)
//   chrome row: emoji-glyph · mic         (system row below keyboard)
// ─────────────────────────────────────────────────────────────
const ROW_1 = ['Q','W','E','R','T','Y','U','I','O','P'];
const ROW_2 = ['A','S','D','F','G','H','J','K','L'];
const ROW_3 = ['Z','X','C','V','B','N','M'];

// `email` mode swaps row 4 from `123 | space | Go` to
// `123 | @ | space | . | Go` so a user can fill an email without
// hopping into the symbols keyboard. Matches iOS' email-input keyboard.
function QwertyKeyboard({ suggestions = ['Suggest', 'Suggest', 'Suggest'], onKey, onBackspace, onGo, email = false }) {
  const KEY_H = 42;
  const KEY_RADIUS = 5;
  const KEY_BG = '#FFFFFF';
  const KEY_SHADOW = '0 1px 0 rgba(29,17,41,0.28)';
  const SYS_BG = 'rgba(172,167,162,0.95)'; // shift / 123 / backspace

  const baseKey = {
    height: KEY_H,
    borderRadius: KEY_RADIUS,
    background: KEY_BG,
    boxShadow: KEY_SHADOW,
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    fontFamily: '-apple-system, "SF Pro Display", system-ui',
    fontWeight: 400, fontSize: 22, lineHeight: 1, color: 'var(--liq-fg)',
    transition: 'background 100ms',
  };

  const Letter = ({ d }) => (
    <button onClick={() => onKey && onKey(d)} style={{ ...baseKey, flex: 1 }}
      onMouseDown={e => e.currentTarget.style.background = '#E6E1DC'}
      onMouseUp={e => e.currentTarget.style.background = KEY_BG}
      onMouseLeave={e => e.currentTarget.style.background = KEY_BG}>
      {d}
    </button>
  );

  // Shift / backspace / 123 use the system-gray fill, slightly wider.
  const SysKey = ({ children, onClick, width = '1.4fr', label }) => (
    <button onClick={onClick} aria-label={label} style={{
      ...baseKey,
      background: SYS_BG,
      boxShadow: '0 1px 0 rgba(29,17,41,0.22)',
    }}>
      {children}
    </button>
  );

  return (
    <div style={{
      background: '#D1CDC8',
      paddingTop: 0,
      flex: 'none',
      fontFamily: '-apple-system, "SF Pro Display", system-ui',
      userSelect: 'none',
    }}>
      {/* Suggestion strip */}
      <div style={{
        height: 42, display: 'grid',
        gridTemplateColumns: '1fr 1fr 1fr',
        alignItems: 'center',
        padding: '0 4px',
      }}>
        {suggestions.map((s, i) => (
          <div key={i} style={{
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            height: 28, fontFamily: '-apple-system, "SF Pro Display", system-ui',
            fontWeight: 400, fontSize: 14, color: 'var(--liq-fg)',
            borderLeft: i === 0 ? 'none' : '1px solid rgba(29,17,41,0.18)',
          }}>{s}</div>
        ))}
      </div>

      {/* Letter rows */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '4px 3px 0' }}>
        {/* Row 1 — 10 keys flush */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(10, 1fr)', gap: 6 }}>
          {ROW_1.map(d => <Letter key={d} d={d} />)}
        </div>

        {/* Row 2 — 9 keys, half-key inset on each side */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(9, 1fr)',
          gap: 6,
          padding: '0 18px',
        }}>
          {ROW_2.map(d => <Letter key={d} d={d} />)}
        </div>

        {/* Row 3 — shift + 7 letters + backspace.
            Grid: 1.4fr | 7 × 1fr | 1.4fr — so the shift/backspace
            mirror each other and the M sits where the keyboard
            picture expects. */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '1.4fr repeat(7, 1fr) 1.4fr',
          gap: 6,
        }}>
          <SysKey label="Shift">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none"
              stroke="var(--liq-fg)" strokeWidth="1.6" strokeLinejoin="round" strokeLinecap="round">
              <path d="M8 1.5L1.5 7.5H4.5V13H11.5V7.5H14.5L8 1.5Z" fill="var(--liq-fg)"/>
            </svg>
          </SysKey>
          {ROW_3.map(d => <Letter key={d} d={d} />)}
          <SysKey label="Backspace" onClick={onBackspace}>
            <svg width="22" height="16" viewBox="0 0 26 20" fill="none">
              <path d="M8.5 2H23a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8.5L1 10l7.5-8z"
                fill="var(--liq-fg)" fillOpacity="0.04"
                stroke="var(--liq-fg)" strokeWidth="1.4" strokeLinejoin="round"/>
              <path d="M12 7l6 6M18 7l-6 6"
                stroke="var(--liq-fg)" strokeWidth="1.6" strokeLinecap="round"/>
            </svg>
          </SysKey>
        </div>

        {/* Row 4 — default: 123 | space | Go.
            Email mode: 123 | @ | space | . | Go. The @ and . keys are
            white letter-keys (not system gray) because they're input,
            not modifiers. */}
        {email ? (
          <div style={{
            display: 'grid',
            gridTemplateColumns: '1.4fr 1fr 3.6fr 1fr 1.4fr',
            gap: 6,
            paddingBottom: 6,
          }}>
            <SysKey label="Numbers">
              <span style={{ fontSize: 16, fontWeight: 400 }}>123</span>
            </SysKey>
            <button onClick={() => onKey && onKey('@')} style={{ ...baseKey, fontSize: 20 }}
              onMouseDown={e => e.currentTarget.style.background = '#E6E1DC'}
              onMouseUp={e => e.currentTarget.style.background = KEY_BG}
              onMouseLeave={e => e.currentTarget.style.background = KEY_BG}>@</button>
            <button onClick={() => onKey && onKey(' ')} style={{
              ...baseKey, background: KEY_BG, fontSize: 14, color: 'var(--liq-fg)',
            }}>space</button>
            <button onClick={() => onKey && onKey('.')} style={{ ...baseKey, fontSize: 20 }}
              onMouseDown={e => e.currentTarget.style.background = '#E6E1DC'}
              onMouseUp={e => e.currentTarget.style.background = KEY_BG}
              onMouseLeave={e => e.currentTarget.style.background = KEY_BG}>.</button>
            <SysKey label="Go" onClick={onGo}>
              <span style={{ fontSize: 16, fontWeight: 600 }}>Go</span>
            </SysKey>
          </div>
        ) : (
          <div style={{
            display: 'grid',
            gridTemplateColumns: '1.4fr 5fr 1.4fr',
            gap: 6,
            paddingBottom: 6,
          }}>
            <SysKey label="Numbers">
              <span style={{ fontSize: 16, fontWeight: 400 }}>123</span>
            </SysKey>
            <button style={{
              ...baseKey,
              background: KEY_BG,
              fontSize: 14, color: 'var(--liq-fg)',
            }}>space</button>
            <SysKey label="Go" onClick={onGo}>
              <span style={{ fontSize: 16, fontWeight: 600 }}>Go</span>
            </SysKey>
          </div>
        )}
      </div>

      {/* OS chrome row below the keyboard — emoji-keyboard switcher
          on the left, dictation on the right. These are OS UI, not
          product content, so the no-emoji brand rule doesn't apply;
          we still draw them as stroke glyphs for visual consistency
          with the rest of the kit. */}
      <div style={{
        height: 36, padding: '0 18px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        background: '#D1CDC8',
      }}>
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none"
          stroke="var(--liq-fg)" strokeWidth="1.6"
          strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="9"/>
          <circle cx="9" cy="10" r="0.5" fill="var(--liq-fg)"/>
          <circle cx="15" cy="10" r="0.5" fill="var(--liq-fg)"/>
          <path d="M8.5 14.5C9.5 16 10.7 16.6 12 16.6C13.3 16.6 14.5 16 15.5 14.5"/>
        </svg>
        <svg width="16" height="22" viewBox="0 0 16 24" fill="none"
          stroke="var(--liq-fg)" strokeWidth="1.6"
          strokeLinecap="round" strokeLinejoin="round">
          <rect x="5" y="2" width="6" height="12" rx="3" fill="var(--liq-fg)" stroke="none"/>
          <path d="M2.5 11C2.5 14.6 5 17.5 8 17.5C11 17.5 13.5 14.6 13.5 11"/>
          <path d="M8 17.5V21M5.5 21h5"/>
        </svg>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileName — Profile creation, step 1 of 3
// ─────────────────────────────────────────────────────────────
function ScreenProfileName({ initialValue = '', state, onBack, onContinue }) {
  const [name, setName] = useStateProfile(initialValue);
  // Set once the user presses Continue / Go on an empty field — that press
  // is what surfaces the requirement, rather than a dead button.
  const [attempted, setAttempted] = useStateProfile(state === 'empty-submit');
  const [shakeKey, setShakeKey] = useStateProfile(0);

  const filled = name.trim().length > 0;
  const isError = attempted && !filled;

  // Once they type, a later clear shouldn't re-fire the error until they
  // press Continue again.
  React.useEffect(() => { if (filled) setAttempted(false); }, [filled]);

  // CTA is always live (no disabled state) — pressing it while empty
  // surfaces the inline error + shake instead of doing nothing.
  const handleContinue = () => {
    if (filled) { onContinue && onContinue(); return; }
    setAttempted(true);
    setShakeKey(k => k + 1);
  };

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Self-contained keyframes so this screen works standalone in the
          design-canvas focus overlay. Same shake as the DoB screen. */}
      <style>{`
        @keyframes su-shake {
          10%, 90% { transform: translateX(-2px); }
          20%, 80% { transform: translateX(3px); }
          30%, 50%, 70% { transform: translateX(-6px); }
          40%, 60% { transform: translateX(6px); }
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
        <StepProgress steps={3} current={1} style={{ marginBottom: 28 }}/>

        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 34,
          letterSpacing: '-0.015em', lineHeight: 1.1, color: 'var(--liq-fg)',
          margin: '0 0 10px', textWrap: 'balance',
        }}>
          What's your <em>name</em>?
        </h1>

        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15,
          lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty', maxWidth: 320,
        }}>
          Your name will be shown on your profile.
        </p>

        <div
          key={shakeKey}
          style={{
            marginTop: 28,
            animation: isError ? 'su-shake 480ms cubic-bezier(.36,.07,.19,.97) both' : undefined,
          }}
        >
          <FloatingField
            label="Enter first name"
            placeholder="First name"
            value={name}
            onChange={setName}
            focused={true}
            error={isError}
          />
        </div>

        {/* Inline status region — reserved, not conditional, so the CTA and
            the keyboard never move when the error appears. Empty in the
            default and filled states. Same card as the DoB screen's
            incomplete state. */}
        <div aria-live="polite" style={{ minHeight: 44, marginTop: 10, paddingLeft: 2 }}>
          {isError && (
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
                Enter your name to continue.
              </div>
            </div>
          )}
        </div>

        <div style={{ flex: 1, minHeight: 0 }}/>

        <div style={{
          display: 'flex', justifyContent: 'flex-end', alignItems: 'center',
          marginBottom: 18,
        }}>
          <NextButton label="Continue" color="orange" onClick={handleContinue}/>
        </div>
      </div>

      {/* iOS QWERTY keyboard — pinned to the bottom, above the home
          indicator. Keys append to / pop from the demo state so the
          static artboard reads as a live screen. */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <QwertyKeyboard
          onKey={(d) => setName((s) => (s + d.toLowerCase()).replace(/^./, c => c.toUpperCase()).slice(0, 30))}
          onBackspace={() => setName((s) => s.slice(0, -1))}
          onGo={handleContinue}
        />
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, { ScreenProfileName, QwertyKeyboard, FloatingField });
