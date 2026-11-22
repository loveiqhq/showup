// ═════════════════════════════════════════════════════════════
// Date 06 — End date and report a problem · REFERENCE IMPLEMENTATION
// Extracted from ui_kits/show-up/screens-date-chat.jsx and screens-date.jsx
// (10 Nov 2026). The kit is the source of truth; this file is regenerated
// from it, never edited by hand.
//
// What this ticket builds (spec sheet frames A–J):
//   A DateStatusBar        — End Date bar, footer slot after check-in (no "Date complete" variant)
//   B DateWrapSheet        — "How did it go with {name}?" · We met / My date isn't showing up
//   C WrapConfirm (met)    — "Thanks for showing up" · End Date → Review
//   E/F NoShowGuide        — "Not here yet?" · end button disabled before the BINDING meet time (pastMeet)
//   G NoShowConfirm        — "Thanks for waiting" · End date and review · Back
//   H ReportProblemSheet   — menu: My date isn't showing up · I don't feel comfortable
//   I UncomfortableStep    — Head out now (ends the date) · Stay for now (stores a flag)
//   J EndErrorBox          — end failed, inside the sheet, CTA retries
// NoShowGuide, NoShowConfirm, UncomfortableStep and EndErrorBox are SHARED:
// DateWrapSheet and ReportProblemSheet render the same ones. One copy each.
//
// Kit frame props (ScreenMatchCoord):
//   A coordOpen · B coordOpen initialWrapOpen · C … wrapStep="met"
//   D coordOpen checkInFlow initialWrapOpen wrapStep="waiting" (also checkInFlow initialReportOpen reportStep="waiting" | "uncomfortable")
//   E coordOpen initialWrapOpen wrapStep="waiting" · F … pastMeet · G coordOpen pastMeet initialWrapOpen wrapStep="noshow"
//   H coordOpen initialReportOpen · I … reportStep="uncomfortable" · J coordOpen pastMeet initialWrapOpen wrapStep="noshow" endError
//
// Consumed, not built here:
//   ScreenMatchCoord, the footer slot, the Report entry card → Date 01
//   CheckInConfirmSheet (note), CheckInDoneSheet (onUndo), useArrivedGate, CheckInBar's End date link → Date 03
//   The match's screens (date-closed-positive · you-no-showed · date-ended-discomfort) → Date 08
//   Phone · Card · Icon · ModalShell → design_handoff_showup/components/shared.jsx
// Parked, not in this file: "I'm in danger" (AI safety call) · "Something else".
//
// Read values from here, not from the PNG.
// ═════════════════════════════════════════════════════════════

const { useState: useChatState } = React;
const useStateDate = useChatState;

// 'Date complete · Browsing's unlocked · Rate' variant DELETED 10 Nov 2026 (Date 06):
// Search stays blocked until the review (epic rule 5), so the bar never unlocks anything.
function DateStatusBar({ name = 'Leonie', onWrap }) {
  return (
    <div style={{
      padding: '10px 14px', display: 'flex', alignItems: 'center', gap: 12,
      background: 'rgba(255,251,247,0.96)', backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)',
      borderTop: '1px solid var(--liq-border-soft)',
    }}>
      <div style={{
        width: 38, height: 38, borderRadius: 12, flex: 'none',
        background: 'rgba(129,42,236,0.10)', color: 'var(--liq-primary-500)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Icon name="calendar" size={19} stroke={2.1}/>
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13.5, fontWeight: 700, color: 'var(--liq-fg)' }}>Your date with {name}</div>
        <div style={{ fontSize: 11.5, color: 'var(--liq-fg-muted)', lineHeight: 1.35, textWrap: 'pretty' }}>Tap End Date - when your date is finished.</div>
      </div>
      <button onClick={onWrap} style={{
        height: 40, padding: '0 16px', borderRadius: 9999, flex: 'none', cursor: 'pointer',
        background: '#fff', color: 'var(--liq-primary-500)', border: '1.5px solid var(--liq-primary-500)',
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14,
        display: 'inline-flex', alignItems: 'center', gap: 7, whiteSpace: 'nowrap',
      }}>
        End Date
      </button>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// "How did it go?" wrap-up sheet — opened from the date-status footer. The
// single honest surface for EVERY way a date ends, because each one feeds the
// Commitment / Show-up score differently:
//   • great time      → they met → rate the date (the happy path)
//   • waited, no-show  → they never met → no-penalty exit, we follow up
// A quiet "not yet" backs out of taps made too early.
// ─────────────────────────────────────────────────────────────
// Small back-link used at the top of each wrap-up confirmation view.
function WrapBack({ onBack }) {
  return (
    <button onClick={onBack} style={{
      display: 'inline-flex', alignItems: 'center', gap: 5, padding: '4px 2px', marginBottom: 8,
      background: 'none', border: 'none', cursor: 'pointer',
      fontFamily: 'var(--liq-font-sans)', fontSize: 13.5, fontWeight: 700, color: 'var(--liq-fg-subtle)',
    }}>
      <Icon name="chevron-left" size={16} stroke={2.4}/> Back
    </button>
  );
}

// Shared confirmation beat for a wrap-up outcome: hero icon + title + body,
// a "What happens next" card, one primary CTA and a back link. Every outcome
// funnels through the same anatomy so the three endings feel like one system.
// `attestNote`: plain note pointing at the real exit (Cancel date) for dates
// that never happened. The tick-box gate was removed 4 Nov 2026.
function WrapConfirm({ icon, iconTint, iconColor, title, body, steps, cta, onCta, onBack, attest, attestNote, secondary = 'Cancel', error = false }) {
  const gated = false; // attest checkbox removed 4 Nov 2026 — claiming the date happened is enough
  return (
    <div style={{ padding: '14px 20px 24px' }}>
      <div style={{ textAlign: 'center' }}>
        <div style={{
          width: 72, height: 72, borderRadius: 9999, margin: '4px auto 0',
          background: iconTint, color: iconColor,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon name={icon} size={30} stroke={2.2}/>
        </div>
        <h3 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26,
          letterSpacing: '-0.01em', margin: '14px 0 8px', color: 'var(--liq-fg)', lineHeight: 1.1,
        }}>{title}</h3>
        <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: '0 auto 18px', lineHeight: 1.5, maxWidth: 300, textWrap: 'pretty' }}>{body}</p>
      </div>

      <div style={{ padding: '14px 15px', borderRadius: 16, background: 'var(--liq-bg-raised)', border: '1px solid var(--liq-border-soft)', marginBottom: 18 }}>
        <div style={{ fontSize: 11.5, fontWeight: 800, letterSpacing: 0.08, textTransform: 'uppercase', color: 'var(--liq-fg-subtle)', marginBottom: 10 }}>What happens next</div>
        {steps.map((x, i) => (
          <div key={i} style={{ display: 'flex', gap: 11, alignItems: 'flex-start', marginTop: i ? 11 : 0 }}>
            <Icon name={x.icon} size={17} stroke={2.1} style={{ color: 'var(--liq-primary-500)', flex: 'none', marginTop: 1 }}/>
            <div style={{ fontSize: 13.5, color: 'var(--liq-fg)', lineHeight: 1.4 }}>{x.t}</div>
          </div>
        ))}
      </div>

      {attestNote && (
        <div style={{ display: 'flex', gap: 9, alignItems: 'flex-start', borderRadius: 16, background: 'rgba(254,104,57,0.07)', border: '1px solid rgba(254,104,57,0.28)', padding: 13, marginBottom: 14 }}>
          <Icon name="shield-alert" size={15} stroke={2.2} style={{ color: 'var(--liq-orange-500)', flex: 'none', marginTop: 1 }}/>
          <div style={{ fontSize: 12, color: 'var(--liq-fg-muted)', lineHeight: 1.45, textWrap: 'pretty' }}>{attestNote}</div>
        </div>
      )}

      {error && <EndErrorBox/>}
      <button onClick={gated ? undefined : onCta} disabled={gated} style={{
        width: '100%', height: 56, borderRadius: 18, border: 'none',
        cursor: gated ? 'not-allowed' : 'pointer', opacity: gated ? 0.42 : 1,
        background: 'var(--su-grad-sunset)', color: '#fff', fontFamily: 'var(--liq-font-sans)',
        fontWeight: 700, fontSize: 16.5, boxShadow: gated ? 'none' : 'var(--liq-shadow-cta)',
      }}>{cta}</button>
      <button onClick={onBack} style={{
        marginTop: 10, width: '100%', height: 50, borderRadius: 16,
        background: '#fff', border: '1px solid var(--liq-border-soft)',
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14.5, color: 'var(--liq-fg-muted)', cursor: 'pointer',
      }}>{secondary}</button>
    </div>
  );
}

// "What happens next" bullets for the showed-up outcome.
const WRAP_SHOWED_UP_STEPS = [
  { icon: 'star', t: 'Please review your date now.' },
  { icon: 'search', t: 'Search for new dates resumes after the review.' },
];

// ─────────────────────────────────────────────────────────────
// Date 06 shared pieces (10 Nov 2026). ONE copy each, used by DateWrapSheet
// AND ReportProblemSheet — "My date isn't showing up" is one flow, two entries.
// ─────────────────────────────────────────────────────────────
// End failed: the date is not ended until the server confirms. Same box as
// Date 02's End the date sheet and Date 03's locError. The CTA retries.
function EndErrorBox() {
  return (
    <div role="alert" style={{ display: 'flex', alignItems: 'flex-start', gap: 9, margin: '0 0 14px', padding: '11px 13px', borderRadius: 14, background: 'rgba(251,50,59,0.08)', border: '1px solid rgba(251,50,59,0.20)', textAlign: 'left' }}>
      <Icon name="alert-circle" size={17} stroke={2.2} style={{ color: 'var(--liq-danger-fg)', flex: 'none', marginTop: 1 }}/>
      <span style={{ fontSize: 13.5, fontWeight: 600, lineHeight: 1.4, color: 'var(--liq-fg)', textWrap: 'pretty' }}>Couldn't end the date. Check your connection and try again.</span>
    </div>
  );
}

// "Not here yet?" — the wait guidance. Reached only once checked in (one gate).
// The end button unlocks at the BINDING meet time (pastMeet); before that it is
// disabled and says when. Message {name} is hidden while the chat is locked.
function NoShowGuide({ name = 'Leonie', onBack, onMessage, onEnd, pastMeet = true, meetTime = '19:00', chatOpen = true }) {
  const guide = [
    { n: 1, t: <><b>Message {name}</b> to ask where they are. A quick “on my way?” clears up most late arrivals.</> },
    { n: 2, t: <>Please give it at least <strong style={{ color: 'var(--liq-fg)' }}>10 minutes</strong>. Something can always come up.</> },
    { n: 3, t: <>Please check you are at the right location.</> },
    { n: 4, t: <>If you get no reply and you’re <strong style={{ color: 'var(--liq-fg)' }}>10 minutes</strong> past your meet time — you’re free to end the date and leave.</> },
  ];
  return (
    <ModalShell onCancel={onBack}>
      <div style={{ padding: '14px 20px 24px' }}>
        <WrapBack onBack={onBack}/>
        <div style={{ textAlign: 'center' }}>
          <div style={{ width: 72, height: 72, borderRadius: 9999, margin: '4px auto 0', background: 'rgba(254,104,57,0.12)', color: 'var(--liq-orange-500)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon name="hourglass" size={30} stroke={2.2}/>
          </div>
          <h3 className="su-underlined" style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26, letterSpacing: '-0.01em', margin: '14px 0 8px', color: 'var(--liq-fg)', lineHeight: 1.1 }}>Not here yet?</h3>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 18 }}>
          {guide.map((g) => (
            <div key={g.n} style={{ display: 'flex', gap: 12, alignItems: 'center', padding: '13px 14px', borderRadius: 14, background: 'var(--liq-bg-raised)', border: '1px solid var(--liq-border-soft)' }}>
              <div style={{ width: 30, height: 30, borderRadius: 9999, flex: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#fff', border: '1px solid var(--liq-border-soft)', fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 15, color: 'var(--liq-orange-500)' }}>{g.n}</div>
              <div style={{ fontSize: 13.5, color: 'var(--liq-fg)', lineHeight: 1.45 }}>{g.t}</div>
            </div>
          ))}
        </div>
        {chatOpen && (
          <button onClick={onMessage} style={{ width: '100%', height: 56, borderRadius: 18, border: 'none', cursor: 'pointer', background: 'var(--su-grad-sunset)', color: '#fff', fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 16.5, boxShadow: 'var(--liq-shadow-cta)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 9 }}>
            <Icon name="message-circle" size={20} stroke={2.2}/> Message {name}
          </button>
        )}
        <button onClick={pastMeet ? onEnd : undefined} disabled={!pastMeet} aria-disabled={!pastMeet} style={{
          marginTop: chatOpen ? 10 : 0, width: '100%', height: 50, borderRadius: 16,
          background: pastMeet ? '#fff' : 'var(--liq-bg-raised)', border: '1px solid var(--liq-border-soft)',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14.5,
          color: pastMeet ? 'var(--liq-fg-muted)' : 'var(--liq-fg-subtle)', cursor: pastMeet ? 'pointer' : 'default',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 7,
        }}>
          {pastMeet ? <>It’s been 10+ minutes — end date</> : <><Icon name="clock" size={16} stroke={2.2}/> You can end the date from {meetTime}</>}
        </button>
      </div>
    </ModalShell>
  );
}

// "Thanks for waiting" — the no-show end. No verdict about the match (flow 10
// decides); only the ender's own rate is promised. Back returns to the guide.
function NoShowConfirm({ name = 'Leonie', onCta, onBack, error = false }) {
  return (
    <ModalShell onCancel={onBack}>
      <WrapConfirm
        icon="hourglass" iconTint="rgba(254,104,57,0.12)" iconColor="var(--liq-orange-500)"
        title="Thanks for waiting"
        body={<>You gave {name} time. <strong style={{ color: 'var(--liq-fg)' }}>Leaving now won’t lower your Show-up Rate.</strong></>}
        steps={[
          { icon: 'star', t: 'Please give us a little more information in the review now - helps us to determine the consequences for your date partner.' },
          { icon: 'shield-check', t: 'No penalty to you — your Show-up Rate stays where it is.' },
          { icon: 'search', t: 'Searching for a new date will resume.' },
        ]}
        cta="End date and review" onCta={onCta} onBack={onBack} secondary="Back" error={error}/>
    </ModalShell>
  );
}

// "I don't feel comfortable" — reached only once checked in (one gate).
// Head out now ENDS the date: A → flow 10, B → Date 08 "Your date ended it".
// Stay for now stores an internal flag (evidence for flow 10) and closes.
function UncomfortableStep({ name = 'Leonie', onBack, onHeadOut, onStay, error = false }) {
  return (
    <ModalShell onCancel={onBack}>
      <div style={{ padding: '14px 20px 24px' }}>
        <WrapBack onBack={onBack}/>
        <div style={{ textAlign: 'center' }}>
          <div style={{ width: 72, height: 72, borderRadius: 9999, margin: '4px auto 0', background: 'rgba(129,42,236,0.10)', color: 'var(--liq-primary-500)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon name="heart" size={32} stroke={2.1}/>
          </div>
          <h3 className="su-underlined" style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26, letterSpacing: '-0.01em', margin: '14px 0 8px', color: 'var(--liq-fg)', lineHeight: 1.1 }}>We're sorry to hear that.</h3>
          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: '0 auto 18px', lineHeight: 1.5, maxWidth: 300, textWrap: 'pretty' }}>
            You don't have to explain a thing — you showed up, so <b>you're free to head out now,</b> with <strong style={{ color: 'var(--liq-fg)' }}>no penalty to your Show-up Rate.</strong>
          </p>
        </div>
        <div style={{ padding: '14px 15px', borderRadius: 16, background: 'var(--liq-bg-raised)', border: '1px solid var(--liq-border-soft)', marginBottom: 18 }}>
          <div style={{ fontSize: 11.5, fontWeight: 800, letterSpacing: 0.08, textTransform: 'uppercase', color: 'var(--liq-fg-subtle)', marginBottom: 10 }}>What happens next</div>
          {[
            { icon: 'shield-check', t: "No penalty to you — you showed up, and that's what counts." },
            { icon: 'home', t: 'Head out whenever you\'re ready — no need to say goodbye.' },
            { icon: 'star', t: 'Review your date next. What you tell us stays confidential.' },
          ].map((x, i) => (
            <div key={i} style={{ display: 'flex', gap: 11, alignItems: 'flex-start', marginTop: i ? 11 : 0 }}>
              <Icon name={x.icon} size={17} stroke={2.1} style={{ color: 'var(--liq-primary-500)', flex: 'none', marginTop: 1 }}/>
              <div style={{ fontSize: 13.5, color: 'var(--liq-fg)', lineHeight: 1.4 }}>{x.t}</div>
            </div>
          ))}
        </div>
        {error && <EndErrorBox/>}
        <button onClick={onHeadOut} style={{ width: '100%', height: 56, borderRadius: 18, border: 'none', cursor: 'pointer', background: 'var(--su-grad-sunset)', color: '#fff', fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 16.5, boxShadow: 'var(--liq-shadow-cta)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 9 }}>Head out now</button>
        <button onClick={onStay} style={{ marginTop: 10, width: '100%', height: 50, borderRadius: 16, background: '#fff', border: '1px solid var(--liq-border-soft)', fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14.5, color: 'var(--liq-fg)', cursor: 'pointer' }}>Stay for now</button>
      </div>
    </ModalShell>
  );
}

// "How did it go?" — opened from the End Date bar AND the Check-in bar's End
// date link. "We met" needs no check-in (both confirming in the review counts);
// "My date isn't showing up" runs the one check-in gate first (10 Nov 2026).
function DateWrapSheet({ name = 'Leonie', onMet, onNoShow, onClose, onMessage, initialStep = 'menu', checkedIn = true, onCheckedIn, pastMeet = true, meetTime = '19:00', chatOpen = true, endError = false }) {
  const [step, setStep] = useChatState(initialStep);
  const [ciDone, setCiDone] = useChatState(checkedIn);
  const [ciStep, setCiStep] = useChatState('confirm');

  if (step === 'met') {
    return (
      <ModalShell onCancel={onClose}>
        <WrapConfirm
          icon="sparkles" iconTint="rgba(0,171,85,0.14)" iconColor="var(--liq-success-fg)"
          title="Thanks for showing up"
          body={<>You are wonderful for showing up to {name}.<br/>We hope you had a great time together.</>}
          steps={WRAP_SHOWED_UP_STEPS}
          attestNote={<>This is only for dates that happened. Need to call it off <strong style={{ color: 'var(--liq-fg)' }}>before</strong> meeting? Use <strong style={{ color: 'var(--liq-fg)' }}>Cancel date</strong> in the chat menu. Ending a date you didn’t attend counts as a no-show and lowers your Show-up Rate.</>}
          cta="End Date → Review" onCta={onMet} onBack={() => setStep('menu')} error={endError}/>
      </ModalShell>
    );
  }

  if (step === 'waiting' && !ciDone) {
    if (ciStep === 'confirm') return <CheckInConfirmSheet name={name} onYes={() => setCiStep('done')} onNotYet={() => setStep('menu')}
      note="Check in first, so we can confirm you were there."/>;
    return <CheckInDoneSheet name={name} onClose={() => { setCiDone(true); onCheckedIn && onCheckedIn(); }} onUndo={onClose}/>;
  }

  if (step === 'waiting') {
    return <NoShowGuide name={name} onBack={() => setStep('menu')} onMessage={onMessage || onClose} onEnd={() => setStep('noshow')} pastMeet={pastMeet} meetTime={meetTime} chatOpen={chatOpen}/>;
  }

  if (step === 'noshow') {
    return <NoShowConfirm name={name} onCta={onNoShow} onBack={() => setStep('waiting')} error={endError}/>;
  }

  return (
    <ModalShell onCancel={onClose}>
      <div style={{ padding: '14px 20px 22px' }}>
        <h3 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26,
          letterSpacing: '-0.01em', margin: '4px 0 18px', color: 'var(--liq-fg)', lineHeight: 1.1,
        }}>How did it go with {name}?</h3>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {/* Happy path — the date went well; hands into a confirmation step. */}
          <button onClick={() => setStep('met')} style={{
            display: 'flex', alignItems: 'center', gap: 14, width: '100%', textAlign: 'left',
            padding: 14, borderRadius: 18, cursor: 'pointer',
            background: '#fff', border: '1.5px solid var(--liq-primary-500)',
          }}>
            <div style={{
              width: 46, height: 46, borderRadius: 13, flex: 'none',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              background: 'rgba(0,171,85,0.12)', color: 'var(--liq-success-fg)',
            }}>
              <Icon name="sparkles" size={22} stroke={2.4}/>
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 700, fontSize: 15.5, lineHeight: 1.25, color: 'var(--liq-fg)' }}>We met — date is finished</div>
              <div style={{ fontSize: 12.5, color: 'var(--liq-fg-muted)', marginTop: 3, lineHeight: 1.4 }}>Only once you’ve actually met in person.</div>
            </div>
            <Icon name="chevron-right" size={18} stroke={2.2} style={{ color: 'var(--liq-fg-subtle)', flex: 'none' }}/>
          </button>

          {/* My date isn't showing up — opens the guidance beat first (give
              it 10 min, message them), then the no-penalty exit. */}
          <button onClick={() => setStep('waiting')} style={{
            display: 'flex', alignItems: 'center', gap: 14, width: '100%', textAlign: 'left',
            padding: 14, borderRadius: 18, cursor: 'pointer',
            background: '#fff', border: '1px solid var(--liq-border-soft)',
          }}>
            <div style={{
              width: 46, height: 46, borderRadius: 13, flex: 'none',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              background: 'rgba(254,104,57,0.12)', color: 'var(--liq-orange-500)',
            }}>
              <Icon name="hourglass" size={22} stroke={2.1}/>
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 700, fontSize: 15.5, lineHeight: 1.25, color: 'var(--liq-fg)' }}>My date isn't showing up</div>
              <div style={{ fontSize: 12.5, color: 'var(--liq-fg-muted)', marginTop: 3, lineHeight: 1.4 }}>They're not here yet — here's what to do and how the wait works.</div>
            </div>
            <Icon name="chevron-right" size={18} stroke={2.2} style={{ color: 'var(--liq-fg-subtle)', flex: 'none' }}/>
          </button>
        </div>

        <button onClick={onClose} style={{
          marginTop: 14, width: '100%', height: 52, borderRadius: 16,
          background: '#fff', border: '1px solid var(--liq-border-soft)',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15, color: 'var(--liq-fg-muted)', cursor: 'pointer',
        }}>Cancel</button>
      </div>
    </ModalShell>
  );
}

// Small back-link used at the top of each report outcome view.
function ReportBack({ onBack }) {
  return (
    <button onClick={onBack} style={{
      display: 'inline-flex', alignItems: 'center', gap: 5, padding: '4px 2px', marginBottom: 8,
      background: 'none', border: 'none', cursor: 'pointer',
      fontFamily: 'var(--liq-font-sans)', fontSize: 13.5, fontWeight: 700, color: 'var(--liq-fg-subtle)',
    }}>
      <Icon name="chevron-left" size={16} stroke={2.4}/> Back
    </button>
  );
}

function ReportProblemSheet({ onClose, name = 'Leonie', initialStep = 'menu', initialEnded = false, aiCall = false, checkedIn = true, onCheckedIn, pastMeet = true, meetTime = '19:00', chatOpen = true, endError = false, onMessage }) {
  const [step, setStep] = useStateDate(initialStep);
  // Not checked in yet → "My date isn't showing up" runs the check-in flow
  // first (2 Nov 2026). Confirmed → the guidance continues. Not yet → menu.
  const [ciDone, setCiDone] = useStateDate(checkedIn);
  const [ciStep, setCiStep] = useStateDate('confirm');
  // Whether "Something else" was submitted WITH ending the date, so the
  // shared confirmation beat can adapt its copy.
  const [ended, setEnded] = useStateDate(initialEnded);
  const OPTIONS = [
    { id: 'waiting', icon: 'hourglass',
      title: "My date isn't showing up",
      sub: "They're not here yet — here's what to do and how the wait works." },
    { id: 'uncomfortable', caution: true, icon: 'hand',
      title: "I don't feel comfortable",
      sub: "If you don't feel comfortable in the situation - head out now. That's ok!" },
    // PARKED (2 Nov 2026) — AI calls are out of MVP. The "I'm in danger" route
    // and its 'unsafe' step stay in code for the backlog; pass aiCall to show.
    ...(aiCall ? [{ id: 'unsafe', danger: true, icon: 'shield-alert',
      title: "I'm in danger",
      sub: <><strong style={{ fontWeight: 700, color: 'var(--liq-danger-fg)' }}>For genuine safety emergencies only.</strong> Tap to get a call from us — take it and leave the situation.</> }] : []),
  ];

  // ── Menu — the triage list (unchanged). Each route now advances a step. ──
  if (step === 'menu') {
    return (
      <ModalShell onCancel={onClose}>
        <div style={{ padding: '14px 20px 22px' }}>
          <h3 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26,
            letterSpacing: '-0.01em', margin: '4px 0 4px', color: 'var(--liq-fg)', lineHeight: 1.1,
          }}>Report a problem</h3>
          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: '0 0 18px', lineHeight: 1.45 }}>
            Everything here is confidential. We've got you.
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {OPTIONS.map(o => (
              <button key={o.id} onClick={() => setStep(o.id)} style={{
                display: 'flex', alignItems: 'center', gap: 14, width: '100%', textAlign: 'left',
                padding: 14, borderRadius: 18, cursor: 'pointer',
                background: o.danger ? 'rgba(251,50,59,0.055)' : o.caution ? 'rgba(245,158,11,0.07)' : '#fff',
                border: o.danger ? '1px solid rgba(251,50,59,0.28)' : o.caution ? '1px solid rgba(217,119,6,0.32)' : '1px solid var(--liq-border-soft)',
              }}>
                <div style={{
                  width: 46, height: 46, borderRadius: 13, flex: 'none',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: o.danger ? 'rgba(251,50,59,0.12)' : o.caution ? 'rgba(245,158,11,0.16)' : o.warm ? 'rgba(254,104,57,0.12)' : 'var(--liq-bg-raised)',
                  color: o.danger ? 'var(--liq-danger)' : o.caution ? '#b45309' : o.warm ? 'var(--liq-orange-500)' : 'var(--liq-primary-500)',
                }}>
                  <Icon name={o.icon} size={22} stroke={2.1}/>
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 700, fontSize: 15.5, lineHeight: 1.25, color: o.danger ? 'var(--liq-danger-fg)' : o.caution ? '#92400e' : 'var(--liq-fg)' }}>{o.title}</div>
                  <div style={{ fontSize: 12.5, color: 'var(--liq-fg-muted)', marginTop: 3, lineHeight: 1.4 }}>{o.sub}</div>
                </div>
                <Icon name="chevron-right" size={18} stroke={2.2} style={{ color: 'var(--liq-fg-subtle)', flex: 'none' }}/>
              </button>
            ))}
          </div>

          <button onClick={onClose} style={{
            marginTop: 14, width: '100%', height: 52, borderRadius: 16,
            background: '#fff', border: '1px solid var(--liq-border-soft)',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15, color: 'var(--liq-fg)', cursor: 'pointer',
          }}>Cancel</button>
        </div>
      </ModalShell>
    );
  }

  // PARKED — 'unsafe' (I'm in danger, AI safety call) is out of MVP. Not in this reference; the kit keeps it behind aiCall.

  // ── Date 06 (10 Nov 2026): "I don't feel comfortable" and "My date isn't
  //    showing up" both run the ONE check-in gate first (useArrivedGate's
  //    sheets with the note). Not yet → back to the menu. Undo → Date screen.
  //    The guide, the no-show end and the uncomfortable step are the SHARED
  //    components from screens-date-chat.jsx — DateWrapSheet uses the same ones.
  if ((step === 'waiting' || step === 'uncomfortable') && !ciDone) {
    if (ciStep === 'confirm') return <CheckInConfirmSheet name={name} onYes={() => setCiStep('done')} onNotYet={() => setStep('menu')}
      note="Check in first, so we can confirm you were there."/>;
    return <CheckInDoneSheet name={name} onClose={() => { setCiDone(true); onCheckedIn && onCheckedIn(); }} onUndo={onClose}/>;
  }

  if (step === 'uncomfortable') {
    return <UncomfortableStep name={name} onBack={() => setStep('menu')} onHeadOut={onClose} onStay={onClose} error={endError}/>;
  }

  if (step === 'waiting') {
    return <NoShowGuide name={name} onBack={() => setStep('menu')} onMessage={onMessage || onClose} onEnd={() => setStep('noshow')} pastMeet={pastMeet} meetTime={meetTime} chatOpen={chatOpen}/>;
  }

  if (step === 'noshow') {
    return <NoShowConfirm name={name} onCta={onClose} onBack={() => setStep('waiting')} error={endError}/>;
  }

  // PARKED (10 Nov 2026) — "Something else" (free text, safety-team review) is not in MVP. Not in this reference.
  return null;
}

Object.assign(window, { DateStatusBar, DateWrapSheet, WrapConfirm, WrapBack, NoShowGuide, NoShowConfirm, UncomfortableStep, EndErrorBox, ReportProblemSheet, ReportBack, WRAP_SHOWED_UP_STEPS });
