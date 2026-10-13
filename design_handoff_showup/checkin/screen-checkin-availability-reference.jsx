// screen-checkin-availability-reference.jsx — 48hrs Check-In 02 · Availability: Today + Tomorrow
//
// Design reference for [Check-In 02] Availability. This is the code that
// renders spec-sheets/02-availability.png. Read values from HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/screens-checkin.jsx:
// the step shell inside ScreenCheckIn, AvailabilityStep, HowItWorks, the grid
// helpers, CheckInFooter, NoHoursTodayDialog). It is a reference
// implementation, not production code: recreate it in the target codebase's
// own patterns. Do not ship this file.
//
// Consumes from Check-In 01 (screen-checkin-intro-reference.jsx):
// CheckInCloseButton, CheckInArrow. Do not copy them — import the built ones.
// Shell components (StatusBar, AppHeader, StepProgress, Button, Icon,
// HomeIndicator) are in components/shared.jsx.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// TWO SCREENS, ONE COMPONENT, ONE TICKET.
//   A/B  <AvailabilityScreen day="today"    .../>  startHour = next full hour
//   C/D  <AvailabilityScreen day="tomorrow" .../>  startHour = 7
// State props for the sheet: map = {} (empty), dialogOpen (B), toast (C).
//
// THIS STORY BUILDS THE STEP SHELL every later step consumes (03–06):
//   StatusBar 54 → AppHeader 52 (title "Check-in", back leading,
//   CheckInCloseButton trailing) → content (flex: 1, padding 4/24/0:
//   StepProgress steps={6} mb 22 → step body flex 1 → CheckInFooter mb 22)
//   → HomeIndicator 28.
// and CheckInFooter (label is a prop: "Continue" / "Find my date") and
// HowItWorks. A second copy of any of them is a bug.
//
// STEP HEADLINE IS 30 — Lora 700 / 30 / 1.15 / −0.015em. Settled here for
// the whole step group (13 Oct 2026); Vibe, Filters and Uplift moved 28 → 30.
//
// TODAY STARTS AT THE NEXT FULL HOUR (13 Oct 2026). At 4:20 the first slot
// is 5pm. An hour that has started is never offered: with the 1-hour meet
// buffer it cannot produce a date.
//
// A CHECK-IN DAY RUNS 7am → 7am (13 Oct 2026). Both grids end at the 6am
// slot; night hours belong to the evening before. "Today" is the 7am → 7am
// block that contains the next full hour.
//
// AVAILABILITY IS NEVER KEPT (13 Oct 2026). Both grids start EMPTY on every
// entry. ✕ discards the picks; back/forward inside one flow keeps them. The
// gate reopens at Today once the intro has been passed, at the intro before.
// There is no draft and no stale-hours rule.
//
// TAP CYCLES idle → preferred → available → idle. Both tiers are stored and
// used by the backend.
//
// TODAY IS OPTIONAL, TOMORROW IS NOT.
//   Today    Continue is never disabled. Empty → NoHoursTodayDialog.
//   Tomorrow Continue dims (opacity .45, saturate .4) until ≥ 1 hour; a tap
//            on the dimmed button still fires and raises the toast (2600ms).
//
// CTA ARROWS ARE CheckInArrow (SVG), never a "→" text glyph — on Continue
// and on "Continue to tomorrow" (13 Oct 2026).
//
// LAYOUT — the grid is the only flexible element (flex: 1, min-height 0,
// overflow-y auto). Header block above it is flex none; summary and legend
// below it are flex none. Opening HowItWorks takes height from the grid.
// Never hard-code a Y.


const { useState: useStateCheckin, useEffect: useEffectCheckin, useRef: useRefCheckin } = React;

const CHECKIN_STEPS_TOTAL = 6; // progress bars — intro doesn't count


// The device clock reads 4:20pm across the kit. Today starts at the NEXT FULL
// HOUR (5pm), never the current one: with the 1-hour meet buffer an hour that
// has already started can never produce a date (decided 13 Oct 2026).
//
// A Check-In DAY RUNS 7am → 7am (decided 13 Oct 2026): night hours belong to
// the evening before, which is why both grids end at the 6am slot. "Today"
// is the 7am → 7am block that contains the next full hour; "tomorrow" is the
// block after it. At 1:20am today is 2am–6am (the night of the evening
// before); at 6:20am today is the new day, 7am onwards.
const CHECKIN_NOW_HOUR = 16;
const CHECKIN_FIRST_HOUR = CHECKIN_NOW_HOUR + 1;

// 12-hour am/pm label for an hour 0–23 (e.g. 16 → "4pm").
const CHECKIN_FMT_HOUR = (h) => {
  const x = ((h % 24) + 24) % 24;
  if (x === 0) return '12am';
  if (x === 12) return 'noon';
  return x < 12 ? `${x}am` : `${x - 12}pm`;
};


// AvailabilityScreen — the step shell around AvailabilityStep, as drawn by
// ScreenCheckIn for steps 2 and 3. day / map / handlers come from the
// Check-In state machine (built before this story); this component owns
// no state of its own.
function AvailabilityScreen({ day, map, setMap, cycle, onBack, onClose, onContinue, toast, dialogOpen, onConfirmNoToday, onCancelNoToday }) {
  const isToday = day === 'today';
  const disabled = !isToday && Object.keys(map).length === 0;
  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)', display: 'flex', flexDirection: 'column',
    }}>
      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20" dark={false}/>
        <AppHeader title="Check-in" leading="back" onBack={onBack} trailing={<CheckInCloseButton onClick={onClose}/>}/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', zIndex: 1, overflow: 'auto',
        padding: '4px 24px 0', display: 'flex', flexDirection: 'column',
      }}>
        <StepProgress steps={CHECKIN_STEPS_TOTAL} current={isToday ? 1 : 2} style={{ marginBottom: 22, flex: 'none' }}/>

        <div style={{ flex: 1, minHeight: 0 }}>
          {isToday ? (
            <AvailabilityStep
              day="today" title={<>When can you date <em>today</em>?</>}
              sub={<>Tap each hour you're free. We'll only show you people free at the same times. Can't date today? Just continue to tomorrow. See <span style={{ color: 'var(--liq-primary-500)', fontWeight: 700 }}>How it works</span> for more info.</>}
              map={map} setMap={setMap} cycle={cycle} startHour={CHECKIN_FIRST_HOUR}
            />
          ) : (
            <AvailabilityStep
              day="tomorrow" title={<>When can you date <em>tomorrow</em>?</>}
              sub="Tap each hour you're free to meet. We'll only show people free within your hours."
              map={map} setMap={setMap} cycle={cycle}
            />
          )}
        </div>

        <CheckInFooter label="Continue" disabled={disabled} onClick={onContinue} toast={toast} color="sunset"/>
      </div>

      {dialogOpen && <NoHoursTodayDialog onConfirm={onConfirmNoToday} onCancel={onCancelNoToday}/>}

      <HomeIndicator/>
    </div>
  );
}

// Tap cycle used by ScreenCheckIn: idle → preferred → available → idle.
const cycleAvailability = (map, h) => {
  const next = { ...map };
  if (!next[h]) next[h] = 'preferred';
  else if (next[h] === 'preferred') next[h] = 'available';
  else delete next[h];
  return next;
};

// Toast copy for Tomorrow (validation in ScreenCheckIn).
const CHECKIN_TOMORROW_MISSING = 'Pick at least one hour you can date tomorrow.';


function HowItWorks({ title = 'How it works', action, children }) {
  const [open, setOpen] = useStateCheckin(false);
  return (
    <>
      <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
      <button onClick={() => setOpen(o => !o)} style={{
        display: 'inline-flex', alignItems: 'center', gap: 6,
        background: 'transparent', border: 'none', padding: 0, cursor: 'pointer',
        fontFamily: 'var(--liq-font-sans)', fontSize: 13, fontWeight: 700,
        color: 'var(--liq-primary-500)',
      }}>
        <Icon name="alert-circle" size={15} stroke={2}/>
        {title}
        <span style={{
          display: 'inline-flex', transition: 'transform 200ms',
          transform: open ? 'rotate(180deg)' : 'rotate(0deg)',
        }}>
          <Icon name="chevron-down" size={15} stroke={2.4}/>
        </span>
      </button>
      {action}
      </div>

      {open && (
        <div style={{
          marginTop: 10, padding: '14px 16px', borderRadius: 14,
          background: '#EFE6FF', color: '#4F2B8C',
          fontFamily: 'var(--liq-font-sans)', fontSize: 13, lineHeight: 1.5,
          display: 'flex', flexDirection: 'column', gap: 8,
          animation: 'su-toast-in 200ms cubic-bezier(.22,1,.36,1)',
        }}>
          {children}
        </div>
      )}
    </>
  );
}

function AvailabilityStep({ title, sub, map, setMap, cycle, startHour = 7 }) {
  const hasSelection = Object.keys(map).length > 0;
  // Runs from startHour through the 6am slot — a Check-In day is 7am → 7am.
  // "Today" begins at the next full hour; "tomorrow" starts at 7am.
  const span = ((6 - startHour + 24) % 24) + 1;
  const HOURS = Array.from({ length: span }, (_, i) => (startHour + i) % 24);
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 'none' }}>
        <div style={{ marginTop: 8 }}>
          <h2 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
            letterSpacing: '-0.015em', lineHeight: 1.15, color: 'var(--liq-fg)',
            margin: 0, textWrap: 'pretty',
          }}>{title}</h2>
          <p style={{
            fontFamily: 'var(--liq-font-sans)', fontSize: 14, lineHeight: 1.45,
            color: 'var(--liq-fg-subtle)', margin: '10px 0 0', textWrap: 'pretty',
          }}>{sub}</p>

          {/* How-it-works disclosure — explains the availability→match→meet
              logic that is the core USP. Collapsed by default so the step
              stays focused; a lilac panel matching CoachNote expands inline. */}
          <HowItWorks action={
            hasSelection && (
              <button onClick={() => setMap({})} style={{
                display: 'inline-flex', alignItems: 'center', gap: 5,
                background: 'transparent', border: 'none', padding: 0, cursor: 'pointer',
                fontFamily: 'var(--liq-font-sans)', fontSize: 13, fontWeight: 700,
                color: 'var(--liq-fg-subtle)',
              }}>
                <Icon name="x" size={14} stroke={2.2}/>
                Clear all
              </button>
            )
          }>
            <p style={{ margin: 0 }}>
              We only show you people who are free to date at the <b>same times as you</b>.
            </p>
            <p style={{ margin: 0 }}>
              Only pick hours when you can <b>actually be at a meeting spot</b> — make sure you have time for the transit.
            </p>
            <p style={{ margin: 0 }}>
              When you get a Match, we suggest a meet time based on your <b>overlapping availability</b>.
            </p>
            <p style={{ margin: 0 }}>
              <b>Example:</b> At 7pm you get a Match. Your overlapping availability is 6pm–11pm. We suggest meeting at 8pm, so you both can arrive on time.
            </p>
            <p style={{ margin: 0 }}>
              That's the magic of Show Up — <b>you actually meet someone — fast.</b>
            </p>
          </HowItWorks>
        </div>
      </div>

      {/* Selectable hours — all shown at once; scrolls when the list is long
          so the legend below stays pinned above the CTA. */}
      <div style={{ flex: 1, minHeight: 0, overflowY: 'auto', marginTop: 13, paddingBottom: 2 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 5 }}>
          {HOURS.map(h => {
            const state = map[h] || 'idle';
            return <AvailabilitySlot key={h} label={CHECKIN_FMT_HOUR(h)}
              state={state} onClick={() => setMap(m => cycle(m, h))}/>;
          })}
        </div>
      </div>

      {/* Outcome line — what the taps actually add up to, in one row that
          never wraps (auto-shrinks to fit, ellipsis as last resort). Sits
          outside the scrollable grid so it stays visible. */}
      <AvailabilitySummary hours={HOURS} map={map}/>

      {/* Legend — pinned above the CTA with breathing room below it. */}
      <div style={{
        flex: 'none', marginTop: 6, paddingTop: 9, marginBottom: 8,
        borderTop: '1px solid var(--liq-border-soft)',
        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 5,
        fontFamily: 'var(--liq-font-sans)', fontSize: 11, fontWeight: 700,
        color: 'var(--liq-fg-muted)',
      }}>
        <span style={{ whiteSpace: 'nowrap', lineHeight: 1.1 }}>Tap an hour to cycle through:</span>
        <div style={{ display: 'flex', gap: 7, justifyContent: 'center', alignItems: 'center' }}>
          <LegendPill state="preferred" label="1st tap Preferred"/>
          <LegendPill state="available" label="2nd tap Available"/>
          <LegendPill state="clear" label="3rd tap Clear"/>
        </div>
      </div>
    </div>
  );
}

// ── AvailabilitySummary ───────────────────────────────────────
// Collapses the selected hours into human ranges ("6–11am · 6–11pm") and
// renders them on ONE line. An hour slot means "h until h+1", so a run of
// 6,7,8,9,10 reads "6–11am". Consecutiveness follows the order of the
// rendered hour list, so runs crossing midnight (11pm → 1am) stay one range.
function checkinRanges(hours, map) {
  const runs = [];
  let cur = null;
  hours.forEach(h => {
    if (map[h]) { if (cur) cur.push(h); else { cur = [h]; runs.push(cur); } }
    else cur = null;
  });
  const per = x => (x < 12 ? 'am' : 'pm');
  const num = x => (x % 12 === 0 ? 12 : x % 12);
  return runs.map(run => {
    const a = run[0], b = (run[run.length - 1] + 1) % 24;
    return per(a) === per(b)
      ? `${num(a)}–${num(b)}${per(b)}`
      : `${num(a)}${per(a)}–${num(b)}${per(b)}`;
  });
}

function AvailabilitySummary({ hours, map }) {
  const ranges = checkinRanges(hours, map);
  // Past the shrink floor a long list would ellipsize silently, so cap the
  // visible ranges and say how many are hidden — never an incomplete truth.
  const MAX = 4;
  const shown = ranges.length > MAX ? ranges.slice(0, MAX - 1) : ranges;
  const more = ranges.length - shown.length;
  const text = ranges.length
    ? shown.join(' · ') + (more ? ` · +${more} more` : '')
    : 'nothing selected yet';
  const wrapRef = useRefCheckin(null);
  const textRef = useRefCheckin(null);
  const [scale, setScale] = useStateCheckin(1);
  useEffectCheckin(() => {
    const w = wrapRef.current, t = textRef.current;
    if (!w || !t) return;
    const avail = w.clientWidth, need = t.scrollWidth;
    const s = need > avail ? Math.max(0.78, avail / need) : 1;
    setScale(prev => (Math.abs(prev - s) > 0.005 ? s : prev));
  }, [text]);
  return (
    <div style={{
      flex: 'none', marginTop: 8, padding: '8px 14px', borderRadius: 12,
      background: ranges.length ? '#F1ECF9' : '#fff',
      border: `1px solid ${ranges.length ? '#E3DBF0' : 'var(--liq-border-soft)'}`,
      display: 'flex', alignItems: 'baseline', gap: 8, overflow: 'hidden',
    }}>
      <span style={{
        flex: 'none', fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 12.5,
        color: ranges.length ? '#7C6BA0' : 'var(--liq-fg-subtle)', whiteSpace: 'nowrap',
      }}>Your availability</span>
      <div ref={wrapRef} style={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
        <span ref={textRef} style={{
          display: 'block', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
          fontFamily: 'var(--liq-font-sans)', fontWeight: ranges.length ? 800 : 600, fontSize: 13.5,
          color: ranges.length ? 'var(--liq-primary-600, #5B3FBF)' : 'var(--liq-fg-faint)',
          transform: `scale(${scale})`, transformOrigin: 'left center',
        }}>{text}</span>
      </div>
    </div>
  );
}

// Availability slots use a two-tier purple system:
//   - PREFERRED  → saturated lavender fill (the strongest "yes — date me here"
//                  signal).
//   - AVAILABLE  → very faint lavender-grey fill (a softer yes; clearly
//                  distinguishable from preferred at a glance).
function AvailabilitySlot({ label, state, onClick }) {
  const slotStyles = {
    idle:      { background: '#fff', color: 'var(--liq-fg)', border: '1px solid var(--liq-border)' },
    preferred: { background: 'var(--liq-primary-500)', color: '#fff', border: '1px solid var(--liq-primary-500)' },
    available: { background: '#F1ECF9', color: '#7C6BA0', border: '1px solid #E3DBF0' },
    past:      { background: 'transparent', color: 'var(--liq-fg-faint)', border: '1px solid var(--liq-border-soft)' },
  }[state];
  return (
    <button onClick={onClick} style={{
      height: 36, padding: '0 4px', borderRadius: 9999,
      fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 13,
      transition: 'all 180ms', ...slotStyles,
    }}>{label}</button>
  );
}

function LegendPill({ state, label }) {
  const legendStyles = state === 'preferred'
    ? { bg: 'var(--liq-primary-500)', fg: '#fff', border: 'var(--liq-primary-500)' }
    : state === 'available'
    ? { bg: '#F1ECF9', fg: '#7C6BA0', border: '#E3DBF0' }
    : { bg: '#fff', fg: 'var(--liq-fg)', border: 'var(--liq-border)' };
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      padding: '5px 10px', borderRadius: 9999, whiteSpace: 'nowrap',
      background: legendStyles.bg, color: legendStyles.fg,
      border: `1px solid ${legendStyles.border}`,
      fontFamily: 'var(--liq-font-sans)', fontSize: 11.5, fontWeight: 700,
    }}>{label}</span>
  );
}

function CheckInFooter({ label, disabled, onClick, toast, color = 'sunset' }) {
  // The intro screen sets the tone: full-width pill in the kit's sunset
  // gradient (violet → warm). The kit has no orange button outside of
  // small icon CTAs, so we stay on sunset across every check-in step.
  const variant = 'sunset';
  return (
    <div style={{ flex: 'none', marginBottom: 22, position: 'relative' }}>
      {/* Stacked extras above the CTA. Centered to align with the full-width button. */}
      {toast && (
        <div style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
          marginBottom: 12,
        }}>
          {toast && (
            <div style={{
              padding: '8px 14px', borderRadius: 10,
              background: 'rgba(29,17,41,0.92)', color: '#fff',
              fontFamily: 'var(--liq-font-sans)', fontSize: 12.5, fontWeight: 600,
              lineHeight: 1.35, textWrap: 'pretty', maxWidth: 280, textAlign: 'center',
              boxShadow: '0 6px 20px rgba(29,17,41,0.18)',
              animation: 'su-toast-in 200ms cubic-bezier(.22,1,.36,1)',
            }}>{toast}</div>
          )}
        </div>
      )}

      {/* Tap-capture wrapper: lets a disabled tap still surface the toast. */}
      <div onClick={disabled ? onClick : undefined} style={{ cursor: disabled ? 'not-allowed' : 'default' }}>
        <div style={{
          opacity: disabled ? 0.45 : 1,
          filter: disabled ? 'saturate(0.4)' : 'none',
          pointerEvents: disabled ? 'none' : 'auto',
          transition: 'opacity 180ms, filter 180ms',
        }}>
          <Button variant={variant} size="lg" fullWidth onClick={onClick} trailing={<CheckInArrow/>}>
            {label}
          </Button>
        </div>
      </div>
    </div>
  );
}

// Keyframes for the toast — injected once when this module loads.
if (typeof document !== 'undefined' && !document.getElementById('su-checkin-toast-kf')) {
  const s = document.createElement('style');
  s.id = 'su-checkin-toast-kf';
  s.textContent = `@keyframes su-toast-in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
@keyframes su-checkin-pop { from { opacity: 0; transform: translateY(10px) scale(.97); } to { opacity: 1; transform: translateY(0) scale(1); } }`;
  document.head.appendChild(s);
}

function NoHoursTodayDialog({ onConfirm, onCancel }) {
  return (
    <div role="dialog" aria-modal="true" aria-label="No available hours today" style={{
      position: 'absolute', inset: 0, zIndex: 40,
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '0 26px',
    }}>
      <div onClick={onCancel} style={{
        position: 'absolute', inset: 0,
        background: 'rgba(29,17,41,0.44)', backdropFilter: 'blur(3px)', WebkitBackdropFilter: 'blur(3px)',
        animation: 'su-toast-in 200ms ease-out both',
      }}/>

      <div style={{
        position: 'relative', width: '100%', maxWidth: 320,
        borderRadius: 26, background: 'var(--liq-bg-elevated)',
        boxShadow: '0 24px 60px rgba(20,12,28,0.34)',
        padding: '26px 24px 20px',
        animation: 'su-checkin-pop 260ms cubic-bezier(.22,1,.36,1) both',
      }}>
        <div style={{
          width: 52, height: 52, borderRadius: 16, margin: '0 auto 16px',
          background: 'var(--su-grad-lilac)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'var(--liq-primary-500)',
        }}>
          <Icon name="calendar" size={24} stroke={1.9}/>
        </div>

        <h2 style={{
          margin: 0, textAlign: 'center',
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 23,
          letterSpacing: '-0.01em', lineHeight: 1.15, color: 'var(--liq-fg)',
        }}>
          No hours <em>today</em>?
        </h2>

        <p style={{
          margin: '10px 0 0', textAlign: 'center',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14,
          lineHeight: 1.5, color: 'var(--liq-neutral-200)', textWrap: 'pretty',
        }}>
          You didn't add any available hours to date today. Continue to set your
          availability for tomorrow — or cancel and choose the hours you can date today.
        </p>

        <div style={{ marginTop: 18, display: 'flex', flexDirection: 'column', gap: 10 }}>
          <Button variant="sunset" size="lg" fullWidth onClick={onConfirm} trailing={<CheckInArrow/>}>
            Continue to tomorrow
          </Button>
          <button onClick={onCancel} style={{
            height: 50, width: '100%', borderRadius: 9999,
            background: 'transparent', border: 'none',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15,
            color: 'var(--liq-primary-500)', cursor: 'pointer',
          }}>
            Cancel, pick hours today
          </button>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { AvailabilityScreen, AvailabilityStep, HowItWorks, AvailabilitySummary, AvailabilitySlot, LegendPill, CheckInFooter, NoHoursTodayDialog, checkinRanges, cycleAvailability });
