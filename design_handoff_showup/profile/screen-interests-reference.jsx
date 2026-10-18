// screen-interests-reference.jsx — Profile creation 21 · Interests
//
// Design reference for [Profile 21] Interests — step 8 of 10 of "Share some
// details". This is the code that renders spec-sheets/21-interests.png. Read
// values from HERE, not from the PNG.
//
// Extracted verbatim from the Show Up UI kit
// (ui_kits/show-up/screens-profile-interests.jsx). It is a reference
// implementation, not production code: recreate it in the target codebase's
// own environment and patterns. Do not ship this file.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// STATES — one component, three states, four frames on the sheet:
//   A · empty                <ScreenProfileInterests/>
//   B · 2 lifestyle + 6 interests
//       <ScreenProfileInterests initialLifestyle={{ activity: 'Active', diet: 'Flexitarian' }}
//         initialPicked={['Running','Cooking','Brunch','Live music','Board games','City breaks']}/>
//   C · 10 of 10 (the cap), scrolled to "What I'm into" — see the sheet for props
//   D · frame only — state B with initialExpanded={['move']}, scrolled.
//   There is NO refused state: Continue with nothing picked does exactly
//   what Skip does (decided 8 Oct 2026). All frames share one column.
//
// NOT DetailsScaffold. This screen has no ProfileVisibility band (fully
// public, decided 8 Oct 2026) and its own answer region. The chrome is
// shared: StatusBar, AppHeader, StepProgress, SkipLink, NextButton,
// Atmosphere, HomeIndicator from components/shared.jsx.
//
// BUILDS: InterestChip, InterestsCounter, ShowMoreToggle, InterestGroup,
// CategoryTitle. One chip component serves both blocks.
//
// TWO BLOCKS, TWO RULES:
//   How I live     — six groups, SINGLE-select per group, uncapped, never
//                    collapsed. Tapping the selected chip clears it.
//   What I'm into  — MULTI-select across six groups, CAPPED AT 10. First 4
//                    chips per group + Show more. At 10, unpicked chips fade
//                    to .4 and a tap is a no-op.
//   The counter counts What I'm into only.
//
// Labels are display strings. Store and track the enums.json §1 values
// (How I live) and §3 tag_ids (What I'm into), registry 1.4.16.
//
// StepProgress: steps={10} current={8} (10 since 8 Oct 2026).
// ─────────────────────────────────────────────────────────────

// screens-profile-interests.jsx — "Show how you live and what you love, so others know if you'd click"
//
// 8 Oct 2026 (Profile 21): headline 28 → 30 (group size), Continue never
// disabled (empty = Skip), no visibility switch — the screen is fully public.
//
// Content revised 6 Oct 2026: two categories, each split into
// subcategories. "How I live" is single-select per subcategory and does
// NOT count toward the 10. "What I'm into" is multi-select, capped at 10;
// each subcategory shows its first 4 chips + "Show more".
// Chips are text-only.

const { useState: useStateInterests, useMemo: useMemoInterests } = React;

// Total steps in the Share-some-details sub-flow. Mirror the constant
// from screens-profile-details.jsx so the StepProgress matches.
const SHARE_STEPS_TOTAL_INTERESTS = 10;
const INTERESTS_MAX = 10;

// ─────────────────────────────────────────────────────────────
// Content. Subcategory ids are stable keys for expand state.
// ─────────────────────────────────────────────────────────────
const LIFESTYLE_GROUPS = [
  { id: 'activity', label: 'Activity level', options: ['Couch-lover', 'Moderately active', 'Active', 'Sport is my life'] },
  { id: 'diet', label: 'Diet', options: ['Everything', 'Flexitarian', 'Vegetarian', 'Vegan'] },
  { id: 'alcohol', label: 'Alcohol', options: ['Never', 'Rarely', 'Socially', 'Regularly'] },
  { id: 'smoking', label: 'Smoking', options: ['No', 'Sometimes', 'Yes'] },
  { id: 'rhythm', label: 'Rhythm', options: ['Early bird', 'Night owl'] },
  { id: 'social', label: 'Social battery', options: ['Cozy at home', 'Balanced', 'Always out'] },
];

const INTEREST_GROUPS = [
  { id: 'move', label: 'Move', options: ['Gym', 'Running', 'Hyrox/CrossFit', 'Yoga/Pilates', 'Bouldering', 'Cycling', 'Swimming', 'Hiking', 'Team sports', 'Tennis/Padel', 'Martial arts', 'Winter sports'] },
  { id: 'food', label: 'Food & Drink', options: ['Cooking', 'Baking', 'Trying new restaurants', 'Brunch', 'Street food & markets', 'Wine', 'Craft beer', 'Coffee spots', 'Cocktail bars'] },
  { id: 'culture', label: 'Culture', options: ['Live music', 'Museums & exhibitions', 'Theatre', 'Cinema', 'Reading', 'Comedy', 'Festivals', 'Making art/music'] },
  { id: 'home', label: 'Cozy at home', options: ['TV series', 'Movie nights', 'Gaming', 'Board games', 'Podcasts', 'Plants & gardening'] },
  { id: 'out', label: 'Going out', options: ['Bars', 'Clubbing', 'Dinner parties', 'Quiz nights', 'Karaoke', 'Concerts with friends', 'Volunteering'] },
  { id: 'travel', label: 'Travel style', options: ['City breaks', 'Road trips', 'Beach & relax', 'All-inclusive', 'Backpacking', 'Surf & adventure', 'Wellness & spa', 'Camping', 'Cruises'] },
];

const INTEREST_VISIBLE = 4;
const INTEREST_GROUP_IDS = INTEREST_GROUPS.map(g => g.id);

// ─────────────────────────────────────────────────────────────
// Chip — text-only pill. selected = filled violet;
// disabled (at cap, not selected) = faded, tap is a no-op.
// ─────────────────────────────────────────────────────────────
function InterestChip({ label, selected, disabled, onClick }) {
  return (
    <button
      type="button"
      onClick={disabled ? undefined : onClick}
      aria-pressed={!!selected}
      aria-disabled={!!disabled}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 8,
        height: 38, padding: '0 15px',
        borderRadius: 9999,
        fontFamily: 'var(--liq-font-sans)', fontSize: 14,
        fontWeight: selected ? 700 : 600,
        whiteSpace: 'nowrap',
        background: selected ? 'var(--liq-primary-500)' : '#FFFFFF',
        color: selected ? '#FFFFFF' : 'var(--liq-fg)',
        border: selected ? '1.5px solid var(--liq-primary-500)' : '1px solid var(--liq-border)',
        boxShadow: selected ? '0 4px 10px rgba(129, 42, 236, 0.18)' : '0 1px 2px rgba(46, 1, 71, 0.03)',
        opacity: disabled ? 0.4 : 1,
        cursor: disabled ? 'not-allowed' : 'pointer',
        transition: 'background 180ms, color 180ms, border-color 180ms, transform 180ms cubic-bezier(.22,1,.36,1), box-shadow 180ms',
      }}
      onMouseDown={(e) => { if (!disabled) e.currentTarget.style.transform = 'scale(0.97)'; }}
      onMouseUp={(e) => e.currentTarget.style.transform = ''}
      onMouseLeave={(e) => e.currentTarget.style.transform = ''}
    >
      <span>{label}</span>
    </button>
  );
}

// Counter — "N picked · M left" / "10 of 10 picked" (orange at cap).
function InterestsCounter({ picked, max }) {
  const full = picked >= max;
  const left = Math.max(0, max - picked);
  const color = full ? 'var(--liq-orange-500)' : 'var(--liq-primary-500)';
  const label = full ? `${max} of ${max} picked` : `${picked} picked · ${left} left`;
  const pct = Math.min(100, (picked / max) * 100);
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '4px 0 18px' }}>
      <span style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 13.5,
        color, letterSpacing: 0.02, whiteSpace: 'nowrap', transition: 'color 180ms',
      }}>{label}</span>
      <div style={{ flex: 1, height: 4, borderRadius: 9999, background: 'rgba(29,17,41,0.08)', overflow: 'hidden' }}>
        <div style={{ width: `${pct}%`, height: '100%', background: color, transition: 'width 280ms cubic-bezier(.22,1,.36,1), background 180ms' }}/>
      </div>
    </div>
  );
}

function ShowMoreToggle({ expanded, onClick }) {
  return (
    <button type="button" onClick={onClick} style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      marginTop: 10, padding: '4px 2px',
      fontFamily: 'var(--liq-font-sans)', fontSize: 13.5, fontWeight: 700,
      color: 'var(--liq-primary-500)',
    }}>
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
        stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"
        style={{ transform: expanded ? 'rotate(180deg)' : 'none', transition: 'transform 220ms cubic-bezier(.22,1,.36,1)' }}>
        <polyline points="6 9 12 15 18 9"/>
      </svg>
      <span>{expanded ? 'Show less' : 'Show more'}</span>
    </button>
  );
}

// Subcategory — small label, chip cluster, optional Show more.
function InterestGroup({ group, isSelected, isDisabled, onPick, collapsible, expanded, onToggleExpand }) {
  const canCollapse = collapsible && group.options.length > INTEREST_VISIBLE;
  const visible = canCollapse && !expanded ? group.options.slice(0, INTEREST_VISIBLE) : group.options;
  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 13,
        color: 'var(--liq-fg-muted)', margin: '0 0 8px',
      }}>{group.label}</div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {visible.map(opt => (
          <InterestChip key={opt} label={opt}
            selected={isSelected(opt)} disabled={isDisabled(opt)}
            onClick={() => onPick(opt)}/>
        ))}
      </div>
      {canCollapse && <ShowMoreToggle expanded={expanded} onClick={onToggleExpand}/>}
    </div>
  );
}

function CategoryTitle({ children }) {
  return (
    <h3 style={{
      fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 16,
      color: 'var(--liq-fg)', margin: '0 0 12px', letterSpacing: 0,
    }}>{children}</h3>
  );
}

// Footer — decided 8 Oct 2026, aligned with Profiles 17–20: Continue is
// never disabled; with nothing picked it behaves exactly as Skip.
function InterestsFooter({ empty, onSkip, onContinue }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: 12 }}>
      <SkipLink onClick={onSkip}/>
      <NextButton label="Continue" color="orange" onClick={empty ? onSkip : onContinue}/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileInterests
//   initialLifestyle: { activity: 'Active', ... } — single value per group
//   initialPicked:    ['Gym', ...]               — counts toward the 10
//   initialExpanded:  ['move', ...] or 'all'     — interest groups opened
//   tall:             render at content height (no inner scroll)
// ─────────────────────────────────────────────────────────────
function ScreenProfileInterests({ initialLifestyle = {}, initialPicked = [], initialExpanded = [], tall = false, onBack, onSkip, onContinue }) {
  const [lifestyle, setLifestyle] = useStateInterests({ ...initialLifestyle });
  const [picked, setPicked] = useStateInterests(new Set(initialPicked));
  const [expanded, setExpanded] = useStateInterests(new Set(initialExpanded === 'all' ? INTEREST_GROUP_IDS : initialExpanded));

  const capReached = picked.size >= INTERESTS_MAX;
  const pickLifestyle = (gid, opt) => setLifestyle(prev => ({ ...prev, [gid]: prev[gid] === opt ? undefined : opt }));
  const toggleChip = (key) => setPicked(prev => {
    const next = new Set(prev);
    if (next.has(key)) next.delete(key);
    else if (next.size < INTERESTS_MAX) next.add(key);
    return next;
  });
  const toggleGroup = (id) => setExpanded(prev => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });
  const anything = picked.size > 0 || Object.values(lifestyle).some(Boolean);

  return (
    <div className="su-app" style={{
      width: 390, height: tall ? 'auto' : 844, minHeight: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)', display: 'flex', flexDirection: 'column',
    }}>
      <Atmosphere variant="form"/>
      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
        <AppHeader title="Share some details" leading="back" onBack={onBack}/>
      </div>
      <div style={{
        flex: tall ? 'none' : 1, position: 'relative', overflow: tall ? 'visible' : 'hidden',
        padding: '4px 24px 0', display: 'flex', flexDirection: 'column',
      }}>
        <StepProgress steps={SHARE_STEPS_TOTAL_INTERESTS} current={8} style={{ marginBottom: 22 }}/>
        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
          letterSpacing: '-0.015em', lineHeight: 1.12, color: 'var(--liq-fg)',
          margin: '0 0 10px', textWrap: 'balance',
        }}>
          Show how you live and what you love, so others know if you'd <em>click</em>
        </h1>
        <InterestsCounter picked={picked.size} max={INTERESTS_MAX}/>
        <div className="su-noscroll" style={{
          flex: tall ? 'none' : 1, minHeight: 0, overflowY: tall ? 'visible' : 'auto',
          paddingRight: 4, paddingBottom: 12, marginRight: -4,
        }}>
          <div style={{ marginBottom: 14 }}>
            <CategoryTitle>How I live</CategoryTitle>
            {LIFESTYLE_GROUPS.map(g => (
              <InterestGroup key={g.id} group={g}
                isSelected={opt => lifestyle[g.id] === opt}
                isDisabled={() => false}
                onPick={opt => pickLifestyle(g.id, opt)}/>
            ))}
          </div>
          <div>
            <CategoryTitle>What I'm into</CategoryTitle>
            {INTEREST_GROUPS.map(g => (
              <InterestGroup key={g.id} group={g}
                isSelected={opt => picked.has(opt)}
                isDisabled={opt => !picked.has(opt) && capReached}
                onPick={toggleChip}
                collapsible expanded={expanded.has(g.id)}
                onToggleExpand={() => toggleGroup(g.id)}/>
            ))}
          </div>
        </div>
        <div style={{ flex: 'none', paddingBottom: 22, borderTop: '1px solid var(--liq-border-soft)' }}>
          <InterestsFooter empty={!anything} onSkip={onSkip} onContinue={onContinue}/>
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
  LIFESTYLE_GROUPS,
  INTEREST_GROUPS,
});
