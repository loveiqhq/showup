// screen-future-reference.jsx — Profile creation 23 · Future
//
// Design reference for [Profile 23] Future — step 10 of 10 of "Share some
// details", the last step of profile creation. This is the code that renders
// spec-sheets/23-future.png. Read values from HERE, not from the PNG.
//
// Extracted verbatim from the Show Up UI kit
// (ui_kits/show-up/screens-profile-future.jsx). It is a reference
// implementation, not production code: recreate it in the target codebase's
// own environment and patterns. Do not ship this file.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// STATES — one component, two states, three frames on the sheet:
//   A · empty               <ScreenProfileFuture/>
//   B · all three answered  <ScreenProfileFuture initialValues={{
//         family: ['I want kids'],
//         partnership: ['Monogamous relationship'],
//         living: ['Stay local', 'Move abroad'] }}/>
//   C · frame only — state B with the answer region scrolled to the bottom.
//   There is NO refused state: Continue with nothing picked does exactly
//   what Skip does (decided 8 Oct 2026).
//
// CONSUMES from Profile 22 (screen-life-reference.jsx): LifePill,
// CategoryHeader, toggleLifePick. This file only adds FUTURE_CATEGORIES and
// FUTURE_EXCLUSIVE_OPTIONS. Do not copy the pill or the toggle.
//
// The answer region is flex: 1 · min-height 0 · overflow-y auto. It is
// ≈ 30 px short at 390 × 844 and scrolls there; it fits at 430 × 932.
//
// Store and track the enums.json §4 values, never these labels.
// ─────────────────────────────────────────────────────────────

// screens-profile-future.jsx — "What do you imagine for your future life?"
//
// Sister screen to screens-profile-life.jsx. Where the previous step
// captured the user's CURRENT life-situation (partnership, family,
// living), this one captures their VISION for it. Same chrome, pills
// and backdrop. Multi-select per category since 8 Oct 2026, with
// FUTURE_EXCLUSIVE_OPTIONS: `I want to find out together` clears the
// rest of its category everywhere, and the three family answers are
// mutually exclusive (wanting and not wanting kids cannot both hold).
//
// Three categories:
//   - Family vision        (kids / no kids / find out together)
//   - Partnership vision   (monogamous / open / ENM / find out together)
//   - Living style vision  (stay local / expat / abroad / find out together)
//
// Visual notes:
//   - Reuses LifePill, CategoryHeader and toggleLifePick from
//     screens-profile-life.jsx, loaded earlier in index.html. The visual
//     language is identical so the two screens read as a pair.
//   - Step 10 of 10 of the Share-some-details sub-flow — the last
//     segment and the last step of profile creation. Answers are NEVER
//     shown on the profile (matching only), so no ProfileVisibility band.

const { useState: useStateFuture } = React;

// ─────────────────────────────────────────────────────────────
// Data — all options sentence-cased per the brand rule.
// "I want to find out together" appears as the third option in
// every category — it's the relational, non-deterministic answer
// the brand wants to keep front-and-centre.
// ─────────────────────────────────────────────────────────────
const FUTURE_CATEGORIES = [
  {
    id: 'family',
    icon: 'users',
    label: 'Family vision',
    options: [
      'I want kids',
      "I don't want kids",
      'I want to find out together',
    ],
  },
  {
    id: 'partnership',
    icon: 'heart',
    label: 'Partnership vision',
    options: [
      'Monogamous relationship',
      'Open relationship',
      'Ethically non-monogamous',
      'I want to find out together',
    ],
  },
  {
    id: 'living',
    icon: 'compass',
    label: 'Living style vision',
    options: [
      'Stay local',
      'Expat life',
      'Move abroad',
      'I want to find out together',
    ],
  },
];

const FUTURE_TOGETHER = 'I want to find out together';
const FUTURE_EXCLUSIVE_OPTIONS = {
  family: ['I want kids', "I don't want kids", FUTURE_TOGETHER],
  partnership: [FUTURE_TOGETHER],
  living: [FUTURE_TOGETHER],
};

// ─────────────────────────────────────────────────────────────
// ScreenProfileFuture — three multi-select groups on one canvas.
// Last step of "Share some details"; Continue / Skip exit to the
// 48hrs Check-In intro (ScreenCheckIn step 1).
// ─────────────────────────────────────────────────────────────
function ScreenProfileFuture({
  initialValues = {},
  step = 10,
  totalSteps = (typeof SHARE_STEPS_TOTAL !== 'undefined' ? SHARE_STEPS_TOTAL : 10),
  onBack, onSkip, onContinue,
}) {
  const [values, setValues] = useStateFuture(initialValues);

  const anyAnswered = Object.values(values).some(v => Array.isArray(v) && v.length > 0);
  const toggle = (catId, option) => setValues(prev => toggleLifePick(prev, catId, option, FUTURE_EXCLUSIVE_OPTIONS));

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
        <StepProgress steps={totalSteps} current={step} style={{ marginBottom: 22 }}/>

        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
          letterSpacing: '-0.015em', lineHeight: 1.12, color: 'var(--liq-fg)',
          margin: '0 0 8px', textWrap: 'balance',
        }}>
          What do you imagine for your future <em>life</em>?
        </h1>

        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
          lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty', maxWidth: 320,
        }}>
          Select all that apply. Never shown on your profile. Used to find the right matches.
        </p>

        <div className="su-noscroll" style={{
          marginTop: 22, flex: 1, minHeight: 0, overflowY: 'auto',
          display: 'flex', flexDirection: 'column', gap: 22,
        }}>
          {FUTURE_CATEGORIES.map(cat => (
            <section key={cat.id} role="group" aria-label={cat.label}>
              <CategoryHeader icon={cat.icon} label={cat.label}/>
              <div style={{
                display: 'flex', flexWrap: 'wrap', gap: 8,
              }}>
                {cat.options.map(opt => (
                  <LifePill
                    key={opt}
                    label={opt}
                    multi
                    selected={(values[cat.id] || []).includes(opt)}
                    onClick={() => toggle(cat.id, opt)}
                  />
                ))}
              </div>
            </section>
          ))}
        </div>

        <div style={{
          flex: 'none', marginBottom: 22, marginTop: 16,
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        }}>
          <SkipLink onClick={onSkip}/>
          <NextButton label="Continue" color="orange" onClick={anyAnswered ? onContinue : onSkip}/>
        </div>
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, {
  ScreenProfileFuture,
  FUTURE_CATEGORIES,
  FUTURE_EXCLUSIVE_OPTIONS,});
