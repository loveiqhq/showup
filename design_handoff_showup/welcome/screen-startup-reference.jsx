// screen-startup-reference.jsx — Show Up · Startup (first run)
//
// This is the DESIGN REFERENCE the Startup ticket was written from. It is the
// same code that renders the attached spec-sheet PNG, so every number in that
// sheet exists here as a real value. Read values from this file rather than
// measuring the PNG.
//
// It is NOT the implementation. It is React + inline styles in a browser
// prototype; the app is iOS-first. Port the structure and the values, not
// the file.
//
// What matters, in order:
//   1. The layout is one flex column — status bar (54) / content (flex: 1,
//      padding 20 24 0) / home indicator (28). Nothing is positioned by Y.
//   2. TWO <div style={{ flex: 1 }}/> spacers, not one: the social-proof row
//      floats between them, optically centred in the empty band. At 390x844
//      they resolve to ~86 each.
//   3. The three background layers are full-bleed (z 0) and run BEHIND the
//      status bar and home indicator, which sit at z 2.
//   4. The 132px wordmark -> headline gap is the only large fixed gap, and it
//      is the element that yields on short frames. Never the type, never the
//      CTA's safe-area margin, never a scroll.
//
// This screen is first run only. Once an account exists on the device, launch
// opens "Welcome back" (ScreenLogin) instead, which is where phone / Apple /
// Google / Facebook live. The only two exits here are the CTA (sign-up) and
// the "Log in" link (Welcome back).
//
// Depends on: StatusBar, Wordmark, Button, Icon, HomeIndicator
//   -> design_handoff_showup/components/shared.jsx
// Tokens (--liq-*, --su-*): design_handoff_showup/tokens/colors_and_type.css

function ScreenStartup({ onContinue, onLogin }) {
  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Ambient backdrop — full-bleed, three layers, z 0.
          NOTE: this is a hand-rolled variant of <Atmosphere variant="hero"/>
          (hero is orange .26 / violet .22 and has no peach wash). Open
          question in the ticket: reconcile with the token set. */}
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

      {/* Chrome sits ON TOP of the backdrop. In the app, anchor to
          env(safe-area-inset-top), not to this mock's 54px status bar. */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', zIndex: 1,
        padding: '20px 24px 0',
        display: 'flex', flexDirection: 'column', overflow: 'hidden',
      }}>
        <Wordmark size={26}/>

        {/* 132 is the flexible gap — shrink this first on short frames */}
        <div style={{ marginTop: 132, display: 'flex', flexDirection: 'column', gap: 18 }}>
          <h1 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 44,
            letterSpacing: '-0.02em', lineHeight: 1.05, color: 'var(--liq-fg)',
            margin: 0, textWrap: 'balance',
          }}>
            <span style={{
              display: 'block', fontSize: 32, fontWeight: 700,
              letterSpacing: '-0.015em', marginBottom: 4,
            }}>Stop texting for days.</span>
            Start <em>meeting</em> today.
          </h1>
          <p style={{
            fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 19,
            lineHeight: 1.4, color: 'var(--liq-fg)', margin: 0,
            textWrap: 'pretty', maxWidth: 320,
          }}>
            Your availability. Your intent. Your date — today or tomorrow.
          </p>
        </div>

        <div style={{ flex: 1 }}/>

        {/* Social proof — centred between the intro copy and the legal line */}
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          marginBottom: 14,
        }}>
          <Icon name="calendar" size={16} stroke={2}
            style={{ color: 'var(--liq-orange-500)' }}/>
          <span style={{
            fontFamily: 'var(--liq-font-sans)', fontSize: 13, fontWeight: 500,
            color: 'var(--liq-fg-muted)', letterSpacing: 0.01,
          }}>
            <span style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>234.000 Dates</span> already organized
          </span>
        </div>

        <div style={{ flex: 1 }}/>

        {/* The three phrases must ship as real links with their own hit areas */}
        <p style={{
          fontSize: 12, color: 'var(--liq-fg-subtle)',
          textAlign: 'center', margin: '0 0 14px', lineHeight: 1.45,
        }}>
          By creating an account, you agree to our <span style={{ color: 'var(--liq-fg)', fontWeight: 600 }}>Terms &amp; Conditions</span> and acknowledge that you have read our <span style={{ color: 'var(--liq-fg)', fontWeight: 600 }}>Privacy Policy</span>. See our <span style={{ color: 'var(--liq-fg)', fontWeight: 600 }}>Legal Notice</span>.
        </p>

        <div style={{ marginBottom: 10 }}>
          <Button variant="sunset" size="lg" fullWidth onClick={onContinue}>
            Create free account
          </Button>
        </div>

        {/* Hit area is ~31 here; the app must reach 44 without changing the type */}
        <div style={{
          display: 'flex', justifyContent: 'center', marginBottom: 14,
        }}>
          <button type="button" onClick={onLogin}
            style={{
              appearance: 'none', background: 'transparent', border: 'none',
              padding: '6px 8px', cursor: 'pointer',
              fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 600,
              color: 'var(--liq-fg-muted)', letterSpacing: 0.01,
            }}>
            Already have an account?{' '}
            <span style={{
              color: 'var(--liq-primary-500)', textDecoration: 'underline',
              textUnderlineOffset: 3, textDecorationThickness: 1.5,
            }}>Log in</span>
          </button>
        </div>

      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, { ScreenStartup });
