// screen-checkin-filters-reference.jsx — 48hrs Check-In 05 · Filters
//
// Design reference for [Check-In 05] Filters. This is the code that renders
// spec-sheets/05-filters.png. Read values from HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/screens-checkin.jsx:
// CHECKIN_GENDERS, checkinGenderDefault, FiltersStep, CheckinFilterRow,
// RangeSlider, SliderThumb, and step 6 of ScreenCheckIn).
// It is a reference implementation, not production code: recreate it in the
// target codebase's own patterns. Do not ship this file.
//
// CONSUMES, never copies:
//   Check-In 02 (screen-checkin-availability-reference.jsx) — the step shell
//     and CheckInFooter (with its toast and dimmed-but-tappable state).
//   Check-In 03 (screen-checkin-motivation-reference.jsx) — CoachNote.
//   Check-In 01 (screen-checkin-intro-reference.jsx) — CheckInCloseButton,
//     CheckInArrow.
//   Profile 22 (profile/screen-life-reference.jsx) — LifePill, with multi.
//   components/shared.jsx — StatusBar, AppHeader, StepProgress, Button, Icon,
//     HomeIndicator.
//
// BUILDS: FiltersStep, CHECKIN_GENDERS, checkinGenderDefault, CheckinFilterRow,
// RangeSlider + SliderThumb (the slider is new to the kit's build inventory;
// Search's filter sheet consumes it).
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// THREE STATES, ONE SCREEN.
//   A  <FiltersScreen/>                                       first Check-In, pre-filled from the profile
//   B  <FiltersScreen filters={{gender: [], …}} toast={FILTERS_TOAST_GENDER}/>   no gender
//   C  <FiltersScreen filters={{gender: ['woman','non_binary'], age: [25,35], dist: 15}}/>  narrowed / returning
//
// THREE FILTERS (decided 13 Oct 2026). Gender is MANDATORY (≥ 1). Age range is
// optional — the full range (18 → 65+) means no limit. Distance defaults to
// 10 km. The SEXUALITY FILTER IS REMOVED: matching is two-way on gender.
//
// ONE STORE with Search's filter sheet. This step edits the user's filter
// settings; a returning user sees the current settings. A first Check-In
// pre-fills gender from the profile (checkinGenderDefault below).
//
// VALUES: meet_gender is enums §10 (woman · man · non_binary · other, the §1
// gender set). Class 2 — never in a tracking payload.
//
// STEP SHELL: StepProgress steps={6} current={5}. Headline 30 (step size).
//
// LAYOUT — the filter list is the only flexible element (flex: 1,
// min-height 0, overflow-y auto). Fits at 390 × 844; scrolls inside itself
// at 375 × 667. Slider rows are 44 tall (hit target) and the track is inset 22
// (half the hit box) so an end thumb never overflows or clips the list. Never hard-code a Y.

const { useRef: useRefCheckin } = React;

const CHECKIN_GENDERS = [
  { id: 'woman',      t: 'Women' },
  { id: 'man',        t: 'Men' },
  { id: 'non_binary', t: 'Non-binary' },
  { id: 'other',      t: 'Other' },
];
const CHECKIN_AGE_MIN = 18, CHECKIN_AGE_MAX = 65;   // the top stop reads "65+" = no upper limit
const CHECKIN_DIST_MIN = 1, CHECKIN_DIST_MAX = 50, CHECKIN_DIST_DEFAULT = 10;
const FILTERS_TOAST_GENDER = 'Pick at least one gender to date.';

// First-Check-In gender pre-fill from the profile (Profile 15 gender ×
// Profile 16 orientation — both mandatory, so always known).
function checkinGenderDefault(gender, orientation) {
  if (gender === 'non_binary' || gender === 'other' || orientation === 'other') return ['woman', 'man', 'non_binary', 'other'];
  if (orientation === 'bisexual' || orientation === 'pansexual') return ['woman', 'man'];
  if (orientation === 'gay' || orientation === 'lesbian') return [gender];
  return [gender === 'man' ? 'woman' : 'man']; // straight
}
const CHECKIN_FILTERS_FIRST = { gender: checkinGenderDefault('man', 'straight'), age: [CHECKIN_AGE_MIN, CHECKIN_AGE_MAX], dist: CHECKIN_DIST_DEFAULT };

const checkinAgeLabel = ([lo, hi]) =>
  lo === CHECKIN_AGE_MIN && hi === CHECKIN_AGE_MAX ? 'Any age'
  : hi === CHECKIN_AGE_MAX ? `${lo}+`
  : `${lo} – ${hi}`;

function FiltersStep({ filters, setFilters }) {
  const toggleGender = id => setFilters(f => {
    const n = new Set(f.gender);
    n.has(id) ? n.delete(id) : n.add(id);
    return { ...f, gender: n };
  });
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 'none' }}>
        <h2 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
          letterSpacing: '-0.015em', lineHeight: 1.15, color: 'var(--liq-fg)',
          margin: '8px 0 0', textWrap: 'pretty',
        }}>Filter who <em>you wish to date</em></h2>
        <CoachNote color="warm" body="This will limit who you will see to potentially date."/>
      </div>

      <div data-filter-list className="su-noscroll" style={{
        flex: 1, minHeight: 0, overflowY: 'auto', marginTop: 18, paddingBottom: 12,
        display: 'flex', flexDirection: 'column', gap: 22,
      }}>
        <CheckinFilterRow label="Gender">
          <div role="group" aria-label="Gender" style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {CHECKIN_GENDERS.map(g => (
              <LifePill key={g.id} label={g.t} multi selected={filters.gender.has(g.id)} onClick={() => toggleGender(g.id)}/>
            ))}
          </div>
        </CheckinFilterRow>

        <CheckinFilterRow label="Age range" hint={checkinAgeLabel(filters.age)}>
          <RangeSlider values={filters.age} min={CHECKIN_AGE_MIN} max={CHECKIN_AGE_MAX}
            labels={['Minimum age', 'Maximum age']}
            onChange={(i, v) => setFilters(f => {
              const age = [...f.age]; age[i] = v;
              if (age[0] > age[1]) { if (i === 0) age[1] = age[0]; else age[0] = age[1]; }
              return { ...f, age };
            })}/>
        </CheckinFilterRow>

        <CheckinFilterRow label="How far away?" hint={`${filters.dist} km`}>
          <RangeSlider values={[filters.dist]} min={CHECKIN_DIST_MIN} max={CHECKIN_DIST_MAX}
            labels={['Distance in km']}
            onChange={(i, v) => setFilters(f => ({ ...f, dist: v }))}/>
        </CheckinFilterRow>
      </div>
    </div>
  );
}


// The screen as the step shell renders it. StepShell / CheckInFooter are the
// ones built by Check-In 02 — shown here only to name the props this step passes.
function FiltersScreen({ filters: initial = CHECKIN_FILTERS_FIRST, toast = '' }) {
  const [filters, setFilters] = React.useState({ gender: new Set(initial.gender), age: [...initial.age], dist: initial.dist });
  return (
    <StepShell current={5} onBack={/* → checkin_vibe */ () => {}} onClose={/* → gate, nothing saved */ () => {}}>
      <FiltersStep filters={filters} setFilters={setFilters}/>
      <CheckInFooter label="Continue" disabled={filters.gender.size === 0} toast={toast} onClick={() => {}}/>
    </StepShell>
  );
}

function CheckinFilterRow({ label, hint, children }) {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 10 }}>
        <div style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 14, color: 'var(--liq-fg)' }}>{label}</div>
        {hint && <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--liq-primary-500)' }}>{hint}</span>}
      </div>
      {children}
    </div>
  );
}

function RangeSlider({ values, min, max, onChange, labels = [] }) {
  const trackRef = useRefCheckin(null);
  const dual = values.length > 1;
  const pct = v => (v - min) / (max - min) * 100;
  const lo = pct(values[0]);
  const hi = dual ? pct(values[1]) : null;

  const startDrag = idx => e => {
    e.preventDefault();
    e.stopPropagation();
    const move = ev => {
      if (!trackRef.current) return;
      const rect = trackRef.current.getBoundingClientRect();
      const clientX = ev.touches ? ev.touches[0].clientX : ev.clientX;
      let ratio = (clientX - rect.left) / rect.width;
      ratio = Math.max(0, Math.min(1, ratio));
      const val = Math.round(min + ratio * (max - min));
      onChange && onChange(idx, val);
    };
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };

  return (
    // Inset by half the 44 hit box (22) so a thumb at either end — hit area
    // included — stays inside the scrolling list: no sideways pan, no clipping.
    <div ref={trackRef} style={{ height: 44, margin: '0 22px', position: 'relative', touchAction: 'none' }}>
      <div style={{ position: 'absolute', top: 20, left: 0, right: 0, height: 4, borderRadius: 999, background: 'var(--liq-border)' }}/>
      <div style={{
        position: 'absolute', top: 20, left: `${dual ? lo : 0}%`,
        right: `${100 - (dual ? hi : lo)}%`,
        height: 4, borderRadius: 999, background: 'var(--liq-primary-500)',
      }}/>
      {dual && <SliderThumb left={lo} onPointerDown={startDrag(0)} label={labels[0]} value={values[0]} min={min} max={values[1]} onStep={d => onChange && onChange(0, Math.max(min, Math.min(values[1], values[0] + d)))}/>}
      <SliderThumb left={dual ? hi : lo} onPointerDown={startDrag(dual ? 1 : 0)} label={labels[dual ? 1 : 0]} value={values[dual ? 1 : 0]} min={dual ? values[0] : min} max={max} onStep={d => onChange && onChange(dual ? 1 : 0, Math.max(dual ? values[0] : min, Math.min(max, values[dual ? 1 : 0] + d)))}/>
    </div>
  );
}
// SliderThumb — 28 visual, 44 hit target; an adjustable slider for VoiceOver /
// TalkBack (swipe up/down = ±1).
function SliderThumb({ left, onPointerDown, label, value, min, max, onStep }) {
  return (
    <div onPointerDown={onPointerDown} role="slider" tabIndex={0} aria-label={label}
      aria-valuemin={min} aria-valuemax={max} aria-valuenow={value}
      onKeyDown={e => { if (e.key === 'ArrowRight' || e.key === 'ArrowUp') { e.preventDefault(); onStep && onStep(1); } if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') { e.preventDefault(); onStep && onStep(-1); } }}
      style={{
        position: 'absolute', top: 0, left: `calc(${left}% - 22px)`,
        width: 44, height: 44, display: 'flex', alignItems: 'center', justifyContent: 'center',
        cursor: 'grab', touchAction: 'none', outline: 'none',
      }}>
      <div style={{
        width: 28, height: 28, borderRadius: 9999, background: '#fff',
        border: '1.5px solid var(--liq-primary-500)',
        boxShadow: '0 2px 8px rgba(129,42,236,0.18)',
      }}/>
    </div>
  );
}

