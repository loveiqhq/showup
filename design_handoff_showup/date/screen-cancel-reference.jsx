// ═════════════════════════════════════════════════════════════
// Date 07 — Cancel this date · REFERENCE IMPLEMENTATION
// Extracted from ui_kits/show-up/screens-date.jsx and screens-extras.jsx
// (16 Nov 2026). The kit is the source of truth; this file is regenerated
// from it, never edited by hand.
//
// What this ticket builds (spec sheet frames A–H):
//   A the red link          — last item of the Date screen scroll, shown until THIS user checks in
//                             (ScreenMatchCoord, screens-date-chat.jsx: {!checkedIn && <button>Cancel this date</button>})
//   B CancelDateConfirmSheet — the penalty guard · chat locked
//   C … chatOpen            — + "Message {name} instead" (chat open, from meet − 1:30)
//   D … initialSent         — after a change request from the tiles (Date 05 pickers)
//   I … changeClosed        — Date 05's cutoff (meet − 0:30 / either check-in): no body line, no tiles, only Message {name} instead
//   E … error               — cancel failed: EndErrorBox text="Couldn't cancel the date…" above the choices, Cancel anyway retries
//   H ScreenPartnerCancelled — default variant: what the match sees (rate untouched, no review, → Search)
//
// Kit frame props:
//   A ScreenMatchCoord coordOpen checkInFlow (scrolled to the end) · F ScreenMatchCoord coordOpen (checked in → no link)
//   B ScreenCancelConfirm · C … chatOpen · D … initialSent={{kind:'time',value:'20:00'}} · E … error · I … changeClosed
//   G ScreenRatingYouNoShow (flow 10, consumed) · H ScreenPartnerCancelled
//
// The −10 in the cost box is the drop prop, fed from the shared rate config — never a literal.
// CancelDoneSheet ("Your date is cancelled", 94 → 84, Back to search) is RETIRED — not built.
//
// Consumed, not built here:
//   ScreenMatchCoord, the scroll, footer and TabBar → Date 01
//   ScreenPartnerCancelled viaChat variant, EndErrorBox → Date 02 / Date 06
//   TimeChangeSheet, PlaceChangeSheet → Date 05
//   ScreenRatingYouNoShow → flow 10
//   Phone · Icon · Avatar · Button · ModalShell · PortraitPlaceholder · Atmosphere → design_handoff_showup/components/shared.jsx
//
// Read values from here, not from the PNG.
// ═════════════════════════════════════════════════════════════

const { useState: useStateDate } = React;

// ─────────────────────────────────────────────────────────────
// CancelDateConfirmSheet — the mis-tap guard for cancelling a *confirmed*
// upcoming date. Unlike EndDateConfirmSheet (ending an in-progress date,
// no penalty), cancelling a binding date carries a real cost: the
// Show-up Rate drops by 10. So this sheet makes the penalty unmissable —
// a blush warning tone, the before→after rate shown numerically — and
// defaults the prominent CTA to "Don't cancel", with the destructive
// "Confirm cancellation" kept as a quiet, deliberate outline.
// ─────────────────────────────────────────────────────────────
// chatOpen: inside meet − 1:30 the chat is open → 'Message {name} instead' is shown too.
// Change meet time / location open the same pickers as the Coordinate card (4 Nov 2026).
function CancelDateConfirmSheet({ name = 'Leonie', place = 'Café Krone', when = 'Tonight · 19:00', from = 94, drop = 10, onKeep, onConfirm, onMessage, review = false, chatOpen = false, error = false, initialSent = null, changeClosed = false }) {
  // changeClosed: Date 05's cutoff — from binding meet − 0:30 (or once either person has
  // checked in) a new time or place could never be agreed. The tiles and the body line go;
  // only Message {name} instead stays (the chat is always open by then). 16 Nov 2026, Date 07.
  const showMessage = chatOpen || changeClosed;
  // error: the cancel request failed — the sheet stays, the box sits above the choices,
  // "Cancel anyway" retries. Not cancelled until the server confirms (16 Nov 2026, Date 07).
  const to = from - drop;
  const talk = onMessage || onKeep;
  const [picker, setPicker] = useStateDate(null);
  const [sent, setSent] = useStateDate(initialSent);
  const sentLabel = sent ? (sent.kind === 'time' ? sent.value : (sent.value && sent.value.name) || '') : '';
  const reasons = [
    { icon: 'smile', text: <>Almost every date leaves you <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>glad you went</strong>. The hard part is only the front door.</> },
    { icon: 'heart', text: <>She chose <em>you</em> out of everyone. That's rare — showing up honours it.</> },
    { icon: 'star',  text: <>You'll go to sleep <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>proud you stayed real</strong> and kept your word.</> },
  ];
  const body = (
      <div style={{ padding: '10px 0 26px', display: 'flex', flexDirection: 'column' }}>

        {/* Her, made real — a face, not a line item */}
        <div style={{ position: 'relative', margin: '6px 20px 0', borderRadius: 24, overflow: 'hidden' }}>
          <PortraitPlaceholder name={name} height={244} radius={24} label={false} photoIndex={0}
            objectPosition="center 22%"/>
          <div style={{
            position: 'absolute', inset: 0, borderRadius: 24, pointerEvents: 'none',
            background: 'linear-gradient(180deg, rgba(29,17,41,0) 38%, rgba(29,17,41,0.78) 100%)',
          }}/>
          <div style={{ position: 'absolute', left: 18, right: 18, bottom: 16 }}>
            <div style={{
              display: 'inline-flex', alignItems: 'center', gap: 6, marginBottom: 8,
              padding: '5px 11px', borderRadius: 999, background: 'rgba(255,255,255,0.16)',
              backdropFilter: 'blur(6px)', border: '1px solid rgba(255,255,255,0.28)',
            }}>
              <Icon name="calendar" size={13} stroke={2.4} style={{ color: '#fff' }}/>
              <span style={{ fontSize: 11.5, fontWeight: 700, color: '#fff', letterSpacing: '-0.01em' }}>{when} · {place}</span>
            </div>
            <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 24, color: '#fff', letterSpacing: '-0.01em', lineHeight: 1 }}>
              {name} is counting on you.
            </div>
          </div>
        </div>

        {/* The gut-punch */}
        <div style={{ padding: '20px 24px 0', textAlign: 'center' }}>
          <h3 style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 27, letterSpacing: '-0.01em',
            margin: '0 0 10px', color: 'var(--liq-fg)', lineHeight: 1.14,
          }}>Are you really ready to <em>disappoint her?</em></h3>
          <p style={{ fontSize: 15, color: 'var(--liq-fg-muted)', margin: '0 auto', lineHeight: 1.55, maxWidth: 330 }}>
            This isn't a button — it's a real person who cleared her evening, picked something to wear, and is quietly looking forward to meeting <em>you</em>. Cancelling lands on her side of the screen.
          </p>
        </div>

        {/* Reasons to get your ass up and go */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '20px 24px 0' }}>
          {reasons.map((r, i) => (
            <div key={i} style={{
              display: 'flex', alignItems: 'flex-start', gap: 13, padding: '13px 15px',
              borderRadius: 16, background: 'var(--su-grad-lilac)', border: '1px solid var(--liq-border-soft)',
            }}>
              <div style={{
                flex: 'none', width: 34, height: 34, borderRadius: 11, marginTop: 1,
                background: 'rgba(129,42,236,0.10)', display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon name={r.icon} size={18} stroke={2.2} style={{ color: 'var(--liq-primary-500)' }}/>
              </div>
              <p style={{ margin: 0, fontSize: 13.5, lineHeight: 1.5, color: 'var(--liq-fg-muted)', textAlign: 'left' }}>{r.text}</p>
            </div>
          ))}
        </div>

        {/* Talk to her, don't vanish */}
        <div style={{ padding: '18px 24px 0' }}>
          <div style={{
            padding: '16px 16px 15px', borderRadius: 18,
            background: 'var(--su-grad-blush)', border: '1px solid rgba(254,104,57,0.18)',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 9, marginBottom: 6 }}>
              <Icon name="message-circle" size={17} stroke={2.3} style={{ color: 'var(--liq-orange-500)' }}/>
              <span style={{ fontSize: 13.5, fontWeight: 800, color: 'var(--liq-fg)', letterSpacing: '-0.01em' }}>Sometimes life happens — find a solution with {name}!</span>
            </div>
            {!changeClosed && <p style={{ margin: '0 0 13px', fontSize: 13, lineHeight: 1.5, color: 'var(--liq-fg-muted)', textWrap: 'pretty' }}>
              Would a later time or another place work? Request it — if {name} agrees, your date goes ahead.
            </p>}
            {changeClosed ? null : sent ? (
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: 9, padding: '12px 13px', borderRadius: 14, background: '#fff', border: '1px solid var(--liq-border-soft)' }}>
                <Icon name="check" size={16} stroke={2.6} style={{ color: 'var(--liq-success-fg)', flex: 'none', marginTop: 1 }}/>
                <span style={{ fontSize: 13, lineHeight: 1.45, color: 'var(--liq-fg)', textWrap: 'pretty' }}>
                  <strong style={{ fontWeight: 700 }}>{sentLabel}</strong> requested. Waiting for {name} to agree — until then your date stays as planned.
                </span>
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                {[['clock', 'Change meet time', 'time'], ['map-pin', 'Change location', 'place']].map(([ic, lb, k]) => (
                  <button key={k} onClick={() => setPicker(k)} style={{
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
            {showMessage && (
              <button onClick={talk} style={{
                marginTop: changeClosed ? 4 : 8, width: '100%', height: 48, borderRadius: 14, cursor: 'pointer',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
                background: '#fff', border: '1px solid rgba(254,104,57,0.35)', color: 'var(--liq-orange-500)',
                fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15,
              }}>
                <Icon name="message-circle" size={17} stroke={2.4}/>
                Message {name} instead
              </button>
            )}
          </div>
        </div>

        {/* The cost, kept honest but quiet */}
        <div style={{ padding: '16px 24px 0' }}>
          <div style={{
            display: 'flex', alignItems: 'flex-start', gap: 11,
            padding: '14px 16px', borderRadius: 14,
            background: 'rgba(220,38,38,0.10)', border: '1px solid rgba(220,38,38,0.32)',
          }}>
            <Icon name="shield-alert" size={17} stroke={2.3} style={{ color: '#dc2626', flexShrink: 0, marginTop: 1 }}/>
            {/* 16 Nov 2026: the drop is stated as 10 (placeholder value until the Show-up Rate screens). */}
            <span style={{ fontSize: 12.5, color: 'var(--liq-fg)', lineHeight: 1.5 }}>
              Cancelling this date counts as a <strong style={{ color: '#dc2626', fontWeight: 800 }}>no-show</strong> and lowers your <strong style={{ fontWeight: 700 }}>Show-up Rate by {drop}</strong> — a lower rate means fewer likes and matches. Let it stay low and you'll eventually lose access to the app entirely.
            </span>
          </div>
        </div>

        {/* Choices */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '20px 24px 0' }}>
          {error && window.EndErrorBox && <window.EndErrorBox text="Couldn't cancel the date. Check your connection and try again."/>}
          <button onClick={onKeep} style={{
            width: '100%', height: 54, borderRadius: 16, border: 'none', cursor: 'pointer',
            background: 'var(--su-grad-sunset)', color: '#fff',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 16,
            boxShadow: 'var(--liq-shadow-violet)',
          }}>Don't cancel my date</button>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <button onClick={onConfirm} style={{
              width: '100%', height: 30, borderRadius: 16, cursor: 'pointer',
              background: 'transparent', border: 'none', color: '#dc2626',
              fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 14,
              textDecoration: 'underline', textUnderlineOffset: 3,
            }}>Cancel anyway and let her down</button>
            <div style={{ padding: '0 8px', textAlign: 'center' }}>
              <span style={{ fontSize: 12, fontWeight: 700, color: '#dc2626', textAlign: 'center', lineHeight: 1.4, letterSpacing: '-0.01em', textWrap: 'pretty' }}>
                This can't be undone — your date is off and you'll never match {name} again.
              </span>
            </div>
          </div>
        </div>
      </div>
  );
  const pickerEl = picker === 'time'
    ? <TimeChangeSheet name={name} onClose={() => setPicker(null)} onSend={(v) => { setSent({ kind: 'time', value: v }); setPicker(null); }}/>
    : picker === 'place'
    ? <PlaceChangeSheet name={name} onClose={() => setPicker(null)} onSend={(v) => { setSent({ kind: 'place', value: v }); setPicker(null); }}/>
    : null;
  // review = render the sheet at its full natural height for canvas review
  // (no bottom-sheet height cap that would clip the taller content).
  if (review) {
    return (
      <div style={{
        width: '100%', background: 'var(--liq-bg)', borderRadius: '28px 28px 0 0',
        boxShadow: '0 -8px 32px rgba(46,1,71,0.18)', overflow: 'hidden',
      }}>
        <div style={{ display: 'flex', justifyContent: 'center', padding: '8px 0 0' }}>
          <div style={{ width: 36, height: 4, borderRadius: 999, background: 'var(--liq-fg-faint)' }}/>
        </div>
        {body}
        {pickerEl}
      </div>
    );
  }
  return <><ModalShell onCancel={onKeep}>{body}</ModalShell>{pickerEl}</>;
}

// ─────────────────────────────────────────────────────────────
// ScreenCancelConfirm — a DEDICATED artboard that shows the cancel-
// confirmation sheet on its own, so it's reviewable on the canvas
// without having to trigger it from the date screen. A faint, blurred
// date-context skeleton sits behind the sheet to give it a real surface,
// but the sheet itself is the subject of the frame.
// ─────────────────────────────────────────────────────────────
function ScreenCancelConfirm({ name = 'Leonie', from = 94, drop = 10, chatOpen = false, error = false, initialSent = null, changeClosed = false }) {
  // Confirming the cancellation counts as a no-show → jump straight into the
  // "YOU didn't show up" review flow (focus that artboard on the canvas).
  const dc = window.DCCtx ? React.useContext(window.DCCtx) : null;
  const goNoShowReview = () => dc && dc.setFocus('rating/rating-you-noshow-full');
  return (
    <div className="su-app" style={{
      width: 390, minHeight: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)', display: 'flex', flexDirection: 'column',
    }}>
      <StatusBar time="4:20"/>
      {/* Slim blurred date-context strip so the sheet has a real surface,
          then the full sheet flows below at its natural height (reviewable). */}
      <div style={{ padding: '6px 18px 10px', filter: 'blur(2px)', opacity: 0.85 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 0 4px' }}>
          <div style={{ width: 46, height: 46, borderRadius: 14, background: 'var(--liq-bg-raised)' }}/>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <div style={{ width: 150, height: 12, borderRadius: 999, background: 'var(--liq-bg-raised)' }}/>
            <div style={{ width: 96, height: 10, borderRadius: 999, background: 'var(--liq-border-soft)' }}/>
          </div>
        </div>
      </div>
      <CancelDateConfirmSheet name={name} from={from} drop={drop} review chatOpen={chatOpen} error={error} initialSent={initialSent} changeClosed={changeClosed} onKeep={() => {}} onConfirm={goNoShowReview}/>
      <HomeIndicator/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Partner cancelled on YOU — the receiving side.
//
// Scenario: the match tapped "Cancel this date" from the It's-a-date
// confirmation screen (07). The user is confronted with it and needs a
// screen that (a) is empathetic about the cancellation, (b) reassures
// them their own Show-up Rate is untouched, (c) states plainly that the
// canceller takes the penalty (rate drop, no re-match — no search pause in MVP, 4 Nov 2026), and
// (d) points them straight back to search to find someone reliable.
//
// The mirror of ScreenLateCancelled: same anatomy (hero → rows → CTA),
// but the tone flips from "this one has a cost" (blush) to "this one's
// not on you" (reassuring lilac), and the CTA is forward-looking —
// straight back into the date search.
// ─────────────────────────────────────────────────────────────
function ScreenPartnerCancelled({ name = 'Leonie', viaChat = false, endedByYou = false }) {
  const rows = viaChat ? [
    {
      i: 'shield-check', accent: '#0A7A47', tint: 'rgba(0,171,85,0.12)',
      t: endedByYou
        ? <>You told {name} yourself, so your <strong style={{ color: 'var(--liq-fg)' }}>Show-up Rate stays untouched.</strong> There's no review, as the date didn't happen.</>
        : <>Your <strong style={{ color: 'var(--liq-fg)' }}>Show-up Rate stays untouched.</strong> There's no review, as the date didn't happen.</>,
    },
  ] : [
    {
      i: 'shield-check', accent: '#0A7A47', tint: 'rgba(0,171,85,0.12)',
      t: <>Your <strong style={{ color: 'var(--liq-fg)' }}>Show-up Rate stays untouched.</strong> A date someone else cancels will <strong style={{ color: 'var(--liq-fg)' }}>never</strong> count against you. You did everything right.</>,
    },
    {
      i: 'heart', accent: 'var(--liq-primary-500)', tint: 'var(--liq-bg-raised)',
      t: <>Show Up is built on <strong style={{ color: 'var(--liq-fg)' }}>reliability</strong> and <strong style={{ color: 'var(--liq-fg)' }}>treasuring each other's time</strong> — real, warm, in-person connection. <strong style={{ color: 'var(--liq-fg)' }}>This isn't how our community behaves.</strong></>,
    },
    {
      i: 'user-x', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <>People who <strong style={{ color: 'var(--liq-fg)' }}>repeatedly cancel or don't show up</strong> become less visible, and eventually <strong style={{ color: 'var(--liq-fg)' }}>lose access.</strong> {name} carries that — not you.</>,
    },
  ];

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)', display: 'flex', flexDirection: 'column',
    }}>
      <Atmosphere variant="hero"/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', zIndex: 1, overflow: 'hidden',
        padding: '4px 20px 0', display: 'flex', flexDirection: 'column',
      }}>
        {/* Hero card — reassuring lilac (vs. the blush "this one has a
            cost" late-cancel screen). A gentle, caring hand-on-shoulder
            moment: name who backed out, then hold space for the user. */}
        <div style={{
          padding: '26px 24px 24px', borderRadius: 28,
          background: 'var(--su-grad-lilac)', textAlign: 'center',
        }}>
          <div style={{ position: 'relative', width: 72, height: 72, margin: '0 auto 16px' }}>
            <Avatar name={name} size={72} photoIndex={0}/>
            <div style={{
              position: 'absolute', right: -3, bottom: -3,
              width: 28, height: 28, borderRadius: 9999,
              background: 'var(--liq-orange-500)', color: '#fff',
              border: '3px solid #fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: 'var(--liq-shadow-md)',
            }}><Icon name="x" size={15} stroke={2.6}/></div>
          </div>

          <h2 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26,
            color: 'var(--liq-fg)', margin: '0 0 12px', lineHeight: 1.15,
            letterSpacing: '-0.01em', textWrap: 'balance',
          }}>{endedByYou ? <>You <em>ended</em> the date</> : viaChat ? <>{name} had to <em>cancel</em> the date</> : <>{name} had to <em>cancel</em> — we're truly sorry.</>}</h2>

          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.55, textWrap: 'pretty' }}>
            {endedByYou ? <>We hope you and {name} found another day to meet. <strong style={{ color: 'var(--liq-fg)' }}>Search is open again</strong> — go and find your next date.</> : viaChat ? <>We hope you two found another day to meet. <strong style={{ color: 'var(--liq-fg)' }}>Search is open again</strong> — go and find your next date.</> : <>You made room in your day and you were ready to show up. <strong style={{ color: 'var(--liq-fg)' }}>Don't be discouraged - someone true as you is just a like away from meeting you.</strong></>}
          </p>
        </div>

        <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 9 }}>
          {rows.map((row, i) => (
            <div key={i} style={{
              display: 'flex', gap: 12, padding: 13, borderRadius: 14,
              background: '#fff', border: '1px solid var(--liq-border-soft)',
            }}>
              <div style={{
                width: 34, height: 34, borderRadius: 11, flex: 'none',
                background: row.tint, color: row.accent,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}><Icon name={row.i} size={17} stroke={2}/></div>
              <div style={{ fontSize: 13, color: 'var(--liq-fg)', lineHeight: 1.42, alignSelf: 'center' }}>{row.t}</div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1, minHeight: 12 }}/>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, paddingBottom: 6 }}>
          <Button variant="sunset" size="lg" fullWidth leading={<Icon name="search" size={18} stroke={2.4}/>}>{viaChat ? 'Find your next date' : 'Find someone who shows up'}</Button>
        </div>
      </div>

      <HomeIndicator/>
    </div>
  );
}

Object.assign(window, { CancelDateConfirmSheet, ScreenCancelConfirm, ScreenPartnerCancelled });
