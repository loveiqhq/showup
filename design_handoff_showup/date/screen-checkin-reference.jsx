// ═════════════════════════════════════════════════════════════
// Date 03 — Arrival check-in (Date 04 folded in) · REFERENCE IMPLEMENTATION
// Extracted from ui_kits/show-up/screens-date-chat.jsx (6 Nov 2026).
// The kit is the source of truth; this file is regenerated from it, never
// edited by hand.
//
// What this ticket builds (spec sheet frames A–H):
//   A CheckInBar                       — footer slot until checked in (End date link → Date 06, hidden until it ships)
//   B CheckInConfirmSheet              — "Have you arrived…?" · Not yet / Yes, I've arrived
//   C CheckInConfirmSheet locError     — no location fix: nothing stored, Yes → Try again
//   D CheckInDoneSheet                 — "You are amazing for showing up!" · Close · Undo check-in
//   E footer swap on checkedIn         — content is DateStatusBar (Date 06)
//   F useArrivedGate                   — the one gate: Date screen · chat chip · Report (06)
//   H CoordinateModule unread.arrival  — what the match sees. ONLY the `arrival` branch is
//                                        this ticket; the rest of CoordinateModule is Date 01 / 05.
//
// Kit frame props: A ScreenMatchCoord coordOpen checkInFlow · B … initialCheckInPopup="confirm"
//   C … ="locerror" · D … ="done" · E ScreenMatchCoord coordOpen · F ScreenChatEntry initialCheckInPopup="confirm"
//   G ScreenProfileLocation context="date" state="denied" · H ScreenMatchCoord coordOpen checkInFlow coordUnread={{ arrival: true, time }}
//
// Consumed, not built here:
//   ScreenMatchCoord, the footer slot → Date 01 · CHAT_REASONS, REASON_TONES, ReasonBadge, ScreenChatEntry → Date 02
//   DateStatusBar, DateWrapSheet, ReportProblemSheet → Date 06 · ScreenProfileLocation → Search 04 / Profile 12
//   Phone · Card · Icon · Avatar · ModalShell → design_handoff_showup/components/shared.jsx
//
// Read values from here, not from the PNG.
// ═════════════════════════════════════════════════════════════

const { useState: useChatState } = React;

// ─────────────────────────────────────────────────────────────
// Check-in bar — replaces the End Date bar until the user checks in at the
// meeting location. Larger and filled so it reads as THE action on arrival.
// Tapping Check-in captures location and notifies the match.
// ─────────────────────────────────────────────────────────────
function CheckInBar({ name = 'Leonie', onCheckIn, onEnd }) {
  return (
    <div style={{
      padding: '16px 16px 10px', display: 'flex', flexDirection: 'column', gap: 6,
      background: 'rgba(29,17,41,0.86)', backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)', borderRadius: '22px 22px 0 0',
      boxShadow: '0 -6px 24px rgba(29,17,41,0.22)',
    }}>
    <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
      <div style={{
        width: 46, height: 46, borderRadius: 14, flex: 'none',
        background: 'rgba(255,255,255,0.12)', color: '#fff',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Icon name="map-pin" size={22} stroke={2.2}/>
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 18, color: '#fff', lineHeight: 1.15 }}>Your date with {name}</div>
        <div style={{ fontSize: 12.5, color: '#fff', lineHeight: 1.4, marginTop: 3, textWrap: 'pretty' }}>Confirm you arrived at the meeting location, we will let your date know.</div>
      </div>
      <button onClick={onCheckIn} style={{
        height: 46, padding: '0 20px', borderRadius: 9999, flex: 'none', cursor: 'pointer',
        background: 'var(--su-grad-sunset)', color: '#fff', border: 'none',
        fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 15,
        display: 'inline-flex', alignItems: 'center', gap: 7, whiteSpace: 'nowrap',
        boxShadow: 'var(--liq-shadow-cta)',
      }}>
        Check-in
      </button>
    </div>
    {/* End date — the End Date bar's action, available before check-in (decided 2 Nov 2026).
        Opens the same wrap-up sheet. NOT the Cancel this date flow, which stays at the end of the scroll. */}
    <button onClick={onEnd} style={{
      alignSelf: 'center', padding: '8px 12px', background: 'transparent', border: 'none', cursor: 'pointer',
      fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 13, color: '#E8DCF5',
    }}>End date</button>
    </div>
  );
}

// Check-in guard — a deliberate "yes" before the location is locked, so a
// mis-tap or a curious tap never records an arrival. Not yet = nothing stored.
// locError (6 Nov 2026): Yes was tapped but the phone returned no location —
// nothing is stored; the same sheet stays up and Yes becomes Try again.
function CheckInConfirmSheet({ name = 'Leonie', onYes, onNotYet, note = null, locError = false }) {
  return (
    <ModalShell onCancel={onNotYet}>
      <div style={{ padding: '14px 24px 24px', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
        <div style={{
          width: 80, height: 80, borderRadius: 9999, margin: '8px 0 0',
          background: 'rgba(129,42,236,0.12)', color: 'var(--liq-primary-500)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon name="map-pin" size={34} stroke={2.2}/>
        </div>
        <h3 style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26, letterSpacing: '-0.01em',
          margin: '16px 0 22px', color: 'var(--liq-fg)', lineHeight: 1.15, maxWidth: 310, textWrap: 'pretty',
        }}>Have you arrived at the meeting location for your date with {name}?</h3>
        {note && <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: '-10px 0 20px', lineHeight: 1.5, maxWidth: 310, textWrap: 'pretty' }}>{note}</p>}
        {locError && (
          <div role="alert" style={{ alignSelf: 'stretch', display: 'flex', alignItems: 'flex-start', gap: 9, margin: '-8px 0 16px', padding: '11px 13px', borderRadius: 14, background: 'rgba(251,50,59,0.08)', border: '1px solid rgba(251,50,59,0.20)', textAlign: 'left' }}>
            <Icon name="alert-circle" size={17} stroke={2.2} style={{ color: 'var(--liq-danger-fg)', flex: 'none', marginTop: 1 }}/>
            <span style={{ fontSize: 13.5, fontWeight: 600, lineHeight: 1.4, color: 'var(--liq-fg)', textWrap: 'pretty' }}>We couldn't find your location. Check your signal, then try again.</span>
          </div>
        )}
        <button onClick={onNotYet} style={{
          width: '100%', height: 50, borderRadius: 16, cursor: 'pointer',
          background: '#fff', border: '1px solid var(--liq-border-soft)',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14.5, color: 'var(--liq-fg-muted)',
        }}>Not yet</button>
        <button onClick={onYes} style={{
          marginTop: 10, width: '100%', height: 56, borderRadius: 18, border: 'none', cursor: 'pointer',
          background: 'var(--su-grad-sunset)', color: '#fff', fontFamily: 'var(--liq-font-sans)',
          fontWeight: 700, fontSize: 16.5, boxShadow: 'var(--liq-shadow-cta)',
        }}>{locError ? 'Try again' : "Yes, I've arrived"}</button>
      </div>
    </ModalShell>
  );
}

// Check-in confirmation pop-up — Close reveals the End Date bar.
// Undo check-in (6 Nov 2026): only here, right after Yes. Deletes the stored
// location + timestamp, returns to the Date screen with the Check-in bar.
// The match is notified on Close, so an undo never reaches them.
function CheckInDoneSheet({ name = 'Leonie', onClose, onUndo }) {
  return (
    <ModalShell onCancel={onClose}>
      <div style={{ padding: '14px 24px 24px', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
        <div style={{
          width: 80, height: 80, borderRadius: 9999, margin: '8px 0 0',
          background: 'rgba(129,42,236,0.12)', color: 'var(--liq-primary-500)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon name="heart-filled" size={34} stroke={2.2}/>
        </div>
        <h3 style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26, letterSpacing: '-0.01em',
          margin: '16px 0 8px', color: 'var(--liq-fg)', lineHeight: 1.15, maxWidth: 300, textWrap: 'pretty',
        }}>You are amazing for showing up!</h3>
        <p style={{ fontSize: 15, color: 'var(--liq-fg-muted)', margin: '0 0 22px', lineHeight: 1.5, maxWidth: 300, textWrap: 'pretty' }}>
          We will let {name} know that you have arrived. Enjoy your date.
        </p>
        <button onClick={onClose} style={{
          width: '100%', height: 56, borderRadius: 18, border: 'none', cursor: 'pointer',
          background: 'var(--su-grad-sunset)', color: '#fff', fontFamily: 'var(--liq-font-sans)',
          fontWeight: 700, fontSize: 16.5, boxShadow: 'var(--liq-shadow-cta)',
        }}>Close</button>
        <button onClick={onUndo} style={{
          marginTop: 6, minHeight: 44, padding: '0 16px', background: 'transparent', border: 'none', cursor: 'pointer',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 14, color: 'var(--liq-fg-muted)',
        }}>Undo check-in</button>
      </div>
    </ModalShell>
  );
}

// ─────────────────────────────────────────────────────────────
// "I arrived at the spot" gate (2 Nov 2026). Until the user has checked in,
// the Arrived chip runs the check-in flow (guard → done) instead of opening
// the chat. Once checked in, it opens the chat with the prefill as before.
// Returns { gate(key, proceed), sheets } — render sheets inside the Phone.
// ─────────────────────────────────────────────────────────────
function useArrivedGate(name, initialCheckedIn = false, initialPopup = null) {
  const [checkedIn, setCheckedIn] = useChatState(initialCheckedIn);
  const [popup, setPopup] = useChatState(initialPopup);
  const [undone, setUndone] = useChatState(false); // Undo → back on the Date screen, Check-in bar again
  function gate(key, proceed) {
    if (key === 'arrived' && !checkedIn) { setPopup('confirm'); return; }
    proceed(key);
  }
  const sheets = (
    <>
      {popup === 'confirm' && <CheckInConfirmSheet name={name} onYes={() => setPopup('done')} onNotYet={() => setPopup(null)}/>}
      {popup === 'done' && <CheckInDoneSheet name={name} onClose={() => { setPopup(null); setCheckedIn(true); }} onUndo={() => { setPopup(null); setUndone(true); }}/>}
    </>
  );
  return { gate, sheets, checkedIn, undone };
}

// ─────────────────────────────────────────────────────────────
// The Coordinate module — the single surface that replaces 06's five
// dedicated interaction buttons (suggest time / suggest place / on my way /
// running late / can't find you). It sits on the date-confirmation screen
// where "LET LEONIE KNOW" used to be. Two states:
//
//   locked (open=false)     — the ANNOUNCEMENT. It's not live yet; the card
//                             explains what it's for and counts down to the
//                             moment it unlocks (1.5 h before you meet).
//   available (open=true)   — live. A short reason teaser + a primary CTA
//                             that opens the coordination thread.
//
// Deliberately NOT called "chat" anywhere in the copy — it's framed as
// coordinating the meet-up.
// ─────────────────────────────────────────────────────────────
// earlyChange (DECIDED 3 Nov 2026 · default on 4 Nov): Change meet time / Change location are
// available from the match on, before free chat opens. Locked card gains the
// two picker buttons; a change request while locked swaps Open chat for
// Suggest another (no chat yet).
function CoordinateModule({ open = false, name = 'Leonie', opensAt = '17:30', countdown = '3h 05m', onOpen, onAgree, onSuggest, onChangeTime, onChangePlace, unread = null, earlyChange = true }) {
  // ── Live + a message from the match is waiting, unread ──
  // The card stops being a passive "open chat" affordance and becomes the
  // signal itself: it adopts the reason's tone, surfaces WHO + WHY + a
  // one-line preview, and the CTA switches to a reply prompt. This is the
  // in-app counterpart to the push notification — the user should be able to
  // grasp the reason for the interruption without opening the thread.
  if (unread && (open || (earlyChange && unread.request))) {
    // unread.arrival — the match checked in. Same slot and treatment as a new
    // message, so the in-app signal is one pattern; no reply needed.
    const arrival = !!unread.arrival;
    // unread.request — the match suggested a new time or place (3 Nov 2026).
    // Same slot, outranks an ordinary message because it has a deadline.
    const req = unread.request || null;
    const r = reasonFor(arrival ? 'arrived' : req ? req.kind : unread.reason);
    // One look for every "needs a reaction" card: always orange, whatever the
    // reason (3 Nov 2026) — users learn orange = chat, react. The reason badge
    // inside keeps its own tone.
    const t = REASON_TONES.orange;
    const count = unread.count || 1;
    return (
      <Card style={{ padding: 0, overflow: 'hidden', background: '#fff', border: `1.5px solid ${t.chipBorder}`, boxShadow: 'var(--liq-shadow-md)' }}>
        {/* Reason-tinted accent rail so the whole card reads in the message's tone */}
        <div style={{ height: 4, background: t.fg }}/>
        <div style={{ padding: 16 }}>
          {/* Who + when — avatar carries a live, pulsing unread dot */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ position: 'relative', flex: 'none' }}>
              <Avatar name={name} size={46}/>
              <span style={{ position: 'absolute', top: -3, right: -3, width: 18, height: 18, borderRadius: 9999, background: t.fg, border: '2.5px solid #fff', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: 10.5, fontWeight: 800, fontFamily: 'var(--liq-font-sans)', lineHeight: 1, zIndex: 1 }}>{count}</span>
              <span className="su-pulse-ring" style={{ position: 'absolute', top: -3, right: -3, width: 18, height: 18, borderRadius: 9999, background: t.fg }}/>
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 19, color: 'var(--liq-fg)', lineHeight: 1.1 }}>{name}</span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, padding: '2px 8px', borderRadius: 9999, background: t.bg, color: t.fg, fontSize: 10, fontWeight: 800, letterSpacing: 0.05, textTransform: 'uppercase', whiteSpace: 'nowrap' }}>
                  <span style={{ width: 5, height: 5, borderRadius: 9999, background: t.fg }}/>{arrival ? 'Arrived' : req ? 'Change request' : count === 1 ? 'New message' : `${count} new`}
                </span>
              </div>
              <div style={{ fontSize: 12.5, color: 'var(--liq-fg-muted)', marginTop: 2 }}>{arrival ? 'checked in' : req ? 'needs your answer' : 'needs a response'} · {unread.time}</div>
            </div>
          </div>

          {/* One-line preview — reason badge tells you WHY at a glance, the
              snippet gives just enough context to decide how urgently to open. */}
          <div style={{ marginTop: 13, padding: '11px 13px', borderRadius: 14, background: t.bg, display: 'flex', flexDirection: 'column', gap: 7 }}>
            {arrival ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 9, fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 600, lineHeight: 1.4, color: 'var(--liq-fg)', textWrap: 'pretty' }}>
                <Icon name="map-pin" size={17} stroke={2.2} style={{ color: t.fg, flex: 'none' }}/>
                <span>{name} has arrived at the meeting location.</span>
              </div>
            ) : req ? (<>
            <div style={{ alignSelf: 'flex-start' }}>
              <ReasonBadge reasonKey={req.kind} onLight/>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <span style={{ fontSize: 13, color: 'var(--liq-fg-muted)' }}>{name} suggested</span>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap' }}>
                <span style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 22, color: 'var(--liq-fg)', lineHeight: 1.15 }}>{req.kind === 'time' ? req.value : req.value.name}</span>
                <span style={{ fontSize: 13, color: 'var(--liq-fg-subtle)', textDecoration: 'line-through' }}>{req.kind === 'time' ? req.was : req.was.name}</span>
              </div>
              {req.kind === 'place' && <span style={{ fontSize: 12.5, color: 'var(--liq-fg-muted)' }}>{req.value.type} · {req.value.address}</span>}
            </div>
            </>) : (<>
            <div style={{ alignSelf: 'flex-start' }}>
              <ReasonBadge reasonKey={unread.reason} onLight/>
            </div>
            <div style={{ fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 500, lineHeight: 1.4, color: 'var(--liq-fg)', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', textWrap: 'pretty' }}>
              “{unread.text}”
            </div>
            </>)}
          </div>

          {req && (
            <div style={{ marginTop: 10, display: 'flex', alignItems: 'flex-start', gap: 9, padding: '9px 11px', borderRadius: 12, background: 'rgba(245,158,11,0.09)' }}>
              <Icon name="clock" size={15} stroke={2.4} style={{ color: '#b45309', flex: 'none', marginTop: 1 }}/>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
                <span style={{ fontSize: 13.5, fontWeight: 700, color: '#78350f' }}>Answer by {req.deadline}</span>
                <span style={{ fontSize: 12.5, fontWeight: 500, color: '#92400e', lineHeight: 1.4, textWrap: 'pretty' }}>Until you agree, <strong style={{ fontWeight: 700, color: '#78350f' }}>{req.bindTime} at {req.bindPlace} stays binding.</strong> Not being there counts as a no-show.</span>
              </div>
            </div>
          )}

          {req ? (
            // Answer on the card (3 Nov 2026): I agree here, Suggest another in the
            // chat. Guard = order — the binding action sits second, so a quick tap
            // on the first button never agrees. Agreeing posts the same system line
            // in the thread ("Meet time is now 19:30").
            <div style={{ marginTop: 14, display: 'flex', gap: 8 }}>
              <button onClick={open ? onOpen : onSuggest} style={{
                flex: 1, height: 48, borderRadius: 9999, cursor: 'pointer', background: '#fff', color: t.fg, border: `1.5px solid ${t.chipBorder}`,
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 7,
                fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15,
              }}>
                {open ? <><Icon name="message-circle" size={17} stroke={2.2}/> Open chat</> : <><Icon name="refresh" size={17} stroke={2.2}/> Suggest another</>}
              </button>
              <button onClick={onAgree} style={{
                flex: 1, height: 48, borderRadius: 9999, cursor: 'pointer', border: 'none', background: t.fg, color: '#fff', boxShadow: 'var(--liq-shadow-cta)',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 7,
                fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15,
              }}>
                <Icon name="check" size={17} stroke={2.6}/> I agree
              </button>
            </div>
          ) : (
          <button onClick={onOpen} style={{
            marginTop: 14, width: '100%', height: 48, borderRadius: 9999,
            background: t.fg, color: '#fff', boxShadow: 'var(--liq-shadow-cta)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
            fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15,
          }}>
            <Icon name="message-circle" size={18} stroke={2.2}/> {arrival ? 'Open chat' : <>Read &amp; reply</>}
          </button>
          )}
          {req && !open && (
            <div style={{ marginTop: 10, textAlign: 'center', fontSize: 12.5, color: 'var(--liq-fg-muted)' }}>Messaging opens at {opensAt}</div>
          )}
        </div>
      </Card>
    );
  }

  if (!open) {
    // ── Announcement / locked ──
    return (
      <Card style={{ padding: 18, background: '#fff', border: '1.5px solid var(--liq-lavender-100)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div style={{
            width: 48, height: 48, borderRadius: 14, flex: 'none',
            background: 'rgba(129,42,236,0.10)', color: 'var(--liq-primary-500)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Icon name="message-circle" size={22} stroke={2.1}/>
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 20, lineHeight: 1.1, color: 'var(--liq-fg)' }}>Coordinate with {name}</div>
            <div style={{ fontSize: 14, color: 'var(--liq-fg-muted)', lineHeight: 1.35, marginTop: 4, textWrap: 'pretty' }}>
              Sort last-minute details before you meet
            </div>
          </div>
        </div>
        {/* Progress bar removed 2 Nov 2026 — the time alone says it. */}
        <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--liq-primary-500)', marginTop: 14 }}>
          Messaging available from {opensAt}
        </div>
        {earlyChange && (
          <div style={{ fontSize: 13.5, color: 'var(--liq-fg-muted)', marginTop: 4, lineHeight: 1.4, textWrap: 'pretty' }}>
            Request to change the location or meet time.
          </div>
        )}
        {earlyChange && (
          // Side-by-side tiles in the Date screen's own quick-action style
          // (Directions · Share Date · Calendar) — same radius, border, icon colour.
          // Labels match the chat chips: same action before and after unlock.
          <div style={{ marginTop: 14, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
            {[['clock', 'Change meet time', onChangeTime], ['map-pin', 'Change location', onChangePlace]].map(([ic, lb, fn]) => (
              <button key={lb} onClick={fn} style={{
                padding: '14px 8px', borderRadius: 16, background: '#fff', cursor: 'pointer',
                border: '1px solid var(--liq-border-soft)',
                display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6,
                fontFamily: 'var(--liq-font-sans)', fontSize: 13, fontWeight: 600, color: 'var(--liq-fg)',
              }}>
                <Icon name={ic} size={18} stroke={2} style={{ color: 'var(--liq-primary-500)' }}/>{lb}
              </button>
            ))}
          </div>
        )}
      </Card>
    );
  }

  // ── Available / live ──
  return (
    <Card style={{ padding: 18, background: '#fff', border: '1.5px solid var(--liq-lavender-100)' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14 }}>
        <div style={{
          width: 48, height: 48, borderRadius: 14, flex: 'none',
          background: 'var(--liq-primary-500)', color: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon name="message-circle" size={22} stroke={2.1}/>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
            <span style={{ width: 9, height: 9, borderRadius: 9999, background: 'var(--liq-success)', flex: 'none' }}/>
            <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 20, lineHeight: 1.1, color: 'var(--liq-fg)' }}>Coordinate with {name}</div>
          </div>
          <div style={{ fontSize: 14, color: 'var(--liq-fg-muted)', lineHeight: 1.35, marginTop: 5, textWrap: 'pretty' }}>
            Running late / Can't find your date at the spot? Tell {name} here!
          </div>
        </div>
      </div>

      <button onClick={onOpen} style={{
        marginTop: 16, width: '100%', height: 48, borderRadius: 9999,
        background: 'var(--liq-primary-500)', color: '#fff', boxShadow: 'var(--liq-shadow-violet)',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15,
      }}>
        <Icon name="message-circle" size={18} stroke={2.2}/> Open chat
      </button>
    </Card>
  );
}

Object.assign(window, { CheckInBar, CheckInConfirmSheet, CheckInDoneSheet, useArrivedGate, CoordinateModule });
