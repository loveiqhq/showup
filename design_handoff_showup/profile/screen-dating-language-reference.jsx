// screen-dating-language-reference.jsx — Profile creation 17 · Dating language
//
// Design reference for [Profile 17] Dating language — step 4 of "Share some details".
// This is the code that renders spec-sheets/17-dating-language.png. Read values
// from HERE, not from the PNG.
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
// STATES — one component, two states:
//   A · empty          <ScreenProfileLanguages/>
//   B · 3 picked       <ScreenProfileLanguages initialValues={['German','English','Spanish']}/>
//   There is NO refused state: Continue with nothing ticked does exactly what
//   Skip does. Both share one column; nothing moves.
//
// THIS SCREEN BUILDS ONE THING: CheckRow, the group's multi-select row.
//   It is its own component, NOT a kind/variant of OptionRow (decided
//   5 Oct 2026): pad 12 / 4 (OptionRow is 16 / 4), round 22 indicator with
//   an 11px tick. Later multi-select steps consume it.
//   DetailsScaffold, footer atoms — built in Profile 14 (screen-height-reference.jsx)
//   They are included below only so this file renders. Consume that build.
//
// SKIPPABLE — SkipLink present, so the step is skippable (profile README
// rule 0). Skip and an empty Continue both advance, save nothing, and never
// delete a value saved earlier. Neither shows a toast.
//
// OPTIONS — eight, fixed order, no limit (all eight may be ticked). Never
// reordered by device locale. Saved in LIST order, never tap order.
//
// SHORT SCREENS — eight rows (≈ 375) only just fit at 390 × 844 (spacer ≈ 6).
// At 375 × 667 the list is ≈ 170 short: the answer region (overflowY: auto,
// already in DetailsScaffold) scrolls internally. Rows keep 12 / 4 — never
// shorten them. Band and footer stay pinned.
//
// LAYOUT — the scaffold's column, unchanged:
//   StatusBar 54 · AppHeader 52 ("Share some details", back chevron)
//   content flex: 1, padding 4 / 24 / 0, overflow hidden
//     StepProgress steps={10} current={4}, marginBottom 26
//     h1 — Lora 700 / 30 / 1.12, two lines at 390, em on "language"
//     p  — Manrope 500 / 14.5 / 1.45
//     answer region — marginTop 12 + paddingTop 10, flex: 1, minHeight 0,
//                     overflowY auto
//       group: 8 × CheckRow (pad 12 / 4, divider except last)
//       → <div style={{flex:1}}/>
//     ProfileVisibility band — marginTop 6
//     footer row — marginTop 26, marginBottom 22: SkipLink · NextButton
//   HomeIndicator 28
//
// Shell components (StatusBar, AppHeader, StepProgress, NextButton, SkipLink,
// ProfileVisibility, Atmosphere, HomeIndicator, Icon) live in
// ../components/shared.jsx. Tokens: ../tokens/colors_and_type.css.
// ─────────────────────────────────────────────────────────────

const { useState: useStateDetails } = React;
const SHARE_STEPS_TOTAL = 10;

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
// CheckRow — multi-select option row (Profile 17, dating language)
//
// Its own component, NOT a variant of OptionRow (decided 5 Oct 2026).
// Pad 12 / 4 (OptionRow is 16 / 4), round 22 check indicator with an
// 11px white tick on violet when checked. Same wash, divider, radius
// and 180ms transitions as OptionRow so the two lists read as a pair.
// ─────────────────────────────────────────────────────────────
function CheckRow({ label, checked, onClick, last = false }) {
  return (
    <button type="button" onClick={onClick} role="checkbox" aria-checked={!!checked}
      style={{
        display: 'flex', alignItems: 'center', gap: 12,
        width: '100%', textAlign: 'left',
        padding: '12px 4px',
        background: checked ? 'rgba(129,42,236,0.06)' : 'transparent',
        borderRadius: 10,
        borderBottom: last ? 'none' : '1px solid var(--liq-border-soft)',
        transition: 'background 180ms',
      }}>
      <span style={{
        flex: 1, minWidth: 0,
        fontFamily: 'var(--liq-font-sans)', fontSize: 16,
        fontWeight: checked ? 700 : 500, lineHeight: 1.3,
        color: 'var(--liq-fg)',
      }}>{label}</span>
      <span style={{
        flex: 'none', width: 22, height: 22, borderRadius: '50%',
        background: checked ? 'var(--liq-primary-500)' : '#FFFFFF',
        border: checked ? '1.5px solid var(--liq-primary-500)' : '1.5px solid var(--liq-border)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        transition: 'background 180ms, border-color 180ms',
      }}>
        {checked && (
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none"
            stroke="#fff" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="5 12 10 17 19 7"/>
          </svg>
        )}
      </span>
    </button>
  );
}

// ─────────────────────────────────────────────────────────────
// 4. ScreenProfileLanguages — multi-select (Profile 17)
//
// 8 fixed languages, no limit, list order. Skippable (rule 0).
// Continue with nothing ticked behaves exactly like Skip — advances,
// saves nothing, never shows a toast (decided 5 Oct 2026). Rows are
// CheckRow (12 / 4). The answer region scrolls on short screens.
// ─────────────────────────────────────────────────────────────
const LANGUAGE_OPTIONS = ['German', 'English', 'Spanish', 'Italian', 'French', 'Turkish', 'Russian', 'Arabic'];

function ScreenProfileLanguages({ initialValues = [], initialPrivate = false, onBack, onSkip, onContinue }) {
  const [values, setValues] = useStateDetails(new Set(initialValues));
  const [priv, setPriv] = useStateDetails(initialPrivate);
  const toggle = (opt) => setValues(prev => {
    const next = new Set(prev);
    if (next.has(opt)) next.delete(opt); else next.add(opt);
    return next;
  });
  // Empty Continue = Skip. Never refused, so no toast on this screen.
  const handleContinue = () => {
    if (values.size === 0) { onSkip && onSkip(); return; }
    onContinue && onContinue(LANGUAGE_OPTIONS.filter(o => values.has(o)));
  };

  return (
    <DetailsScaffold
      step={4}
      headline={<>What's your preferred dating <em>language</em>?</>}
      subhelper="Select all that apply."
      onBack={onBack}
      visibility={<ProfileVisibility visible={!priv} onToggle={() => setPriv(v => !v)}/>}
      footer={<FooterSkipContinue onSkip={onSkip} onContinue={handleContinue}/>}
    >
      <div role="group" aria-label="Dating languages" style={{ display: 'flex', flexDirection: 'column' }}>
        {LANGUAGE_OPTIONS.map((opt, i) => (
          <CheckRow key={opt} label={opt}
            checked={values.has(opt)}
            onClick={() => toggle(opt)}
            last={i === LANGUAGE_OPTIONS.length - 1}/>
        ))}
      </div>

      <div style={{ flex: 1 }}/>
    </DetailsScaffold>
  );
}

Object.assign(window, { CheckRow, DetailsScaffold, FooterSkipContinue, ScreenProfileLanguages, LANGUAGE_OPTIONS });
