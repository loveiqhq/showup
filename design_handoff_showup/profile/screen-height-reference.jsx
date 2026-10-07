// screen-height-reference.jsx — Profile creation 14 · Height
//
// Design reference for [Profile 14] Height — step 1 of "Share some details".
// This is the code that renders spec-sheets/14-height.png. Read values from
// HERE, not from the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/screens-profile.jsx
// for FloatingField, ui_kits/show-up/screens-profile-details.jsx for the rest).
// It is a reference implementation, not production code: recreate it in the
// target codebase's own environment and patterns. Do not ship this file.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// STATES — one component, three states:
//   A · empty, field focused   <ScreenProfileHeight initialValue=""/>
//   B · 175 cm, valid          <ScreenProfileHeight initialValue="175"/>
//   C · Continue refused       <ScreenProfileHeight initialValue="" initialHint={true}/>
//   initialHint exists FOR THE SPEC SHEET ONLY. In the app the toast appears
//   only from a Continue press. All three share one column; nothing moves.
//
// THIS SCREEN BUILDS THE GROUP SHELL. DetailsScaffold is what every
// "Share some details" step renders through: header "Share some details" with
// a back chevron, StepProgress (count = SHARE_STEPS_TOTAL, a prop of the
// shell), Lora 700 / 30 headline, sub copy, a flex: 1 answer region, the
// ProfileVisibility band and the footer. Build it once here; steps 2–9
// consume it. A second copy is a bug.
//
// LAYOUT — flex column, no absolute Y anywhere except Atmosphere and the toast:
//   StatusBar 54 · AppHeader 52
//   content flex: 1, padding 4 / 24 / 0, overflow hidden
//     StepProgress steps={10} current={1}, marginBottom 26
//     h1 .su-underlined — Lora 700 / 30 / 1.12 / -0.015em, margin 0 0 8
//     p — Manrope 500 / 14.5 / 1.45 / --liq-neutral-200, maxWidth 320
//     answer region — marginTop 12 + paddingTop 10 (= 22), flex: 1, minHeight 0
//       FloatingField (h 64) → note (marginTop 10) → <div style={{flex:1}}/>
//     ProfileVisibility band — marginTop 6
//     footer row — marginTop 26, marginBottom 22: SkipLink · NextButton
//   HomeIndicator 28
//
// VALIDATION — integer 120–230 inclusive. Input keeps digits only, max 3.
// Continue is NEVER disabled: a refused press shows ValidationToast for
// 2600 ms; it hides as soon as the value becomes valid.
//
// Shell components (StatusBar, AppHeader, StepProgress, NextButton, SkipLink,
// ProfileVisibility, Atmosphere, HomeIndicator, Icon) live in
// ../components/shared.jsx. Tokens: ../tokens/colors_and_type.css.
// ─────────────────────────────────────────────────────────────

const { useState: useStateDetails, useState: useStateProfile } = React;
const SHARE_STEPS_TOTAL = 10;

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
// DetailsScaffold — shared layout for every "Share some details" step
//
// Wraps the chrome (status bar, header, step progress, headline,
// subhelper) so each individual screen only has to declare its
// question + answer surface + footer. Keeps the five screens
// visually identical above the fold.
//
// Props:
//   step         which segment of SHARE_STEPS_TOTAL is current
//   eyebrow      kept null by default — the reference uses the header
//                title "Share some details" as the section indicator,
//                not a separate eyebrow tag
//   headline     React node (we want italic flourish on one word)
//   subhelper    React node (the short directive line)
//   onBack
//   children     answer surface (input, list, etc.) — scrolls if tall
//   visibility   <ProfileVisibility> band, pinned above the footer
//   footer       footer row (continue / skip + continue)
//
// The answer surface is the ONLY scrolling region: the visibility band
// and the footer are pinned outside it, so both stay in the viewport no
// matter how many options a question has.
// ─────────────────────────────────────────────────────────────
function DetailsScaffold({ step, headline, subhelper, onBack, children, visibility, footer }) {
  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      <Atmosphere variant="form"/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
        <AppHeader title="Share some details" leading="back" onBack={onBack}/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', overflow: 'hidden',
        padding: '4px 24px 0', display: 'flex', flexDirection: 'column',
      }}>
        <StepProgress steps={SHARE_STEPS_TOTAL} current={step} style={{ marginBottom: 26 }}/>

        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
          letterSpacing: '-0.015em', lineHeight: 1.12, color: 'var(--liq-fg)',
          margin: '0 0 8px', textWrap: 'balance',
        }}>
          {headline}
        </h1>

        {subhelper && (
          <p style={{
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
            lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
            textWrap: 'pretty', maxWidth: 320,
          }}>
            {subhelper}
          </p>
        )}

        {/* marginTop 12 + paddingTop 10 = the 22 gap; the padding keeps a
            FloatingField's notched label (top −8) from being clipped by
            this region's overflow. */}
        <div className="su-noscroll" style={{
          marginTop: 12, paddingTop: 10, flex: 1, minHeight: 0, overflowY: 'auto',
          display: 'flex', flexDirection: 'column',
        }}>
          {children}
        </div>

        {visibility && (
          <div style={{ flex: 'none', marginTop: 6 }}>{visibility}</div>
        )}

        <div style={{ marginTop: 26, marginBottom: 22, flex: 'none' }}>
          {footer}
        </div>
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Footer atoms — composed by each screen below
// ─────────────────────────────────────────────────────────────
// The CTA never dims. Tapping it short of a valid answer surfaces a
// toast stating the rule — same pattern as Prompts / Photos / Check-in.
function useValidationToast(disabled, onContinue, initialShown = false) {
  const [toast, setToast] = React.useState(initialShown);
  const timer = React.useRef(null);
  React.useEffect(() => () => clearTimeout(timer.current), []);
  React.useEffect(() => { if (!disabled) setToast(false); }, [disabled]);
  const press = () => {
    if (!disabled) { onContinue && onContinue(); return; }
    setToast(true);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setToast(false), 2600);
  };
  return [toast, press];
}

function ValidationToast({ show, children }) {
  return (
    <div style={{
      position: 'absolute', left: 0, right: 0, bottom: '100%',
      display: 'flex', justifyContent: 'center',
      pointerEvents: 'none', paddingBottom: 10,
      visibility: show ? 'visible' : 'hidden',
      opacity: show ? 1 : 0,
      transform: show ? 'translateY(0)' : 'translateY(6px)',
      transition: 'opacity 200ms, transform 200ms',
    }}>
      <div style={{
        display: 'inline-flex', alignItems: 'center', gap: 8,
        padding: '10px 14px', borderRadius: 14,
        background: 'var(--liq-fg)', color: '#fff',
        fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 13,
        lineHeight: 1.2, boxShadow: '0 8px 22px rgba(46,1,71,0.22)',
        maxWidth: 300, textWrap: 'pretty',
      }}>{children}</div>
    </div>
  );
}

function FooterContinue({ disabled, onContinue, hint = 'Answer this to continue' }) {
  const [toast, press] = useValidationToast(disabled, onContinue);
  return (
    <div style={{
      position: 'relative',
      display: 'flex', justifyContent: 'flex-end', alignItems: 'center',
    }}>
      <ValidationToast show={toast}>{hint}</ValidationToast>
      <NextButton label="Continue" color="orange" onClick={press}/>
    </div>
  );
}

function FooterSkipContinue({ disabled, onSkip, onContinue, hint = 'Answer this to continue', initialHint = false }) {
  const [toast, press] = useValidationToast(disabled, onContinue, initialHint);
  return (
    <div style={{
      position: 'relative',
      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    }}>
      <ValidationToast show={toast}>{hint}</ValidationToast>
      <SkipLink onClick={onSkip}/>
      <NextButton label="Continue" color="orange" onClick={press}/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// 1. ScreenProfileHeight — "How tall are you?"
//
// FloatingField in numeric mode. We accept the value as a plain
// number (cm) — no inch toggle in the reference, and Show Up's
// brief is European-centric (warm violet + paper + km/cm/30-minute
// timer is metric). A subhelper sits inside the answer block, not
// below the headline, because the height-specific honesty nudge
// ("Be honest…") reads as part of the field, not the question.
// ─────────────────────────────────────────────────────────────
// initialHint: renders the refused-Continue toast already showing (spec sheet
// state C). In the app the toast only ever appears from a Continue press.
function ScreenProfileHeight({ initialValue = '175', initialPrivate = false, initialHint = false, onBack, onContinue }) {
  const [value, setValue] = useStateDetails(initialValue);
  const [priv, setPriv] = useStateDetails(initialPrivate);
  const num = parseInt(value, 10);
  const valid = Number.isFinite(num) && num >= 120 && num <= 230;

  return (
    <DetailsScaffold
      step={1}
      headline={<>How <em>tall</em> are you?</>}
      subhelper="Used to find the right matches."
      onBack={onBack}
      visibility={<ProfileVisibility visible={!priv} onToggle={() => setPriv(v => !v)}/>}
      footer={<FooterSkipContinue disabled={!valid} onSkip={onContinue} onContinue={onContinue} hint="Enter a height between 120 and 230 cm to continue" initialHint={initialHint}/>}
    >
      <FloatingField
        label="Enter height in cm"
        placeholder="e.g. 175"
        type="text"
        value={value}
        onChange={(v) => setValue((v || '').replace(/[^\d]/g, '').slice(0, 3))}
        focused={true}
        valid={valid}
      />

      <p style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 12.5,
        lineHeight: 1.4, color: 'var(--liq-fg-subtle)',
        margin: '10px 2px 0',
      }}>
        Be honest — it helps us find the right matches.
      </p>

      <div style={{ flex: 1 }}/>
    </DetailsScaffold>
  );
}

Object.assign(window, { FloatingField, DetailsScaffold, ScreenProfileHeight });
