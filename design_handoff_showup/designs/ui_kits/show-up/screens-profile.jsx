// screens-profile.jsx — Profile creation flow (SQPS)
//
// After identity has been verified (phone or social) the user fills in
// the things that make a profile a profile. The flow is its own
// 3-step SQPS sequence — name, photos, bio — and shares the same
// "The basics" eyebrow group as the rest of the sign-up.
//
// ─────────────────────────────────────────────────────────────
// 1. ScreenProfileName — "What's your name?"
//    - centered "The basics" header (sets the section the user is in)
//    - 3-segment StepProgress, segment 1 active
//    - italic-underlined headline + sub
//    - outlined-input with a floating violet label
//    - bottom-right "Continue" NextButton (orange — routine, not a
//      commitment screen so we don't use the sunset variant)
//    - iOS QWERTY keyboard pinned to the bottom
// ─────────────────────────────────────────────────────────────

const { useState: useStateProfile } = React;

// Outlined text input with a floating label that sits over the
// border (Material-style notch). The notch is rendered with a small
// inset background patch so the label cleanly cuts the border.
//
// Supports three optional state flags so the same component can serve
// success / error / focused states across the profile flow:
//   - `valid`: green-ish border + tick affordance on the right
//   - `error`: danger border + soft red wash + alert icon
// The floating label color tracks state so the field reads as one
// coherent component rather than a tinted box with a stray caption.
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
function ScreenProfileName({ initialValue = '', onBack, onContinue }) {
  const [name, setName] = useStateProfile(initialValue);

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
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

        <div style={{ marginTop: 28 }}>
          <FloatingField
            label="Enter first name"
            placeholder="First name"
            value={name}
            onChange={setName}
            focused={true}
          />
        </div>

        <div style={{ flex: 1 }}/>

        <div style={{
          display: 'flex', justifyContent: 'flex-end', alignItems: 'center',
          marginBottom: 18,
        }}>
          <NextButton label="Continue" color="orange" onClick={onContinue}/>
        </div>
      </div>

      {/* iOS QWERTY keyboard — pinned to the bottom, above the home
          indicator. Keys append to / pop from the demo state so the
          static artboard reads as a live screen. */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <QwertyKeyboard
          onKey={(d) => setName((s) => (s + d.toLowerCase()).replace(/^./, c => c.toUpperCase()).slice(0, 30))}
          onBackspace={() => setName((s) => s.slice(0, -1))}
          onGo={onContinue}
        />
        <HomeIndicator/>
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────────
// Marketing opt-out row — used on the email screen so a user can
// opt out of product updates while still receiving essential mail.
// The copy is the *opt-out* ("Don't send me…"), so the checkbox
// reflects the act of opting out: unchecked = receive updates,
// checked = opted out.
//
// Single-line copy. The earlier "You'll still get essential account
// mail — verification codes, receipts, security." secondary line was
// removed: it cost ~28px of vertical space and pushed the Continue
// CTA underneath the keyboard. The opt-out is opt-OUT of marketing
// only, so users keep receiving transactional mail by default — the
// secondary reassurance line wasn't earning its space.
// ──────────────────────────────────────────────────────────
function MarketingOptOut({ checked, onToggle }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      style={{
        display: 'flex', alignItems: 'center', gap: 12,
        width: '100%', textAlign: 'left',
        padding: '12px 14px',
        borderRadius: 12,
        background: 'var(--liq-bg-raised)',
        border: '1px solid var(--liq-border-soft)',
        transition: 'background 180ms',
      }}>
      <div style={{
        flex: 1, minWidth: 0,
        fontFamily: 'var(--liq-font-sans)', fontSize: 13.5,
        fontWeight: 500, lineHeight: 1.35, color: 'var(--liq-fg)',
      }}>
        Don't send me product updates or marketing email.
      </div>
      <div style={{
        flex: 'none', width: 22, height: 22, borderRadius: 6,
        background: checked ? 'var(--liq-primary-500)' : '#FFFFFF',
        border: checked ? '1.5px solid var(--liq-primary-500)' : '1.5px solid var(--liq-border)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        transition: 'background 180ms, border-color 180ms',
      }}>
        {checked && (
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
            stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="5 12 10 17 19 7"/>
          </svg>
        )}
      </div>
    </button>
  );
}

// ──────────────────────────────────────────────────────────
// ScreenProfileEmail — Profile creation, email step
//
// Two states via `error`:
//   - default: format-valid email, success-tinted field, calm helper line
//     under the field reassures the user a code will be sent.
//   - error:   the user submitted something malformed (e.g. missing TLD
//              or `@`). Value preserved — we don't wipe what they typed.
//              Specific copy names the missing piece so the fix is one
//              keystroke. Single-shot shake on the field; CTA stays
//              enabled so re-submit is one tap.
//
// Same chrome as ScreenProfileName so the two read as siblings: same
// "The basics" header, same 3-segment StepProgress, italic-underlined
// headline, NextButton bottom-right, QWERTY keyboard pinned bottom
// (email variant — @ and . in row 4).
// ──────────────────────────────────────────────────────────
function ScreenProfileEmail({ initialValue, error = false, onBack, onContinue }) {
  // Defaults that show off each state cleanly:
  //   - default: a valid email so the success tick is visible
  //   - error:   the same email mis-typed (missing TLD) so it's clearly
  //              the same primitive in two states.
  const defaultValue = initialValue !== undefined
    ? initialValue
    : (error ? 'leo@hey' : 'leo@hey.com');
  const [email, setEmail] = useStateProfile(defaultValue);
  const [optOut, setOptOut] = useStateProfile(false);
  const [shakeKey, setShakeKey] = useStateProfile(0);
  React.useEffect(() => { if (error) setShakeKey(k => k + 1); }, [error]);

  const valid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);

  // Build the error message from what the user actually typed so the
  // recovery action is specific (not "please enter a valid email").
  const errorCopy = React.useMemo(() => {
    if (!error) return null;
    if (!email) return { line: 'Enter your email so we can send a verification code.' };
    if (!email.includes('@')) return {
      line: 'Add an ', em: '@', tail: ' \u2014 e.g. you@example.com',
    };
    const after = email.split('@')[1] || '';
    if (!after.includes('.')) return {
      line: 'Looks like the domain is missing ', em: '.com', tail: ' (or similar).',
    };
    return { line: 'That email doesn\u2019t look quite right. Please check it.' };
  }, [error, email]);

  // CTA gating: in default state require a valid format. In the error
  // state keep it enabled so the user can re-submit after one edit —
  // greying it out forces an extra "what now?" beat.
  const ctaDisabled = error ? false : !valid;

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Self-contained keyframes — the shake also lives inside
          ScreenVerifyCode, but defining it here means this screen
          works on its own (e.g. opened in design-canvas focus). */}
      <style>{`@keyframes su-shake {
        10%, 90% { transform: translateX(-2px); }
        20%, 80% { transform: translateX(3px); }
        30%, 50%, 70% { transform: translateX(-6px); }
        40%, 60% { transform: translateX(6px); }
      }`}</style>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
        <AppHeader title="The basics" leading="back" onBack={onBack}/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', overflow: 'hidden',
        padding: '4px 24px 0', display: 'flex', flexDirection: 'column',
      }}>
        <StepProgress steps={3} current={2} style={{ marginBottom: 18 }}/>

        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 32,
          letterSpacing: '-0.015em', lineHeight: 1.1, color: 'var(--liq-fg)',
          margin: '0 0 8px', textWrap: 'balance',
        }}>
          What's your <em>email</em>?
        </h1>

        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
          lineHeight: 1.4, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty', maxWidth: 320,
        }}>
          Never shown on your profile.<br/>Used for password reset, sending receipts, reaching out if you reported an issue.
        </p>

        {/* Field — wrapped in a shake-keyed div so the failure registers
            kinetically. Re-rendered with a new key on each error to
            re-trigger the one-shot shake animation. */}
        <div
          key={shakeKey}
          style={{
            marginTop: 18,
            animation: error ? 'su-shake 480ms cubic-bezier(.36,.07,.19,.97) both' : undefined,
          }}
        >
          <FloatingField
            label="Email"
            placeholder="you@example.com"
            type="email"
            value={email}
            onChange={setEmail}
            focused={!error}
            valid={valid && !error}
            error={!!error}
          />
        </div>

        {/* Helper / error line — sits immediately under the field so cause
            and effect are colocated. aria-live="polite" so screen readers
            announce the failure without yanking focus. */}
        <div
          aria-live="polite"
          style={{ minHeight: 40, marginTop: 10, paddingLeft: 2 }}
        >
          {error ? (
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
                {errorCopy.line}
                {errorCopy.em && (
                  <span style={{
                    fontFamily: 'ui-monospace, "SF Mono", Menlo, monospace',
                    fontWeight: 700, fontSize: 13,
                    background: 'rgba(251,50,59,0.10)',
                    padding: '1px 5px', borderRadius: 4,
                    margin: '0 1px',
                  }}>{errorCopy.em}</span>
                )}
                {errorCopy.tail}
              </div>
            </div>
          ) : (
            <div style={{
              display: 'flex', alignItems: 'flex-start', gap: 8,
              padding: '4px 2px',
            }}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
                stroke="var(--liq-success-fg)" strokeWidth="2"
                strokeLinecap="round" strokeLinejoin="round"
                style={{ flex: 'none', marginTop: 1 }}>
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              <span style={{
                fontFamily: 'var(--liq-font-sans)', fontSize: 13, lineHeight: 1.4,
                color: 'var(--liq-fg-muted)', fontWeight: 500,
              }}>
                We'll send a 6-digit code to confirm it's really you.
              </span>
            </div>
          )}
        </div>

        <div style={{ marginTop: 10 }}>
          <MarketingOptOut checked={optOut} onToggle={() => setOptOut(v => !v)}/>
        </div>

        <div style={{ flex: 1 }}/>

        {/* Footer — Skip for now (left) + primary Continue (right).
            Skip exists because users who signed in via Apple/Google
            upstream already provided an email; the email step is
            optional for them.

            marginBottom is tight (10px) so the Continue CTA clears
            the keyboard in both default and error states — the error
            callout takes ~18px more vertical space than the success
            helper, and we want the button visible in both. */}
        <div style={{
          display: 'flex', justifyContent: 'flex-end',
          marginBottom: 10,
          opacity: ctaDisabled ? 0.42 : 1,
          filter: ctaDisabled ? 'saturate(0.4)' : 'none',
          pointerEvents: ctaDisabled ? 'none' : 'auto',
          transition: 'opacity 180ms, filter 180ms',
        }}>
          <NextButton label="Continue" color="orange" onClick={onContinue}/>
        </div>
      </div>

      {/* QWERTY keyboard — email variant has @ and . in row 4 */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <QwertyKeyboard
          email
          suggestions={[email || 'you', 'gmail.com', 'hey.com']}
          onKey={(d) => setEmail((s) => (s + d.toLowerCase()).slice(0, 64))}
          onBackspace={() => setEmail((s) => s.slice(0, -1))}
          onGo={onContinue}
        />
        <HomeIndicator/>
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────────
// Numeric keypad — iOS dialer/passcode style. Lives here (not in
// shared.jsx) because it's only needed by ScreenVerifyEmail; phone
// verification has its own variant in screens-welcome.jsx.
// ──────────────────────────────────────────────────────────
function NumericKeypad({ onKey, onBackspace }) {
  const KEYS = [
    { d: '1', s: '' },
    { d: '2', s: 'A B C' },
    { d: '3', s: 'D E F' },
    { d: '4', s: 'G H I' },
    { d: '5', s: 'J K L' },
    { d: '6', s: 'M N O' },
    { d: '7', s: 'P Q R S' },
    { d: '8', s: 'T U V' },
    { d: '9', s: 'W X Y Z' },
    null,
    { d: '0', s: '+' },
    'back',
  ];
  const Key = ({ d, s }) => (
    <button onClick={() => onKey && onKey(d)} style={{
      flex: 1, height: '100%', borderRadius: 6, background: '#FFFFFF',
      boxShadow: '0 1px 0 rgba(29,17,41,0.18)',
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      padding: '4px 0', transition: 'background 120ms',
    }}
      onMouseDown={e => e.currentTarget.style.background = '#E6E1DC'}
      onMouseUp={e => e.currentTarget.style.background = '#FFFFFF'}
      onMouseLeave={e => e.currentTarget.style.background = '#FFFFFF'}>
      <span style={{
        fontFamily: '-apple-system, "SF Pro Display", system-ui',
        fontWeight: 400, fontSize: 24, lineHeight: 1, color: 'var(--liq-fg)', letterSpacing: 0.4,
      }}>{d}</span>
      {s && <span style={{
        fontFamily: '-apple-system, "SF Pro Display", system-ui',
        fontWeight: 600, fontSize: 9, lineHeight: 1, color: 'var(--liq-fg-muted)',
        letterSpacing: 1.4, marginTop: 3,
      }}>{s}</span>}
    </button>
  );
  const Back = () => (
    <button onClick={onBackspace} style={{
      flex: 1, height: '100%', borderRadius: 6, background: 'transparent',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      <svg width="26" height="20" viewBox="0 0 26 20" fill="none">
        <path d="M8.5 2H23a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8.5L1 10l7.5-8z"
          stroke="var(--liq-fg)" strokeWidth="1.4" strokeLinejoin="round" fill="none"/>
        <path d="M12 7l6 6M18 7l-6 6"
          stroke="var(--liq-fg)" strokeWidth="1.6" strokeLinecap="round"/>
      </svg>
    </button>
  );
  return (
    <div style={{
      background: '#D1CDC8',
      padding: '8px 3px 4px',
      display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)',
      gridAutoRows: 46, gap: 6,
    }}>
      {KEYS.map((k, i) => {
        if (k === null) return <div key={i}/>;
        if (k === 'back') return <Back key={i}/>;
        return <Key key={i} d={k.d} s={k.s}/>;
      })}
    </div>
  );
}

// ──────────────────────────────────────────────────────────
// CodeSlot — one of six input cells for the verification code.
// Empty / active (caret) / filled / error states. Error uses a
// soft danger wash + 4px halo, never a solid red fill.
// ──────────────────────────────────────────────────────────
function CodeSlot({ value, active, error }) {
  const borderColor = error
    ? 'var(--liq-danger)'
    : active
      ? 'var(--liq-primary-500)'
      : value
        ? 'rgba(29,17,41,0.32)'
        : 'var(--liq-border)';
  const halo = error
    ? '0 0 0 4px rgba(251,50,59,0.10)'
    : active
      ? '0 0 0 4px rgba(129,42,236,0.10)'
      : '0 1px 2px rgba(46,1,71,0.04)';
  return (
    <div style={{
      width: 49, height: 62, borderRadius: 14, flex: 'none',
      background: error ? 'rgba(251,50,59,0.04)' : '#FFFFFF',
      border: `1.5px solid ${borderColor}`,
      boxShadow: halo,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
      color: error ? '#7A1F26' : 'var(--liq-fg)',
      transition: 'border-color 180ms cubic-bezier(.22,1,.36,1), box-shadow 180ms, background 180ms',
      fontVariantNumeric: 'tabular-nums',
    }}>
      {value || (active && !error
        ? <span style={{
            display: 'inline-block', width: 2, height: 26,
            background: 'var(--liq-primary-500)',
            animation: 'su-caret-blink 1s steps(2, end) infinite',
          }}/>
        : null
      )}
    </div>
  );
}

// ──────────────────────────────────────────────────────────
// ScreenVerifyEmail — Profile creation, email verification step.
//
// Sits between ScreenProfileEmail and ScreenProfilePhotos as part of
// the "The basics" sub-flow. Same chrome: "The basics" header,
// 3-segment StepProgress (step 2 — still on the email step until the
// code is confirmed), italic-underlined headline.
//
// Two states via `error`:
//   - default: empty slots, caret on slot 1, fresh 24s resend cooldown
//   - error:   digits the user just submitted are PRESERVED so they can
//              correct only what's wrong (wiping 6 slots on a near-miss
//              is hostile). Resend cooldown released. Single-shot shake
//              on the slot row. Verify CTA stays visible — no modal in
//              the way; the error sits inline beneath the slots.
// ──────────────────────────────────────────────────────────
function ScreenVerifyEmail({ email = 'leo@hey.com', error = false, onBack, onContinue }) {
  const [digits, setDigits] = useStateProfile(error ? ['4', '8', '2', '1', '7', '0'] : ['', '', '', '', '', '']);
  const activeIdx = digits.findIndex(d => !d);
  const filled = digits.every(d => d);

  // Default: just sent → 24s cooldown.
  // Error:   previous send is presumed old enough → resend immediately available.
  const [cooldown, setCooldown] = useStateProfile(error ? 0 : 24);
  React.useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown(c => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  const [shakeKey, setShakeKey] = useStateProfile(0);
  React.useEffect(() => { if (error) setShakeKey(k => k + 1); }, [error]);

  const handleKey = (d) => {
    setDigits(prev => {
      const idx = prev.findIndex(x => !x);
      if (idx === -1) return prev;
      const next = [...prev]; next[idx] = d; return next;
    });
  };
  const handleBack = () => {
    setDigits(prev => {
      let idx = prev.length - 1;
      while (idx >= 0 && !prev[idx]) idx--;
      if (idx < 0) return prev;
      const next = [...prev]; next[idx] = ''; return next;
    });
  };

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Self-contained keyframes so this screen works in isolation
          (focus-overlay in design canvas, or standalone). */}
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
      `}</style>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
        <AppHeader title="The basics" leading="back" onBack={onBack}/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', zIndex: 1, overflow: 'hidden',
        padding: '4px 24px 0', display: 'flex', flexDirection: 'column',
      }}>
        <StepProgress steps={3} current={2} style={{ marginBottom: 18 }}/>

        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 32,
          letterSpacing: '-0.015em', lineHeight: 1.1, color: 'var(--liq-fg)',
          margin: '0 0 8px', textWrap: 'balance',
        }}>
          Please verify your <em>email</em>.
        </h1>

        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
          lineHeight: 1.4, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty', maxWidth: 320,
        }}>
          We sent a 6-digit code to{' '}
          <span style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>
            {email}
          </span>.
        </p>

        {/* Code slots — shake-keyed for one-shot failure feedback */}
        <div
          key={shakeKey}
          style={{
            marginTop: 22, display: 'flex', gap: 8, justifyContent: 'space-between',
            animation: error ? 'su-shake 480ms cubic-bezier(.36,.07,.19,.97) both' : undefined,
          }}
          aria-label="Enter your 6-digit verification code"
        >
          {digits.map((d, i) => (
            <CodeSlot key={i} value={d} active={!error && i === activeIdx} error={error}/>
          ))}
        </div>

        {/* Inline helper / error — glued to the slots so cause and effect
            are colocated. aria-live=polite announces failure without
            stealing focus. */}
        <div
          aria-live="polite"
          style={{ minHeight: 30, marginTop: 12, paddingLeft: 2 }}
        >
          {error ? (
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
                That code doesn't match. Check your inbox or request a new one.
              </div>
            </div>
          ) : null}
        </div>

        {/* Primary CTA — always visible. Disabled until 6 digits in
            default state; enabled in error state so re-submit is one tap. */}
        <div style={{ marginTop: 14 }}>
          <Button variant="sunset" size="lg" fullWidth disabled={!error && !filled} onClick={onContinue}>
            Verify code
          </Button>
        </div>

        {/* Secondary actions — text-styled so the sunset CTA stays the
            sole visual destination. */}
        <div style={{
          marginTop: 18, display: 'flex', flexDirection: 'column',
          alignItems: 'center', gap: 8,
        }}>
          <div style={{
            fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 600,
            color: 'var(--liq-fg)', lineHeight: 1.3,
          }}>
            Didn't receive a code?
          </div>
          {cooldown > 0 ? (
            <span style={{
              fontFamily: 'var(--liq-font-sans)', fontSize: 13, fontWeight: 600,
              color: 'var(--liq-fg-subtle)',
              fontVariantNumeric: 'tabular-nums',
            }}>
              Send a new code in 0:{String(cooldown).padStart(2, '0')}
            </span>
          ) : (
            <button style={{
              fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 700,
              color: 'var(--liq-primary-500)',
              textDecoration: 'underline', textUnderlineOffset: 3,
              textDecorationThickness: 1.5,
              padding: '2px 4px',
            }}>
              Send a new code
            </button>
          )}

          {/* Always-visible escape hatch — wrong-email is a common cause
              of "I never got the code". */}
          <button onClick={onBack} style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            fontFamily: 'var(--liq-font-sans)', fontSize: 13, fontWeight: 500,
            color: 'var(--liq-fg-muted)',
            padding: '2px 4px', marginTop: 2,
          }}>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" strokeWidth="1.8"
              strokeLinecap="round" strokeLinejoin="round">
              <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
              <path d="M15 5l4 4"/>
            </svg>
            To edit your email, simply go back
          </button>
        </div>

        <div style={{ flex: 1 }}/>
      </div>

      {/* Numeric keypad pinned bottom — above the home indicator */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <NumericKeypad onKey={handleKey} onBackspace={handleBack}/>
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, { ScreenProfileName, ScreenProfileEmail, ScreenVerifyEmail, QwertyKeyboard, NumericKeypad, FloatingField, MarketingOptOut, CodeSlot });
