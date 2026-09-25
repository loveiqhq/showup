// screen-connect-reference.jsx
//
// DESIGN REFERENCE — Welcome & sign-up, screens 04 and 05.
// This file is the source of truth for NUMBERS. The ticket wins on
// behaviour, scope and copy; the PNG wins on nothing.
//
// Ticket:  welcome/tickets/04-connect-flow.md
// Sheet:   welcome/spec-sheets/04-connect-flow.png  (ten states, keyed 1-33)
//
// One screen, three providers, eight states + one modal. Everything is
// variant-driven off ONE component — do not fork per provider:
//
//   <ScreenSocialAuth/>                                   // A idle
//   <ScreenSocialAuth provider="apple"  state="tapped"/>  // B in flight
//   <ScreenSocialAuth provider="apple"  state="sheet"/>   // C OS handoff
//   <ScreenSocialAuth provider="google" state="sheet"/>   // D OS handoff
//   <ScreenSocialAuth provider="apple"  state="linking"/> // E
//   <ScreenSocialAuth provider="apple"  state="success"/> // F
//   <ScreenSocialAuth provider="apple"  state="cancelled"/> // G
//   <ScreenSocialAuth provider="apple"  state="error" kind="network"/>  // H
//   <ScreenSocialAuth provider="google" state="error" kind="declined"/> // H
//   <ScreenSocialAuth state="conflict"/>                  // welcome 05
//
// WHAT MATTERS IN HERE
//
//   1. SAAuthMethods is the shared method list. Welcome back (screen 02)
//      renders the same list with phone added. Build it once.
//   2. `suggested` promotes one provider to primary. That single prop is
//      what keeps the tapped provider in first position across B, G and H.
//   3. The skip button is NEVER disabled and NEVER dimmed, in any state.
//      It is the escape hatch out of a hung provider request.
//   4. The notice / error banner is INSERTED ABOVE the method list, not
//      overlaid. The buttons must not move between A, B, G and H.
//   5. SAOSHandoff / SAAppleSheetBody / SAGoogleSheetBody are STYLIZED
//      STAND-INS for OS-owned UI. DO NOT BUILD THEM. Do not measure them.
//      The system owns everything inside that rectangle, including its
//      height — which differs per provider, OS version and account count.
//   6. The dim scrim sits INSIDE the screen's relative wrapper (z 2) so
//      the sheet / modal (z 3) stays above it without a portal. On iOS the
//      system usually draws its own dim: ship ours only where it does not.
//   7. Provider button styling follows Apple's / Google's / Facebook's own
//      published specs at equal prominence — NOT the gradient treatment in
//      this file. Decided 26 Aug 2026; see CLAUDE.md → Provider sign-in
//      buttons. The gradient values below stay only for our own CTAs.
//
// Layout: status bar 54 -> content (flex: 1, padding 20/24/0) -> home
// indicator 28. No AppHeader, no back — the phone is already verified.
// Content column: wordmark -> 96 -> headline block -> ONE flex: 1 spacer
// -> banner -> method list -> legal line. No absolute Y positions.

// ─────────────────────────────────────────────────────────────
// Shell + bottom auth-method block — shared across most states.
// ─────────────────────────────────────────────────────────────
function SAShell({ children, dim = false }) {
  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
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

      <div style={{
        position: 'relative', zIndex: 1, height: '100%',
        display: 'flex', flexDirection: 'column',
      }}>
        <StatusBar time="4:20"/>
        {children}
        <HomeIndicator/>
        {/* Dim sits INSIDE the relative wrapper so the OS sheet / conflict
            modal (zIndex: 3 in this same stacking context) stays above it. */}
        {dim && (
          <div style={{
            position: 'absolute', inset: 0, zIndex: 2,
            background: 'rgba(20,12,30,0.42)',
            pointerEvents: 'none',
          }}/>
        )}
      </div>
    </div>
  );
}

function saCapitalise(s) { return s ? s[0].toUpperCase() + s.slice(1) : ''; }

function SASpinner({ color = '#fff' }) {
  return (
    <span style={{
      width: 18, height: 18, borderRadius: '50%',
      border: `2px solid ${color === '#fff' ? 'rgba(255,255,255,0.35)' : 'rgba(29,17,41,0.22)'}`,
      borderTopColor: color,
      animation: 'sa-spin 0.9s linear infinite',
      display: 'inline-block', flex: 'none',
    }}/>
  );
}

function SAAuthMethods({ loading = null, error = null, suggested = null, errorMsg }) {
  // Canonical order, mirrored from ScreenLogin.
  // Phone is intentionally absent: the user has just verified their number,
  // so re-offering it here would be a no-op.
  const METHODS = [
    { k: 'apple',    label: 'Continue with Apple',        icon: <Icon name="apple"    size={18}/> },
    { k: 'google',   label: 'Continue with Google',       icon: <Icon name="google"   size={18}/> },
    { k: 'facebook', label: 'Continue with Facebook',     icon: <Icon name="facebook" size={18}/> },
  ];
  const primaryKey = suggested || (loading ? loading : 'apple');
  const primary = METHODS.find(m => m.k === primaryKey);
  const rest    = METHODS.filter(m => m.k !== primaryKey);

  const isLoadingPrimary = loading === primary.k;
  const isErrorPrimary   = error   === primary.k;

  return (
    <>
      <style>{`
        @keyframes sa-spin { to { transform: rotate(360deg); } }
      `}</style>

      {error && errorMsg && (
        <div style={{
          display: 'flex', alignItems: 'flex-start', gap: 10,
          padding: '12px 14px', borderRadius: 14,
          background: 'rgba(251,50,59,0.07)',
          border: '1px solid rgba(251,50,59,0.18)',
          marginBottom: 14,
        }}>
          <span style={{
            flex: 'none', width: 20, height: 20, borderRadius: '50%',
            background: 'var(--liq-danger)', color: '#fff',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 13,
            lineHeight: 1, marginTop: 1,
          }}>!</span>
          <div style={{
            fontFamily: 'var(--liq-font-sans)', fontSize: 13.5, lineHeight: 1.4,
            color: 'var(--liq-danger-fg)', fontWeight: 500,
          }}>
            {errorMsg}
          </div>
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 12 }}>
        <button
          disabled={!!loading}
          style={{
            height: 56, padding: '0 24px',
            borderRadius: 9999,
            background: isErrorPrimary ? 'var(--liq-bg-elevated)' : 'var(--su-grad-sunset)',
            color: isErrorPrimary ? 'var(--liq-fg)' : '#fff',
            border: isErrorPrimary ? '1.5px solid var(--liq-border)' : 'none',
            boxShadow: isErrorPrimary ? 'none' : 'var(--liq-shadow-violet)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            gap: 10,
            fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 16,
            opacity: loading && !isLoadingPrimary ? 0.5 : 1,
            transition: 'opacity 180ms, background 220ms',
          }}>
          {isLoadingPrimary
            ? <SASpinner color="#fff"/>
            : <span style={{ display: 'inline-flex', color: isErrorPrimary ? 'var(--liq-fg)' : '#fff' }}>{primary.icon}</span>}
          <span>
            {isLoadingPrimary
              ? `Connecting to ${saCapitalise(primary.k)}…`
              : (isErrorPrimary
                  ? `Try ${saCapitalise(primary.k)} again`
                  : primary.label)}
          </span>
        </button>

        {rest.map(m => {
          const isLoading = loading === m.k;
          return (
            <button key={m.k}
              disabled={!!loading}
              style={{
                height: 56, padding: '0 24px',
                borderRadius: 9999,
                background: 'transparent',
                color: 'var(--liq-fg)',
                border: '1px solid var(--liq-border)',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                gap: 10,
                fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 16,
                opacity: loading && !isLoading ? 0.45 : 1,
                transition: 'opacity 180ms',
              }}>
              {isLoading ? <SASpinner color="var(--liq-fg)"/> : <span style={{ display: 'inline-flex' }}>{m.icon}</span>}
              <span>{isLoading ? `Connecting to ${saCapitalise(m.k)}…` : m.label}</span>
            </button>
          );
        })}

        {/* Escape hatch — deliberately a different shape and weight from the
            three connect buttons so it never reads as a fourth provider.
            NEVER disabled and never dimmed: connecting an account is optional,
            so this exit stays live even while a provider is in flight. */}
        <button
          style={{
            marginTop: 4, height: 52, padding: '0 20px',
            borderRadius: 16,
            background: 'var(--liq-bg-elevated)',
            color: loading ? 'var(--liq-fg)' : 'var(--liq-fg-muted)',
            border: '1.5px dashed var(--liq-border)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
            fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 15,
            opacity: 1,
            transition: 'color 180ms',
          }}>
          <span>Skip and continue to profile</span>
          <Icon name="arrow-right" size={17} stroke={2.2}/>
        </button>
      </div>
    </>
  );
}

function SAContent({ children }) {
  return (
    <div style={{
      flex: 1, padding: '20px 24px 0',
      display: 'flex', flexDirection: 'column',
      position: 'relative',
    }}>
      {children}
    </div>
  );
}

function SAHeader() {
  return (
    <>
      <Wordmark size={26}/>
      <div style={{ marginTop: 96, display: 'flex', flexDirection: 'column', gap: 16 }}>
        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 40,
          letterSpacing: '-0.02em', lineHeight: 1.05, color: 'var(--liq-fg)',
          margin: 0, textWrap: 'balance',
          display: 'flex', alignItems: 'baseline', gap: 12,
        }}>
          <span>Welcome to <em>Show Up</em></span>
          <Icon name="heart-filled" size={30} stroke={0}
            style={{ stroke: 'none', fill: 'var(--liq-orange-500)', flex: 'none', alignSelf: 'center' }}/>
        </h1>
        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 16,
          lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty', maxWidth: 310,
        }}>
          Connect an account for easier future sign-ins.<br/>
          Or continue and start creating your profile.
        </p>
      </div>
    </>
  );
}

function SAFoot() {
  return (
    <p style={{
      fontSize: 12, color: 'var(--liq-fg-subtle)',
      textAlign: 'center', margin: '0 0 8px', lineHeight: 1.45,
    }}>
      By continuing you agree to our <span style={{ color: 'var(--liq-fg)', fontWeight: 600 }}>Terms</span> and{' '}
      <span style={{ color: 'var(--liq-fg)', fontWeight: 600 }}>Privacy Policy</span>.
    </p>
  );
}

// ─────────────────────────────────────────────────────────────
// OS handoff sheet — stylized, NOT a reproduction of Apple's or
// Google's actual UI. Used while the OS owns the foreground.
// ─────────────────────────────────────────────────────────────
function SAOSHandoff({ provider = 'apple', body }) {
  const isApple = provider === 'apple';
  return (
    <div style={{
      position: 'absolute', inset: 0, zIndex: 3,
      display: 'flex', flexDirection: 'column', justifyContent: 'flex-end',
      pointerEvents: 'none',
    }}>
      <div style={{
        margin: '0 8px 12px', borderRadius: 22,
        background: 'rgba(245,242,240,0.96)',
        backdropFilter: 'blur(20px)',
        boxShadow: '0 24px 60px rgba(0,0,0,0.32), 0 2px 8px rgba(0,0,0,0.18)',
        padding: '22px 22px 18px',
        fontFamily: '-apple-system, "SF Pro Text", system-ui',
        pointerEvents: 'auto',
      }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 14, paddingBottom: 16,
          borderBottom: '0.5px solid rgba(0,0,0,0.10)',
        }}>
          <span style={{
            width: 44, height: 44, borderRadius: 10,
            background: isApple ? '#000' : '#FFFFFF',
            border: isApple ? 'none' : '1px solid rgba(0,0,0,0.08)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            color: isApple ? '#fff' : 'var(--liq-fg)',
          }}>
            <Icon name={isApple ? 'apple' : 'google'} size={24} stroke={0} style={{ stroke: 'none', fill: 'currentColor' }}/>
          </span>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 11, fontWeight: 500, color: 'rgba(0,0,0,0.55)', letterSpacing: 0.2 }}>
              {isApple ? 'Sign in with your account' : 'Choose an account'}
            </div>
            <div style={{ fontSize: 15, fontWeight: 600, color: '#000', marginTop: 1 }}>
              to continue to Show Up
            </div>
          </div>
          <span style={{
            width: 36, height: 36, borderRadius: 9,
            background: 'var(--su-grad-sunset)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            color: '#fff', fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 18,
            fontStyle: 'italic',
          }}>S</span>
        </div>

        {body}
      </div>

      <div style={{ height: 28, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ width: 134, height: 5, borderRadius: 3, background: 'rgba(20,12,30,0.55)' }}/>
      </div>
    </div>
  );
}

function SAAppleSheetBody() {
  return (
    <div style={{ paddingTop: 16, display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span style={{
          width: 22, height: 22, borderRadius: '50%',
          border: '1.5px solid #000', display: 'inline-flex',
          alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon name="check" size={12} stroke={2.4} style={{ color: '#000' }}/>
        </span>
        <span style={{ flex: 1, fontSize: 14, color: '#000', fontWeight: 500 }}>Share my name</span>
        <span style={{ fontSize: 14, color: '#000', fontWeight: 500 }}>Leo Schwarzkopf</span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span style={{
          width: 22, height: 22, borderRadius: '50%',
          border: '1.5px solid #000', display: 'inline-flex',
          alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon name="check" size={12} stroke={2.4} style={{ color: '#000' }}/>
        </span>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 14, color: '#000', fontWeight: 500 }}>Share my email</div>
          <div style={{ fontSize: 12, color: 'rgba(0,0,0,0.55)' }}>leo@hey.com</div>
        </div>
        <span style={{ fontSize: 13, color: 'rgba(0,0,0,0.6)', textDecoration: 'underline', textUnderlineOffset: 2 }}>Hide</span>
      </div>

      <button style={{
        marginTop: 6, height: 50, borderRadius: 12,
        background: '#000', color: '#fff',
        fontFamily: 'inherit', fontWeight: 600, fontSize: 16,
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 10,
        border: 'none',
      }}>
        <span style={{
          width: 24, height: 24, borderRadius: 6,
          border: '1.5px solid #fff',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 11, fontWeight: 700,
        }}>ID</span>
        Double-press to confirm
      </button>
      <button style={{
        height: 44, borderRadius: 12, background: 'transparent',
        fontFamily: 'inherit', fontWeight: 500, fontSize: 15,
        color: '#000', border: 'none',
      }}>Cancel</button>
    </div>
  );
}

function SAGoogleSheetBody() {
  const accounts = [
    { name: 'Leo Schwarzkopf', email: 'leo@gmail.com',         colour: 'linear-gradient(135deg,#FE6839,#812AEC)', initial: 'L' },
    { name: 'Leo (work)',      email: 'leo@studio-mantis.com', colour: 'linear-gradient(135deg,#5BA8FF,#1F5FD4)', initial: 'L' },
  ];
  return (
    <div style={{ paddingTop: 14, display: 'flex', flexDirection: 'column', gap: 4 }}>
      {accounts.map((a, i) => (
        <button key={i} style={{
          display: 'flex', alignItems: 'center', gap: 14,
          padding: '12px 6px', border: 'none', background: 'transparent',
          borderRadius: 10, fontFamily: 'inherit',
        }}>
          <span style={{
            width: 36, height: 36, borderRadius: '50%',
            background: a.colour, color: '#fff',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            fontWeight: 700, fontSize: 16,
          }}>{a.initial}</span>
          <div style={{ flex: 1, textAlign: 'left' }}>
            <div style={{ fontSize: 15, fontWeight: 600, color: '#000', lineHeight: 1.2 }}>{a.name}</div>
            <div style={{ fontSize: 13, color: 'rgba(0,0,0,0.55)', marginTop: 2 }}>{a.email}</div>
          </div>
          <Icon name="chevron-right" size={16} stroke={2} style={{ color: 'rgba(0,0,0,0.45)' }}/>
        </button>
      ))}
      <button style={{
        display: 'flex', alignItems: 'center', gap: 14,
        padding: '12px 6px', border: 'none', background: 'transparent',
        borderRadius: 10, fontFamily: 'inherit',
      }}>
        <span style={{
          width: 36, height: 36, borderRadius: '50%',
          background: 'rgba(0,0,0,0.04)',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          color: 'rgba(0,0,0,0.55)',
        }}>
          <Icon name="plus" size={18} stroke={2}/>
        </span>
        <div style={{ flex: 1, textAlign: 'left' }}>
          <div style={{ fontSize: 15, fontWeight: 500, color: 'rgba(0,0,0,0.78)' }}>Use another account</div>
        </div>
      </button>

      <div style={{ paddingTop: 6, fontSize: 11, color: 'rgba(0,0,0,0.5)', lineHeight: 1.4 }}>
        To continue, Google will share your name, email address, language preference, and profile picture with Show Up.
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Internal renderers per state
// ─────────────────────────────────────────────────────────────
function saIdleLike({ loading = null, suggested = null, notice = null }) {
  return (
    <SAShell>
      <SAContent>
        <SAHeader/>
        <div style={{ flex: 1 }}/>
        {notice}
        <SAAuthMethods loading={loading} suggested={suggested}/>
        <SAFoot/>
      </SAContent>
    </SAShell>
  );
}

function saHandoff({ provider }) {
  return (
    <SAShell dim>
      <SAContent>
        <SAHeader/>
        <div style={{ flex: 1 }}/>
        <SAAuthMethods loading={provider} suggested={provider}/>
        <SAFoot/>
      </SAContent>
      <SAOSHandoff provider={provider}
        body={provider === 'apple' ? <SAAppleSheetBody/> : <SAGoogleSheetBody/>}/>
    </SAShell>
  );
}

function saLinking({ provider }) {
  const isApple = provider === 'apple';
  return (
    <SAShell>
      <SAContent>
        <Wordmark size={26}/>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 24 }}>
          <div style={{ position: 'relative', width: 120, height: 120 }}>
            <svg width="120" height="120" viewBox="0 0 120 120" style={{ position: 'absolute', inset: 0 }}>
              <defs>
                <linearGradient id="sa-ring" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0%" stopColor="#FE6839"/>
                  <stop offset="100%" stopColor="#812AEC"/>
                </linearGradient>
              </defs>
              <circle cx="60" cy="60" r="52" stroke="rgba(29,17,41,0.06)" strokeWidth="4" fill="none"/>
              <circle cx="60" cy="60" r="52" stroke="url(#sa-ring)" strokeWidth="4" fill="none"
                strokeDasharray={`${2 * Math.PI * 52 * 0.28} ${2 * Math.PI * 52}`}
                strokeLinecap="round"
                style={{ animation: 'sa-spin 1.4s linear infinite', transformOrigin: '60px 60px' }}/>
            </svg>
            <div style={{
              position: 'absolute', inset: 18, borderRadius: '50%',
              background: '#FFFFFF', boxShadow: '0 4px 12px rgba(46,1,71,0.06)',
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              color: isApple ? '#000' : 'var(--liq-fg)',
            }}>
              <Icon name={isApple ? 'apple' : 'google'} size={42} stroke={0} style={{ stroke: 'none', fill: 'currentColor' }}/>
            </div>
          </div>
          <div style={{ textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 8, padding: '0 32px' }}>
            <h2 className="su-underlined" style={{
              fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 28,
              letterSpacing: '-0.015em', lineHeight: 1.15, color: 'var(--liq-fg)',
              margin: 0, textWrap: 'balance',
            }}>
              Signing you <em>in</em>…
            </h2>
            <p style={{
              fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15,
              lineHeight: 1.45, color: 'var(--liq-fg-muted)', margin: 0,
              textWrap: 'pretty',
            }}>
              {isApple ? 'Verifying your Apple ID and setting things up. This takes a second.'
                       : 'Verifying your Google account and setting things up. This takes a second.'}
            </p>
          </div>
        </div>
        <p style={{
          fontSize: 12, color: 'var(--liq-fg-subtle)',
          textAlign: 'center', margin: '0 0 8px', lineHeight: 1.45,
        }}>
          Don't close the app.
        </p>
      </SAContent>
    </SAShell>
  );
}

function saSuccess({ provider }) {
  const isApple = provider === 'apple';
  return (
    <SAShell>
      <SAContent>
        <Wordmark size={26}/>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 28, padding: '0 24px' }}>
          <div style={{ position: 'relative', width: 120, height: 120 }}>
            <div style={{
              position: 'absolute', inset: 0, borderRadius: '50%',
              background: 'radial-gradient(circle, rgba(0,171,85,0.22) 0%, rgba(0,171,85,0) 70%)',
            }}/>
            <div style={{
              position: 'absolute', inset: 16, borderRadius: '50%',
              background: '#FFFFFF', boxShadow: '0 8px 24px rgba(0,171,85,0.18), 0 2px 6px rgba(46,1,71,0.08)',
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              color: isApple ? '#000' : 'var(--liq-fg)',
            }}>
              <Icon name={isApple ? 'apple' : 'google'} size={44} stroke={0} style={{ stroke: 'none', fill: 'currentColor' }}/>
            </div>
            <div style={{
              position: 'absolute', right: 4, bottom: 4, width: 36, height: 36, borderRadius: '50%',
              background: 'var(--liq-success)', color: '#fff',
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 4px 10px rgba(0,171,85,0.45)',
              border: '3px solid var(--liq-bg)',
            }}>
              <Icon name="check" size={18} stroke={2.4}/>
            </div>
          </div>
          <div style={{ textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 10 }}>
            <Eyebrow color="orange">{isApple ? 'Apple connected' : 'Google connected'}</Eyebrow>
            <h2 className="su-underlined" style={{
              fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 32,
              letterSpacing: '-0.015em', lineHeight: 1.1, color: 'var(--liq-fg)',
              margin: 0, textWrap: 'balance',
            }}>
              You're <em>in</em>, Leo.
            </h2>
            <p style={{
              fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15,
              lineHeight: 1.45, color: 'var(--liq-fg-muted)', margin: 0,
              textWrap: 'pretty', maxWidth: 280,
            }}>
              We'll never post or message anyone on your behalf. Let's finish your profile in 90 seconds.
            </p>
          </div>
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 18 }}>
          <NextButton label="Continue" color="sunset"/>
        </div>
      </SAContent>
    </SAShell>
  );
}

function saCancelled({ provider }) {
  const isApple = provider === 'apple';
  const notice = (
    <div style={{
      display: 'flex', alignItems: 'flex-start', gap: 10,
      padding: '12px 14px', borderRadius: 14,
      background: 'var(--liq-bg-raised)',
      border: '1px solid var(--liq-border-soft)',
      marginBottom: 12,
    }}>
      <span style={{
        flex: 'none', width: 20, height: 20, borderRadius: '50%',
        background: 'rgba(29,17,41,0.10)',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--liq-fg)',
      }}>
        <Icon name="x" size={12} stroke={2.4}/>
      </span>
      <div style={{
        flex: 1, fontFamily: 'var(--liq-font-sans)', fontSize: 13.5,
        lineHeight: 1.4, color: 'var(--liq-fg)',
      }}>
        <span style={{ fontWeight: 600 }}>Sign-in cancelled.</span>{' '}
        <span style={{ color: 'var(--liq-fg-muted)' }}>
          You closed the {isApple ? 'Apple' : 'Google'} sheet before we could finish.
        </span>
      </div>
    </div>
  );
  return saIdleLike({ suggested: provider, notice });
}

function saError({ provider, kind = 'network' }) {
  const msgs = {
    network: <>We couldn't reach {provider === 'apple' ? 'Apple' : 'Google'}. Check your connection and try again.</>,
    declined: <>{provider === 'apple' ? 'Apple' : 'Google'} didn't return a valid sign-in. Try again, or use a different method.</>,
  };
  return (
    <SAShell>
      <SAContent>
        <SAHeader/>
        <div style={{ flex: 1 }}/>
        <SAAuthMethods suggested={provider} error={provider} errorMsg={msgs[kind]}/>
        <SAFoot/>
      </SAContent>
    </SAShell>
  );
}

function saConflict() {
  return (
    <SAShell dim>
      <SAContent>
        <SAHeader/>
        <div style={{ flex: 1 }}/>
        <SAAuthMethods suggested="apple"/>
        <SAFoot/>
      </SAContent>

      <div style={{
        position: 'absolute', inset: 0, zIndex: 3,
        display: 'flex', flexDirection: 'column', justifyContent: 'flex-end',
      }}>
        <div style={{
          margin: 8, borderRadius: 28,
          background: 'var(--liq-bg-elevated)',
          boxShadow: '0 30px 80px rgba(46,1,71,0.28), 0 4px 12px rgba(46,1,71,0.10)',
          padding: '26px 24px 22px',
        }}>
          <div style={{
            width: 56, height: 56, borderRadius: '50%',
            background: 'rgba(254,104,57,0.12)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            color: 'var(--liq-orange-500)',
            marginBottom: 16,
          }}>
            <Icon name="shield" size={26} stroke={1.8}/>
          </div>
          <h3 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26,
            letterSpacing: '-0.015em', lineHeight: 1.15, color: 'var(--liq-fg)',
            margin: '0 0 10px', textWrap: 'balance',
          }}>
            You already have an account.
          </h3>
          <p style={{
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15,
            lineHeight: 1.45, color: 'var(--liq-neutral-200)',
            margin: '0 0 8px',
          }}>
            <span style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>leo@gmail.com</span>{' '}
            is already on Show Up — you signed up with Google. Continue with Google to pick up where you left off.
          </p>
          <p style={{
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13,
            lineHeight: 1.4, color: 'var(--liq-fg-subtle)',
            margin: '0 0 22px',
          }}>
            One person, one account. Show-up Rates only work if you can't start over.
          </p>

          <button style={{
            width: '100%', height: 54, borderRadius: 9999,
            background: 'var(--su-grad-sunset)',
            boxShadow: 'var(--liq-shadow-violet)',
            color: '#fff', border: 'none',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 16,
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 10,
            marginBottom: 8,
          }}>
            <Icon name="google" size={18} stroke={0} style={{ stroke: 'none', fill: 'currentColor' }}/>
            Continue with Google
          </button>
          <button style={{
            width: '100%', height: 50, borderRadius: 9999, background: 'transparent',
            color: 'var(--liq-fg-muted)', border: 'none',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 15,
          }}>
            Use a different account
          </button>
        </div>
        <div style={{ height: 28, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ width: 134, height: 5, borderRadius: 3, background: 'rgba(20,12,30,0.55)' }}/>
        </div>
      </div>
    </SAShell>
  );
}

// ─────────────────────────────────────────────────────────────
// Public API — single variant-driven screen
// ─────────────────────────────────────────────────────────────
function ScreenSocialAuth({ state = 'idle', provider = 'apple', kind = 'network' }) {
  switch (state) {
    case 'idle':      return saIdleLike({});
    case 'tapped':    return saIdleLike({ loading: provider, suggested: provider });
    case 'sheet':     return saHandoff({ provider });
    case 'linking':   return saLinking({ provider });
    case 'success':   return saSuccess({ provider });
    case 'cancelled': return saCancelled({ provider });
    case 'error':     return saError({ provider, kind });
    case 'conflict':  return saConflict();
    default:          return saIdleLike({});
  }
}

Object.assign(window, { ScreenSocialAuth });
