// screen-email-reference.jsx — Profile creation 02 · Email
//
// Design reference for [Profile 02] Email. This is the code that renders
// spec-sheets/02-email.png. Read values from HERE, not from the PNG.
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
//   A · valid           <ScreenProfileEmail/>                  (seeds "leo@hey.com")
//   B · invalid format  <ScreenProfileEmail error={true}/>     (seeds "leo@hey")
//   C · empty submit    <ScreenProfileEmail state="empty-submit"/>
//   A and B seed the SAME address, one of them mistyped, on purpose: the
//   states should read as one primitive failing, not two screens.
//
// LAYOUT — flex column, no absolute Y anywhere. Every content child is
// flex: none and top-anchored; ONE flex: 1 spacer (minHeight 8) sits above
// the CTA row:
//   StepProgress steps={3} current={2}, marginBottom 18
//   h1 .su-underlined — Lora 700 / 32 / 1.1, margin 0 0 8
//   p — Manrope 500 / 14.5 / 1.4 / --liq-neutral-200, maxWidth 320
//   FloatingField wrapper, marginTop 16, shake-keyed
//   helper / error region, marginTop 8, aria-live
//   MarketingOptIn, marginTop 10
//   spacer, then NextButton row at marginBottom 10
//
// EVERY GAP IS TIGHTER THAN STEP 1's (18 vs 28, 8 vs 10, 10 vs 18). That is
// not drift: the field, helper, consent row and CTA all have to clear a 286px
// keyboard. The tight marginBottom 10 on the CTA row exists so the button
// stays visible in BOTH states — the error card is ~18px taller than the
// helper line it replaces.
//
// THIS SCREEN DOES NOT RESERVE THE STATUS REGION'S HEIGHT — the one
// exception in the group. Steps 1 and 3 reserve it (minHeight 44 / 30) so
// nothing moves; here the screen is too dense to reserve the taller height
// in the calm state without pushing the CTA into the keyboard, so content
// below the region shifts ~18px in the error state. What must hold instead:
// THE CTA STAYS FULLY VISIBLE ABOVE THE KEYBOARD IN BOTH STATES, at every
// size in the matrix. If that fails at 375 x 667, reserve the height and
// shorten the consent copy — never let the CTA slide under the keys.
//
// THE ERROR COPY IS DERIVED FROM WHAT THE USER TYPED (see errorCopy):
//   no "@"          → 'Add an @ — e.g. you@example.com'
//   "@" but no dot  → 'Looks like the domain is missing .com (or similar).'
//   empty           → 'Enter your email to continue.'  (no chip — nothing to name)
//   otherwise       → 'That email doesn't look quite right. Please check it.'
// The named fragment renders as a mono chip. "Please enter a valid email" is
// banned — it tells the user nothing they do not already know. The empty
// branch deliberately mirrors the name screen's "Enter your name to
// continue." so the requirement reads the same across the group.
// THE VALUE IS PRESERVED on failure. Never wipe what they typed.
//
// THE CTA IS NEVER DISABLED. Pressing Continue (or the keyboard's Go) with
// an empty or malformed value sets "attempted", which surfaces the inline
// error and fires a one-shot su-shake on the field wrapper. A disabled
// button cannot explain itself, and it would mean the four error strings are
// never seen for an empty field. Same rule as the name and DoB screens.
//   isError = attempted && !valid
//   Recovery: the error clears as soon as the value becomes valid (the
//   "valid" effect resets "attempted") — not on blur, not on re-submit.
//   Editing a valid value back to invalid does NOT re-fire it until Continue
//   is pressed again.
//   FOCUS STAYS IN THE FIELD in the error state (focused={true}).
//   Whitespace-only counts as empty.
//
// MARKETING OPT-IN is opt-IN: unchecked by default, never pre-ticked, never
// gates Continue, never styled as an error. Transactional email is
// unaffected and needs no consent. Keep the copy to one sentence — an
// earlier reassurance line cost ~28px and pushed the CTA under the keyboard.
//
// THE KEYBOARD IS A MOCK — email variant (@ and . in row 4, and it is fed
// suggestions). Both the row and the predictions are the OS's job: request
// the email keyboard type and ship the platform keyboard. Never spec its
// height. It is drawn here only so the artboard shows the true content
// height above it (286 + 28 home indicator at 390 x 844).
//
// CONTINUE LEADS TO VERIFY EMAIL, not to step 3. The progress bar stays at
// current={2} on that screen — verification is part of step 2.
//
// MOCK-ONLY BEHAVIOUR: onKey lower-cases and slices at 64 chars; the
// suggestion strip is populated from the typed value. error /
// state="empty-submit" seed "attempted" so the artboards render the
// failures statically — in the app the only way in is a real press.
//
// DEPENDENCIES from ../components/shared.jsx:
//   StatusBar · AppHeader · StepProgress · NextButton · HomeIndicator · Icon
// Also needs FloatingField and QwertyKeyboard — see screen-name-reference.jsx;
// they are the same components, not copies.
// TOKENS from ../tokens/colors_and_type.css, incl. .su-underlined em.
//
// EXPORTS: MarketingOptIn, ScreenProfileEmail
// ─────────────────────────────────────────────────────────────

const { useState: useStateProfile } = React;

// ──────────────────────────────────────────────────────────
// Marketing opt-IN row — used on the email screen. GDPR / German
// UWG requires affirmative consent for marketing mail, so the box
// starts UNCHECKED and checking it grants consent:
// unchecked = no marketing, checked = opted in.
// Transactional mail (verification codes, receipts, security) is
// unaffected and needs no consent.
//
// Single-line copy: the earlier reassurance line cost ~28px and
// pushed the Continue CTA underneath the keyboard.
// ──────────────────────────────────────────────────────────
function MarketingOptIn({ checked, onToggle }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      style={{
        display: 'flex', alignItems: 'center', gap: 12,
        width: '100%', textAlign: 'left',
        padding: '10px 12px',
        borderRadius: 12,
        background: 'var(--liq-bg-raised)',
        border: '1px solid var(--liq-border-soft)',
        transition: 'background 180ms',
      }}>
      <div style={{
        flex: 1, minWidth: 0,
        fontFamily: 'var(--liq-font-sans)', fontSize: 13,
        fontWeight: 500, lineHeight: 1.3, color: 'var(--liq-fg)',
      }}>
        Receive curated tips, local event invites, and special offers. No spam, you can opt out whenever you like.
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
//   - empty-submit: Continue pressed with nothing typed. Same treatment,
//              copy is "Enter your email to continue." — matching the name
//              and DoB screens. The CTA is NEVER disabled: pressing it is
//              what surfaces the requirement.
//
// Same chrome as ScreenProfileName so the two read as siblings: same
// "The basics" header, same 3-segment StepProgress, italic-underlined
// headline, NextButton bottom-right, QWERTY keyboard pinned bottom
// (email variant — @ and . in row 4).
// ──────────────────────────────────────────────────────────
function ScreenProfileEmail({ initialValue, error = false, state, onBack, onContinue }) {
  const emptySubmit = state === 'empty-submit';
  // Defaults that show off each state cleanly:
  //   - default: a valid email so the success tick is visible
  //   - error:   the same email mis-typed (missing TLD) so it's clearly
  //              the same primitive in two states.
  //   - empty-submit: nothing typed, Continue already pressed.
  const defaultValue = initialValue !== undefined
    ? initialValue
    : (emptySubmit ? '' : error ? 'leo@hey' : 'leo@hey.com');
  const [email, setEmail] = useStateProfile(defaultValue);
  const [optIn, setOptIn] = useStateProfile(false);
  // Set once the user presses Continue / Go on an invalid or empty field —
  // that press is what surfaces the requirement, rather than a dead button.
  const [attempted, setAttempted] = useStateProfile(error || emptySubmit);
  const [shakeKey, setShakeKey] = useStateProfile(0);

  const valid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  const isError = attempted && !valid;

  // Once the value is valid, a later edit shouldn't re-fire the error until
  // they press Continue again.
  React.useEffect(() => { if (valid) setAttempted(false); }, [valid]);
  React.useEffect(() => { if (isError) setShakeKey(k => k + 1); }, [isError]);

  // Build the error message from what the user actually typed so the
  // recovery action is specific (not "please enter a valid email").
  const errorCopy = React.useMemo(() => {
    if (!isError) return null;
    if (!email.trim()) return { line: 'Enter your email to continue.' };
    if (!email.includes('@')) return {
      line: 'Add an ', em: '@', tail: ' \u2014 e.g. you@example.com',
    };
    const after = email.split('@')[1] || '';
    if (!after.includes('.')) return {
      line: 'Looks like the domain is missing ', em: '.com', tail: ' (or similar).',
    };
    return { line: 'That email doesn\u2019t look quite right. Please check it.' };
  }, [isError, email]);

  // CTA is always live (no disabled state) — pressing it while empty or
  // malformed surfaces the inline error + shake instead of doing nothing.
  // Matches the name and DoB screens.
  const handleContinue = () => {
    if (valid) { onContinue && onContinue(); return; }
    setAttempted(true);
    setShakeKey(k => k + 1);
  };

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
          Never shown on your profile. Used for password reset, receipts and support.
        </p>

        {/* Field — wrapped in a shake-keyed div so the failure registers
            kinetically. Re-rendered with a new key on each error to
            re-trigger the one-shot shake animation. */}
        <div
          key={shakeKey}
          style={{
            flex: 'none', marginTop: 16,
            animation: error ? 'su-shake 480ms cubic-bezier(.36,.07,.19,.97) both' : undefined,
          }}
        >
          <FloatingField
            label="Email"
            placeholder="you@example.com"
            type="email"
            value={email}
            onChange={setEmail}
            focused={true}
            valid={valid}
            error={isError}
          />
        </div>

        {/* Helper / error line — sits immediately under the field so cause
            and effect are colocated. aria-live="polite" so screen readers
            announce the failure without yanking focus. */}
        <div
          aria-live="polite"
          style={{ flex: 'none', marginTop: 8, paddingLeft: 2 }}
        >
          {isError ? (
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

        <div style={{ flex: 'none', marginTop: 10 }}>
          <MarketingOptIn checked={optIn} onToggle={() => setOptIn(v => !v)}/>
        </div>

        <div style={{ flex: 1, minHeight: 8 }}/>

        {/* Footer — primary Continue, bottom-right. The CTA is NEVER
            disabled: pressing it while empty or malformed surfaces the
            inline error above, matching the name and DoB screens.

            marginBottom is tight (10px) so the Continue CTA clears
            the keyboard in both default and error states — the error
            callout takes ~18px more vertical space than the success
            helper, and we want the button visible in both. */}
        <div style={{
          flex: 'none',
          display: 'flex', justifyContent: 'flex-end',
          marginBottom: 10,
        }}>
          <NextButton label="Continue" color="orange" onClick={handleContinue}/>
        </div>
      </div>

      {/* QWERTY keyboard — email variant has @ and . in row 4 */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <QwertyKeyboard
          email
          suggestions={[email || 'you', 'gmail.com', 'hey.com']}
          onKey={(d) => setEmail((s) => (s + d.toLowerCase()).slice(0, 64))}
          onBackspace={() => setEmail((s) => s.slice(0, -1))}
          onGo={handleContinue}
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

Object.assign(window, { ScreenProfileEmail, MarketingOptIn });
