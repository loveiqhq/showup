// screen-verify-email-reference.jsx — Profile creation 03 · Verify email
//
// Design reference for [Profile 03] Verify email. This is the code that
// renders spec-sheets/03-verify-email.png. Read values from HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/screens-profile.jsx).
// It is a reference implementation, not production code: recreate it in the
// target codebase's own environment and patterns. Do not ship this file.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// STATES — one component, two states, one prop:
//   A · enter code      <ScreenVerifyEmail/>
//   B · code mismatch   <ScreenVerifyEmail error={true}/>
//
// STILL STEP 2 OF 3. StepProgress is current={2}, identical to the email
// screen's. The user is on the email step until the code is confirmed —
// verification is not a fourth step. Do not add a segment and do not
// animate the bar on arrival.
//
// LAYOUT — flex column, top-anchored, with the ONE flex: 1 spacer at the
// BOTTOM of the content column:
//   StepProgress steps={3} current={2}, marginBottom 18
//   h1 .su-underlined — Lora 700 / 32 / 1.1, margin 0 0 8
//   p — Manrope 500 / 14.5, with the address inline at 700 / --liq-fg
//   slot row, marginTop 22, gap 8, space-between, shake-keyed
//   status region — minHeight 30, marginTop 12, aria-live
//   CTA, marginTop 14
//   secondary actions, marginTop 18, column, centred, gap 6
//   spacer
//
// BOTTOM-SLACK, NOT BOTTOM-ANCHORED — unlike steps 1 and 2, whose CTA the
// spacer pushes to the bottom. Here everything is one top-anchored stack and
// the spacer absorbs what is left, so the CTA sits directly under the slots
// it belongs to.
//
// SLOT ARITHMETIC: 6 x 49 + 5 x 8 = 334 against a 342 content width, laid
// out space-between so the row sits flush with no slot stretching. DO NOT
// switch the slots to flex: 1 — at 375 they would drop below 44 and stop
// being reliable targets. Let the gap absorb the difference.
//
// THE DIGITS ARE PRESERVED ON FAILURE (see the error branch of the digits
// initial state). This is the single most important behaviour on the screen:
// wiping six slots on a near-miss is hostile — the user usually mistyped one
// digit, and clearing the row forces them to re-read the whole code from
// their inbox. Backspace then corrects from the right, as normal.
//
// IN THE ERROR STATE NO SLOT IS ACTIVE and there is no caret — the whole row
// is wrong, so highlighting one position would lie about where the problem
// is. Digit colour goes to #7A1F26, darker than --liq-danger-fg, because a
// 30px Lora digit at full danger red vibrates against the wash.
//
// THE COOLDOWN IS RELEASED BY THE FAILURE. State A arrives with a fresh 24s
// cooldown (a code was just sent); state B shows the resend link already
// live. DO NOT re-arm the cooldown on a wrong code — making the user wait
// after a failure punishes them for the app's own ambiguity about which
// recovery they need. The 24 is the design's number; match the backend's
// real resend limit.
//
// THE STATUS REGION IS RESERVED (minHeight 30), so the CTA and both
// secondary actions stay at the same Y in both states. Same rule as step 1;
// the email screen is the exception, not this one.
//
// THE CTA IS THE GROUP'S ONLY FULL-WIDTH, SUNSET BUTTON. Button
// variant="sunset" size="lg" fullWidth, label "Verify code". Sunset is
// reserved for commitment screens and confirming a code is the only
// irreversible act in "The basics" — do not use the round orange NextButton
// here, and do not use sunset anywhere else in this group. It is disabled
// until all six digits are filled in state A, and stays enabled in the error
// state so retry is one tap. (The disabled-in-default behaviour contradicts
// the flow rule; see the ticket.)
//
// THE CHANGE-EMAIL LINK is the most important control after the CTA. A wrong
// address is the usual cause of a code that never arrives, and without it the
// user's only recourse is the header chevron — which does not look like it
// fixes anything. The redundancy with back is INTENTIONAL; keep both.
// The address echoed in the sub copy is what makes the link findable.
//
// RESEND / QUESTION ON ONE BASELINE ROW. Stacking "Didn't receive a code?"
// above the action reads as two separate offers. The countdown uses
// tabular-nums so it does not jitter.
//
// THE KEYPAD IS A MOCK. NumericKeypad lives in this file rather than shared
// because only this screen uses it (phone verification has its own variant).
// Request the numeric / one-time-code keyboard and let the platform render
// it — including autofill. Never spec its height. The mock is shorter than
// the QWERTY one, which is why this screen can afford a full-width CTA plus
// two secondary rows where the email step cannot.
//
// ONE LOGICAL INPUT, SIX BOXES. Paste of a 6-digit string must fill all six,
// and platform email/SMS autofill must be able to populate the row in one go.
//
// DEPENDENCIES from ../components/shared.jsx:
//   StatusBar · AppHeader · StepProgress · Button · HomeIndicator
// TOKENS from ../tokens/colors_and_type.css, incl. .su-underlined em.
// su-shake and su-caret-blink keyframes are inlined so it runs standalone.
//
// EXPORTS: NumericKeypad, CodeSlot, ScreenVerifyEmail
// ─────────────────────────────────────────────────────────────

const { useState: useStateProfile } = React;

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

        {/* Secondary actions — resend on ONE line ("Didn't receive a code?"
            + timer/link), then the change-email escape hatch beneath it.
            Wrong address is the most common cause of "I never got it". */}
        <div style={{
          marginTop: 18, display: 'flex', flexDirection: 'column',
          alignItems: 'center', gap: 6,
        }}>
          <div style={{
            display: 'flex', alignItems: 'baseline', justifyContent: 'center',
            flexWrap: 'wrap', gap: 6,
          }}>
            <span style={{
              fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 600,
              color: 'var(--liq-fg)', lineHeight: 1.3,
            }}>
              Didn't receive a code?
            </span>
            {cooldown > 0 ? (
              <span style={{
                fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 600,
                color: 'var(--liq-fg-subtle)', lineHeight: 1.3,
                fontVariantNumeric: 'tabular-nums',
              }}>
                Send a new code in 0:{String(cooldown).padStart(2, '0')}
              </span>
            ) : (
              <button style={{
                fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 700,
                color: 'var(--liq-primary-500)', lineHeight: 1.3,
                textDecoration: 'underline', textUnderlineOffset: 3,
                textDecorationThickness: 1.5,
              }}>
                Send a new code
              </button>
            )}
          </div>

          <button onClick={onBack} style={{
            fontFamily: 'var(--liq-font-sans)', fontSize: 13.5, fontWeight: 600,
            color: 'var(--liq-fg-muted)', lineHeight: 1.3,
            textDecoration: 'underline', textUnderlineOffset: 3,
            textDecorationThickness: 1,
            padding: '2px 4px',
          }}>
            Change email address
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

Object.assign(window, { ScreenProfileName, ScreenProfileEmail, ScreenVerifyEmail, QwertyKeyboard, NumericKeypad, FloatingField, MarketingOptIn, CodeSlot });

Object.assign(window, { ScreenVerifyEmail, NumericKeypad, CodeSlot });
