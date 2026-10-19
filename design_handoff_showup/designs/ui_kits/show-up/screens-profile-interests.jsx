// screens-profile-interests.jsx — "Choose up to 10 things you are really into"
//
// Step 8 of the "Share some details" sub-flow (after height, gender,
// orientation, language, education, religion, politics — see
// screens-profile-details.jsx). The reference wireframe is a multi-
// select of interest chips, grouped under three "What makes my X
// great" prompts, capped at 10 picks.
//
// Design decisions vs. the old wireframe:
//   - Every chip carries a stroke-line icon to the left of its label.
//     The icons are Lucide-geometry inline SVGs at 18px (stroke 1.7,
//     currentColor) — same system rules the rest of the kit follows.
//   - The picker is a true pill: rounded-full, hairline border in
//     warm-ink alpha, filled violet on selection (matching the rest
//     of the design system; selected = filled, never just-an-outline).
//   - The counter shifts as you pick. Below 10 it reads "N picked ·
//     M left"; at the cap it switches to "10 of 10 picked" in orange
//     so the user knows further taps would deselect rather than add.
//   - "Show more" expands each section in place to reveal a second
//     row of options without breaking the user out of the page.
//   - Continue is gated until at least one is picked. "Skip for now"
//     carries through from the surrounding sub-flow.
//
// We add a small INTEREST_ICONS map (16 glyphs) here rather than in
// shared.jsx — these are specific to this picker and shouldn't bloat
// the global icon set used elsewhere in the app.

const { useState: useStateInterests, useMemo: useMemoInterests } = React;

// Total steps in the Share-some-details sub-flow. Mirror the constant
// from screens-profile-details.jsx so the StepProgress matches.
const SHARE_STEPS_TOTAL_INTERESTS = 10;
const INTERESTS_MAX = 10;

// ─────────────────────────────────────────────────────────────
// Inline icon set for the interest chips.
//
// All 24×24 viewBox, stroke-only, currentColor, stroke-width 1.7 —
// same geometry rules as the global ICONS map in shared.jsx.
// ─────────────────────────────────────────────────────────────
const INTEREST_ICONS = {
  // —— Evenings ——
  gym: (
    <g><circle cx="3.5" cy="12" r="1.2"/><rect x="5" y="9" width="2.5" height="6" rx="0.6"/>
       <line x1="7.5" y1="12" x2="16.5" y2="12"/>
       <rect x="16.5" y="9" width="2.5" height="6" rx="0.6"/><circle cx="20.5" cy="12" r="1.2"/></g>
  ),
  cooking: (
    <g><path d="M5 11h14"/><path d="M6 11l1.4 7a1.6 1.6 0 0 0 1.6 1.4h6a1.6 1.6 0 0 0 1.6-1.4L18 11"/>
       <path d="M9 11V8a3 3 0 0 1 6 0v3"/></g>
  ),
  tv: (
    <g><rect x="3" y="7" width="18" height="12" rx="2"/>
       <line x1="8" y1="3" x2="12" y2="7"/><line x1="16" y1="3" x2="12" y2="7"/></g>
  ),
  reading: (
    <g><path d="M3 5.5C5.5 5 8 5 10 6.5v13C8 18 5.5 18 3 18.5z"/>
       <path d="M21 5.5C18.5 5 16 5 14 6.5v13c2-1.5 4.5-1.5 7-1z"/>
       <line x1="12" y1="6.5" x2="12" y2="19.5"/></g>
  ),
  walk: (
    <g><circle cx="13" cy="4.5" r="1.4"/>
       <path d="M11 11l1-3 2.5 1.5L17 12"/>
       <path d="M12 8l-2 4 2.5 2v5"/><path d="M9 21l1.5-4"/></g>
  ),
  gaming: (
    <g><path d="M5 8h14a3 3 0 0 1 3 3v3a3 3 0 0 1-5.5 1.6l-.8-1.1a2 2 0 0 0-1.6-.8h-4.2a2 2 0 0 0-1.6.8l-.8 1.1A3 3 0 0 1 2 14v-3a3 3 0 0 1 3-3z"/>
       <line x1="7" y1="11" x2="9" y2="11"/><line x1="8" y1="10" x2="8" y2="12"/>
       <circle cx="16" cy="11" r="0.9"/><circle cx="17.5" cy="13" r="0.9"/></g>
  ),
  // —— Weekends ——
  trip: (
    <g><rect x="3" y="7" width="18" height="13" rx="2"/>
       <path d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2"/>
       <line x1="12" y1="11" x2="12" y2="16"/></g>
  ),
  brunch: (
    <g><path d="M4 9h12a1 1 0 0 1 1 1v3a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4v-3a1 1 0 0 1 0-1z"/>
       <path d="M17 10h2a2 2 0 0 1 0 4h-2"/>
       <line x1="7" y1="3" x2="7" y2="6"/><line x1="11" y1="3" x2="11" y2="6"/><line x1="15" y1="3" x2="15" y2="6"/></g>
  ),
  sports: (
    <g><circle cx="12" cy="12" r="9"/>
       <polygon points="12 7 16 10 14.5 14.5 9.5 14.5 8 10"/>
       <line x1="12" y1="3" x2="12" y2="7"/><line x1="20" y1="9" x2="16" y2="10"/>
       <line x1="4" y1="9" x2="8" y2="10"/><line x1="8" y1="20" x2="9.5" y2="14.5"/>
       <line x1="16" y1="20" x2="14.5" y2="14.5"/></g>
  ),
  markets: (
    <g><path d="M4 8h16l-1.5 11a2 2 0 0 1-2 1.6H7.5A2 2 0 0 1 5.5 19z"/>
       <path d="M8 8V6a4 4 0 0 1 8 0v2"/></g>
  ),
  hiking: (
    <g><polyline points="3 19 9 9 13 14 17 7 21 19"/><polyline points="3 19 21 19"/></g>
  ),
  cycling: (
    <g><circle cx="5.5" cy="17.5" r="3.5"/><circle cx="18.5" cy="17.5" r="3.5"/>
       <polyline points="5.5 17.5 11 9 14 14 18.5 17.5"/>
       <line x1="11" y1="9" x2="14" y2="9"/><circle cx="14" cy="6.5" r="1.1"/></g>
  ),
  // —— Work-free time ——
  beach: (
    <g><circle cx="17" cy="6" r="2.5"/>
       <path d="M17 8.5L9 19.5"/><line x1="6" y1="19.5" x2="20" y2="19.5"/>
       <path d="M14 12c-2 .2-4 1-5 2.5"/><path d="M12 11c-1.5-.6-3.5-.5-5 .5"/></g>
  ),
  allinclusive: (
    <g><polyline points="6 4 18 4 13 12 11 12 6 4"/>
       <line x1="12" y1="12" x2="12" y2="19"/><line x1="8" y1="20" x2="16" y2="20"/>
       <line x1="9" y1="7" x2="15" y2="7"/></g>
  ),
  wellness: (
    <g><path d="M12 21c-1-3.5-3.5-5.5-7-6 1-3 4-4.5 7-2"/>
       <path d="M12 21c1-3.5 3.5-5.5 7-6-1-3-4-4.5-7-2"/>
       <line x1="12" y1="13" x2="12" y2="21"/></g>
  ),
  festivals: (
    <g><path d="M9 18V6l10-3v12"/>
       <circle cx="7" cy="18" r="2.2"/><circle cx="17" cy="15" r="2.2"/></g>
  ),
  retreats: (
    <g><polyline points="5 14 9 8 13 14"/><polyline points="11 16 16 8 21 16"/>
       <polyline points="3 19 21 19"/><line x1="16" y1="8" x2="16" y2="19"/></g>
  ),
  cruise: (
    <g><path d="M3 15l1.5 4a2 2 0 0 0 1.9 1.4h11.2a2 2 0 0 0 1.9-1.4L21 15"/>
       <polyline points="4 15 12 12 20 15"/>
       <line x1="12" y1="3" x2="12" y2="12"/>
       <polyline points="8 7 16 7"/><line x1="6" y1="15" x2="18" y2="15"/></g>
  ),
};

function InterestIcon({ name, size = 18, stroke = 1.7 }) {
  const glyph = INTEREST_ICONS[name];
  if (!glyph) return null;
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth={stroke}
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {glyph}
    </svg>
  );
}

// ─────────────────────────────────────────────────────────────
// The categories. Each "more" item is hidden behind Show more.
// Numbers chosen so each section has 6 visible + 4-5 in the
// expansion — same density as the reference wireframe.
// ─────────────────────────────────────────────────────────────
const INTEREST_SECTIONS = [
  {
    id: 'evenings',
    title: 'What makes my evenings great',
    items: [
      { key: 'Gym', icon: 'gym' },
      { key: 'Cooking', icon: 'cooking' },
      { key: 'TV-series', icon: 'tv' },
      { key: 'Reading', icon: 'reading' },
      { key: 'Going for a walk', icon: 'walk' },
      { key: 'Gaming', icon: 'gaming' },
    ],
    more: [
      { key: 'Live music', icon: 'festivals' },
      { key: 'Yoga', icon: 'wellness' },
      { key: 'Board games', icon: 'gaming' },
      { key: 'Movie nights', icon: 'tv' },
    ],
  },
  {
    id: 'weekends',
    title: 'What makes my weekends great',
    items: [
      { key: 'Weekend trips', icon: 'trip' },
      { key: 'Brunch', icon: 'brunch' },
      { key: 'Sports', icon: 'sports' },
      { key: 'Local markets', icon: 'markets' },
      { key: 'Hiking', icon: 'hiking' },
      { key: 'Cycling', icon: 'cycling' },
    ],
    more: [
      { key: 'City breaks', icon: 'trip' },
      { key: 'Museums', icon: 'markets' },
      { key: 'Brewery hops', icon: 'brunch' },
      { key: 'Bouldering', icon: 'hiking' },
    ],
  },
  {
    id: 'workfree',
    title: 'What makes my work-free time great',
    items: [
      { key: 'Beach', icon: 'beach' },
      { key: 'All-inclusive', icon: 'allinclusive' },
      { key: 'Wellness', icon: 'wellness' },
      { key: 'Festivals', icon: 'festivals' },
      { key: 'Retreats', icon: 'retreats' },
      { key: 'Cruise ships', icon: 'cruise' },
    ],
    more: [
      { key: 'Road trips', icon: 'trip' },
      { key: 'Camping', icon: 'retreats' },
      { key: 'Surf trips', icon: 'beach' },
      { key: 'Backpacking', icon: 'hiking' },
    ],
  },
];

// ─────────────────────────────────────────────────────────────
// Chip — pill with leading stroke-icon + label.
//
// State model:
//   selected = filled violet, white text + icon
//   unselected = hairline-border pill on canvas
//   disabled (at cap, not selected) = faded; tap is a no-op
//
// Pill geometry follows the brand non-negotiable: every interactive
// control is fully rounded.
// ─────────────────────────────────────────────────────────────
function InterestChip({ icon, label, selected, disabled, onClick }) {
  return (
    <button
      type="button"
      onClick={disabled ? undefined : onClick}
      aria-pressed={!!selected}
      aria-disabled={!!disabled}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 8,
        height: 38, padding: '0 14px 0 12px',
        borderRadius: 9999,
        fontFamily: 'var(--liq-font-sans)', fontSize: 14,
        fontWeight: selected ? 700 : 600,
        whiteSpace: 'nowrap',
        background: selected ? 'var(--liq-primary-500)' : '#FFFFFF',
        color: selected ? '#FFFFFF' : 'var(--liq-fg)',
        border: selected
          ? '1.5px solid var(--liq-primary-500)'
          : '1px solid var(--liq-border)',
        boxShadow: selected
          ? '0 4px 10px rgba(129, 42, 236, 0.18)'
          : '0 1px 2px rgba(46, 1, 71, 0.03)',
        opacity: disabled ? 0.4 : 1,
        cursor: disabled ? 'not-allowed' : 'pointer',
        transition: 'background 180ms, color 180ms, border-color 180ms, transform 180ms cubic-bezier(.22,1,.36,1), box-shadow 180ms',
      }}
      onMouseDown={(e) => { if (!disabled) e.currentTarget.style.transform = 'scale(0.97)'; }}
      onMouseUp={(e) => e.currentTarget.style.transform = ''}
      onMouseLeave={(e) => e.currentTarget.style.transform = ''}
    >
      <span style={{ color: selected ? '#FFFFFF' : 'var(--liq-primary-500)', display: 'inline-flex' }}>
        <InterestIcon name={icon}/>
      </span>
      <span>{label}</span>
    </button>
  );
}

// ─────────────────────────────────────────────────────────────
// Counter — "N picked · M left" / "10 of 10 picked"
//
// Lives between the headline and the first category. Same eyebrow
// position as the per-screen helper text on the other sub-flow
// steps. When the cap is reached, switches to orange so the user
// reads it as a stop-signal rather than a status line.
// ─────────────────────────────────────────────────────────────
function InterestsCounter({ picked, max }) {
  const full = picked >= max;
  const left = Math.max(0, max - picked);
  const color = full ? 'var(--liq-orange-500)' : 'var(--liq-primary-500)';
  const label = full
    ? `${max} of ${max} picked`
    : `${picked} picked · ${left} left`;

  // Progress bar segment — picks up the same color as the label so
  // the relationship between count and pill is obvious. A subtle
  // affordance, not the focus of the screen.
  const pct = Math.min(100, (picked / max) * 100);

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '4px 0 18px' }}>
      <span style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 13.5,
        color, letterSpacing: 0.02, whiteSpace: 'nowrap',
        transition: 'color 180ms',
      }}>
        {label}
      </span>
      <div style={{
        flex: 1, height: 4, borderRadius: 9999,
        background: 'rgba(29,17,41,0.08)', overflow: 'hidden',
      }}>
        <div style={{
          width: `${pct}%`, height: '100%',
          background: color, transition: 'width 280ms cubic-bezier(.22,1,.36,1), background 180ms',
        }}/>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// CategoryBlock — the title + chip cluster + show-more affordance.
//
// "Show more" toggles a small in-place expansion of extra chips
// rather than navigating away. Once expanded, the affordance reads
// "Show less" so the user has a way back without scrolling for the
// affordance again.
// ─────────────────────────────────────────────────────────────
function CategoryBlock({ section, picked, capReached, onToggle, expanded, onToggleExpand }) {
  const visibleItems = expanded ? [...section.items, ...section.more] : section.items;
  return (
    <div style={{ marginBottom: 22 }}>
      <h3 style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15,
        color: 'var(--liq-fg)', margin: '0 0 12px', letterSpacing: 0,
      }}>
        {section.title}
      </h3>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {visibleItems.map(item => {
          const sel = picked.has(item.key);
          return (
            <InterestChip
              key={item.key}
              icon={item.icon}
              label={item.key}
              selected={sel}
              disabled={!sel && capReached}
              onClick={() => onToggle(item.key)}
            />
          );
        })}
      </div>

      <button
        type="button"
        onClick={onToggleExpand}
        style={{
          display: 'inline-flex', alignItems: 'center', gap: 6,
          marginTop: 12, padding: '4px 2px',
          fontFamily: 'var(--liq-font-sans)', fontSize: 13.5, fontWeight: 700,
          color: 'var(--liq-primary-500)',
        }}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
          stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"
          style={{
            transform: expanded ? 'rotate(180deg)' : 'none',
            transition: 'transform 220ms cubic-bezier(.22,1,.36,1)',
          }}>
          <polyline points="6 9 12 15 18 9"/>
        </svg>
        <span>{expanded ? 'Show less' : 'Show more'}</span>
      </button>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Footer — skip + continue (continue is disabled when nothing
// picked; we don't force a minimum).
// ─────────────────────────────────────────────────────────────
function InterestsFooter({ disabled, onSkip, onContinue }) {
  return (
    <div style={{
      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      paddingTop: 12,
    }}>
      <SkipLink onClick={onSkip}/>
      <div style={{
        opacity: disabled ? 0.42 : 1,
        filter: disabled ? 'saturate(0.4)' : 'none',
        pointerEvents: disabled ? 'none' : 'auto',
        transition: 'opacity 180ms, filter 180ms',
      }}>
        <NextButton label="Continue" color="orange" onClick={onContinue}/>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileInterests — the screen.
//
// We reuse the same scaffold the rest of the sub-flow uses, but the
// answer area is scrollable: three category blocks at full density
// (six chips visible + show-more) plus the counter + footer don't
// quite fit on the 844-tall canvas, and scrolling matches the
// reference wireframe.
// ─────────────────────────────────────────────────────────────
function ScreenProfileInterests({ initialPicked = [], initialExpanded = [], onBack, onSkip, onContinue }) {
  const [picked, setPicked] = useStateInterests(new Set(initialPicked));
  const [expanded, setExpanded] = useStateInterests(new Set(initialExpanded));

  const capReached = picked.size >= INTERESTS_MAX;
  const toggleChip = (key) => {
    setPicked(prev => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else if (next.size < INTERESTS_MAX) {
        next.add(key);
      }
      return next;
    });
  };
  const toggleSection = (id) => {
    setExpanded(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
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

      <div style={{
        flex: 1, position: 'relative', overflow: 'hidden',
        padding: '4px 24px 0', display: 'flex', flexDirection: 'column',
      }}>
        <StepProgress steps={SHARE_STEPS_TOTAL_INTERESTS} current={8} style={{ marginBottom: 22 }}/>

        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 28,
          letterSpacing: '-0.015em', lineHeight: 1.12, color: 'var(--liq-fg)',
          margin: '0 0 10px', textWrap: 'balance',
        }}>
          Choose up to <em>10</em> things you are really into
        </h1>

        <InterestsCounter picked={picked.size} max={INTERESTS_MAX}/>

        {/* Scrollable interest cluster. The categories and chips total
            more height than the canvas; the reference wireframe also
            scrolls. We hide the scrollbar to match the in-app feel. */}
        <div className="su-noscroll" style={{
          flex: 1, minHeight: 0, overflowY: 'auto',
          paddingRight: 4, paddingBottom: 12,
          marginRight: -4, /* compensate for the scrollbar gutter */
        }}>
          {INTEREST_SECTIONS.map(section => (
            <CategoryBlock
              key={section.id}
              section={section}
              picked={picked}
              capReached={capReached}
              onToggle={toggleChip}
              expanded={expanded.has(section.id)}
              onToggleExpand={() => toggleSection(section.id)}
            />
          ))}
        </div>

        <div style={{ flex: 'none', paddingBottom: 22, borderTop: '1px solid var(--liq-border-soft)' }}>
          <InterestsFooter
            disabled={picked.size === 0}
            onSkip={onSkip}
            onContinue={onContinue}
          />
        </div>
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, {
  ScreenProfileInterests,
  InterestChip,
  InterestsCounter,
  InterestIcon,
  INTEREST_ICONS,
  INTEREST_SECTIONS,
});
