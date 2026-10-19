// screen-gender-reference.jsx — Profile creation 15 · Gender
//
// Design reference for [Profile 15] Gender — step 2 of "Share some details".
// This is the code that renders spec-sheets/15-gender.png. Read values from
// HERE, not from the PNG.
//
// Extracted verbatim from the Show Up UI kit
// (ui_kits/show-up/screens-profile-details.jsx). It is a reference
// implementation, not production code: recreate it in the target codebase's
// own environment and patterns. Do not ship this file.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// STATES — one component, three states:
//   A · empty                  <ScreenProfileGender/>
//   B · Non-binary selected    <ScreenProfileGender initialValue="Non-binary"/>
//   C · Continue refused       <ScreenProfileGender initialHint={true}/>
//   initialHint exists FOR THE SPEC SHEET ONLY. In the app the toast appears
//   only from a Continue press. All three share one column; nothing moves.
//
// THIS SCREEN BUILDS OptionRow — the single-select row orientation,
// education, religion and politics reuse. Build it once, as its own
// component. A second copy is a bug.
//
// DetailsScaffold, useValidationToast, ValidationToast and FooterContinue are
// included below only so this file renders. They are BUILT IN PROFILE 14
// (screen-height-reference.jsx) — consume that build, do not rebuild them.
// FooterContinue is FooterSkipContinue without the SkipLink: one footer
// component with an optional skip, not two.
//
// MANDATORY — no SkipLink, so the step cannot be skipped (profile README
// rule 0). The footer row is flex-end with the left empty.
//
// SELECTION — single-select. Tapping the selected row again keeps it
// selected; a radio never clears. No auto-advance: Continue moves on.
// "Other" is a plain option — no follow-up field.
//
// LAYOUT — the scaffold's column, unchanged:
//   StatusBar 54 · AppHeader 52 ("Share some details", back chevron)
//   content flex: 1, padding 4 / 24 / 0, overflow hidden
//     StepProgress steps={10} current={2}, marginBottom 26
//     h1 — Lora 700 / 30 / 1.12, two lines at 390, em on "you"
//     p  — Manrope 500 / 14.5 / 1.45
//     answer region — marginTop 12 + paddingTop 10, flex: 1, minHeight 0
//       radiogroup: 4 × OptionRow (pad 16 / 4, divider except last)
//       → <div style={{flex:1}}/>
//     ProfileVisibility band — marginTop 6
//     footer row — marginTop 26, marginBottom 22: NextButton only
//   HomeIndicator 28
//
// Shell components (StatusBar, AppHeader, StepProgress, NextButton,
// ProfileVisibility, Atmosphere, HomeIndicator, Icon) live in
// ../components/shared.jsx. Tokens: ../tokens/colors_and_type.css.
// ─────────────────────────────────────────────────────────────

const { useState: useStateDetails } = React;
const SHARE_STEPS_TOTAL = 10;

// ─────────────────────────────────────────────────────────────
// OptionRow — single question option (radio / checkbox)
//
// A full-width tappable row with the option text on the left and a
// state indicator on the right — circle for `kind="radio"`, square
// for `kind="checkbox"`. Selected state fills the indicator with the
// primary violet and adds a soft lavender wash to the row so the
// answer reads as committed without needing a coloured border on
// every row. Unselected rows get a hairline divider underneath,
// inset 0 → 0 so they sit as a list rather than separate cards.
// ─────────────────────────────────────────────────────────────
function OptionRow({ kind = 'radio', label, selected, onClick, last = false }) {
  return (
    <button
      type="button"
      onClick={onClick}
      role={kind === 'radio' ? 'radio' : 'checkbox'}
      aria-checked={!!selected}
      style={{
        display: 'flex', alignItems: 'center', gap: 12,
        width: '100%', textAlign: 'left',
        padding: '16px 4px',
        background: selected ? 'rgba(129,42,236,0.06)' : 'transparent',
        borderRadius: 10,
        borderBottom: last ? 'none' : '1px solid var(--liq-border-soft)',
        transition: 'background 180ms',
      }}>
      <span style={{
        flex: 1, minWidth: 0,
        fontFamily: 'var(--liq-font-sans)', fontSize: 16,
        fontWeight: selected ? 700 : 500, lineHeight: 1.3,
        color: 'var(--liq-fg)',
      }}>
        {label}
      </span>

      {kind === 'radio' ? (
        <span style={{
          flex: 'none', width: 22, height: 22, borderRadius: '50%',
          background: selected ? 'var(--liq-primary-500)' : '#FFFFFF',
          border: selected ? '1.5px solid var(--liq-primary-500)' : '1.5px solid var(--liq-border)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          transition: 'background 180ms, border-color 180ms',
        }}>
          {selected && (
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#fff' }}/>
          )}
        </span>
      ) : (
        <span style={{
          flex: 'none', width: 22, height: 22, borderRadius: 6,
          background: selected ? 'var(--liq-primary-500)' : '#FFFFFF',
          border: selected ? '1.5px solid var(--liq-primary-500)' : '1.5px solid var(--liq-border)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          transition: 'background 180ms, border-color 180ms',
        }}>
          {selected && (
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
              stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="5 12 10 17 19 7"/>
            </svg>
          )}
        </span>
      )}
    </button>
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

function FooterContinue({ disabled, onContinue, hint = 'Answer this to continue', initialHint = false }) {
  const [toast, press] = useValidationToast(disabled, onContinue, initialHint);
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
// 2. ScreenProfileGender — single-select
// ─────────────────────────────────────────────────────────────
// Mandatory: no SkipLink (rule 0). Tapping the selected row again keeps it
// selected — a radio never clears. initialHint: spec sheet state C only.
const GENDER_OPTIONS = ['Woman', 'Man', 'Non-binary', 'Other'];

function ScreenProfileGender({ initialValue = null, initialPrivate = false, initialHint = false, onBack, onContinue }) {
  const [value, setValue] = useStateDetails(initialValue);
  const [priv, setPriv] = useStateDetails(initialPrivate);

  return (
    <DetailsScaffold
      step={2}
      headline={<>Which gender describes <em>you</em> best?</>}
      subhelper="Used to find the right matches."
      onBack={onBack}
      visibility={<ProfileVisibility visible={!priv} onToggle={() => setPriv(v => !v)}/>}
      footer={<FooterContinue disabled={!value} onContinue={onContinue} hint="Pick a gender to continue" initialHint={initialHint}/>}
    >
      <div role="radiogroup" style={{ display: 'flex', flexDirection: 'column' }}>
        {GENDER_OPTIONS.map((opt, i) => (
          <OptionRow key={opt} kind="radio" label={opt}
            selected={value === opt}
            onClick={() => setValue(opt)}
            last={i === GENDER_OPTIONS.length - 1}/>
        ))}
      </div>

      <div style={{ flex: 1 }}/>
    </DetailsScaffold>
  );
}


Object.assign(window, { OptionRow, DetailsScaffold, FooterContinue, ScreenProfileGender, GENDER_OPTIONS });
