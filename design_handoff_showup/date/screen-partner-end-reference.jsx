// ═════════════════════════════════════════════════════════════
// Date 08 — What the other side sees · REFERENCE IMPLEMENTATION
// Extracted from ui_kits/show-up/screens-extras.jsx (17 Nov 2026). The kit is
// the source of truth; this file is regenerated from it, never edited by hand.
//
// What this ticket builds (spec sheet frames A–C) — one full screen each,
// shown to B the moment A's end is confirmed:
//   A ScreenNoShowClosed        — end_reason partner_no_show (B checked in or not) · A's claim, not a verdict
//   B ScreenDateEndedDiscomfort — end_reason uncomfortable (Head out now)
//   C ScreenDateClosed          — end_reason met
//
// Kit artboards: you-no-showed · date-ended-discomfort · date-closed-positive
// Props: name = the match's (A's) first name. Defaults are mock data.
//
// One exit on every screen: Proceed to review. No back, no close, no swipe.
// Next screen (flow 10, not built here): A → rating with claimedNoShow · B, C → rating.
//
// Consumed, not built here:
//   Atmosphere · StatusBar · HomeIndicator · Avatar · Button · Icon → design_handoff_showup/components/shared.jsx
//   ScreenRating (+ claimedNoShow) → flow 10
//
// Read values from here, not from the PNG.
// ═════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────
// You didn't show up — your date stopped waiting and closed it.
//
// Scenario: the USER (here "Leonie") was the one who didn't turn up, or
// was very late without a word. Her date waited past the grace window,
// tapped "I'm not waiting any longer" and ended the date. The date is
// already over; this is what Leonie sees the next time she opens the app
// (in-app only — push/SMS handled elsewhere).
//
// Tone: firm accountability, not cruel. Same blush "this one has a cost"
// anatomy as ScreenLateCancelled, but pointed the other way — it names
// the behaviour plainly (no-show / no notice = disrespect for each
// other's time, not accepted here), states the penalty, and then asks
// for information. The single forward CTA hands off into the 10 · Review
// your Date flow, where the details are captured.
//
// 17 Nov 2026 (Date 08): this is A's CLAIM, not a verdict. Shown to B on
// every no-show end — checked in or not. Rows 2–3 say so: B gives their
// side in the review (rating-claimed-noshow), then an automated check
// (check-ins, chat contact) decides. No rate change is stated here.
// ─────────────────────────────────────────────────────────────
function ScreenNoShowClosed({ name = 'Marco' }) {
  const rows = [
    {
      i: 'user-x', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <><strong style={{ color: 'var(--liq-fg)' }}>Not showing up</strong> to a date — or being <strong style={{ color: 'var(--liq-fg)' }}>very late</strong> without reaching out to your date — shows <strong style={{ color: 'var(--liq-fg)' }}>no respect for each other's time.</strong></>,
    },
    {
      i: 'message-circle', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <>To clear this up, <strong style={{ color: 'var(--liq-fg)' }}>proceed to the review</strong> and <strong style={{ color: 'var(--liq-fg)' }}>give us your side.</strong></>,
    },
    {
      i: 'shield-check', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <>Once you've completed the review, we run an <strong style={{ color: 'var(--liq-fg)' }}>automated check</strong>: did you both check in, and did you message each other? Your <strong style={{ color: 'var(--liq-fg)' }}>Show-up Rate only drops</strong> if the no-show is <strong style={{ color: 'var(--liq-fg)' }}>proven.</strong></>,
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
        padding: '8px 20px 0', display: 'flex', flexDirection: 'column',
      }}>
        {/* Hero card — warm blush ("this one has a cost"). The date's
            avatar with a cancel badge names who was left waiting. */}
        <div style={{
          padding: '30px 24px', borderRadius: 28,
          background: 'var(--su-grad-blush)', textAlign: 'center',
        }}>
          <div style={{ position: 'relative', width: 76, height: 76, margin: '0 auto 18px' }}>
            <Avatar name={name} size={76} photoIndex={2}/>
            <div style={{
              position: 'absolute', right: -3, bottom: -3,
              width: 30, height: 30, borderRadius: 9999,
              background: 'var(--liq-orange-500)', color: '#fff',
              border: '3px solid #fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: 'var(--liq-shadow-md)',
            }}><Icon name="clock" size={16} stroke={2.6}/></div>
          </div>

          <h2 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 27,
            color: 'var(--liq-fg)', margin: '0 0 12px', lineHeight: 1.15,
            letterSpacing: '-0.01em', textWrap: 'balance',
          }}>{name} ended the date, stating you <em>didn't show up.</em></h2>

          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.55, textWrap: 'pretty' }}>
            {name} <strong style={{ color: 'var(--liq-fg)' }}>ended the date</strong> after waiting for you.<br/>It's now closed.
          </p>
        </div>

        <div style={{ marginTop: 18, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {rows.map((row, i) => (
            <div key={i} style={{
              display: 'flex', gap: 12, padding: 14, borderRadius: 14,
              background: '#fff', border: '1px solid var(--liq-border-soft)',
            }}>
              <div style={{
                width: 36, height: 36, borderRadius: 12, flex: 'none',
                background: row.tint, color: row.accent,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}><Icon name={row.i} size={18} stroke={2}/></div>
              <div style={{ fontSize: 13.5, color: 'var(--liq-fg)', lineHeight: 1.45, alignSelf: 'center' }}>{row.t}</div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1, minHeight: 14 }}/>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, paddingBottom: 6 }}>
          <Button variant="sunset" size="lg" fullWidth>Proceed to review</Button>
        </div>
      </div>

      <HomeIndicator/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Your date ended it because they didn't feel comfortable / safe.
//
// Scenario: mid-date, the OTHER person tapped "End the date" and picked
// "I don't feel comfortable" or "I don't feel safe". The date is closed
// immediately. This is what the reported user sees the next time they
// open the app.
//
// Tone: serious and unambiguous about the standard — respect is not
// optional here — but explicitly not a verdict. We hold one side of the
// story, we say so, we ask for theirs, and we commit to reviewing both.
// The single forward CTA hands off into the report-detail flow.
//
// 17 Nov 2026 (Date 08): MVP has one exit reason — "I don't feel
// comfortable". "Unsafe" is out of MVP. Headline is gender-neutral.
// Proceed to review → the normal review (rating).
// ─────────────────────────────────────────────────────────────
function ScreenDateEndedDiscomfort({ name = 'Marco' }) {
  const rows = [
    {
      i: 'hand', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <><strong style={{ color: 'var(--liq-fg)' }}>Respect is non-negotiable</strong> at Show Up. Behaviour that leaves someone feeling <strong style={{ color: 'var(--liq-fg)' }}>uncomfortable</strong> is not accepted here.</>,
    },
    {
      i: 'message-circle', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <>So far we only have <strong style={{ color: 'var(--liq-fg)' }}>one side of the story.</strong> In the next step, tell us <strong style={{ color: 'var(--liq-fg)' }}>what happened from your point of view.</strong></>,
    },
    {
      i: 'shield-check', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <>We <strong style={{ color: 'var(--liq-fg)' }}>review every report</strong> and <strong style={{ color: 'var(--liq-fg)' }}>act on it where it's needed.</strong></>,
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
        padding: '8px 20px 0', display: 'flex', flexDirection: 'column',
      }}>
        {/* Hero — same blush "this one has a cost" anatomy as the other
            closed-date screens, badged with a raised hand rather than a
            clock: the date wasn't missed, it was stopped. */}
        <div style={{
          padding: '28px 22px', borderRadius: 28,
          background: 'var(--su-grad-blush)', textAlign: 'center',
        }}>
          <div style={{ position: 'relative', width: 76, height: 76, margin: '0 auto 16px' }}>
            <Avatar name={name} size={76} photoIndex={2}/>
            <div style={{
              position: 'absolute', right: -3, bottom: -3,
              width: 30, height: 30, borderRadius: 9999,
              background: 'var(--liq-orange-500)', color: '#fff',
              border: '3px solid #fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: 'var(--liq-shadow-md)',
            }}><Icon name="hand" size={15} stroke={2.6}/></div>
          </div>

          <h2 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 27,
            color: 'var(--liq-fg)', margin: '0 0 12px', lineHeight: 1.15,
            letterSpacing: '-0.01em', textWrap: 'balance',
          }}>{name} ended the date, saying they <em>didn't feel comfortable</em> in the situation.</h2>

          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.55, textWrap: 'pretty' }}>
            The date is now closed.
          </p>
        </div>

        <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {rows.map((row, i) => (
            <div key={i} style={{
              display: 'flex', gap: 12, padding: 14, borderRadius: 14,
              background: '#fff', border: '1px solid var(--liq-border-soft)',
            }}>
              <div style={{
                width: 36, height: 36, borderRadius: 12, flex: 'none',
                background: row.tint, color: row.accent,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}><Icon name={row.i} size={18} stroke={2}/></div>
              <div style={{ fontSize: 13.5, color: 'var(--liq-fg)', lineHeight: 1.45, alignSelf: 'center' }}>{row.t}</div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1, minHeight: 14 }}/>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, paddingBottom: 6 }}>
          <Button variant="sunset" size="lg" fullWidth>Proceed to review</Button>
        </div>
      </div>

      <HomeIndicator/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// The date ended normally — the warm counterpart to the two
// accountability screens above.
//
// Scenario: one of the two tapped "We met" (17 Nov 2026: ONLY this
// case — a no-show end always shows you-no-showed instead). Nobody was late, nobody was left waiting, nobody felt
// uncomfortable. Both people simply met, in real life, as promised.
//
// Shown ONLY to the person who did NOT end the date: it tells them
// their date has already been closed and it's time for the review.
// Lilac hero rather than blush: this one is a thank-you, not a
// consequence. Single forward CTA into the review, which is also the
// gate back into the date search.
// ─────────────────────────────────────────────────────────────
function ScreenDateClosed({ name = 'Leonie' }) {
  const rows = [
    {
      i: 'heart', accent: 'var(--liq-primary-500)', tint: 'rgba(129,42,236,0.12)',
      t: <>We hope it was a good one. <strong style={{ color: 'var(--liq-fg)' }}>Head into the review</strong> and tell us how it went — it takes a minute.</>,
    },
    {
      i: 'search', accent: 'var(--liq-primary-500)', tint: 'rgba(129,42,236,0.12)',
      t: <>Once the review is done, you're free to <strong style={{ color: 'var(--liq-fg)' }}>search for your next date.</strong></>,
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
        padding: '8px 20px 0', display: 'flex', flexDirection: 'column',
      }}>
        <div style={{
          padding: '30px 24px', borderRadius: 28,
          background: 'var(--su-grad-lilac, linear-gradient(180deg, #F3E8FF 0%, #E9D5FF 100%))',
          textAlign: 'center',
        }}>
          <div style={{ position: 'relative', width: 76, height: 76, margin: '0 auto 18px' }}>
            <div style={{
              width: 76, height: 76, borderRadius: 9999,
              background: 'rgba(255,255,255,0.72)',
              border: '1px solid rgba(129,42,236,0.16)',
              color: 'var(--liq-primary-500)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 2px 10px rgba(46,1,71,0.08)',
            }}><Icon name="heart-filled" size={34}/></div>
            <div style={{
              position: 'absolute', right: -3, bottom: -3,
              width: 30, height: 30, borderRadius: 9999,
              background: 'var(--liq-success-fg, #00AB55)', color: '#fff',
              border: '3px solid #fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: 'var(--liq-shadow-md)',
            }}><Icon name="check" size={15} stroke={3}/></div>
          </div>

          <h2 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 27,
            color: 'var(--liq-fg)', margin: '0 0 12px', lineHeight: 1.15,
            letterSpacing: '-0.01em', textWrap: 'balance',
          }}>{name} ended the date, stating you both have <em>met.</em></h2>

          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.55, textWrap: 'pretty' }}>
            Thank you for being real and showing up to your date.
          </p>
        </div>

        <div style={{ marginTop: 18, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {rows.map((row, i) => (
            <div key={i} style={{
              display: 'flex', gap: 12, padding: 14, borderRadius: 14,
              background: '#fff', border: '1px solid var(--liq-border-soft)',
            }}>
              <div style={{
                width: 36, height: 36, borderRadius: 12, flex: 'none',
                background: row.tint, color: row.accent,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}><Icon name={row.i} size={18} stroke={2}/></div>
              <div style={{ fontSize: 13.5, color: 'var(--liq-fg)', lineHeight: 1.45, alignSelf: 'center' }}>{row.t}</div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1, minHeight: 14 }}/>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, paddingBottom: 6 }}>
          <Button variant="sunset" size="lg" fullWidth>Proceed to review</Button>
        </div>
      </div>

      <HomeIndicator/>
    </div>
  );
}

Object.assign(window, { ScreenNoShowClosed, ScreenDateEndedDiscomfort, ScreenDateClosed });
