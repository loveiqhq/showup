// screens-profile-life.jsx — "What fits your current life?" step
//
// Step 8 of the "Share some details" sub-flow. Three categorical
// matching questions on a single canvas — partnership, family,
// living. Unlike the prior single-question SQPS steps (height,
// gender, orientation…), this one collapses three lightweight
// categoricals into one screen because each one has 4–6 short
// options and the answers are mostly mutually exclusive.
//
// Selection model: ONE answer per category (radio, not checkbox).
// A second variant (multi-select) lives in a sister screen and
// shares the same chrome.
//
// Visual notes:
//   - Each category header carries a lucide stroke icon (heart /
//     users / home) tinted with the lavender accent. The icon sits
//     inside a soft lavender circle so it reads as a discrete
//     category marker, not decoration.
//   - Pills follow the existing Chip primitive: white-on-warm-ink
//     border idle, primary-violet fill on select. Sentence-case
//     all options per the brand voice rule.
//   - Footer follows the "Skip for now" + Continue pattern used by
//     the later (more optional) details screens.

const { useState: useStateLife } = React;

// ─────────────────────────────────────────────────────────────
// Data — all options sentence-cased per the brand rule.
// ─────────────────────────────────────────────────────────────
const LIFE_CATEGORIES = [
  {
    id: 'partnership',
    icon: 'heart',
    label: 'Partnership situation',
    options: [
      'Single',
      'Open relationship',
      'Ethically non-monogamous',
      'Partnered',
      'Married',
      'Divorced',
    ],
  },
  {
    id: 'family',
    icon: 'users',
    label: 'Family situation',
    options: [
      'No children',
      '1 child',
      'Multiple children',
      'Single parent',
      'Co-parenting',
      'Empty nester',
    ],
  },
  {
    id: 'living',
    icon: 'home',
    label: 'Living situation',
    options: [
      'Living alone',
      'Living with roommates',
      'Living with family',
      'Living with children',
    ],
  },
];

// ─────────────────────────────────────────────────────────────
// CategoryHeader — icon medallion + label
//
// The icon is set inside a 28×28 lavender wash circle. Lavender
// (`--liq-lavender-400`) on `--liq-lavender-50` so the marker is
// visible at the small size without competing with the primary
// violet that the selected pill uses. Stroke icons at 1.7 weight,
// 16px — matches the rest of the kit's icon geometry.
// ─────────────────────────────────────────────────────────────
function CategoryHeader({ icon, label }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10,
      marginBottom: 12,
    }}>
      <span style={{
        width: 28, height: 28, borderRadius: '50%',
        background: 'rgba(167,139,250,0.16)',
        color: 'var(--liq-primary-500)',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        flex: 'none',
      }}>
        <Icon name={icon} size={16} stroke={1.9}/>
      </span>
      <h2 style={{
        margin: 0,
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700,
        fontSize: 15, letterSpacing: '0.005em',
        color: 'var(--liq-fg)',
      }}>
        {label}
      </h2>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// LifePill — chip optimised for this screen
//
// Marginally tighter than the kit's default Chip (32px tall, 12px
// horizontal padding, 13px text) because three categories of
// chips need to fit on one 844-tall canvas without scroll. Same
// selected/unselected colour story as the kit's Chip "lavender"
// tone — white border idle, solid primary-violet on select — so
// the visual language stays consistent across the product.
// ─────────────────────────────────────────────────────────────
function LifePill({ label, selected, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      role="radio"
      aria-checked={!!selected}
      style={{
        height: 34,
        padding: '0 14px',
        borderRadius: 9999,
        background: selected ? 'var(--liq-primary-500)' : '#FFFFFF',
        color: selected ? '#FFFFFF' : 'var(--liq-fg)',
        border: selected
          ? '1px solid var(--liq-primary-500)'
          : '1px solid var(--liq-border)',
        fontFamily: 'var(--liq-font-sans)',
        fontWeight: selected ? 700 : 500,
        fontSize: 13.5,
        lineHeight: 1,
        whiteSpace: 'nowrap',
        display: 'inline-flex', alignItems: 'center',
        transition: 'background 180ms cubic-bezier(.22,1,.36,1), color 180ms, border-color 180ms, transform 120ms',
        boxShadow: selected ? '0 2px 8px rgba(129,42,236,0.22)' : 'none',
      }}>
      {label}
    </button>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileLife — three radio-groups on one canvas
// ─────────────────────────────────────────────────────────────
function ScreenProfileLife({
  initialValues = {},
  step = 8,
  totalSteps = (typeof SHARE_STEPS_TOTAL !== 'undefined' ? SHARE_STEPS_TOTAL : 10),
  onBack, onSkip, onContinue,
}) {
  const [values, setValues] = useStateLife(initialValues);

  // At least one category answered → enable continue. (Per
  // category isn't required — the "Skip for now" affordance is
  // already in the footer; we don't want to gate progression on a
  // perfect tri-fecta of answers.)
  const anyAnswered = Object.values(values).some(Boolean);

  const setOne = (catId, option) => {
    setValues(prev => ({
      ...prev,
      // Tap-again-to-clear: if the user re-taps the selected
      // option, the answer clears. Matches the kit's general
      // radio-but-also-deselectable behaviour on other SQPS
      // screens (gender, orientation).
      [catId]: prev[catId] === option ? null : option,
    }));
  };

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
          What fits your current <em>life</em>?
        </h1>

        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
          lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty', maxWidth: 320,
        }}>
          Pick one per category. Used to find the right matches.
        </p>

        <div style={{
          marginTop: 22, flex: 1, minHeight: 0,
          display: 'flex', flexDirection: 'column', gap: 22,
        }}>
          {LIFE_CATEGORIES.map(cat => (
            <section key={cat.id} role="radiogroup" aria-label={cat.label}>
              <CategoryHeader icon={cat.icon} label={cat.label}/>
              <div style={{
                display: 'flex', flexWrap: 'wrap', gap: 8,
              }}>
                {cat.options.map(opt => (
                  <LifePill
                    key={opt}
                    label={opt}
                    selected={values[cat.id] === opt}
                    onClick={() => setOne(cat.id, opt)}
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
          <div style={{
            opacity: anyAnswered ? 1 : 0.42,
            filter: anyAnswered ? 'none' : 'saturate(0.4)',
            pointerEvents: anyAnswered ? 'auto' : 'none',
            transition: 'opacity 180ms, filter 180ms',
          }}>
            <NextButton label="Continue" color="orange" onClick={onContinue}/>
          </div>
        </div>
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, {
  ScreenProfileLife,
  LIFE_CATEGORIES,
  LifePill,
  CategoryHeader,
});
