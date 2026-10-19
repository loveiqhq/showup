// screen-phone-reference.jsx — Show Up · Phone verification (4 states)
//
// This is the DESIGN REFERENCE the Phone verification ticket was written from.
// It is the same code that renders the attached spec-sheet PNG, so every number
// in that sheet exists here as a real value. Read values from this file rather
// than measuring the PNG.
//
// It is NOT the implementation. It is React + inline styles in a browser
// prototype; the app is iOS-first. Port the structure and the values, not
// the file.
//
// FOUR STATES, TWO SCREENS, ONE COLUMN:
//   A  <ScreenPhoneNumber />            enter number
//   B  <ScreenPhoneNumber error />      invalid number
//   C  <ScreenVerifyCode />             enter code
//   D  <ScreenVerifyCode error />       code mismatch
// The `error` prop is the mock's switch for the failure state. In the app the
// failure comes from validation / the verify call — the prop is only how the
// artboards show you the target.
//
// What matters, in order:
//   1. One flex column, everything top-anchored — status bar (54) / AppHeader /
//      content (flex: 1, padding 4 24 0) / keypad / home indicator (28). A
//      SINGLE flex: 1 spacer sits at the bottom of the content column. Nothing
//      is positioned by Y.
//   2. BOTH HELPER REGIONS ARE RESERVED, NOT CONDITIONAL — min-height 20 on
//      A / B, min-height 62 on C / D — 62 because the verify error box is 60
//      tall at the 342 content width. Reserve the FAILURE state's height, not
//      the empty state's. The error text replaces the helper text
//      in a box that already exists, so the CTA never jumps. This is what makes
//      the four states feel like one screen.
//   3. FAILURE PRESERVES INPUT. The invalid number stays in the field; the
//      mismatched code stays in the slots. Auto-clearing a near-miss punishes
//      the user for a single mistyped digit — never do it.
//   4. Calm danger: 1.5px --liq-danger border + a 4px halo + (on the slots) a
//      4%-alpha wash. Never a solid red fill, never a toast over the CTA.
//   5. On mismatch the resend COOLDOWN IS RELEASED to 0 — a stale or mistyped
//      code must not cost the user another 24s wait — and the row does one
//      480ms shake, then holds still.
//   6. aria-live="polite" on both helper regions: the failure is announced
//      without pulling focus out of the input.
//
// THE KEYPAD IS A MOCK. <NumericKeypad> exists so the artboards show the true
// content height above the system keyboard (≈214 + 28). Ship the real iOS /
// Android numeric keypad; do not build this component.
//
// Entry: the phone method on Welcome back, or the CTA on Startup.
// Exits: A → C on send · C → the app on success · back on C → A with the
// number kept · "Edit phone number" → A.
//
// Depends on: StatusBar, AppHeader, Eyebrow, Button, Icon, HomeIndicator
//   -> design_handoff_showup/components/shared.jsx
// Tokens (--liq-*, --su-*): design_handoff_showup/tokens/colors_and_type.css

function FlagDE({ size = 20 }) {
  // 3:5 ratio. Drawn as inline rectangles — no emoji per brand rules.
  return (
    <span aria-label="Germany" style={{
      width: size, height: size * 0.6, borderRadius: 3, overflow: 'hidden',
      display: 'inline-flex', flexDirection: 'column', flex: 'none',
      boxShadow: '0 0 0 0.5px rgba(29,17,41,0.18)',
    }}>
      <span style={{ flex: 1, background: '#000' }}/>
      <span style={{ flex: 1, background: '#DD0000' }}/>
      <span style={{ flex: 1, background: '#FFCC00' }}/>
    </span>
  );
}

function NumericKeypad({ onKey, onBackspace }) {
  // iOS-style numeric pad. 3 cols × 4 rows. The 1 key has no letters; 2–9
  // carry the canonical letter triplet/quadruplet below the digit; 0 has
  // a "+" subscript (long-press semantics on iOS); bottom-right is ⌫.
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
    null,                     // blank slot
    { d: '0', s: '+' },
    'back',                   // backspace
  ];

  const Key = ({ d, s }) => (
    <button onClick={() => onKey && onKey(d)} style={{
      flex: 1, height: '100%',
      borderRadius: 6,
      background: '#FFFFFF',
      boxShadow: '0 1px 0 rgba(29,17,41,0.18)',
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      gap: 0, padding: '4px 0',
      transition: 'background 120ms, transform 120ms',
    }}
      onMouseDown={e => e.currentTarget.style.background = '#E6E1DC'}
      onMouseUp={e => e.currentTarget.style.background = '#FFFFFF'}
      onMouseLeave={e => e.currentTarget.style.background = '#FFFFFF'}>
      <span style={{
        fontFamily: '-apple-system, "SF Pro Display", system-ui',
        fontWeight: 400, fontSize: 24, lineHeight: 1, color: 'var(--liq-fg)',
        letterSpacing: 0.4,
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
      flex: 1, height: '100%', borderRadius: 6,
      background: 'transparent',
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
      display: 'grid',
      gridTemplateColumns: 'repeat(3, 1fr)',
      gridAutoRows: 46,
      gap: 6,
    }}>
      {KEYS.map((k, i) => {
        if (k === null) return <div key={i}/>;
        if (k === 'back') return <Back key={i}/>;
        return <Key key={i} d={k.d} s={k.s}/>;
      })}
    </div>
  );
}

function ScreenPhoneNumber({ error = false, onBack, onSubmit }) {
  // Two demo digit strings so both artboards read instantly:
  //   • default: a plausible DE mobile in pleasant chunks
  //   • error:   a too-short string so the helper text reads as the fix
  const [digits, setDigits] = useState(error ? '0151 2' : '176 123 45 678');
  const isInvalid = error;

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Soft sunset orb backdrop — same atmosphere as ScreenLogin */}
      <div style={{
        position: 'absolute', top: '-18%', right: '-30%', width: 460, height: 460,
        background: 'radial-gradient(circle, rgba(254,104,57,0.26) 0%, rgba(254,104,57,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>
      <div style={{
        position: 'absolute', top: '-10%', left: '-25%', width: 420, height: 420,
        background: 'radial-gradient(circle, rgba(129,42,236,0.22) 0%, rgba(129,42,236,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
        <AppHeader title="" leading="back" onBack={onBack}/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', zIndex: 1, overflow: 'hidden',
        padding: '4px 24px 0', display: 'flex', flexDirection: 'column',
      }}>
        <Eyebrow color="orange">Phone verification</Eyebrow>

        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 38,
          letterSpacing: '-0.02em', lineHeight: 1.05, color: 'var(--liq-fg)',
          margin: '14px 0 10px', textWrap: 'balance',
        }}>
          What's your <em>number</em>?
        </h1>

        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15,
          lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty', maxWidth: 320,
        }}>
          We'll send a 6-digit code to verify it is you.
        </p>

        {/* Country selector + phone input */}
        <div style={{ marginTop: 24, display: 'flex', gap: 8 }}>
          {/* Country pill */}
          <button style={{
            display: 'flex', alignItems: 'center', gap: 8,
            height: 56, padding: '0 14px',
            borderRadius: 14, background: '#fff',
            border: '1.5px solid var(--liq-border)',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 16,
            color: 'var(--liq-fg)',
          }}>
            <FlagDE size={22}/>
            <span style={{ marginLeft: 2 }}>+49</span>
            <Icon name="chevron-down" size={16} stroke={2} style={{ color: 'var(--liq-fg-muted)', marginLeft: 2 }}/>
          </button>

          {/* Phone number input */}
          <div style={{
            flex: 1, display: 'flex', alignItems: 'center', gap: 10,
            height: 56, padding: '0 18px',
            borderRadius: 14, background: '#fff',
            border: `1.5px solid ${isInvalid ? 'var(--liq-danger)' : 'var(--liq-border)'}`,
            transition: 'border-color 180ms',
            boxShadow: isInvalid ? '0 0 0 4px rgba(251,50,59,0.10)' : 'none',
          }}>
            <span style={{
              flex: 1,
              fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 17,
              color: digits ? 'var(--liq-fg)' : 'var(--liq-fg-faint)',
              letterSpacing: 0.3,
              fontVariantNumeric: 'tabular-nums',
              overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
            }}>
              {digits || '176 123 45 678'}
              {/* iOS-style blinking caret — drawn, not animated, so the
                  static artboard reads as an active input */}
              <span style={{
                display: 'inline-block', width: 2, height: 20,
                marginLeft: 2, marginBottom: -3,
                background: 'var(--liq-primary-500)',
                verticalAlign: 'middle',
              }}/>
            </span>
            {isInvalid && (
              <span style={{
                width: 22, height: 22, borderRadius: '50%',
                background: 'var(--liq-danger)', color: '#fff',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 14,
                lineHeight: 1, flex: 'none',
              }}>!</span>
            )}
          </div>
        </div>

        {/* Helper / error line directly under the input row */}
        <div style={{
          minHeight: 20, marginTop: 10, paddingLeft: 4,
          display: 'flex', alignItems: 'center', gap: 6,
        }}>
          {isInvalid ? (
            <span style={{
              fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 13,
              color: 'var(--liq-danger-fg)', lineHeight: 1.35,
            }}>
              Please enter a valid number e.g. 176 123 45 678
            </span>
          ) : (
            <span style={{
              fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13,
              color: 'var(--liq-fg-subtle)', lineHeight: 1.35,
            }}>
              Standard message rates may apply.
            </span>
          )}
        </div>

        {/* Primary CTA — sunset gradient, full-width. Disabled in error state. */}
        <div style={{ marginTop: 22 }}>
          <Button variant="sunset" size="lg" fullWidth disabled={isInvalid} onClick={onSubmit}>
            Send me the code
          </Button>
        </div>

        <div style={{ flex: 1 }}/>
      </div>

      {/* Numeric keypad — pinned to the bottom, above the home indicator */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <NumericKeypad
          onKey={(d) => setDigits((s) => (s + d).slice(0, 20))}
          onBackspace={() => setDigits((s) => s.slice(0, -1))}
        />
        <HomeIndicator/>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// 1d. Phone-number verification — the screen after ScreenPhoneNumber.
//   - back button (returns to ScreenPhoneNumber so the user can edit)
//   - "Enter your code." — same italic-emphasis pattern
//   - six code slots; active slot shows a violet caret + soft halo so
//     the user knows where the next digit will land
//   - "We just sent a 6-digit code to <phone>" subtitle echoes the
//     phone number the user submitted on the previous step
//   - "Verify code" primary CTA (sunset), disabled until 6 digits entered
//   - secondary actions stacked below: resend (cooldown timer → button
//     at 0:00) and edit phone number (always available)
//   - iOS numeric keypad pinned to the bottom (above the home indicator)
//
//   `error` prop renders the code-mismatch variant. Modern OTP-failure UX:
//     • DIGITS ARE PRESERVED — let the user fix one digit, don't wipe
//       the whole code on failure (auto-clearing is a hostile pattern
//       that punishes a near-miss).
//     • Calm danger — 1.5px danger border + 4-px halo + 4%-alpha wash
//       on the slot bg; never a solid red fill.
//     • Inline error message glued to the slots, not a toast over the
//       CTA — cause and recovery stay co-located.
//     • One-shot horizontal shake on the slot row to confirm the
//       attempt registered, then back to a still UI.
//     • Cooldown released — if the SMS was wrong (typed digit, stale
//       code), the user shouldn't have to wait through a fresh 24s
//       timer to try a different code.
//     • aria-live="polite" on the helper region so screen readers
//       announce the failure without yanking focus from the input.
//     • Brand-voice copy: "That code doesn't match" — informative, not
//       cautionary. No "Wrong" / "Failed" / "Error".
// ─────────────────────────────────────────────────────────────
function PhoneCodeSlot({ value, active, error }) {
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

function ScreenVerifyCode({ error = false, phone = '+49 176 123 45 678', onBack, onSubmit }) {
  // Default state: empty slots, first one active (caret).
  // Error state:   digits the user just submitted are preserved.
  const [digits, setDigits] = useState(error ? ['4', '8', '2', '1', '7', '0'] : ['', '', '', '', '', '']);
  const activeIdx = digits.findIndex(d => !d);
  const filled = digits.every(d => d);

  // Resend cooldown. In the error state the previous send is treated as
  // stale — cooldown = 0 — so the user can resend immediately.
  const [cooldown, setCooldown] = useState(error ? 0 : 24);
  React.useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown(c => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  // One-shot shake on the slot row when entering the error state.
  const [shakeKey, setShakeKey] = useState(0);
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
      {/* Inject the caret-blink keyframes locally so the kit doesn't
          require a page-level <style>. Safe to mount multiple times. */}
      <style>{`@keyframes su-caret-blink { 0%, 49% { opacity: 1; } 50%, 100% { opacity: 0; } }
        @keyframes su-shake {
          10%, 90% { transform: translateX(-2px); }
          20%, 80% { transform: translateX(3px); }
          30%, 50%, 70% { transform: translateX(-6px); }
          40%, 60% { transform: translateX(6px); }
        }`}</style>

      {/* Soft sunset orb backdrop — same atmosphere as ScreenPhoneNumber */}
      <div style={{
        position: 'absolute', top: '-18%', right: '-30%', width: 460, height: 460,
        background: 'radial-gradient(circle, rgba(254,104,57,0.22) 0%, rgba(254,104,57,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>
      <div style={{
        position: 'absolute', top: '-10%', left: '-25%', width: 420, height: 420,
        background: 'radial-gradient(circle, rgba(129,42,236,0.20) 0%, rgba(129,42,236,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
        <AppHeader title="" leading="back" onBack={onBack}/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', zIndex: 1, overflow: 'hidden',
        padding: '4px 24px 0', display: 'flex', flexDirection: 'column',
      }}>
        <Eyebrow color="orange">Phone verification</Eyebrow>

        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 38,
          letterSpacing: '-0.02em', lineHeight: 1.05, color: 'var(--liq-fg)',
          margin: '14px 0 10px', textWrap: 'balance',
        }}>
          Enter your <em>code</em>.
        </h1>

        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15,
          lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty', maxWidth: 320,
        }}>
          We just sent a 6-digit code to{' '}
          <span style={{ color: 'var(--liq-fg)', fontWeight: 700, whiteSpace: 'nowrap' }}>
            {phone}
          </span>.
        </p>

        {/* Code slots */}
        <div
          key={shakeKey}
          style={{
            marginTop: 26, display: 'flex', gap: 8, justifyContent: 'space-between',
            animation: error ? 'su-shake 480ms cubic-bezier(.36,.07,.19,.97) both' : undefined,
          }}
          aria-label="Enter your 6-digit verification code"
        >
          {digits.map((d, i) => (
            <PhoneCodeSlot key={i} value={d} active={!error && i === activeIdx} error={error}/>
          ))}
        </div>

        {/* Helper / error — IMMEDIATELY under the inputs so the association is unambiguous.
            aria-live makes it discoverable to assistive tech without stealing focus. */}
        <div aria-live="polite" style={{ minHeight: 62, marginTop: 14, paddingLeft: 2 }}>
          {error ? (
            <div style={{
              display: 'flex', alignItems: 'flex-start', gap: 10,
              padding: '10px 14px', borderRadius: 12,
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
                color: 'var(--liq-danger-fg)',
              }}>
                <span style={{ fontWeight: 500 }}>
                  Code doesn't match. Please check or request a new code.
                </span>
              </div>
            </div>
          ) : null}
        </div>

        {/* Primary CTA — disabled until the user has entered 6 digits.
            In the error state the digits are still filled, so the button
            stays enabled — try again after editing the wrong digit. */}
        <div style={{ marginTop: 16 }}>
          <Button variant="sunset" size="lg" fullWidth disabled={!filled} onClick={onSubmit}>
            Verify code
          </Button>
        </div>

        {/* Secondary actions, stacked vertically.
            (1) Resend with cooldown — text → button at 0:00.
            (2) Edit phone number — always available; sends them back. */}
        <div style={{
          marginTop: 22, display: 'flex', flexDirection: 'column',
          alignItems: 'center', gap: 10,
        }}>
          <div style={{
            fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 500,
            color: 'var(--liq-fg-muted)', lineHeight: 1.3,
          }}>
            Didn't receive a code?
          </div>
          {cooldown > 0 ? (
            <span style={{
              fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 600,
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

          <button onClick={onBack} style={{
            display: 'inline-flex', alignItems: 'center', gap: 7,
            fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 600,
            color: 'var(--liq-fg-muted)',
            padding: '4px 8px',
          }}>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" strokeWidth="1.8"
              strokeLinecap="round" strokeLinejoin="round">
              <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
              <path d="M15 5l4 4"/>
            </svg>
            Edit phone number
          </button>
        </div>

        <div style={{ flex: 1 }}/>
      </div>

      {/* Numeric keypad pinned to the bottom — above the home indicator */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <NumericKeypad
          onKey={handleKey}
          onBackspace={handleBack}
        />
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, { FlagDE, NumericKeypad, ScreenPhoneNumber, PhoneCodeSlot, ScreenVerifyCode });
