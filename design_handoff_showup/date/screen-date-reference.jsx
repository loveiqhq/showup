// screen-date-reference.jsx — Date 01 · the Date screen (07a Match & date confirmation).
//
// Extracted 2 Nov 2026 (re-extracted after the round-3 decisions) from ui_kits/show-up/ (screens-date-chat.jsx, screens-date.jsx,
// screens-date-change.jsx). The kit is the source of truth; re-extract, never edit here.
//
// WHAT MATTERS FOR DATE 01
//   ScreenMatchCoord      — the screen. Props for the sheet frames:
//                           A  coordOpen={false} checkInFlow
//                           B  coordOpen={true}  checkInFlow
//                           C  coordOpen={true}  checkInFlow coordUnread={{ reason, text, time, count }}
//                           D  coordOpen={false} tall checkInFlow   (full scroll)
//   CoordinateModule      — locked / available / unread (+ the arrival variant, Date 04)
//   CopyAddress           — the Copy pill in the time + place card
//   DateWorksCard         — "How the date works", rewritten: no timer
//   intentShared / rideAction props — intent line only when motivations match;
//                           Order Ride hidden (rideAction={false}) until a ride API exists
//
// SLOTS — rendered here, BUILT IN OTHER 07a TICKETS (included only so this file renders):
//   CheckInBar (Date 03) · DateStatusBar (Date 06) · ChangeTag + BindingNote (Date 05)
//   MatchMateTabsCard — PLACEHOLDER for the Match Mate slot; content is Date 10. Do not build it from here.
//   The sheets ScreenMatchCoord opens — CheckInConfirmSheet, CheckInDoneSheet,
//   ReportProblemSheet, DateWrapSheet, CancelDateConfirmSheet, CancelDoneSheet —
//   are NOT in this file. Each lives in its own ticket's reference file.
//
// Shell components (Phone, Card, Icon, Chip, Button, Avatar, PortraitPlaceholder,
// ShowUpCount, TabBar) come from design_handoff_showup/components/shared.jsx.
// Tokens: design_handoff_showup/tokens/colors_and_type.css
//
// Order of authority: this file wins on numbers · the ticket wins on behaviour,
// scope and copy · the PNG wins on nothing.

const { useState: useChatState, useRef: useChatRef } = React;
const { useState: useStateDate } = React;

const CHAT_REASONS = [
  { key: 'onway',  label: "I'm on my way",    short: 'On my way',   icon: 'navigation', tone: 'orange', prefill: 'On my way now — see you in a few!' },
  { key: 'arrived',label: 'I arrived at the spot', short: 'Arrived', icon: 'check', tone: 'orange', prefill: "I'm here at the meeting spot — see you soon!" },
  { key: 'find',   label: "I can't spot you", short: "Can't find", icon: 'binoculars', tone: 'orange', prefill: "I'm here but can't spot you — where are you sitting?" },
  { key: 'when',   label: 'When will you arrive?', short: 'When?', icon: 'walking',    tone: 'orange', prefill: "When do you think you'll get here?" },
  { key: 'time',   label: 'Change meet time', short: 'Meet time',  icon: 'clock',      tone: 'violet', prefill: 'Could we push the start a little? Would 19:30 work for you?' },
  { key: 'place',  label: 'Change location',  short: 'Location',    icon: 'map-pin',    tone: 'violet', prefill: 'Would you mind if we switched spots? ' },
  { key: 'late',   label: "I'm running late", short: 'Late',        icon: 'hourglass',  tone: 'orange', prefill: 'Running about 10 min late — sorry! Almost there.' },
  { key: 'cancel', label: "I can't make it today", short: "Can't make it", icon: 'frown',    tone: 'darkred', prefill: "I'm so sorry — something came up and I can't make it today." },
];

function reasonFor(key) { return CHAT_REASONS.find(r => r.key === key) || CHAT_REASONS[0]; }

const REASON_TONES = {
  violet: { fg: 'var(--liq-primary-500)', bg: 'rgba(129,42,236,0.10)', chipBorder: 'rgba(129,42,236,0.20)' },
  orange: { fg: 'var(--liq-orange-500)',  bg: 'rgba(254,104,57,0.11)', chipBorder: 'rgba(254,104,57,0.22)' },
  danger: { fg: 'var(--liq-danger-fg)',   bg: 'rgba(251,50,59,0.10)',  chipBorder: 'rgba(251,50,59,0.22)' },
  darkred:{ fg: '#8E1116',                bg: 'rgba(142,17,22,0.09)',  chipBorder: 'rgba(142,17,22,0.28)' },
};

function ReasonBadge({ reasonKey, onLight = false }) {
  const r = reasonFor(reasonKey);
  const t = REASON_TONES[r.tone];
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 5,
      padding: '3px 9px 3px 7px', borderRadius: 9999,
      background: onLight ? t.bg : 'rgba(255,255,255,0.22)',
      color: onLight ? t.fg : '#fff',
      fontFamily: 'var(--liq-font-sans)', fontSize: 10.5, fontWeight: 800,
      letterSpacing: 0.05, textTransform: 'uppercase', whiteSpace: 'nowrap',
    }}>
      <Icon name={r.icon} size={11} stroke={2.5}/>
      {r.label}
    </span>
  );
}

function CopyAddress({ address }) {
  const [copied, setCopied] = useChatState(false);
  const timerRef = useChatRef(null);
  const copy = () => {
    try { navigator.clipboard && navigator.clipboard.writeText(address); } catch (e) {}
    setCopied(true);
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setCopied(false), 1600);
  };
  return (
    <button onClick={copy} style={{
      display: 'inline-flex', alignItems: 'center', gap: 5,
      padding: '4px 10px 4px 8px', borderRadius: 9999, flex: 'none',
      background: copied ? 'rgba(0,171,85,0.14)' : '#fff',
      border: `1px solid ${copied ? 'rgba(0,171,85,0.32)' : 'var(--liq-border-soft)'}`,
      color: copied ? 'var(--liq-success-fg)' : 'var(--liq-primary-500)',
      fontFamily: 'var(--liq-font-sans)', fontSize: 11.5, fontWeight: 700,
      cursor: 'pointer', transition: 'background 160ms, border-color 160ms, color 160ms',
    }}>
      <Icon name={copied ? 'check' : 'copy'} size={13} stroke={2.4}/>
      {copied ? 'Copied' : 'Copy'}
    </button>
  );
}

const CHANGE_ORIGINAL = { time: '19:00', place: { name: 'Cafe Krone', type: 'Café', address: 'Oranienstrasse 42, Kreuzberg' } };

function BindingNote({ name = 'Leonie', time = CHANGE_ORIGINAL.time, place = CHANGE_ORIGINAL.place.name, compact = false }) {
  return (
    <div style={{
      display: 'flex', gap: 10, alignItems: 'flex-start',
      padding: compact ? '9px 11px' : '12px 14px', borderRadius: 14,
      background: 'rgba(245,158,11,0.09)', border: '1px solid rgba(217,119,6,0.28)',
    }}>
      <Icon name="alert-circle" size={compact ? 15 : 17} stroke={2.2} style={{ color: '#b45309', flex: 'none', marginTop: 1 }}/>
      <div style={{ fontSize: compact ? 12 : 13, lineHeight: 1.45, color: '#78350f', textWrap: 'pretty' }}>
        <strong style={{ fontWeight: 700 }}>Until {name} agrees, {time} at {place} stays binding.</strong> Not showing up there counts as a no-show.
      </div>
    </div>
  );
}

function ChangeTag({ change, name = 'Leonie' }) {
  const agreed = change.status === 'agreed';
  const was = change.kind === 'time' ? change.was : change.was.name;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginTop: 4, width: '100%' }}>
      <span style={{
        display: 'inline-flex', alignItems: 'center', gap: 5, padding: '3px 9px', borderRadius: 9999,
        fontSize: 10.5, fontWeight: 800, letterSpacing: 0.05, textTransform: 'uppercase',
        background: agreed ? 'rgba(0,171,85,0.12)' : 'rgba(245,158,11,0.14)',
        color: agreed ? 'var(--liq-success-fg)' : '#92400e',
      }}>
        <Icon name={agreed ? 'check' : 'clock'} size={11} stroke={2.8}/>
        {agreed ? 'Agreed' : change.by === 'them' ? `Suggested by ${name}` : `Waiting for ${name}`}
      </span>
      <span style={{ fontSize: 12, color: 'var(--liq-fg-subtle)', textDecoration: 'line-through' }}>was {was}</span>
    </div>
  );
}

function CoordinateModule({ open = false, name = 'Leonie', opensAt = '17:30', countdown = '3h 05m', onOpen, unread = null }) {
  // ── Live + a message from the match is waiting, unread ──
  // The card stops being a passive "open chat" affordance and becomes the
  // signal itself: it adopts the reason's tone, surfaces WHO + WHY + a
  // one-line preview, and the CTA switches to a reply prompt. This is the
  // in-app counterpart to the push notification — the user should be able to
  // grasp the reason for the interruption without opening the thread.
  if (open && unread) {
    // unread.arrival — the match checked in. Same slot and treatment as a new
    // message, so the in-app signal is one pattern; no reply needed.
    const arrival = !!unread.arrival;
    const r = reasonFor(arrival ? 'arrived' : unread.reason);
    const t = REASON_TONES[r.tone];
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
                  <span style={{ width: 5, height: 5, borderRadius: 9999, background: t.fg }}/>{arrival ? 'Arrived' : count === 1 ? 'New message' : `${count} new`}
                </span>
              </div>
              <div style={{ fontSize: 12.5, color: 'var(--liq-fg-muted)', marginTop: 2 }}>{arrival ? 'checked in' : 'needs a response'} · {unread.time}</div>
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
            ) : (<>
            <div style={{ alignSelf: 'flex-start' }}>
              <ReasonBadge reasonKey={unread.reason} onLight/>
            </div>
            <div style={{ fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 500, lineHeight: 1.4, color: 'var(--liq-fg)', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', textWrap: 'pretty' }}>
              “{unread.text}”
            </div>
            </>)}
          </div>

          <button onClick={onOpen} style={{
            marginTop: 14, width: '100%', height: 48, borderRadius: 9999,
            background: t.fg, color: '#fff', boxShadow: r.tone === 'orange' ? 'var(--liq-shadow-cta)' : r.tone === 'danger' ? 'none' : 'var(--liq-shadow-violet)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
            fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15,
          }}>
            <Icon name="message-circle" size={18} stroke={2.2}/> {arrival ? 'Open chat' : <>Read &amp; reply</>}
          </button>
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

function DateWorksCard({ meetLine = 'Meet at Cafe Krone at 19:00', name = 'Leonie', style }) {
  // Rewritten 2 Nov 2026 (Date 01): only what 07a actually does. No timer.
  const rows = [
    { i: 'map-pin',    t: meetLine,                       s: 'A public place — a café, bar or restaurant.' },
    { i: 'navigation', t: 'Check in when you arrive',     s: `Tap Check-in at the spot — we let ${name} know you're there.` },
    { i: 'clock',      t: '30 minutes, no pressure',      s: "Stay if you're vibing, leave with a smile." },
    { i: 'check',      t: 'End the date in the app',      s: 'Tap End date when you’re done — then a short review.' },
  ];
  return (
    <Card style={{ padding: 16, ...style }}>
      <h3 style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 18, color: 'var(--liq-fg)', margin: '0 0 14px' }}>How the date works</h3>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {rows.map((row, i) => (
          <div key={i} style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
            <div style={{
              width: 32, height: 32, borderRadius: 10, flex: 'none',
              background: 'var(--su-grad-blush)', color: 'var(--liq-orange-500)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon name={row.i} size={16} stroke={2.2}/>
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--liq-fg)' }}>{row.t}</div>
              <div style={{ fontSize: 13, color: 'var(--liq-fg-muted)', lineHeight: 1.4, marginTop: 2 }}>{row.s}</div>
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}

function MatchMateTabsCard({ initialTab = 'topics', name = 'Leonie', style }) {
  const [tab, setTab] = useStateDate(initialTab);
  return (
    <Card style={{ padding: 0, overflow: 'hidden', ...style }}>
      <div style={{ display: 'flex', padding: '0 8px', borderBottom: '1px solid var(--liq-border-soft)' }}>
        {[
          { id: 'topics', t: 'Topics' },
          { id: 'icebreakers', t: 'Ice breakers' },
          { id: 'games', t: 'Quick games' },
        ].map(x => {
          const on = tab === x.id;
          return (
            <button key={x.id} onClick={() => setTab(x.id)} style={{
              position: 'relative', flex: 1, padding: '13px 0 12px',
              fontSize: 13, fontWeight: on ? 700 : 600,
              color: on ? 'var(--liq-fg)' : 'var(--liq-fg-muted)',
              transition: 'color 160ms',
            }}>
              {x.t}
              {on && <span style={{
                position: 'absolute', left: '50%', bottom: -1, transform: 'translateX(-50%)',
                width: 'calc(100% - 16px)', height: 2.5, borderRadius: 2,
                background: 'var(--su-grad-sunset)',
              }}/>}
            </button>
          );
        })}
      </div>

      {tab === 'topics' && (
        <React.Fragment>
          <div style={{ padding: '14px 16px 6px', fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 17, color: 'var(--liq-fg)' }}>
            Things to talk about with <em>{name}</em>
          </div>
          <div style={{ padding: '0 16px 8px' }}>
            {[
              "Ask about the vinyl records in her bio — what's on her current rotation?",
              "She's a ramen fan — best bowl she's had in Berlin?",
              "She moved from Munich 3 years ago — what made Berlin feel like home?",
              "She has a cat — what's its name and is it judgemental?",
              "Her ideal Saturday is yoga → gallery → cooking. Which one is your weakness?",
            ].map((t, i) => (
              <div key={i} style={{
                display: 'flex', gap: 12, padding: '12px 0',
                borderBottom: i < 4 ? '1px solid var(--liq-border-soft)' : 'none',
              }}>
                <div style={{
                  width: 22, height: 22, borderRadius: 6, flex: 'none',
                  background: 'var(--liq-bg-raised)', color: 'var(--liq-primary-500)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 11, fontWeight: 800,
                }}>{i + 1}</div>
                <span style={{ fontSize: 14, lineHeight: 1.45, color: 'var(--liq-fg)' }}>{t}</span>
              </div>
            ))}
          </div>
          <button style={{
            padding: '14px 0', width: '100%', background: 'var(--liq-bg-raised)',
            fontSize: 13, fontWeight: 600, color: 'var(--liq-primary-500)',
            borderTop: '1px solid var(--liq-border-soft)',
          }}>Generate more topics</button>
        </React.Fragment>
      )}

      {tab === 'icebreakers' && (
        <div style={{ padding: 16 }}>
          <div style={{ padding: 18, borderRadius: 16, background: 'var(--su-grad-blush)' }}>
            <div style={{ fontSize: 11, fontWeight: 800, color: 'var(--liq-orange-500)', textTransform: 'uppercase', letterSpacing: 0.08 }}>Ice breaker · 36 Questions deck</div>
            <p className="su-underlined" style={{
              fontFamily: 'var(--liq-font-serif)', fontStyle: 'italic', fontWeight: 500, fontSize: 22,
              lineHeight: 1.3, color: 'var(--liq-fg)', margin: '10px 0 0', textWrap: 'pretty',
            }}>"What's <em>the most spontaneous</em> thing you've ever done — and would you do it again?"</p>
            <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
              <Button size="sm" variant="ghost">Skip</Button>
              <Button size="sm" variant="sunset" trailing={<Icon name="arrow-right" size={14} stroke={2.4}/>}>Next card</Button>
            </div>
          </div>
        </div>
      )}

      {tab === 'games' && (
        <div style={{ padding: 16, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          {[
            { t: 'Spin the bottle', s: 'Light, silly, fast', g: 'var(--su-grad-blush)' },
            { t: 'Vibe-check cards', s: 'Read each other quickly', g: 'var(--su-grad-lilac)' },
            { t: '36 Questions', s: 'Falling in love, kind of', g: 'var(--su-grad-mint)' },
            { t: 'Take a selfie', s: 'For the memory', g: 'linear-gradient(180deg, #FFE2C5 0%, #F8C39A 100%)' },
          ].map((g, i) => (
            <button key={i} style={{
              padding: 14, borderRadius: 18, background: g.g, textAlign: 'left',
              display: 'flex', flexDirection: 'column', gap: 4, height: 110,
              fontFamily: 'var(--liq-font-sans)', color: 'var(--liq-fg)',
            }}>
              <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 16 }}>{g.t}</div>
              <div style={{ fontSize: 12, color: 'var(--liq-fg-muted)' }}>{g.s}</div>
            </button>
          ))}
        </div>
      )}
    </Card>
  );
}

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

function DateStatusBar({ complete = false, name = 'Leonie', onWrap, onRate }) {
  if (complete) {
    return (
      <div style={{
        padding: '11px 14px', display: 'flex', alignItems: 'center', gap: 12,
        background: 'rgba(0,171,85,0.09)', borderTop: '1px solid rgba(0,171,85,0.22)',
      }}>
        <div style={{
          width: 38, height: 38, borderRadius: 12, flex: 'none',
          background: 'var(--liq-success)', color: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon name="check" size={20} stroke={2.6}/>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 13.5, fontWeight: 800, color: 'var(--liq-fg)' }}>Date complete</div>
          <div style={{ fontSize: 11.5, color: 'var(--liq-fg-muted)', lineHeight: 1.35 }}>Browsing's unlocked — tell us how it went.</div>
        </div>
        <button onClick={onRate} style={{
          height: 40, padding: '0 18px', borderRadius: 9999, flex: 'none', cursor: 'pointer',
          background: 'var(--liq-success)', color: '#fff', border: 'none',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14,
          display: 'inline-flex', alignItems: 'center', gap: 7,
        }}>
          <Icon name="heart-filled" size={15}/> Rate
        </button>
      </div>
    );
  }
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

function ScreenMatchCoord({ coordOpen = false, coordUnread = null, tall = false, initialReportOpen = false, reportStep = 'menu', reportEnded = false, matchMateTab = 'topics', dateComplete = false, initialWrapOpen = false, wrapStep = 'menu', initialCancelStep = null, checkInFlow = false, initialCheckInPopup = false, change = null, intentShared = true, rideAction = false }) {
  // intentShared: only what matches is shown — different motivations → no intent row (2 Nov 2026).
  // rideAction: Order Ride waits for a ride-app API; hidden until it exists (2 Nov 2026).
  // change: { kind: 'time'|'place', value, was, status: 'pending'|'agreed', by: 'you'|'them' }
  const timeChange = change && change.kind === 'time' ? change : null;
  const placeChange = change && change.kind === 'place' ? change : null;
  const showTime = timeChange ? timeChange.value : '19:00';
  const showPlace = placeChange ? placeChange.value : { name: 'Cafe Krone', address: 'Oranienstrasse 42, Kreuzberg' };
  // Check-in flow (new, 2 Nov 2026): on arrival the user checks in at the
  // meeting location (location captured). Until then the footer shows the
  // Check-in bar; after the pop-up it falls back to the End Date bar.
  const [checkedIn, setCheckedIn] = useChatState(!checkInFlow);
  // checkInPopup: null → 'confirm' (guard against mis-taps) → 'done'.
  const [checkInPopup, setCheckInPopup] = useChatState(initialCheckInPopup || null);
  const meetsIn = coordOpen ? '1h 18m' : '3h 05m';
  const nowTime = coordOpen ? '17:42' : '15:55';
  const [reportOpen, setReportOpen] = useChatState(initialReportOpen);
  const [wrapOpen, setWrapOpen] = useChatState(initialWrapOpen);
  const [complete, setComplete] = useChatState(dateComplete);
  // Cancel-this-date flow: null → 'confirm' (penalty guard) → 'done' (confirmed outcome).
  const [cancelStep, setCancelStep] = useChatState(initialCancelStep);
  return (
    <Phone time={nowTime} height={tall ? 'auto' : 844} scroll={!tall}>
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, height: 320,
        background: 'var(--su-grad-sunset)', opacity: 0.18, filter: 'blur(40px)',
      }}/>
      <div style={{ padding: '0 16px 24px', display: 'flex', flexDirection: 'column', gap: 8 }}>

        {/* Match hero */}
        <div style={{ position: 'relative', marginTop: 8 }}>
          <PortraitPlaceholder name="Leonie" height={196} radius={24} label={false} objectPosition="center 24%"
            gradient="linear-gradient(155deg, #FCC8A7 0%, #E89B7A 40%, #B86B5C 100%)">
            <div style={{
              position: 'absolute', top: 14, left: 0, right: 0,
              textAlign: 'center', color: '#fff',
            }}>
              <h1 className="su-underlined" style={{
                fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontStyle: 'italic', fontSize: 30,
                margin: 0, letterSpacing: '-0.02em', textShadow: '0 2px 12px rgba(0,0,0,0.18)',
              }}>It's a date.</h1>
            </div>
          </PortraitPlaceholder>
        </div>

        <div style={{ height: 12 }}/>

        {/* Coordinate — when a message is waiting, it's the most urgent, most
            actionable thing on the screen, so it's hoisted directly under the
            hero, ahead of the static time/place reference card. */}
        {coordOpen && coordUnread && (
          <CoordinateModule open={coordOpen} unread={coordUnread} name="Leonie" countdown={meetsIn} opensAt="17:30"/>
        )}

        {/* Time + place — read-only now; changing them happens in Coordinate */}
        <Card style={{ padding: '13px 14px', background: '#E8DCF5' }}>
          {/* Card header — who + when, with a heart mark */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 9, marginBottom: 13 }}>
            <div style={{
              width: 26, height: 26, borderRadius: 9, flex: 'none',
              background: 'var(--liq-primary-500)', color: '#fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon name="heart-filled" size={14} stroke={2}/>
            </div>
            <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 20, color: 'var(--liq-fg)', lineHeight: 1.15 }}>
              You and Leonie are meeting <em style={{ color: 'var(--liq-primary-500)' }}>today</em>
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
            <div style={{
              width: 40, height: 40, borderRadius: 13, flex: 'none',
              background: '#fff', color: 'var(--liq-primary-500)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon name="clock" size={19} stroke={2.2}/>
            </div>
            <div style={{ flex: 1, display: 'flex', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--liq-primary-500)', letterSpacing: 0.02 }}>Meeting time:</div>
              <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 20, color: 'var(--liq-fg)' }}>{showTime}</div>
              {timeChange && <ChangeTag change={timeChange} name="Leonie"/>}
            </div>
          </div>
          <div style={{ height: 1, background: 'rgba(29,17,41,0.10)', margin: '10px 0' }}/>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
            <div style={{
              width: 40, height: 40, borderRadius: 13, flex: 'none',
              background: '#fff', color: 'var(--liq-primary-500)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon name="map-pin" size={19} stroke={2.2}/>
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
                <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--liq-primary-500)', letterSpacing: 0.02 }}>Meeting place:</div>
                <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 20, color: 'var(--liq-fg)' }}>{showPlace.name}</div>
              </div>
              {placeChange && <ChangeTag change={placeChange} name="Leonie"/>}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 3 }}>
                <div style={{ fontSize: 12.5, color: 'var(--liq-fg-muted)', lineHeight: 1.35 }}>{showPlace.address}</div>
                <CopyAddress address={`${showPlace.name}, ${showPlace.address}`}/>
              </div>
            </div>
          </div>
          {change && change.status === 'pending' && (
            <div style={{ marginTop: 12 }}><BindingNote name="Leonie"/></div>
          )}
        </Card>

        {/* Coordinate — the single surface replacing 06's per-action buttons.
            Rendered here in its normal slot except when an unread message has
            hoisted it above the time/place card (see above). */}
        {!(coordOpen && coordUnread) && (
          <CoordinateModule open={coordOpen} unread={coordUnread} name="Leonie" countdown={meetsIn} opensAt="17:30"/>
        )}

        {/* Report a problem — always-present safety entry point, carried over
            from the live-date screen so it's reachable the whole time the date
            is active. Calm by default; the urgent routes live inside the sheet. */}
        <button onClick={() => setReportOpen(true)} style={{
          display: 'flex', alignItems: 'center', gap: 13, width: '100%', textAlign: 'left',
          padding: '13px 15px', borderRadius: 16,
          background: 'rgba(251,50,59,0.06)', border: '1px solid rgba(251,50,59,0.20)', cursor: 'pointer',
        }}>
          <div style={{
            width: 40, height: 40, borderRadius: 12, flex: 'none', background: '#fff',
            color: 'var(--liq-danger)', display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Icon name="shield" size={19} stroke={2.1}/>
          </div>
          <div style={{ flex: 1, minWidth: 0, fontSize: 13.5, lineHeight: 1.4, color: 'var(--liq-fg-muted)' }}>
            <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>Report a problem</strong> If you feel unsafe or uncomfortable, let us know.
          </div>
          <Icon name="chevron-right" size={18} stroke={2.2} style={{ color: 'var(--liq-fg-subtle)', flex: 'none' }}/>
        </button>

        {/* Intent + score + shared vibes */}
        <Card style={{ padding: '11px 13px', display: 'flex', flexDirection: 'column', gap: 8, background: '#FFFFFF' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <Icon name={intentShared ? 'heart-filled' : 'shield-check'} size={20} style={{ color: intentShared ? 'var(--liq-orange-500)' : 'var(--liq-primary-500)' }}/>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--liq-fg-subtle)', textTransform: 'uppercase', letterSpacing: 0.08 }}>{intentShared ? 'Mutual intent' : "Leonie's Show-up"}</div>
              {intentShared && <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--liq-fg)' }}>Long-term partner</div>}
            </div>
            {/* Show-up count replaces the % (Search decision 20 Oct 2026; moved here with Date 01, 2 Nov 2026) */}
            <ShowUpCount count={4} size="sm"/>
          </div>
          {tall && (
            <>
              <div style={{ height: 1, background: 'var(--liq-border-soft)' }}/>
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--liq-fg-subtle)', textTransform: 'uppercase', letterSpacing: 0.08, marginBottom: 5 }}>You both want</div>
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  {[
                    { l: 'Coffee & a chat',       i: 'coffee' },
                    { l: 'Park or nature walk',   i: 'navigation' },
                    { l: 'Fun & playful',         i: 'flame' },
                    { l: 'Deep conversation',     i: 'reading' },
                  ].map((v, i) => (
                    <Chip key={i} tone="orange" selected size="sm" leading={<Icon name={v.i} size={11} stroke={2.4}/>}>{v.l}</Chip>
                  ))}
                </div>
              </div>
            </>
          )}
        </Card>

        {/* Quick actions */}
        <div style={{ display: 'grid', gridTemplateColumns: `repeat(${rideAction ? 4 : 3}, 1fr)`, gap: 8 }}>
          {[
            { icon: 'navigation', label: 'Directions' },
            rideAction && { icon: 'car', label: 'Order Ride' },
            { icon: 'share',      label: 'Share Date' },
            { icon: 'calendar',   label: 'Calendar' },
          ].filter(Boolean).map((a, i) => (
            <button key={i} style={{
              padding: '14px 0', borderRadius: 16, background: '#fff',
              border: '1px solid var(--liq-border-soft)',
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6,
              fontSize: 12, fontWeight: 600, color: 'var(--liq-fg)',
            }}>
              <Icon name={a.icon} size={18} stroke={2} style={{ color: 'var(--liq-primary-500)' }}/>{a.label}
            </button>
          ))}
        </div>

        {tall && (
          <>
            <div style={{ height: 2 }}/>

            {/* ── About Leonie · mini profile recap ── */}
            <Card style={{ padding: 0, overflow: 'hidden' }}>
              <div style={{ display: 'flex', gap: 14, padding: 14, alignItems: 'center' }}>
                <PortraitPlaceholder name="Leonie" height={92} radius={16} label={false}
                  style={{ width: 92, flex: 'none' }}/>
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 6 }}>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                    <span style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 20, color: 'var(--liq-fg)' }}>Leonie, 28</span>
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--liq-fg-subtle)', fontWeight: 500 }}>
                    Product Designer · Neukölln · 1.2 km away
                  </div>
                  <div><ShowUpCount count={4} size="xs"/></div>
                </div>
              </div>
              <button style={{
                width: '100%', padding: '12px 16px', background: 'var(--liq-bg-raised)',
                borderTop: '1px solid var(--liq-border-soft)',
                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
                fontSize: 13, fontWeight: 600, color: 'var(--liq-primary-500)',
              }}>
                See Leonie's full profile
                <Icon name="chevron-right" size={14} stroke={2.4}/>
              </button>
            </Card>

            {/* ── What you have in common ── */}
            <Card style={{ padding: 16 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--liq-fg-subtle)', textTransform: 'uppercase', letterSpacing: 0.08, marginBottom: 10 }}>What you have in common</div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {[
                  { l: 'Long-term partner', i: 'heart' },
                  { l: 'Reading',            i: 'reading' },
                  { l: 'Brunch',             i: 'brunch' },
                  { l: 'Hiking',             i: 'hiking' },
                  { l: 'Live music',         i: 'festivals' },
                ].map((x, i) => (
                  <Chip key={i} tone="lavender" selected leading={<Icon name={x.i} size={13} stroke={2.2}/>}>{x.l}</Chip>
                ))}
              </div>
            </Card>

            {/* ── How the date works / Match Mate tabs / After — shared cards ── */}
            <DateWorksCard/>
            <MatchMateTabsCard initialTab={matchMateTab} name="Leonie"/>
            {/* After the date — removed from 07a for now; designed with the review (flow 10). 2 Nov 2026. */}
          </>
        )}

        <div style={{ height: 24 }}/>
        {!complete && (
          <button onClick={() => setCancelStep('confirm')} style={{ padding: '10px 0', textAlign: 'center', color: 'var(--liq-danger)', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>Cancel this date</button>
        )}
        <div style={{ height: 80 }}/>
      </div>

      {/* Pinned footer: user-driven date-status bar sits directly above the
          tab bar (whose Search/Instant lock is released the moment the date
          is marked complete). Wrapped in ONE sticky container so the two
          dock together without fighting each other's sticky. */}
      <div style={{ position: 'sticky', bottom: 0, left: 0, right: 0, zIndex: 5 }}>
        {checkedIn
          ? <DateStatusBar complete={complete} name="Leonie" onWrap={() => setWrapOpen(true)}/>
          : <CheckInBar name="Leonie" onCheckIn={() => setCheckInPopup('confirm')} onEnd={() => setWrapOpen(true)}/>}
        {/* Active match/date: every tab but Settings is blocked (decided 2 Nov 2026). */}
        <TabBar sticky={false} active={null}
          disabled={complete ? [] : ['search', 'likes', 'instant']}
          disabledHint="Available again once your date is finished."/>
      </div>

      {checkInPopup === 'confirm' && (
        <CheckInConfirmSheet name="Leonie" onYes={() => setCheckInPopup('done')} onNotYet={() => setCheckInPopup(null)}/>
      )}
      {checkInPopup === 'done' && (
        <CheckInDoneSheet name="Leonie" onClose={() => { setCheckInPopup(false); setCheckedIn(true); }}/>
      )}
      {reportOpen && <ReportProblemSheet name="Leonie" checkedIn={checkedIn} onCheckedIn={() => setCheckedIn(true)} aiCall={reportStep === 'unsafe'} initialStep={reportStep} initialEnded={reportEnded} onClose={() => setReportOpen(false)}/>}
      {wrapOpen && (
        <DateWrapSheet name="Leonie" initialStep={wrapStep}
          onMet={() => setWrapOpen(false)}
          onNoShow={() => setWrapOpen(false)}
          onClose={() => setWrapOpen(false)}/>
      )}
      {cancelStep === 'confirm' && (
        <CancelDateConfirmSheet name="Leonie" from={94} drop={10}
          onKeep={() => setCancelStep(null)} onConfirm={() => setCancelStep('done')}/>
      )}
      {cancelStep === 'done' && (
        <CancelDoneSheet name="Leonie" from={94} to={84}
          onClose={() => setCancelStep(null)}/>
      )}
    </Phone>
  );
}

Object.assign(window, { ScreenMatchCoord, CoordinateModule, CopyAddress, DateWorksCard, MatchMateTabsCard, CheckInBar, DateStatusBar, ChangeTag, BindingNote });
