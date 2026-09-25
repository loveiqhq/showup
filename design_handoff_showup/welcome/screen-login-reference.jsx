// screen-login-reference.jsx — Show Up · Welcome back (re-login)
//
// This is the DESIGN REFERENCE the Welcome back ticket was written from. It is
// the same code that renders the attached spec-sheet PNG, so every number in
// that sheet exists here as a real value. Read values from this file rather
// than measuring the PNG.
//
// It is NOT the implementation. It is React + inline styles in a browser
// prototype; the app is iOS-first. Port the structure and the values, not
// the file.
//
// What matters, in order:
//   1. The layout is one flex column — status bar (54) / content (flex: 1,
//      padding 20 24 0) / home indicator (28). Nothing is positioned by Y.
//   2. TWO <div style={{ flex: 1 }}/> spacers, not one: the button stack
//      floats between them, optically centred in the lower half rather than
//      pinned to the bottom. At 390x844 they resolve to ~65 each.
//   3. The three background layers are IDENTICAL to Startup's, value for
//      value, and full-bleed (z 0) behind the status bar and home indicator
//      (z 2). Build the backdrop once and share it — two copies will drift.
//   4. lastUsed drives which method is the sunset CTA; the other three follow
//      in canonical order as ghost buttons. Always four buttons, never a
//      duplicate, never a missing method.
//   5. The 120px wordmark -> headline gap is the element that yields on short
//      frames. Never a button height, never a dropped method, never a scroll.
//
// This screen is the return path. A device with no account opens "Startup"
// (ScreenStartup) instead. The headline carries the member's name, which is
// dynamic — design for 2-24 characters and for no name at all.
//
// Depends on: StatusBar, Wordmark, Button, Icon, HomeIndicator
//   -> design_handoff_showup/components/shared.jsx
// Tokens (--liq-*, --su-*): design_handoff_showup/tokens/colors_and_type.css

const CANONICAL = ['phone', 'apple', 'google', 'facebook'];

// Label, icon and hint per auth method. The hint string is device state
// ("this device signed in with X last time"), not a user preference.
const METHODS = {
  phone:    { label: 'Continue with phone number', icon: 'phone',    hint: 'Last login was via phone' },
  apple:    { label: 'Continue with Apple',        icon: 'apple',    hint: 'Last login was via Apple' },
  google:   { label: 'Continue with Google',       icon: 'google',   hint: 'Last login was via Google' },
  facebook: { label: 'Continue with Facebook',     icon: 'facebook', hint: 'Last login was via Facebook' },
};

function ScreenLogin({ onContinue, lastUsed = 'phone', name = 'Leo' }) {
  // Unknown or unsupported value falls back to phone.
  const primary = CANONICAL.includes(lastUsed) ? lastUsed : 'phone';
  const rest = CANONICAL.filter(k => k !== primary);

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Ambient backdrop — full-bleed, three layers, z 0.
          Same values as Startup. This duplication is the known drift called
          out in the ticket: promote it to one shared component or an
          <Atmosphere> variant rather than porting it twice. */}
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
        {/* Same size and position as Startup — the wordmark must not shift
            between the two launch screens */}
        <Wordmark size={26}/>

        {/* 120 is the flexible gap — shrink this first on short frames */}
        <div style={{ marginTop: 120, display: 'flex', flexDirection: 'column', gap: 18 }}>
          <h1 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 44,
            letterSpacing: '-0.02em', lineHeight: 1.05, color: 'var(--liq-fg)',
            margin: 0, textWrap: 'balance',
          }}>
            {/* The name is dynamic. A long name adds a line here — budget for
                it rather than truncating. With no name, the headline is
                "Welcome back" and the <em> is omitted. */}
            Welcome back <em>{name}</em>
          </h1>
          <p style={{
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 17,
            lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
            textWrap: 'pretty', maxWidth: 280,
          }}>
            Sign back in to check your availability and see who's free today.
          </p>
        </div>

        <div style={{ flex: 1 }}/>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 12 }}>
          {/* "Last login was via …" hint sits directly above the primary CTA.
              Hide the whole row when the last method is unknown — do not
              guess a method here. */}
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
            marginBottom: 2,
          }}>
            <span style={{
              width: 6, height: 6, borderRadius: '50%',
              background: 'var(--liq-orange-500)',
              boxShadow: '0 0 0 3px rgba(254,104,57,0.18)',
            }}/>
            <span style={{
              fontFamily: 'var(--liq-font-sans)', fontSize: 12, fontWeight: 600,
              color: 'var(--liq-fg-muted)', letterSpacing: 0.01,
            }}>
              {METHODS[primary].hint}
            </span>
          </div>

          {/* The last-used method is the only sunset button on the screen */}
          <Button variant="sunset" size="lg" fullWidth onClick={onContinue}
            leading={<Icon name={METHODS[primary].icon} size={18}/>}>
            {METHODS[primary].label}
          </Button>

          {/* The other three, canonical order, ghost variant */}
          {rest.map(k => (
            <Button key={k} variant="ghost" size="lg" fullWidth
              leading={<Icon name={METHODS[k].icon} size={18}/>}>
              {METHODS[k].label}
            </Button>
          ))}
        </div>

        <div style={{ flex: 1 }}/>

        {/* "Get help" and "Use a different account" must ship as real links */}
        <p style={{
          fontSize: 12, color: 'var(--liq-fg-subtle)',
          textAlign: 'center', margin: '0 0 8px', lineHeight: 1.45,
        }}>
          Trouble signing in? <span style={{ color: 'var(--liq-fg)', fontWeight: 600 }}>Get help</span> or <span style={{ color: 'var(--liq-fg)', fontWeight: 600 }}>Use a different account</span>
        </p>

        {/* No Terms & Conditions here — consent was given at sign-up */}
        <p style={{
          fontSize: 11.5, color: 'var(--liq-fg-subtle)',
          textAlign: 'center', margin: '0 0 8px', lineHeight: 1.45,
        }}>
          <span style={{ color: 'var(--liq-fg-muted)', fontWeight: 600, textDecoration: 'underline', textUnderlineOffset: 2 }}>Legal Notice</span> · <span style={{ color: 'var(--liq-fg-muted)', fontWeight: 600, textDecoration: 'underline', textUnderlineOffset: 2 }}>Privacy Policy</span>
        </p>

      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, { ScreenLogin });
