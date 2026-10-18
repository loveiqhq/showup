// screen-life-reference.jsx — Profile creation 22 · Life
//
// Design reference for [Profile 22] Life — step 9 of 10 of "Share some
// details". This is the code that renders spec-sheets/22-life.png. Read
// values from HERE, not from the PNG.
//
// Extracted verbatim from the Show Up UI kit
// (ui_kits/show-up/screens-profile-life.jsx). It is a reference
// implementation, not production code: recreate it in the target codebase's
// own environment and patterns. Do not ship this file.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// STATES — one component, two states on the sheet:
//   A · empty               <ScreenProfileLife/>
//   B · all three answered  <ScreenProfileLife initialValues={{
//         partnership: ['Single', 'Divorced'],
//         family: ['I have children', 'Co-parenting'],
//         living: ['Living alone'] }}/>
//   There is NO refused state: Continue with nothing picked does exactly
//   what Skip does (decided 8 Oct 2026). Both frames share one column.
//
// BUILT HERE, consumed by Future (Profile 23): LifePill, CategoryHeader,
// toggleLifePick (the multi-select + exclusive-option rule). Future passes
// its own exclusive map; do not fork the toggle.
//
// NOT DetailsScaffold. No ProfileVisibility band — the answers are never
// shown on the profile. Chrome (StatusBar, AppHeader, StepProgress, SkipLink,
// NextButton, Atmosphere, HomeIndicator) comes from components/shared.jsx.
//
// The answer region is flex: 1 · min-height 0 · overflow-y auto. It fits at
// 390 × 844 with ≈ 12 px to spare and scrolls at 375 × 667.
//
// Store and track the enums.json §4 values, never these labels.
// ─────────────────────────────────────────────────────────────

// screens-profile-life.jsx — "What fits your current life?" step
//
// Step 9 of 10 of the "Share some details" sub-flow. Three categorical
// matching questions on a single canvas — partnership, family,
// living. Unlike the prior single-question steps (height, gender,
// orientation…), this one collapses three lightweight categoricals
// into one screen. Answers are NEVER shown on the profile — matching
// only — so there is no ProfileVisibility band.
//
// Selection model (changed 8 Oct 2026): MULTI-select per category
// (checkbox, not radio) — single parent, co-parenting and "I have
// children" can all be true at once. Exclusive options (EXCLUSIVE_
// OPTIONS, today only `No children`) clear the rest of their category
// and are cleared by any other pick. The sister screen (Future)
// uses the same model with its own FUTURE_EXCLUSIVE_OPTIONS.
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
      'Divorced',
    ],
  },
  {
    id: 'family',
    icon: 'users',
    label: 'Family situation',
    options: [
      'No children',
      'I have children',
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

// Picking one of these clears the rest of its category; picking
// anything else in the category clears it.
const LIFE_EXCLUSIVE_OPTIONS = { family: ['No children'] };

// Shared multi-select toggle for Life and Future. An exclusive option
// replaces the category's picks; any other pick drops the exclusive ones.
function toggleLifePick(prev, catId, option, exclusiveMap) {
  const cur = prev[catId] || [];
  const excl = exclusiveMap[catId] || [];
  let next;
  if (cur.includes(option)) next = cur.filter(o => o !== option);
  else if (excl.includes(option)) next = [option];
  else next = [...cur.filter(o => !excl.includes(o)), option];
  return { ...prev, [catId]: next };
}

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
function LifePill({ label, selected, onClick, multi = false }) {
  return (
    <button
      type="button"
      onClick={onClick}
      role={multi ? 'checkbox' : 'radio'}
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
// ScreenProfileLife — three multi-select groups on one canvas
// initialValues: { partnership: [..], family: [..], living: [..] }
// ─────────────────────────────────────────────────────────────
function ScreenProfileLife({
  initialValues = {},
  step = 9,
  totalSteps = (typeof SHARE_STEPS_TOTAL !== 'undefined' ? SHARE_STEPS_TOTAL : 10),
  onBack, onSkip, onContinue,
}) {
  const [values, setValues] = useStateLife(initialValues);

  // Continue is never disabled (8 Oct 2026, same rule as 17–21):
  // with nothing picked it behaves exactly like Skip.
  const anyAnswered = Object.values(values).some(v => Array.isArray(v) && v.length > 0);
  const toggle = (catId, option) => setValues(prev => toggleLifePick(prev, catId, option, LIFE_EXCLUSIVE_OPTIONS));

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
          Select all that apply. Never shown on your profile. Used to find the right matches.
        </p>

        <div className="su-noscroll" style={{
          marginTop: 22, flex: 1, minHeight: 0, overflowY: 'auto',
          display: 'flex', flexDirection: 'column', gap: 22,
        }}>
          {LIFE_CATEGORIES.map(cat => (
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
  ScreenProfileLife,
  LIFE_CATEGORIES,
  LIFE_EXCLUSIVE_OPTIONS,
  toggleLifePick,
  LifePill,
  CategoryHeader,
});
