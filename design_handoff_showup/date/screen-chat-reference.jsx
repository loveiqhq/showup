// ═════════════════════════════════════════════════════════════
// Date 02 — Coordinate chat · REFERENCE IMPLEMENTATION
// Extracted from ui_kits/show-up/screens-date-chat.jsx + screens-extras.jsx
// (5 Nov 2026). The kit is the source of truth; this file is regenerated
// from it, never edited by hand.
//
// What this ticket builds (spec sheet frames A–H):
//   A ScreenChatTutorial          — "Must read — how messaging works"
//   B ScreenChatEntry             — first open, big chip grid, no thread
//   C ScreenChatOpen              — thread + dock: field · chips · keyboard
//   D CancelGuide                 — "I can't make it today": guide, field locked until Got it
//   E EndDateCard                 — ONLY after a cant_make_it message, both sides
//   F EndDateSheet                — End the date? Keep / End
//   G ScreenPartnerCancelled viaChat endedByYou — "You ended the date"
//   H ScreenPartnerCancelled viaChat            — "{name} had to cancel the date"
//   + CHAT_REASONS · REASON_TONES · ReasonBadge · BigActionChip (built once, epic)
//
// Consumed, not built here:
//   useArrivedGate · CheckInConfirmSheet · CheckInDoneSheet  → Date 03
//   ScreenChatChange (the time / place pickers)              → Date 05
//   ScreenPartnerCancelled default variant (Cancel this date) → Date 07 extends it
//   Phone · Card · Icon · Avatar · Button · StatusBar · HomeIndicator · Atmosphere
//     → design_handoff_showup/components/shared.jsx · ModalShell → Search
//   FauxKeyboard is a MOCK — ship the OS keyboard. Never spec its height.
//
// Read values from here, not from the PNG.
// ═════════════════════════════════════════════════════════════

const { useState: useChatState, useRef: useChatRef, useEffect: useChatEffect } = React;

const CHAT_REASONS = [
  { key: 'onway',  label: "I'm on my way",    short: 'On my way',   icon: 'navigation', tone: 'orange', prefill: 'On my way now — see you in a few!' },
  { key: 'arrived',label: 'I arrived at the spot', short: 'Arrived', icon: 'check', tone: 'orange', prefill: "I'm here at the meeting spot — see you soon!" },
  { key: 'find',   label: "I can't spot you", short: "Can't find", icon: 'binoculars', tone: 'orange', prefill: "I'm here but can't spot you — where are you sitting?" },
  { key: 'when',   label: 'When will you arrive?', short: 'When?', icon: 'walking',    tone: 'orange', prefill: "When do you think you'll get here?" },
  { key: 'time',   label: 'Change meet time', short: 'Meet time',  icon: 'clock',      tone: 'violet', prefill: 'Could we push the start a little? Would 19:30 work for you?' },
  { key: 'place',  label: 'Change location',  short: 'Location',    icon: 'map-pin',    tone: 'violet', prefill: 'Would you mind if we switched spots? ' },
  { key: 'late',   label: "I'm running late", short: 'Late',        icon: 'hourglass',  tone: 'orange', prefill: 'Running about 10 min late — sorry! Almost there.' },
  { key: 'cancel', label: "I can't make it today", short: "Can't make it", icon: 'frown',    tone: 'darkred', prefill: '' }, // no prefill — the user writes their own apology after CancelGuide → Got it
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

function ChatBubble({ msg, mine, name = 'Leonie', showAvatar }) {
  const r = reasonFor(msg.reason);
  return (
    <div style={{
      display: 'flex', gap: 8, alignItems: 'flex-end',
      flexDirection: mine ? 'row-reverse' : 'row',
    }}>
      {!mine && (
        <div style={{ width: 26, flex: 'none' }}>
          {showAvatar && <Avatar name={name} size={26}/>}
        </div>
      )}
      <div style={{ maxWidth: '78%', display: 'flex', flexDirection: 'column', gap: 5, alignItems: mine ? 'flex-end' : 'flex-start' }}>
        <div style={{
          padding: '9px 13px 11px',
          borderRadius: mine ? '18px 18px 5px 18px' : '18px 18px 18px 5px',
          background: mine ? 'var(--su-grad-sunset)' : '#fff',
          color: mine ? '#fff' : 'var(--liq-fg)',
          border: mine ? 'none' : '1px solid var(--liq-border-soft)',
          boxShadow: mine ? 'var(--liq-shadow-violet)' : 'var(--liq-shadow-sm)',
          display: 'flex', flexDirection: 'column', gap: 6,
        }}>
          {msg.reason && <div style={{ alignSelf: 'flex-start' }}>
            <ReasonBadge reasonKey={msg.reason} onLight={!mine}/>
          </div>}
          <div style={{ fontFamily: 'var(--liq-font-sans)', fontSize: 14.5, fontWeight: 500, lineHeight: 1.42, textWrap: 'pretty' }}>
            {msg.text}
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '0 4px' }}>
          <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--liq-fg-subtle)' }}>{msg.time}</span>
          {mine && (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 11, fontWeight: 600, color: msg.read ? 'var(--liq-primary-500)' : 'var(--liq-fg-subtle)' }}>
              <Icon name="check" size={12} stroke={3}/>{msg.read ? 'Read' : 'Delivered'}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

function SystemLine({ icon = 'lock', children, tone = 'muted' }) {
  const fg = tone === 'success' ? 'var(--liq-success-fg)' : 'var(--liq-fg-subtle)';
  const bg = tone === 'success' ? 'rgba(0,171,85,0.10)' : 'rgba(29,17,41,0.05)';
  return (
    <div style={{ display: 'flex', justifyContent: 'center', margin: '2px 0' }}>
      <span style={{
        display: 'inline-flex', alignItems: 'center', gap: 6, maxWidth: '86%',
        padding: '6px 12px', borderRadius: 9999, background: bg, color: fg,
        fontFamily: 'var(--liq-font-sans)', fontSize: 11.5, fontWeight: 600,
        lineHeight: 1.35, textAlign: 'center', textWrap: 'pretty',
      }}>
        <Icon name={icon} size={12} stroke={2.4} style={{ flex: 'none' }}/>
        {children}
      </span>
    </div>
  );
}

function ChatHeader({ name = 'Leonie', when = 'Today · 19:00', place = 'Café Krone', countdown = 'in 1h 18m' }) {
  return (
    <div style={{
      flex: 'none', padding: '4px 10px 10px',
      borderBottom: '1px solid var(--liq-border-soft)',
      background: 'rgba(255,251,247,0.92)', backdropFilter: 'blur(20px)',
      position: 'relative', zIndex: 3,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <button style={{ width: 34, height: 34, borderRadius: 17, display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 'none' }}>
          <Icon name="chevron-left" size={23} stroke={2.2}/>
        </button>
        <Avatar name={name} size={40}/>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
            <span style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 17, color: 'var(--liq-fg)' }}>{name}</span>
            <span style={{
              display: 'inline-flex', alignItems: 'center', gap: 4, padding: '2px 7px', borderRadius: 9999,
              background: 'rgba(0,171,85,0.12)', color: 'var(--liq-success-fg)', fontSize: 10, fontWeight: 800, letterSpacing: 0.04, textTransform: 'uppercase',
            }}>
              <span style={{ width: 5, height: 5, borderRadius: 9999, background: 'var(--liq-success)' }}/>Date on
            </span>
          </div>
          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--liq-fg-muted)', marginTop: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {when} · {place} · <span style={{ color: 'var(--liq-orange-500)' }}>{countdown}</span>
          </div>
        </div>
      </div>
    </div>
  );
}

function FauxKeyboard() {
  const rows = [['q','w','e','r','t','y','u','i','o','p'], ['a','s','d','f','g','h','j','k','l'], ['z','x','c','v','b','n','m']];
  const key = (ch, wide) => (
    <div key={ch} style={{
      flex: wide ? '1.5' : '1', height: 40, borderRadius: 6,
      background: '#fff', boxShadow: '0 1px 0 rgba(0,0,0,0.28)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontFamily: '-apple-system, system-ui', fontSize: 17, color: '#1D1129',
    }}>{ch}</div>
  );
  return (
    <div style={{ background: '#D1D3D9', padding: '8px 4px 6px', display: 'flex', flexDirection: 'column', gap: 8, flex: 'none' }}>
      {rows.map((row, i) => (
        <div key={i} style={{ display: 'flex', gap: 5, padding: i === 1 ? '0 18px' : '0 4px' }}>
          {i === 2 && <div style={{ flex: '1.4', height: 40, borderRadius: 6, background: '#ADB3BC', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16, color: '#1D1129' }}>⇧</div>}
          {row.map(ch => key(ch))}
          {i === 2 && <div style={{ flex: '1.4', height: 40, borderRadius: 6, background: '#ADB3BC', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#1D1129', fontSize: 13, fontWeight: 600 }}>⌫</div>}
        </div>
      ))}
      <div style={{ display: 'flex', gap: 5, padding: '0 4px' }}>
        <div style={{ flex: '1.4', height: 40, borderRadius: 6, background: '#ADB3BC', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, color: '#1D1129', fontWeight: 500 }}>123</div>
        <div style={{ flex: '5', height: 40, borderRadius: 6, background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, color: '#1D1129' }}>space</div>
        <div style={{ flex: '1.6', height: 40, borderRadius: 6, background: 'var(--liq-orange-500)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, color: '#fff', fontWeight: 700 }}>send</div>
      </div>
    </div>
  );
}

function TutorialStep({ n, icon, tint, color, title, children }) {
  return (
    <div style={{ display: 'flex', gap: 13, alignItems: 'flex-start' }}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flex: 'none', gap: 6 }}>
        <div style={{
          width: 40, height: 40, borderRadius: 13, flex: 'none',
          background: tint, color,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          position: 'relative',
        }}>
          <Icon name={icon} size={20} stroke={2.2}/>
          <span style={{
            position: 'absolute', top: -6, right: -6, width: 20, height: 20, borderRadius: 9999,
            background: '#fff', border: `1.5px solid ${color}`, color,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 12, lineHeight: 1,
          }}>{n}</span>
        </div>
      </div>
      <div style={{ flex: 1, minWidth: 0, paddingTop: 1 }}>
        <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 17, color: 'var(--liq-fg)', lineHeight: 1.2, marginBottom: 4 }}>{title}</div>
        <div style={{ fontSize: 13.5, color: 'var(--liq-fg-muted)', lineHeight: 1.5, textWrap: 'pretty' }}>{children}</div>
      </div>
    </div>
  );
}

function ScreenChatTutorial({ name = 'Leonie', onStart, time = '17:42', tall = false }) {
  // The three signals we lean on most for the fairness call — shown as real
  // chips so the tutorial matches exactly what the picker looks like.
  const signalKeys = ['onway', 'late', 'find'];
  return (
    <Phone time={time} height={tall ? 'auto' : 844} scroll={!tall}>
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, height: 260,
        background: 'var(--su-grad-sunset)', opacity: 0.16, filter: 'blur(40px)', pointerEvents: 'none',
      }}/>

      <div style={{ display: 'flex', flexDirection: 'column', minHeight: tall ? 0 : '100%' }}>
        {/* Back + context */}
        <div style={{ flex: 'none', padding: '4px 12px 8px', display: 'flex', alignItems: 'center', gap: 8, position: 'relative', zIndex: 2 }}>
          <button style={{ width: 34, height: 34, borderRadius: 17, display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 'none' }}>
            <Icon name="chevron-left" size={23} stroke={2.2}/>
          </button>
          <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--liq-fg-muted)' }}>Coordinate with {name}</span>
        </div>

        {/* Body — scrolls in phone mode, flows fully in `tall` (dev handoff) mode */}
        <div className="su-noscroll" style={{ flex: tall ? 'none' : 1, overflow: tall ? 'visible' : 'auto', padding: '4px 20px 16px', position: 'relative', zIndex: 1 }}>
          {/* Hero */}
          <h1 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
            letterSpacing: '-0.02em', margin: '4px 0 8px', color: 'var(--liq-fg)', lineHeight: 1.12,
          }}><em>Must read</em> — how messaging works</h1>
          <p style={{ fontSize: 14.5, color: 'var(--liq-fg-muted)', margin: '0 0 22px', lineHeight: 1.5, textWrap: 'pretty', background: 'rgba(129,42,236,0.08)', border: '1px solid rgba(129,42,236,0.16)', borderRadius: 14, padding: '12px 14px' }}>
            <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>How you use the chat</strong> is a major part of how we can <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>determine if one of you later says the other never showed while they insist they did.</strong>
          </p>

          {/* Steps */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <TutorialStep n={1} icon="navigation" tint="rgba(254,104,57,0.12)" color="var(--liq-orange-500)"
              title="Use our action chips">
              <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>Make use</strong> of our <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>action chips</strong> whenever possible. It allows us to analyze the situation of your date and <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>understand who played real and who didn't.</strong>
            </TutorialStep>

            {/* Two example chips, one line */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '-6px 0 0 53px' }}>
              <span style={{ fontSize: 11.5, fontWeight: 700, color: 'var(--liq-fg-subtle)' }}>Examples:</span>
              {[{ label: 'I arrived', icon: 'navigation', tone: 'orange' }, { label: 'Running late', icon: 'hourglass', tone: 'orange' }].map(r => {
                const t = REASON_TONES[r.tone];
                return (
                  <span key={r.label} style={{
                    display: 'inline-flex', alignItems: 'center', gap: 6,
                    padding: '7px 12px 7px 10px', borderRadius: 9999,
                    background: '#fff', border: `1px solid ${t.chipBorder}`, color: t.fg,
                    fontFamily: 'var(--liq-font-sans)', fontSize: 12.5, fontWeight: 700,
                  }}>
                    <Icon name={r.icon} size={14} stroke={2.4}/>{r.label}
                  </span>
                );
              })}
            </div>

            <TutorialStep n={2} icon="eye-off" tint="rgba(129,42,236,0.10)" color="var(--liq-primary-500)"
              title="We never read your words">
              <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>What you type stays private between</strong> you and your date. All we can see is <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>which signal you picked</strong> and <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>whether a message was sent and replied to</strong> — never the message itself.
            </TutorialStep>
          </div>

          <div style={{ height: 10 }}/>
        </div>

        {/* CTA — pinned in phone mode, inline at the end in `tall` mode */}
        <div style={{
          flex: 'none', padding: '12px 16px 16px',
          background: tall ? 'transparent' : 'rgba(255,251,247,0.96)', backdropFilter: tall ? 'none' : 'blur(20px)', WebkitBackdropFilter: tall ? 'none' : 'blur(20px)',
          borderTop: tall ? 'none' : '1px solid var(--liq-border-soft)',
        }}>
          <button onClick={onStart} style={{
            width: '100%', height: 54, borderRadius: 9999, border: 'none', cursor: 'pointer',
            background: 'var(--su-grad-sunset)', color: '#fff',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 16, boxShadow: 'var(--liq-shadow-cta)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 9,
          }}>
            <Icon name="message-circle" size={19} stroke={2.2}/> Open the chat
          </button>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: 10 }}>
            <Icon name="lock" size={12} stroke={2.3} style={{ color: 'var(--liq-fg-subtle)' }}/>
            <span style={{ fontSize: 11.5, fontWeight: 600, color: 'var(--liq-fg-subtle)' }}>Your messages are private. Only the signals are logged.</span>
          </div>
        </div>
      </div>
    </Phone>
  );
}

function ThreadScroll({ children, style }) {
  const ref = React.useRef(null);
  const [ind, setInd] = React.useState({ show: false, top: 0, height: 24 });
  const update = React.useCallback(() => {
    const el = ref.current; if (!el) return;
    const { scrollTop, scrollHeight, clientHeight } = el;
    const pad = 8;                       // inset of the track from top/bottom
    const track = clientHeight - pad * 2;
    if (scrollHeight <= clientHeight + 2 || track <= 0) { setInd(s => (s.show ? { ...s, show: false } : s)); return; }
    const thumbH = Math.max(30, track * (clientHeight / scrollHeight));
    const maxTop = track - thumbH;
    const ratio = scrollTop / (scrollHeight - clientHeight);
    const next = { show: true, top: pad + maxTop * ratio, height: thumbH };
    // Bail out when nothing moved — the effect below re-runs on every render
    // (children is a fresh array), so a new object each time looped forever.
    setInd(s => (s.show && Math.abs(s.top - next.top) < 0.5 && Math.abs(s.height - next.height) < 0.5 ? s : next));
  }, []);
  React.useEffect(() => {
    update();
    const el = ref.current; if (!el || typeof ResizeObserver === 'undefined') return;
    // rAF-throttled: at most one measure per frame, so a layout that flips
    // between overflow / no-overflow can never lock the main thread.
    let raf = 0;
    const schedule = () => { if (!raf) raf = requestAnimationFrame(() => { raf = 0; update(); }); };
    const ro = new ResizeObserver(schedule);
    ro.observe(el);
    Array.from(el.children).forEach(c => ro.observe(c));
    const mo = new MutationObserver(() => { Array.from(el.children).forEach(c => ro.observe(c)); schedule(); });
    mo.observe(el, { childList: true });
    return () => { ro.disconnect(); mo.disconnect(); if (raf) cancelAnimationFrame(raf); };
  }, [update]);
  return (
    <div style={{ position: 'relative', flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
      <div ref={ref} onScroll={update} className="su-noscroll" style={{ ...style, flex: 1, minHeight: 0 }}>
        {children}
      </div>
      {/* Always-visible track + thumb */}
      <div style={{ position: 'absolute', top: 8, bottom: 8, right: 3, width: 6, borderRadius: 9999, background: 'rgba(29,17,41,0.06)', pointerEvents: 'none', zIndex: 6, opacity: ind.show ? 1 : 0, transition: 'opacity 160ms' }}/>
      <div style={{ position: 'absolute', right: 3, width: 6, borderRadius: 9999, background: 'rgba(29,17,41,0.34)', pointerEvents: 'none', zIndex: 7, top: ind.top, height: ind.height, opacity: ind.show ? 1 : 0, transition: 'opacity 160ms' }}/>
    </div>
  );
}

function BigActionChip({ reason, active = false, layout = 'tile', compact = false, onClick }) {
  const t = REASON_TONES[reason.tone];
  if (layout === 'pill') {
    return (
      <button onClick={onClick} style={{
        display: 'inline-flex', alignItems: 'center', gap: 8, flex: 'none',
        padding: '11px 16px 11px 13px', borderRadius: 9999, minHeight: 46,
        background: active ? t.fg : '#fff',
        border: `1.5px solid ${active ? t.fg : t.chipBorder}`,
        color: active ? '#fff' : t.fg, cursor: 'pointer',
        fontFamily: 'var(--liq-font-sans)', fontSize: 14.5, fontWeight: 700, whiteSpace: 'nowrap',
        boxShadow: active ? 'var(--liq-shadow-sm)' : 'none',
        transition: 'transform 140ms, background 140ms, border-color 140ms, color 140ms',
      }}
      onMouseDown={e => e.currentTarget.style.transform = 'scale(0.95)'}
      onMouseUp={e => e.currentTarget.style.transform = ''}
      onMouseLeave={e => e.currentTarget.style.transform = ''}>
        <Icon name={reason.icon} size={17} stroke={2.4}/>{reason.short}
      </button>
    );
  }
  return (
    <button onClick={onClick} style={{
      display: 'flex', alignItems: 'center', gap: compact ? 8 : 12, width: '100%', textAlign: 'left',
      padding: compact ? '4px 10px 4px 5px' : '15px 15px', borderRadius: compact ? 11 : 18,
      minHeight: compact ? 36 : 68, cursor: 'pointer',
      background: active ? t.bg : '#fff', border: `1.5px solid ${active ? t.fg : t.chipBorder}`,
      boxShadow: 'var(--liq-shadow-sm)',
      transition: 'transform 150ms, box-shadow 150ms, background 150ms, border-color 150ms',
    }}
    onMouseDown={e => e.currentTarget.style.transform = 'scale(0.975)'}
    onMouseUp={e => e.currentTarget.style.transform = ''}
    onMouseLeave={e => e.currentTarget.style.transform = ''}>
      <span style={{
        width: compact ? 26 : 42, height: compact ? 26 : 42, borderRadius: compact ? 8 : 13, flex: 'none',
        background: t.bg, color: t.fg,
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Icon name={reason.icon} size={compact ? 14 : 21} stroke={2.3}/>
      </span>
      <span style={{ fontFamily: 'var(--liq-font-sans)', fontSize: compact ? 11.5 : 15, fontWeight: 700, color: (reason.tone === 'darkred' || reason.tone === 'danger') ? t.fg : 'var(--liq-fg)', lineHeight: 1.16 }}>{reason.label}</span>
    </button>
  );
}

function useArrivedGate(name, initialCheckedIn = false, initialPopup = null) {
  const [checkedIn, setCheckedIn] = useChatState(initialCheckedIn);
  const [popup, setPopup] = useChatState(initialPopup);
  function gate(key, proceed) {
    if (key === 'arrived' && !checkedIn) { setPopup('confirm'); return; }
    proceed(key);
  }
  const sheets = (
    <>
      {popup === 'confirm' && <CheckInConfirmSheet name={name} onYes={() => setPopup('done')} onNotYet={() => setPopup(null)}/>}
      {popup === 'done' && <CheckInDoneSheet name={name} onClose={() => { setPopup(null); setCheckedIn(true); }}/>}
    </>
  );
  return { gate, sheets, checkedIn };
}

function ScreenChatEntry({ name = 'Leonie', time = '17:42', onPick, checkedIn = false, initialCheckInPopup = null }) {
  const { gate, sheets } = useArrivedGate(name, checkedIn, initialCheckInPopup);
  const [openKey, setOpenKey] = useChatState(null);
  if (openKey === 'time' || openKey === 'place') return <ScreenChatChange name={name} time={time} initialSheet={openKey}/>;
  if (openKey) return <ScreenChatOpen name={name} time={time} initialKey={openKey} checkedIn/>;
  const choose = (key) => gate(key, k => (onPick ? onPick(k) : setOpenKey(k)));
  return (
    <Phone time={time}>
      <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', height: '100%' }}>
        <ChatHeader name={name}/>

        <div className="su-noscroll" style={{
          flex: 1, overflow: 'auto', padding: '20px 16px 20px',
          display: 'flex', flexDirection: 'column',
          background: '#fff',
        }}>
          {/* Guide */}
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', marginBottom: 20 }}>
            <h2 className="su-underlined" style={{
              fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 25, letterSpacing: '-0.02em',
              margin: '0 0 8px', color: 'var(--liq-fg)', lineHeight: 1.14, whiteSpace: 'nowrap',
            }}>Start with a <em>reason</em></h2>
            <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.5, maxWidth: 290, textWrap: 'pretty' }}>
              Tap one that fits best to start the chat.<br/>Make use of our action chips as much as possible to allow us to understand who played real and who didn't.
            </p>
          </div>

          {/* Big chip grid — everything visible, no hunting */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            {CHAT_REASONS.map(r => (
              <BigActionChip key={r.key} reason={r} onClick={() => choose(r.key)}/>
            ))}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: 18 }}>
            <Icon name="lock" size={12} stroke={2.3} style={{ color: 'var(--liq-fg-subtle)' }}/>
            <span style={{ fontSize: 11.5, fontWeight: 600, color: 'var(--liq-fg-subtle)' }}>We log which reason you pick — never what you type.</span>
          </div>
        </div>
        {sheets}
      </div>
    </Phone>
  );
}

function revealInThread(box) {
  if (!box) return;
  const go = () => { const sc = box.parentElement; if (!sc) return; sc.scrollTop = box.offsetHeight > sc.clientHeight ? Math.max(0, box.offsetTop - 8) : sc.scrollHeight; };
  go(); requestAnimationFrame(go); [120, 400, 1000].forEach(t => setTimeout(go, t));
}

function CancelGuide({ name = 'Leonie', acked = false, onAck }) {
  const t = REASON_TONES.darkred;
  const boxRef = React.useRef(null);
  React.useEffect(() => revealInThread(boxRef.current), [acked]);
  if (acked) return (
    <div ref={boxRef} style={{ marginTop: 'auto', alignSelf: 'center', display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 600, color: 'var(--liq-fg-subtle)' }}>
      <Icon name="check" size={12} stroke={2.6}/>Explain why, say sorry — in your own words.
    </div>
  );
  const b = { color: 'var(--liq-fg)', fontWeight: 700 };
  const p = { margin: 0, fontSize: 12.5, lineHeight: 1.38, color: 'var(--liq-fg-muted)', textWrap: 'pretty' };
  return (
    <div ref={boxRef} style={{ marginTop: 'auto', background: t.bg, border: `1.5px solid ${t.chipBorder}`, borderRadius: 16, padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 5 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ width: 26, height: 26, borderRadius: 8, background: '#fff', color: t.fg, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flex: 'none' }}><Icon name="frown" size={14} stroke={2.3}/></span>
        <span style={{ fontSize: 14.5, fontWeight: 700, color: t.fg }}>Life happens — handle it with respect</span>
      </div>
      <p style={p}>Show Up is for reliable, committed people who want to meet in real life, quickly. But sometimes life gets in the way. If there's really no way you can make it, <strong style={b}>explain why and say sorry</strong>. {name} has freed up time for you and is looking forward to meeting you.</p>
      <p style={p}>Better still: <strong style={b}>move it to later today or to another place</strong> instead of cancelling. A different day can't be set in the app — agree on one here, then one of you ends the date so you can both search again.</p>
      <button onClick={onAck} style={{ marginTop: 3, alignSelf: 'stretch', minHeight: 40, borderRadius: 9999, border: 'none', background: t.fg, color: '#fff', fontFamily: 'var(--liq-font-sans)', fontSize: 14.5, fontWeight: 700, cursor: 'pointer' }}>Got it</button>
    </div>
  );
}

function ScreenChatOpen({ name = 'Leonie', time = '17:42', seed = [], initialKey = 'late', checkedIn = false, initialEndSheet = false }) {
  const { gate, sheets } = useArrivedGate(name, checkedIn);
  const [changeKey, setChangeKey] = useChatState(null);
  const [messages, setMessages] = useChatState(seed);
  const [selectedKey, setSelectedKey] = useChatState(initialKey);
  const [text, setText] = useChatState(initialKey ? reasonFor(initialKey).prefill : '');
  const [cancelAck, setCancelAck] = useChatState(false);
  const [endSheet, setEndSheet] = useChatState(initialEndSheet);
  const [ended, setEnded] = useChatState(false);
  const hasCancelMsg = messages.some(m => m.reason === 'cancel');
  const taRef = useChatRef(null);
  useChatEffect(() => {
    const el = taRef.current; if (!el) return;
    el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 90) + 'px';
  }, [text]);

  const sel = selectedKey ? reasonFor(selectedKey) : null;
  // "I can't make it today": field and send stay locked until the guide is acknowledged.
  const fieldLocked = selectedKey === 'cancel' && !cancelAck;
  const canSend = !fieldLocked && !!text.trim();
  function pick(key) {
    if (key === 'time' || key === 'place') { setChangeKey(key); return; }
    gate(key, k => { setSelectedKey(k); setText(reasonFor(k).prefill); setCancelAck(false); });
  }
  function ackCancel() { setCancelAck(true); setTimeout(() => taRef.current && taRef.current.focus(), 0); }
  function send() {
    if (!canSend) return;
    setMessages(m => [...m, { id: Date.now(), reason: selectedKey, text: text.trim(), mine: true, time, read: false }]);
    setSelectedKey(null); setText('');
  }

  if (ended) return <ScreenPartnerCancelled name={name} viaChat endedByYou/>;
  if (changeKey) return <ScreenChatChange name={name} time={time} initialSheet={changeKey} seed={messages}/>;
  return (
    <Phone time={time}>
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <ChatHeader name={name}/>

        {/* Thread */}
        <ThreadScroll style={{
          overflow: 'auto', padding: selectedKey === 'cancel' && !cancelAck ? '6px 14px 6px' : '14px 14px 8px',
          display: 'flex', flexDirection: 'column', gap: 12,
          background: '#fff',
        }}>
          {messages.map((m, i) => (
            <ChatBubble key={m.id} msg={m} mine={m.mine} name={name}
              showAvatar={!m.mine && (i === messages.length - 1 || messages[i + 1]?.mine)}/>
          ))}
          {selectedKey === 'cancel' && <CancelGuide name={name} acked={cancelAck} onAck={ackCancel}/>}
          {hasCancelMsg && selectedKey !== 'cancel' && <EndDateCard name={name} onEnd={() => setEndSheet(true)}/>}
        </ThreadScroll>

        {/* ── DOCK: field (top) · chips (middle) · keyboard (bottom) ── */}
        <div style={{
          flex: 'none',
          background: 'rgba(255,251,247,0.96)', backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)',
          borderTop: '1px solid var(--liq-border-soft)',
        }}>
          {/* Message field — sits a little higher, out of the thumb's main path */}
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 9, padding: '11px 14px 9px' }}>
            <div style={{
              flex: 1, display: 'flex', alignItems: 'center',
              borderRadius: 20, background: '#fff',
              border: `1.5px solid ${sel && !fieldLocked ? 'var(--liq-fg)' : 'var(--liq-border)'}`,
              background: fieldLocked ? 'var(--liq-border-soft)' : '#fff',
              padding: '9px 14px', transition: 'border-color 160ms, background 160ms',
            }}>
              <textarea ref={taRef} rows={1} value={text} disabled={fieldLocked}
                onChange={e => setText(e.target.value)}
                placeholder={fieldLocked ? 'Read the note above, then tap Got it' : selectedKey === 'cancel' ? `Explain why and say sorry to ${name}…` : 'Type a message, or tap a chip below'}
                style={{
                  flex: 1, border: 'none', outline: 'none', background: 'transparent', resize: 'none',
                  fontFamily: 'var(--liq-font-sans)', fontSize: 14.5, fontWeight: 500,
                  lineHeight: 1.4, color: 'var(--liq-fg)', maxHeight: 90, cursor: 'text',
                }}/>
            </div>
            <button onClick={send} disabled={!canSend} style={{
              width: 46, height: 46, borderRadius: 9999, flex: 'none',
              background: canSend ? 'var(--su-grad-sunset)' : 'var(--liq-border)',
              color: '#fff', boxShadow: canSend ? 'var(--liq-shadow-violet)' : 'none',
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              transition: 'background 160ms',
            }}>
              <Icon name="send" size={19} stroke={2.1} style={{ marginLeft: -1, marginTop: 1 }}/>
            </button>
          </div>

          {/* Action chips — big, all visible (wrap), directly above keyboard */}
          <div style={{ padding: '2px 14px 10px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
              {CHAT_REASONS.map(r => (
                <BigActionChip key={r.key} reason={r} compact
                  active={r.key === selectedKey} onClick={() => pick(r.key)}/>
              ))}
            </div>
          </div>
        </div>

        <FauxKeyboard/>
        {endSheet && <EndDateSheet name={name} onKeep={() => setEndSheet(false)} onEnd={() => { setEndSheet(false); setEnded(true); }}/>}
        {sheets}
      </div>
    </Phone>
  );
}

function EndDateCard({ name = 'Leonie', onEnd }) {
  const t = REASON_TONES.darkred;
  const cardRef = React.useRef(null);
  React.useEffect(() => revealInThread(cardRef.current), []);
  return (
    <div ref={cardRef} style={{ alignSelf: 'center', width: '100%', background: 'rgba(29,17,41,0.04)', borderRadius: 16, padding: '11px 13px', display: 'flex', alignItems: 'center', gap: 10 }}>
      <p style={{ flex: 1, margin: 0, fontSize: 12.5, lineHeight: 1.38, color: 'var(--liq-fg-muted)', textWrap: 'pretty' }}>
        <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>Not meeting today?</strong> Once you've talked it through, one of you ends the date so you can both search again.
      </p>
      <button onClick={onEnd} style={{ flex: 'none', minHeight: 40, padding: '0 14px', borderRadius: 9999, background: '#fff', border: `1.5px solid ${t.chipBorder}`, color: t.fg, fontFamily: 'var(--liq-font-sans)', fontSize: 13.5, fontWeight: 700, cursor: 'pointer' }}>End date</button>
    </div>
  );
}

function EndDateSheet({ name = 'Leonie', onKeep, onEnd }) {
  const t = REASON_TONES.darkred;
  return (
    <ModalShell onCancel={onKeep}>
      <div style={{ padding: '14px 24px 24px', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
        <div style={{ width: 80, height: 80, borderRadius: 9999, margin: '8px 0 0', background: t.bg, color: t.fg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Icon name="x" size={34} stroke={2.4}/>
        </div>
        <h3 style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26, letterSpacing: '-0.01em', margin: '16px 0 8px', color: 'var(--liq-fg)', lineHeight: 1.15, maxWidth: 310, textWrap: 'pretty' }}>End the date with {name}?</h3>
        <p style={{ fontSize: 15, color: 'var(--liq-fg-muted)', margin: '0 0 22px', lineHeight: 1.5, maxWidth: 300, textWrap: 'pretty' }}>
          The date won't happen, so there's no review. You can both search again straight away.
        </p>
        <button onClick={onKeep} style={{ width: '100%', height: 50, borderRadius: 16, cursor: 'pointer', background: '#fff', border: '1px solid var(--liq-border-soft)', fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14.5, color: 'var(--liq-fg-muted)' }}>Keep the date</button>
        <button onClick={onEnd} style={{ marginTop: 10, width: '100%', height: 56, borderRadius: 18, border: 'none', cursor: 'pointer', background: t.fg, color: '#fff', fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 16.5 }}>End date</button>
      </div>
    </ModalShell>
  );
}

// ── From screens-extras.jsx — outcome screens after date_ended_in_chat ──

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
      t: <>People who <strong style={{ color: 'var(--liq-fg)' }}>repeatedly cancel or don't show up</strong> are paused, become less visible, and eventually <strong style={{ color: 'var(--liq-fg)' }}>lose access.</strong> {name} carries that — not you.</>,
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
