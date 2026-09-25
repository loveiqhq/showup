// screens-profile-habits.jsx — "Your lifestyle and habits" step
//
// Step 9 of the "Share some details" sub-flow — the final
// matching-attributes screen before the user lands in the app.
// Four short, mutually-exclusive Yes / No / Occasionally
// categoricals collapsed onto a single canvas. The semantics
// (substance-use frequency) are all the same shape, so the
// answer surface repeats one chip-row primitive four times.
//
// Selection model: ONE answer per category (radio). Tapping a
// selected chip clears it — matches the existing SQPS radio
// behaviour on the gender / orientation / life screens.
//
// Why this layout, not the OptionRow stack the rest of the
// details sub-flow uses?
//   - Each question has the same three short options. A stack
//     of full-width rows would burn 4 × 3 × 44 = 528px before
//     padding; the canvas can't take it without scroll and the
//     repetition reads as bureaucratic.
//   - Chip rows compact each answer to ~46px tall, so four
//     categories + privacy toggle clear the canvas.
//   - Same chip geometry as the Life screen so the visual
//     language stays consistent across the sub-flow.

const { useState: useStateHabits } = React;

// ─────────────────────────────────────────────────────────────
// Data — sentence-case per the brand voice rule.
//
// Note: corrected "Marijuhana" → "Cannabis" from the reference
// wireframe. The reference had a spelling error AND used the
// US-leaning slang; Show Up's voice is European-leaning and
// the broader term reads more neutrally on a matching surface.
// "Recreational drugs" stays as written — it's the established
// way these screens phrase the question.
// ─────────────────────────────────────────────────────────────
const HABIT_CATEGORIES = [
  { id: 'alcohol',  label: 'Do you drink alcohol?',          icon: 'wine',     options: ['Yes', 'No', 'Occasionally'] },
  { id: 'smoke',    label: 'Do you smoke?',                  icon: 'cigarette',options: ['Yes', 'No', 'Occasionally'] },
  { id: 'cannabis', label: 'Do you use cannabis?',           icon: 'leaf',     options: ['Yes', 'No', 'Occasionally'] },
  { id: 'drugs',    label: 'Do you use recreational drugs?', icon: 'pill',     options: ['Yes', 'No', 'Occasionally'] },
];

// ─────────────────────────────────────────────────────────────
// HabitIcon — one small stroke glyph per category
//
// Matches the rest of the kit's icon language: 24×24 viewBox,
// stroke-only, 1.75 stroke-width, round caps & joins. Rendered
// at 20px next to each question label in fg-muted so it reads
// as a quiet wayfinding glyph, not a decorative sticker.
//
// - wine      → simple wine glass (bowl, stem, foot)
// - cigarette → cigarette with a curl of smoke
// - leaf      → single offset leaf with central vein
// - pill      → capsule split down the middle (rotated)
// ─────────────────────────────────────────────────────────────
function HabitIcon({ kind, size = 20, color = 'var(--liq-fg-muted)' }) {
  const common = {
    width: size, height: size, viewBox: '0 0 24 24',
    fill: 'none', stroke: color, strokeWidth: 1.75,
    strokeLinecap: 'round', strokeLinejoin: 'round',
    'aria-hidden': true,
  };
  switch (kind) {
    case 'wine':
      return (
        <svg {...common}>
          <path d="M7 3h10l-.6 6a4.4 4.4 0 0 1-8.8 0z"/>
          <path d="M12 15v6"/>
          <path d="M8.5 21h7"/>
        </svg>
      );
    case 'cigarette':
      return (
        <svg {...common}>
          <rect x="2.5" y="14.5" width="15" height="4" rx="0.6"/>
          <path d="M7.5 14.5v4"/>
          <path d="M19 14.5v4"/>
          <path d="M21 14.5v4"/>
          <path d="M16 4c0 1.4 1.5 1.4 1.5 2.8S16 8.2 16 9.6s1.5 1.4 1.5 2.8"/>
          <path d="M20 5.5c0 1.1 1 1.1 1 2.2s-1 1.1-1 2.2"/>
        </svg>
      );
    case 'leaf':
      return (
        <svg {...common}>
          <path d="M4 20c0-7 5.5-13.5 16-13.5C20 16.5 13.5 22 6.5 22Z"/>
          <path d="M4 20c5-2.5 9-6.5 11.5-11.5"/>
        </svg>
      );
    case 'pill':
      return (
        <svg {...common}>
          <path d="m10.5 20.5 10-10a4.95 4.95 0 0 0-7-7l-10 10a4.95 4.95 0 0 0 7 7Z"/>
          <path d="m8.5 8.5 7 7"/>
        </svg>
      );
    default:
      return null;
  }
}

// ─────────────────────────────────────────────────────────────
// HabitPill — chip primitive for this screen
//
// Identical geometry to the LifePill on the previous step so
// the two screens read as part of the same family. Slightly
// wider horizontal padding here (16px instead of 14px) because
// the labels are short ("Yes", "No", "Occasionally") and the
// chips look visually thin without it.
// ─────────────────────────────────────────────────────────────
function HabitPill({ label, selected, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      role="radio"
      aria-checked={!!selected}
      style={{
        height: 38,
        padding: '0 18px',
        borderRadius: 12,
        background: selected ? 'var(--liq-primary-500)' : '#FFFFFF',
        color: selected ? '#FFFFFF' : 'var(--liq-fg)',
        border: selected
          ? '1px solid var(--liq-primary-500)'
          : '1px solid var(--liq-border)',
        fontFamily: 'var(--liq-font-sans)',
        fontWeight: selected ? 700 : 500,
        fontSize: 14,
        lineHeight: 1,
        whiteSpace: 'nowrap',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        transition: 'background 180ms cubic-bezier(.22,1,.36,1), color 180ms, border-color 180ms, transform 120ms',
        boxShadow: selected ? '0 2px 8px rgba(129,42,236,0.22)' : 'none',
      }}>
      {label}
    </button>
  );
}

// ─────────────────────────────────────────────────────────────
// HabitCategory — one labelled radio row
// ─────────────────────────────────────────────────────────────
function HabitCategory({ category, value, onSelect }) {
  return (
    <section role="radiogroup" aria-label={category.label}>
      <h2 style={{
        margin: '0 0 10px',
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700,
        fontSize: 15, letterSpacing: '0.005em',
        color: 'var(--liq-fg)',
        display: 'flex', alignItems: 'center', gap: 8,
      }}>
        <span style={{
          flex: 'none',
          width: 20, height: 20,
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          color: 'var(--liq-fg-muted)',
        }}>
          <HabitIcon kind={category.icon}/>
        </span>
        {category.label}
      </h2>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {category.options.map(opt => (
          <HabitPill
            key={opt}
            label={opt}
            selected={value === opt}
            onClick={() => onSelect(value === opt ? null : opt)}
          />
        ))}
      </div>
    </section>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileHabits
//
// Step 9. All four answers are optional — Skip-for-now sits
// in the footer alongside Continue, matching the later
// (more-optional) details screens. Continue lights up as soon
// as ANY category is answered; we don't gate on a perfect
// four-of-four because the value of this screen is matching
// signal, not a completeness gate.
// ─────────────────────────────────────────────────────────────
function ScreenProfileHabits({
  initialValues = {},
  initialPrivate = false,
  step = 9,
  totalSteps = (typeof SHARE_STEPS_TOTAL !== 'undefined' ? SHARE_STEPS_TOTAL : 10),
  onBack, onSkip, onContinue,
}) {
  const [values, setValues] = useStateHabits(initialValues);
  const [priv, setPriv] = useStateHabits(initialPrivate);

  const anyAnswered = Object.values(values).some(Boolean);

  const setOne = (catId, option) => {
    setValues(prev => ({ ...prev, [catId]: option }));
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
          Your <em>lifestyle</em> and habits
        </h1>

        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
          lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty', maxWidth: 320,
        }}>
          Pick one per question. All optional — we use it for matching.
        </p>

        <div className="su-noscroll" style={{
          marginTop: 22, flex: 1, minHeight: 0, overflowY: 'auto',
          display: 'flex', flexDirection: 'column', gap: 18,
        }}>
          {HABIT_CATEGORIES.map(cat => (
            <HabitCategory
              key={cat.id}
              category={cat}
              value={values[cat.id]}
              onSelect={(opt) => setOne(cat.id, opt)}
            />
          ))}
        </div>

        {/* Pinned visibility band — outside the scrolling list, so it and
            the footer are always in the viewport. */}
        <div style={{ marginTop: 6, flex: 'none' }}>
          <ProfileVisibility visible={!priv} onToggle={() => setPriv(v => !v)}/>
        </div>

        <div style={{
          flex: 'none', marginTop: 26, marginBottom: 22,
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
  ScreenProfileHabits,
  HABIT_CATEGORIES,
  HabitPill,
  HabitCategory,
  HabitIcon,
});
