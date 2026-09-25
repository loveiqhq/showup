// screens-profile-details.jsx — "Share some details" sub-flow
//
// Sits AFTER ScreenProfileEmbrace2 ("Add profile details" CTA) and
// continues the optional background-details that flesh out a profile.
// Five SQPS screens — one question per screen — using the same chrome
// the rest of the kit uses (status bar, AppHeader, StepProgress,
// italic-flourish Lora headline, NextButton bottom-right).
//
// Order:
//   1. ScreenProfileHeight        — numeric cm input
//   2. ScreenProfileGender        — single-select (4 options)
//   3. ScreenProfileOrientation   — single-select (6 options)
//   4. ScreenProfileLanguages     — multi-select  (8 languages)
//   5. ScreenProfileEducation     — single-select (4 options)
//   6. ScreenProfileReligion      — single-select (8 options)
//   7. ScreenProfilePolitics      — single-select (8 options)
//
// Voice notes:
//   - One italicized word per headline per the brand rule.
//   - "Used to find the right matches" is the standard subhelper for
//     the matching-input screens; the height screen has a tighter
//     "Be honest — it helps us find the right matches" note because
//     a self-reported number invites a one-off nudge.
//   - "Don't show on my profile" is a *privacy* toggle (different
//     semantics from the marketing opt-out): you still answer the
//     question (we use it for matching) but it doesn't surface on
//     your public profile. Reused as the same row component.
//   - "Skip for now" appears only on the later (more optional)
//     screens — language and education — matching the reference.

const { useState: useStateDetails } = React;

// Total steps in this sub-flow. The reference wireframes show each
// screen one segment further along the progress bar, so we keep them
// numbered 1–5 here. The remaining slots reserve room for any later
// background-detail screens (work, values, height — the brief is open).
const SHARE_STEPS_TOTAL = 10;

// ─────────────────────────────────────────────────────────────
// PrivacyToggle — deprecated alias.
//
// The canonical control is <ProfileVisibility> in shared.jsx: a switch,
// positively framed, default ON, pinned above the footer. This wrapper
// only exists so older call sites keep rendering the unified control
// (note the inverted semantics: `checked` meant hidden).
// ─────────────────────────────────────────────────────────────
function PrivacyToggle({ checked, onToggle, label }) {
  return <ProfileVisibility visible={!checked} onToggle={onToggle} label={label}/>;
}

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

        <div className="su-noscroll" style={{
          marginTop: 22, flex: 1, minHeight: 0, overflowY: 'auto',
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
function useValidationToast(disabled, onContinue) {
  const [toast, setToast] = React.useState(false);
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

function FooterSkipContinue({ disabled, onSkip, onContinue, hint = 'Answer this to continue' }) {
  const [toast, press] = useValidationToast(disabled, onContinue);
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
function ScreenProfileHeight({ initialValue = '175', initialPrivate = false, onBack, onContinue }) {
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
      footer={<FooterSkipContinue disabled={!valid} onSkip={onContinue} onContinue={onContinue} hint="Enter a height between 120 and 230 cm to continue"/>}
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

// ─────────────────────────────────────────────────────────────
// 2. ScreenProfileGender — single-select
// ─────────────────────────────────────────────────────────────
const GENDER_OPTIONS = ['Woman', 'Man', 'Non-binary', 'Other'];

function ScreenProfileGender({ initialValue = null, initialPrivate = false, onBack, onContinue }) {
  const [value, setValue] = useStateDetails(initialValue);
  const [priv, setPriv] = useStateDetails(initialPrivate);

  return (
    <DetailsScaffold
      step={2}
      headline={<>Which gender describes <em>you</em> best?</>}
      subhelper="Used to find the right matches."
      onBack={onBack}
      visibility={<ProfileVisibility visible={!priv} onToggle={() => setPriv(v => !v)}/>}
      footer={<FooterContinue disabled={!value} onContinue={onContinue} hint="Pick a gender to continue"/>}
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

// ─────────────────────────────────────────────────────────────
// 3. ScreenProfileOrientation — single-select
// ─────────────────────────────────────────────────────────────
const ORIENTATION_OPTIONS = ['Straight', 'Gay', 'Lesbian', 'Bisexual', 'Pansexual', 'Other'];

function ScreenProfileOrientation({ initialValue = null, initialPrivate = false, onBack, onContinue }) {
  const [value, setValue] = useStateDetails(initialValue);
  const [priv, setPriv] = useStateDetails(initialPrivate);

  return (
    <DetailsScaffold
      step={3}
      headline={<>What's your sexual <em>orientation</em>?</>}
      subhelper="Used to find the right matches."
      onBack={onBack}
      visibility={<ProfileVisibility visible={!priv} onToggle={() => setPriv(v => !v)}/>}
      footer={<FooterContinue disabled={!value} onContinue={onContinue} hint="Pick an orientation to continue"/>}
    >
      <div role="radiogroup" style={{ display: 'flex', flexDirection: 'column' }}>
        {ORIENTATION_OPTIONS.map((opt, i) => (
          <OptionRow key={opt} kind="radio" label={opt}
            selected={value === opt}
            onClick={() => setValue(opt)}
            last={i === ORIENTATION_OPTIONS.length - 1}/>
        ))}
      </div>

      <div style={{ flex: 1 }}/>
    </DetailsScaffold>
  );
}

// ─────────────────────────────────────────────────────────────
// 4. ScreenProfileLanguages — multi-select
//
// 8 languages from the reference (German, English, Spanish, Italian,
// French, Turkish, Russian, Arabic). The subhelper switches to
// "Select all that apply" because the answer model is plural.
// Footer gets the "Skip for now" affordance — picking your dating
// language is a softer ask than the matching attributes above.
// Rows compact to 14px vertical padding so all 8 fit comfortably
// above the privacy toggle on the 844-tall canvas.
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

  return (
    <DetailsScaffold
      step={4}
      headline={<>What's your preferred dating <em>language</em>?</>}
      subhelper="Select all that apply."
      onBack={onBack}
      visibility={<ProfileVisibility visible={!priv} onToggle={() => setPriv(v => !v)}/>}
      footer={<FooterSkipContinue onSkip={onSkip} onContinue={onContinue}/>}
    >
      {/* Tighter row padding (12px) so all 8 options + the privacy
          toggle clear the keyboard-less canvas. */}
      <div role="group" style={{ display: 'flex', flexDirection: 'column' }}>
        {LANGUAGE_OPTIONS.map((opt, i) => (
          <button key={opt}
            type="button"
            onClick={() => toggle(opt)}
            role="checkbox"
            aria-checked={values.has(opt)}
            style={{
              display: 'flex', alignItems: 'center', gap: 12,
              width: '100%', textAlign: 'left',
              padding: '12px 4px',
              background: values.has(opt) ? 'rgba(129,42,236,0.06)' : 'transparent',
              borderRadius: 10,
              borderBottom: i === LANGUAGE_OPTIONS.length - 1 ? 'none' : '1px solid var(--liq-border-soft)',
              transition: 'background 180ms',
            }}>
            <span style={{
              flex: 1, minWidth: 0,
              fontFamily: 'var(--liq-font-sans)', fontSize: 16,
              fontWeight: values.has(opt) ? 700 : 500, lineHeight: 1.3,
              color: 'var(--liq-fg)',
            }}>
              {opt}
            </span>
            <span style={{
              flex: 'none', width: 22, height: 22, borderRadius: '50%',
              background: values.has(opt) ? 'var(--liq-primary-500)' : '#FFFFFF',
              border: values.has(opt) ? '1.5px solid var(--liq-primary-500)' : '1.5px solid var(--liq-border)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              transition: 'background 180ms, border-color 180ms',
            }}>
              {values.has(opt) && (
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none"
                  stroke="#fff" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="5 12 10 17 19 7"/>
                </svg>
              )}
            </span>
          </button>
        ))}
      </div>

      <div style={{ flex: 1 }}/>
    </DetailsScaffold>
  );
}

// ─────────────────────────────────────────────────────────────
// 5. ScreenProfileEducation — single-select
//
// Last of the five reference screens. Has the same "Skip for now"
// affordance as language — education is the most optional of the
// matching attributes, and the reference confirms a skip is shown.
// Standard radio list; the values are kept verbatim from the brief
// (A-Levels/Abitur reads German-market — Show Up's reference uses
// metric units, so the same audience).
// ─────────────────────────────────────────────────────────────
const EDUCATION_OPTIONS = ['A-Levels / Abitur', 'Apprenticeship', 'University degree', 'PhD'];

function ScreenProfileEducation({ initialValue = null, initialPrivate = false, onBack, onSkip, onContinue }) {
  const [value, setValue] = useStateDetails(initialValue);
  const [priv, setPriv] = useStateDetails(initialPrivate);

  return (
    <DetailsScaffold
      step={5}
      headline={<>What's your highest level of <em>education</em>?</>}
      subhelper="Used to find the right matches."
      onBack={onBack}
      visibility={<ProfileVisibility visible={!priv} onToggle={() => setPriv(v => !v)}/>}
      footer={<FooterSkipContinue onSkip={onSkip} onContinue={onContinue}/>}
    >
      <div role="radiogroup" style={{ display: 'flex', flexDirection: 'column' }}>
        {EDUCATION_OPTIONS.map((opt, i) => (
          <OptionRow key={opt} kind="radio" label={opt}
            selected={value === opt}
            onClick={() => setValue(opt)}
            last={i === EDUCATION_OPTIONS.length - 1}/>
        ))}
      </div>

      <div style={{ flex: 1 }}/>
    </DetailsScaffold>
  );
}

// ─────────────────────────────────────────────────────────────
// 6. ScreenProfileReligion — single-select
//
// Same radio primitive as gender/orientation/education. Eight
// options from the reference — the seven major traditions plus a
// catch-all "Spiritual / other" so the answer isn't binary between
// a named religion and Atheist. "Skip for now" is present: beliefs
// are an inherently optional matching attribute and a real subset
// of users will refuse to volunteer them.
//
// One italicized word per headline rule → emphasis on *religious*
// (the noun-as-adjective carries the editorial flourish; the rest
// of the phrase is plain).
// ─────────────────────────────────────────────────────────────
const RELIGION_OPTIONS = [
  'Protestant', 'Catholic', 'Orthodox', 'Jewish',
  'Buddhist', 'Hindu', 'Atheist', 'Spiritual / other',
];

function ScreenProfileReligion({ initialValue = null, initialPrivate = false, onBack, onSkip, onContinue }) {
  const [value, setValue] = useStateDetails(initialValue);
  const [priv, setPriv] = useStateDetails(initialPrivate);

  return (
    <DetailsScaffold
      step={6}
      headline={<>What are your <em>religious</em> beliefs?</>}
      subhelper="Used to find the right matches."
      onBack={onBack}
      visibility={<ProfileVisibility visible={!priv} onToggle={() => setPriv(v => !v)}/>}
      footer={<FooterSkipContinue onSkip={onSkip} onContinue={onContinue}/>}
    >
      {/* 8 rows on the 844 canvas — tighten vertical padding to 12px
          (mirrors ScreenProfileLanguages) so the privacy toggle and
          footer clear without scroll. */}
      <div role="radiogroup" style={{ display: 'flex', flexDirection: 'column' }}>
        {RELIGION_OPTIONS.map((opt, i) => (
          <OptionRowCompact key={opt} kind="radio" label={opt}
            selected={value === opt}
            onClick={() => setValue(opt)}
            last={i === RELIGION_OPTIONS.length - 1}/>
        ))}
      </div>

      <div style={{ flex: 1 }}/>
    </DetailsScaffold>
  );
}

// ─────────────────────────────────────────────────────────────
// 7. ScreenProfilePolitics — single-select
//
// Eight options laid out roughly along a left → right spectrum
// with Libertarian and Apolitical as the off-axis answers. The
// order in the reference is *not* strictly linear (Conservative
// sits between Mid-right and Right rather than at the far end);
// we preserve that ordering verbatim — it's how the original
// product team framed the spectrum and it keeps Libertarian /
// Apolitical at the tail where "other" options belong.
// ─────────────────────────────────────────────────────────────
const POLITICS_OPTIONS = [
  'Left', 'Mid-left', 'Middle', 'Mid-right',
  'Conservative', 'Right', 'Libertarian', 'Apolitical',
];

function ScreenProfilePolitics({ initialValue = null, initialPrivate = false, onBack, onSkip, onContinue }) {
  const [value, setValue] = useStateDetails(initialValue);
  const [priv, setPriv] = useStateDetails(initialPrivate);

  return (
    <DetailsScaffold
      step={7}
      headline={<>What are your <em>political</em> beliefs?</>}
      subhelper="Used to find the right matches."
      onBack={onBack}
      visibility={<ProfileVisibility visible={!priv} onToggle={() => setPriv(v => !v)}/>}
      footer={<FooterSkipContinue onSkip={onSkip} onContinue={onContinue}/>}
    >
      <div role="radiogroup" style={{ display: 'flex', flexDirection: 'column' }}>
        {POLITICS_OPTIONS.map((opt, i) => (
          <OptionRowCompact key={opt} kind="radio" label={opt}
            selected={value === opt}
            onClick={() => setValue(opt)}
            last={i === POLITICS_OPTIONS.length - 1}/>
        ))}
      </div>

      <div style={{ flex: 1 }}/>
    </DetailsScaffold>
  );
}

// ─────────────────────────────────────────────────────────────
// OptionRowCompact — same primitive as OptionRow but with the
// 12px vertical padding used on the 8-row screens (religion,
// politics). Mirrors the inline-style choice in
// ScreenProfileLanguages — kept as a named component so the two
// 8-option screens read identically and stay in sync if the row
// chrome ever changes.
// ─────────────────────────────────────────────────────────────
function OptionRowCompact({ kind = 'radio', label, selected, onClick, last = false }) {
  return (
    <button
      type="button"
      onClick={onClick}
      role={kind === 'radio' ? 'radio' : 'checkbox'}
      aria-checked={!!selected}
      style={{
        display: 'flex', alignItems: 'center', gap: 12,
        width: '100%', textAlign: 'left',
        padding: '12px 4px',
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
          {selected && <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#fff' }}/>}
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

Object.assign(window, {
  ScreenProfileHeight,
  ScreenProfileGender,
  ScreenProfileOrientation,
  ScreenProfileLanguages,
  ScreenProfileEducation,
  ScreenProfileReligion,
  ScreenProfilePolitics,
  PrivacyToggle,
  OptionRow,
  OptionRowCompact,
});
