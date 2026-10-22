// screen-search-profile-reference.jsx — Date search 01 · Freemium profile + Like
//
// Design reference for [Search 01] Freemium profile + Like. This is the code
// that renders spec-sheets/01-profile.png. Read values from HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/:
// screens-search-profile.jsx in full; SearchTopBar, ActionBtn, ModalShell,
// LikeConfirmModal, LikeMessageModal from screens-search.jsx; SectionTitle,
// PromptCard, VoicePromptCard, EssentialsBlock, MotivationPill, PhotoSlot,
// SearchIcon from screens-search-sections.jsx; ShowUpCount from shared.jsx).
// It is a reference implementation, not production code: recreate it in the
// target codebase's own patterns. Do not ship this file.
//
// CONSUMES, never copies:
//   components/shared.jsx — StatusBar, Phone, Card, Chip, Icon, Button,
//     PortraitPlaceholder, TabBar, HomeIndicator, Wordmark, showUpTone.
//   Check-In 03 — CHECKIN_MOTIVATIONS (§30) for the MotivationPill.
//   Check-In 04 — CHECKIN_VIBE_LABEL (§31) for vibe chip labels.
//   Profile 21 — LIFESTYLE_GROUPS for "How I live".
//
// BUILDS: ScreenSearchProfile, ShowUpCount (NEW shared component — put it
// next to Chip in the shared set; every Show-up display moves to it),
// SharedVibesRow, SharedInterestsBlock, SharedChip, HowILiveBlock,
// VideoPromptCardV2, searchDistance, the Like flow sheets, and the
// actions coach (SearchActionsCoach + SEARCH_COACH_ROWS, 21 Oct 2026).
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// STATES ON THE SHEET
//   A  <ScreenSearchProfile/>                                  at rest
//   B  <ScreenSearchProfile initialModal="confirm"/>           Be sure
//   C  <ScreenSearchProfile initialModal="message"/>           optional message
//   D  <ScreenSearchProfile profile={SEARCH_V2_PROFILE_MIN} tall tallHeight={2000}/>  minimum content
//   E  <ScreenSearchProfile tall tallHeight={4600}/>           full scroll, every section
//   F  <ScreenSearchProfile coach/>                            actions coach — what the three buttons do
//      (21 Oct 2026). Every Search visit, Freemium and Premium, until "Don't
//      show again". Scrim over the screen, the action bar lifted above it,
//      SearchActionsCoach in the bar's own column — its position follows the
//      bar, never a fixed Y. Buttons inert while it shows.
//   tall / tallHeight exist only to draw the whole stack on the sheet. The
//   product screen is always the scrolling 844 case.
//
// EVERY SECTION IS OPTIONAL. No content → the section is not rendered. Never
// an empty card, a placeholder or "not answered". A hidden detail drops its
// essentials row; a hidden age leaves the name alone.
//
// STACK ORDER is the JSX order in ScreenSearchProfile — it is the spec.
//
// OVERLAP: vibe today is the only highlighted overlap (SharedVibesRow, ≤ 3 +
// "+N"); interests and vibe chips mark shared ones with SharedChip. Nothing
// else is compared. SEARCH_VIEWER stands in for the viewer's own Check-In +
// profile.
//
// SHOW-UP: ShowUpCount — "N dates attended", 0 → "New in the App". Never a
// percentage. The kit's ShowUpPill (percentage) is NOT used here.
//
// LIKE FLOW: Like → Be sure (Cancel | Continue) → message (Skip message |
// Send message). The like is sent from the message sheet only. Dismissing
// either sheet sends nothing. Message max 100.
//
// LAYOUT — one scroll column ([data-search-stack], flex column, gap 14). The
// action bar is sticky above the TabBar. Never hard-code a Y.

function ShowUpCount({ count, variant = 'soft', size = 'md' }) {
  const isNew = !(count > 0);
  const t = showUpTone(100);
  const fs = size === 'xs' ? 11 : size === 'sm' ? 12 : 12.5;
  const pad = size === 'xs' ? '4px 9px' : '7px 12px';
  const pal = isNew
    ? { bg: 'rgba(129,42,236,0.10)', fg: 'var(--liq-primary-500)' }
    : variant === 'dark' ? { bg: t.pillBg, fg: t.pillFg } : { bg: t.softBg, fg: t.fg };
  return (
    <div style={{
      display: 'inline-flex', alignItems: 'center', gap: 6, padding: pad, borderRadius: 9999,
      background: pal.bg, color: pal.fg, backdropFilter: variant === 'dark' ? 'blur(8px)' : undefined,
      fontFamily: 'var(--liq-font-sans)', fontSize: fs, fontWeight: 700, lineHeight: 1, whiteSpace: 'nowrap',
    }}>
      <Icon name={isNew ? 'sparkles' : 'shield-check'} size={fs + 1} stroke={2.2}/>
      {isNew ? 'New in the App' : <span><span style={{ fontWeight: 800 }}>{count}</span> {count === 1 ? 'date' : 'dates'} attended</span>}
    </div>
  );
}

const SEARCH_ICONS = {
  'briefcase': (
    <g>
      <rect x="2" y="7" width="20" height="14" rx="2"/>
      <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
      <line x1="2" y1="13" x2="22" y2="13"/>
    </g>
  ),
  'book-open': (
    <g>
      <path d="M2 4h6a4 4 0 0 1 4 4v12a3 3 0 0 0-3-3H2z"/>
      <path d="M22 4h-6a4 4 0 0 0-4 4v12a3 3 0 0 1 3-3h7z"/>
    </g>
  ),
  'globe': (
    <g>
      <circle cx="12" cy="12" r="10"/>
      <line x1="2" y1="12" x2="22" y2="12"/>
      <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
    </g>
  ),
  'faith': (
    <g>
      <line x1="12" y1="3" x2="12" y2="21"/>
      <line x1="7" y1="9" x2="17" y2="9"/>
    </g>
  ),
  'vote': (
    <g>
      <path d="M3 19h18"/>
      <path d="M5 19V9l7-5 7 5v10"/>
      <polyline points="9 14 11 16 15 12"/>
    </g>
  ),
  'wine': (
    <g>
      <path d="M7 3h10l-1 6a4 4 0 0 1-8 0z"/>
      <line x1="12" y1="13" x2="12" y2="20"/>
      <line x1="8" y1="20" x2="16" y2="20"/>
    </g>
  ),
  'cigarette': (
    <g>
      <rect x="2" y="12" width="14" height="4" rx="1"/>
      <line x1="6" y1="12" x2="6" y2="16"/>
      <path d="M18 8c0-2 1-3 2-3"/>
      <path d="M22 8c0-2-1-3-2-3"/>
      <line x1="18" y1="12" x2="22" y2="12"/>
      <line x1="18" y1="16" x2="22" y2="16"/>
    </g>
  ),
  'leaf': (
    <path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19.2 2c1 3.5.5 8-3 11-2.5 2.5-6 2.5-7.5 1M2 21c0-3 1.85-5.36 5.08-6"/>
  ),
  'pill': (
    <g>
      <path d="M10.5 20.5a4.95 4.95 0 0 1-7-7l10-10a4.95 4.95 0 0 1 7 7z"/>
      <line x1="8.5" y1="8.5" x2="15.5" y2="15.5"/>
    </g>
  ),
  // small forward-pointing icon for the "Ahead" column
  'arrow-circle': (
    <g>
      <circle cx="12" cy="12" r="9"/>
      <polyline points="12 8 16 12 12 16"/>
      <line x1="8" y1="12" x2="16" y2="12"/>
    </g>
  ),
};

function SearchIcon({ name, size = 16, stroke = 1.8, style = {} }) {
  const g = SEARCH_ICONS[name];
  if (!g) return null;
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth={stroke}
      strokeLinecap="round" strokeLinejoin="round"
      style={{ display: 'block', flex: 'none', ...style }}>
      {g}
    </svg>
  );
}

function SectionTitle({ children, sub, style = {} }) {
  return (
    <div style={{ marginBottom: 14, ...style }}>
      <div style={{
        fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 18,
        color: 'var(--liq-fg)', letterSpacing: '-0.005em',
      }}>{children}</div>
      {sub && (
        <div style={{
          fontSize: 12, color: 'var(--liq-fg-subtle)', marginTop: 4,
          fontWeight: 500, letterSpacing: 0.01,
        }}>{sub}</div>
      )}
    </div>
  );
}

function PromptCard({ topic, answer }) {
  return (
    <div style={{
      padding: '18px 20px 20px',
      borderRadius: 20,
      background: 'var(--su-grad-lilac)',
      border: '1px solid rgba(129,42,236,0.10)',
    }}>
      <div style={{
        fontFamily: 'var(--liq-font-serif)', fontWeight: 700,
        fontStyle: 'italic',
        fontSize: 17, lineHeight: 1.3, color: 'var(--liq-primary-500)',
        margin: 0, textWrap: 'balance',
      }}>{topic}</div>
      <p style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 500,
        fontSize: 15, lineHeight: 1.5, color: 'var(--liq-fg)',
        margin: '10px 0 0', textWrap: 'pretty',
      }}>{answer}</p>
    </div>
  );
}

function VoicePromptCard({ name, topic = "Why I'm on Show Up", duration = 38 }) {
  // Deterministic waveform — 48 bars, varying height.
  const bars = React.useMemo(() => {
    const out = [];
    for (let i = 0; i < 48; i++) {
      const env = Math.sin((i / 47) * Math.PI);              // 0→1→0
      const jitter = (Math.sin(i * 12.9898) * 43758.5453) % 1;
      const v = Math.max(0.12, env * 0.85 + Math.abs(jitter) * 0.25);
      out.push(v);
    }
    return out;
  }, []);
  const mm = String(Math.floor(duration / 60)).padStart(1, '0');
  const ss = String(duration % 60).padStart(2, '0');

  return (
    <Card style={{ padding: 16 }}>
      <SectionTitle sub={`Voice prompt · ${mm}:${ss}`}>{name}'s voice</SectionTitle>

      <div style={{
        display: 'flex', alignItems: 'center', gap: 14,
        padding: '14px 14px', borderRadius: 16,
        background: 'var(--liq-bg-raised)',
      }}>
        <button style={{
          width: 46, height: 46, borderRadius: 9999,
          background: 'var(--su-grad-sunset)', color: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          flex: 'none', boxShadow: 'var(--liq-shadow-violet)',
        }}>
          <Icon name="play" size={18} style={{ marginLeft: 2, fill: 'currentColor' }}/>
        </button>

        <div style={{
          flex: 1, display: 'flex', alignItems: 'center',
          height: 36, gap: 2.5,
        }}>
          {bars.map((v, i) => (
            <div key={i} style={{
              flex: 1,
              height: `${Math.round(v * 100)}%`,
              minHeight: 4,
              borderRadius: 9999,
              background: i < 18
                ? 'var(--liq-primary-500)'
                : 'rgba(29,17,41,0.18)',
            }}/>
          ))}
        </div>

        <span style={{
          fontFamily: 'var(--liq-font-sans)', fontVariantNumeric: 'tabular-nums',
          fontSize: 13, fontWeight: 700, color: 'var(--liq-fg-subtle)',
          flex: 'none',
        }}>{mm}:{ss}</span>
      </div>

      <p style={{
        fontFamily: 'var(--liq-font-serif)', fontStyle: 'italic',
        fontSize: 14, lineHeight: 1.45, color: 'var(--liq-neutral-200)',
        margin: '12px 0 0', textWrap: 'pretty',
      }}>"{topic}"</p>
    </Card>
  );
}

function EssentialsBlock({ name, rows = [] }) {
  return (
    <Card style={{ padding: 16 }}>
      <SectionTitle>{name}'s essentials</SectionTitle>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {rows.map((r, i) => (
          <div key={i} style={{
            display: 'flex', alignItems: 'center', gap: 12,
            fontSize: 14, color: 'var(--liq-fg)',
          }}>
            <span style={{
              color: 'var(--liq-primary-500)', display: 'flex',
              width: 18, justifyContent: 'center',
            }}>
              {r.local
                ? <SearchIcon name={r.icon} size={16}/>
                : <Icon name={r.icon} size={16} stroke={2}/>}
            </span>
            <span style={{ minWidth: 76, fontSize: 11, fontWeight: 700, color: 'var(--liq-fg-subtle)', letterSpacing: 0.06, textTransform: 'uppercase' }}>{r.label}</span>
            <span style={{ flex: 1, fontSize: 14, color: 'var(--liq-fg)', lineHeight: 1.35 }}>{r.value}</span>
          </div>
        ))}
      </div>
    </Card>
  );
}

function MotivationPill({ icon = 'heart', label = 'Long-term partner' }) {
  return (
    <div style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      padding: '5px 11px', borderRadius: 9999,
      background: 'rgba(255,255,255,0.22)',
      backdropFilter: 'blur(10px)',
      border: '1px solid rgba(255,255,255,0.3)',
      color: '#fff', fontSize: 12, fontWeight: 700,
      letterSpacing: 0.02, whiteSpace: 'nowrap',
    }}>
      <Icon name={icon} size={13} stroke={2.2}/>
      {label}
    </div>
  );
}

function PhotoSlot({ name, photoIndex = 1, gradient, height = 260, radius = 24 }) {
  return (
    <PortraitPlaceholder
      name={name}
      gradient={gradient}
      height={height}
      radius={radius}
      label={false}
      photoIndex={photoIndex}
    />
  );
}

function SearchTopBar({ premium, instant = false, onStopInstant, likes = (window.likesOpenCount && window.LIKES_RECEIVED ? window.likesOpenCount(window.LIKES_RECEIVED) : 2), onLikes, filter = true, chip = true }) {
  // The likes chip — in EVERY user's Search top bar (21 Oct 2026; reverses
  // the 20 Oct "Premium has no chip"). Count = open received likes, the same
  // number as the Likes tab badge. Tap: Premium → the Likes tab (Search 03);
  // Freemium → the likes upsell screen (own story, not built: no-op until then).
  // Shown on the SEARCH and INSTANT tabs only; never on the Likes tab
  // (chip=false there, and on a profile opened from it).
  const likesChip = !chip ? null : (
    <button onClick={onLikes} aria-label={likes === 1 ? '1 like' : `${likes} likes`} style={{
      display: 'inline-flex', alignItems: 'center', gap: 6, flex: 'none',
      padding: '6px 12px', borderRadius: 9999, background: 'var(--su-grad-sunset)', border: 'none',
      color: '#fff', fontFamily: 'var(--liq-font-sans)', fontSize: 12, fontWeight: 700,
      cursor: 'pointer',
    }}>
      <Icon name="heart-filled" size={13}/>
      {likes === 1 ? '1 like' : `${likes} likes`}
    </button>
  );
  const filterBtn = !filter ? null : (
    <button aria-label="Filters" style={{
      width: 36, height: 36, borderRadius: 9999, border: '1px solid var(--liq-border)',
      background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center',
      color: 'var(--liq-fg)', flex: 'none',
    }}>
      <Icon name="filter" size={16}/>
    </button>
  );
  const tag = premium && window.PremiumTag ? <window.PremiumTag size={10}/> : null;
  if (instant) {
    return (
      <div style={{ padding: '4px 16px 8px', display: 'flex', flexDirection: 'column', gap: 8, background: 'var(--liq-bg)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Wordmark size={20}/>
          <div style={{ flex: 1, minWidth: 0 }}/>
          {tag}
          {likesChip}
          {filterBtn}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 5, flex: 'none',
            padding: '5px 10px', borderRadius: 9999, background: 'var(--su-grad-sunset)',
            color: '#fff', fontSize: 11, fontWeight: 800, letterSpacing: 0.04,
          }}>
            <Icon name="zap" size={11} stroke={2.4}/>INSTANT
          </span>
          <div style={{ flex: 1, minWidth: 0 }}/>
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 8, flex: 'none', whiteSpace: 'nowrap',
            padding: '4px 4px 4px 12px', borderRadius: 9999, background: '#fff',
            border: '1px solid var(--liq-border)', fontSize: 12, fontWeight: 800,
            color: 'var(--liq-orange-500)', fontVariantNumeric: 'tabular-nums',
          }}>
            <span style={{ width: 7, height: 7, borderRadius: 9999, background: 'var(--liq-orange-500)' }}/>
            Live · 2:43 left
            <button onClick={onStopInstant} title="Stop instant mode" aria-label="Stop instant mode" style={{
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              width: 24, height: 24, borderRadius: 9999, border: 'none',
              background: 'var(--liq-orange-500)', color: '#fff', flex: 'none',
            }}>
              <Icon name="x" size={14} stroke={2.6}/>
            </button>
          </div>
        </div>
      </div>
    );
  }
  return (
    <div style={{ padding: '4px 16px 8px', display: 'flex', alignItems: 'center', gap: 10, background: 'var(--liq-bg)' }}>
      <Wordmark size={20}/>
      <div style={{ flex: 1 }}/>
      {tag}
      {likesChip}
      {filterBtn}
    </div>
  );
}

function ActionBtn({ icon, label, tone, big = false, onClick }) {
  const styles = {
    danger:  { bg: '#fff', color: 'var(--liq-danger)', border: '1.5px solid rgba(251,50,59,0.4)' },
    muted:   { bg: '#fff', color: 'var(--liq-fg-muted)', border: '1.5px solid var(--liq-border)' },
    primary: { bg: 'var(--su-grad-sunset)', color: '#fff', border: 'none', shadow: 'var(--liq-shadow-violet)' },
  }[tone];
  const s = big ? 64 : 56;
  return (
    <button onClick={onClick} style={{
      width: big ? 'auto' : s, height: s,
      padding: big ? '0 24px' : 0,
      borderRadius: 9999,
      display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
      background: styles.bg, color: styles.color, border: styles.border,
      boxShadow: styles.shadow || 'var(--liq-shadow-sm)',
      fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14,
    }}>
      <Icon name={icon} size={big ? 22 : 22} stroke={tone === 'primary' ? 2.4 : 2}/>
      {big && <span>{label}</span>}
    </button>
  );
}

function LikeMessageModal({ name, msg, setMsg, onCancel, onSend, onSkip }) {
  return (
    <ModalShell onCancel={onCancel}>
      <div style={{ padding: 24 }}>
        <h3 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26,
          letterSpacing: '-0.01em', margin: '0 0 8px', color: 'var(--liq-fg)',
        }}><em>Optional</em> message to {name}</h3>
        <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: '0 0 18px', lineHeight: 1.5 }}>
          Premium users can see who sent them a like and can see your message. Adding a personal message increases your chances of getting matched.
        </p>
        <textarea
          value={msg} maxLength={100} onChange={e => setMsg(e.target.value.slice(0, 100))}
          style={{
            width: '100%', minHeight: 120, padding: 16, borderRadius: 16,
            border: '1.5px solid var(--liq-primary-500)', background: '#fff',
            fontFamily: 'var(--liq-font-sans)', fontSize: 15, color: 'var(--liq-fg)',
            outline: 'none', resize: 'none', lineHeight: 1.5,
          }}/>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'var(--liq-fg-subtle)', marginTop: 6 }}>
          <span></span>
          <span>{msg.length} / 100</span>
        </div>
        <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
          <Button variant="ghost" size="md" fullWidth onClick={onSkip || onCancel}>Skip message</Button>
          <Button variant="sunset" size="md" fullWidth onClick={onSend}
            leading={<Icon name="send" size={16} stroke={2.2}/>}>Send message</Button>
        </div>
      </div>
    </ModalShell>
  );
}

function LikeConfirmModal({ name, onCancel, onSend, instant = false, day = 'today', cta = 'Send like' }) {
  const rows = instant ? [
    { icon: 'zap', t: <>You meet <strong style={{ fontWeight: 800, color: 'var(--liq-fg)' }}>right now</strong> — as soon as you both can get there.</> },
    { icon: 'map-pin', t: 'A safe, curated halfway spot — picked between the two of you.' },
    { icon: 'navigation', t: 'Your meet time is set from your travel distance and how soon you each said you can head out.' },
    { icon: 'message-circle', t: 'Stuck for words? Match Mate has topics, ice breakers, and quick games.' },
    { icon: 'shield-check', t: "You meet who you matched — the Show-up Rate keeps it real." },
  ] : [
    { icon: 'map-pin', t: 'A safe, curated meeting spot — so you can focus on each other, not on logistics.' },
    { icon: 'clock', t: 'A time that fits both of your stated availability windows.' },
    { icon: 'message-circle', t: 'Stuck for words? Match Mate has topics, ice breakers, and quick games.' },
    { icon: 'sparkles', t: "Only 30 minutes committed. If there's no spark — leave with a smile." },
    { icon: 'shield-check', t: "You meet who you matched — the Show-up Rate keeps it real." },
  ];
  return (
    <ModalShell onCancel={onCancel}>
      <div style={{ padding: 24 }}>
        <div style={{
          padding: '14px 16px', borderRadius: 14, background: 'var(--su-grad-blush)',
          textAlign: 'center', marginBottom: 18,
        }}>
          <div className="su-underlined" style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 22, color: 'var(--liq-primary)' }}><em>Be sure.</em></div>
          <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--liq-fg)', marginTop: 4, lineHeight: 1.45 }}>
            If {name} becomes your match, you two have a <span style={{ fontWeight: 700, color: 'var(--liq-primary-500)' }}>confirmed, binding date{instant ? ' — right now' : ' ' + day}</span>.
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ textAlign: 'center', fontFamily: 'var(--liq-font-serif)', fontStyle: 'italic', fontSize: 16, color: 'var(--liq-primary-500)' }}>What happens after a match?</div>
          {rows.map((row, i) => (
            <div key={i} style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
              <div style={{
                width: 28, height: 28, borderRadius: 8, flex: 'none',
                background: 'var(--liq-bg-raised)', color: 'var(--liq-primary-500)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon name={row.icon} size={14} stroke={2.2}/>
              </div>
              <div style={{ fontSize: 13, lineHeight: 1.45, color: 'var(--liq-neutral-200)' }}>{row.t}</div>
            </div>
          ))}
        </div>

        <div style={{ display: 'flex', gap: 10, marginTop: 22 }}>
          <Button variant="ghost" size="md" fullWidth onClick={onCancel}>Cancel</Button>
          <Button variant="sunset" size="md" fullWidth onClick={onSend}
            leading={<Icon name="send" size={16} stroke={2.2}/>}>{cta}</Button>
        </div>
      </div>
    </ModalShell>
  );
}

function ModalShell({ onCancel, children }) {
  return (
    <div style={{
      position: 'absolute', inset: 0, background: 'rgba(29,17,41,0.42)',
      backdropFilter: 'blur(8px)', zIndex: 10,
      display: 'flex', alignItems: 'flex-end', justifyContent: 'center',
    }} onClick={onCancel}>
      <div onClick={e => e.stopPropagation()} style={{
        width: '100%', maxHeight: '88%', overflowY: 'auto',
        background: 'var(--liq-bg)', borderRadius: '28px 28px 0 0',
        boxShadow: '0 -8px 32px rgba(46,1,71,0.18)',
      }} className="su-noscroll">
        <div style={{ display: 'flex', justifyContent: 'center', padding: '8px 0 0' }}>
          <div style={{ width: 36, height: 4, borderRadius: 999, background: 'var(--liq-fg-faint)' }}/>
        </div>
        {children}
      </div>
    </div>
  );
}

const SEARCH_V2_PROFILE = {
  name: 'Leonie', age: 28, dist: 1.2, district: 'Neukölln, Berlin',
  attended: 4,
  meetAt: '19:00', meetDay: 'today',
  motivation: 'long_term',
  uplift: "I'll take you on a rafting tour on Wannsee — picnic included",
  vibes: ['coffee_chat', 'sunset', 'deep_talk', 'playful', 'no_rush'],
  prompts: [
    { topic: "On a first date, I usually…", answer: "Talk about anything real. Not jobs, not pets, not the weather. The thing actually on your mind this week. Bring it. I'll listen." },
    { topic: "I'd cross town for…", answer: "A proper conversation. An old cinema. The 8pm walk after a long day." },
    { topic: "The hill I'm willing to die on…", answer: "Showing up. Cancelling last minute isn't a scheduling problem, it's an answer." },
  ],
  voice: { prompt: "A sound that always makes me feel relaxed", duration: 14 },
  video: { prompt: "What my ideal sunny morning looks like", duration: 10 },
  height: 172, gender: 'Woman', orientation: 'Straight',
  datingLanguages: ['German', 'English', 'French'],
  education: 'University degree', religion: 'Spiritual / other', politics: 'Mid-left',
  howILive: { activity: 'Active', diet: 'Flexitarian', alcohol: 'Socially', smoking: 'No', rhythm: 'Early bird', social: 'Balanced' },
  interests: ['Yoga/Pilates', 'Hiking', 'Cooking', 'Brunch', 'Street food & markets', 'Live music', 'Museums & exhibitions', 'Reading', 'Board games', 'City breaks'],
  photoColors: [
    'linear-gradient(155deg, #FCC8A7 0%, #E89B7A 40%, #B86B5C 100%)',
    'linear-gradient(155deg, #D6E8E6 0%, #99C7C3 50%, #4D8F88 100%)',
    'linear-gradient(155deg, #F5C8A2 0%, #B2C5B4 70%, #7A9A86 100%)',
    'linear-gradient(155deg, #FCDCDC 0%, #E69CB5 50%, #B85B7C 100%)',
    'linear-gradient(155deg, #EAD9F4 0%, #C8A6E8 40%, #8B5CF6 100%)',
    'linear-gradient(155deg, #FFE2C5 0%, #FFB58A 40%, #E07A4C 100%)',
  ],
  charms: [
    { icon: 'star', label: 'Lovely smile (4)' },
    { icon: 'gift', label: 'From Tom' },
    { icon: 'star', label: '5★ by Anna' },
  ],
};

// "~1 km" — never the exact distance.
function searchDistance(km) {
  return km < 1 ? '< 1 km' : `~${Math.round(km)} km`;
}

// The viewer — their own active Check-In + profile, used only for overlap.
const SEARCH_VIEWER = {
  vibes: ['coffee_chat', 'sunset', 'no_rush'],
  interests: ['Hiking', 'Live music', 'Reading', 'Board games', 'Climbing'],
};

function SharedChip({ children }) {
  return <Chip tone="orange" selected leading={<Icon name="check" size={13} stroke={2.6}/>}>{children}</Chip>;
}

function SharedVibesRow({ name, ids, label }) {
  if (!ids.length) return null;
  const shown = ids.slice(0, 3), more = ids.length - shown.length;
  return (
    <Card style={{ padding: '14px 16px', background: '#fff', border: '1.5px solid var(--liq-orange-500)' }}>
      <div style={{ fontSize: 11, fontWeight: 800, color: 'var(--liq-orange-500)', textTransform: 'uppercase', letterSpacing: 0.1, marginBottom: 10 }}>You and {name} both want today</div>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        {shown.map(id => <SharedChip key={id}>{label[id] || id}</SharedChip>)}
        {more > 0 && <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--liq-orange-500)' }}>+{more}</span>}
      </div>
    </Card>
  );
}

function SharedInterestsBlock({ name, interests, shared }) {
  const n = interests.filter(k => shared.has(k)).length;
  return (
    <Card style={{ padding: 16 }}>
      <SectionTitle sub={`${interests.length} things ${name} is into${n ? ` · ${n} in common` : ''}`}>{name}'s interests</SectionTitle>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {interests.map(k => shared.has(k) ? <SharedChip key={k}>{k}</SharedChip> : <Chip key={k} tone="orange">{k}</Chip>)}
      </div>
    </Card>
  );
}

// Minimum content — only the mandatory fields: name (age hidden), 4 photos,
// 1 prompt, motivation, 1 vibe. Overlap only tomorrow, no dates yet.
const SEARCH_V2_PROFILE_MIN = {
  name: 'Leonie', age: null, dist: 1.2, district: 'Neukölln, Berlin',
  attended: 0, isNew: true, meetAt: '19:00', meetDay: 'tomorrow', motivation: 'long_term',
  vibes: ['coffee_chat'],
  prompts: [{ topic: "I'd cross town for…", answer: "A proper conversation. An old cinema. The 8pm walk after a long day." }],
  photoColors: SEARCH_V2_PROFILE.photoColors.slice(0, 4),
};

const HOW_I_LIVE_ICONS = {
  activity: { icon: 'zap' }, diet: { local: 'leaf' }, alcohol: { local: 'wine' },
  smoking: { local: 'cigarette' }, rhythm: { icon: 'clock' }, social: { icon: 'users' },
};

function HowILiveBlock({ name, values = {} }) {
  const groups = (window.LIFESTYLE_GROUPS || []).filter(g => values[g.id]);
  if (!groups.length) return null;
  return (
    <Card style={{ padding: 16 }}>
      <SectionTitle sub="How I live">{name}'s everyday</SectionTitle>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        {groups.map(g => {
          const ic = HOW_I_LIVE_ICONS[g.id] || {};
          return (
            <div key={g.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px', borderRadius: 14, background: 'var(--liq-bg-raised)', minWidth: 0 }}>
              <span style={{ width: 30, height: 30, borderRadius: 10, background: '#fff', color: 'var(--liq-primary-500)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flex: 'none' }}>
                {ic.local ? <SearchIcon name={ic.local} size={16}/> : <Icon name={ic.icon} size={15} stroke={2}/>}
              </span>
              <div style={{ minWidth: 0 }}>
                <div style={{ fontSize: 10.5, fontWeight: 700, color: 'var(--liq-fg-subtle)', letterSpacing: 0.06, textTransform: 'uppercase' }}>{g.label}</div>
                <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--liq-fg)', lineHeight: 1.25, marginTop: 1 }}>{values[g.id]}</div>
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

function VideoPromptCardV2({ name, prompt, duration, gradient }) {
  return (
    <Card style={{ padding: 16 }}>
      <SectionTitle sub={`Video prompt · 0:${String(duration).padStart(2, '0')}`}>{name}'s video</SectionTitle>
      <div style={{ height: 220, borderRadius: 16, background: gradient, position: 'relative', overflow: 'hidden' }}>
        <div style={{ position: 'absolute', inset: 0, background: 'radial-gradient(circle at 40% 35%, rgba(255,255,255,0.30), transparent 60%)' }}/>
        <button style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <span style={{ width: 58, height: 58, borderRadius: 9999, background: 'rgba(255,255,255,0.92)', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 8px 24px rgba(0,0,0,0.16)' }}>
            <Icon name="play" size={22} style={{ color: 'var(--liq-fg)', marginLeft: 3, fill: 'currentColor' }}/>
          </span>
        </button>
      </div>
      <p style={{ fontFamily: 'var(--liq-font-serif)', fontStyle: 'italic', fontSize: 14, lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: '12px 0 0', textWrap: 'pretty' }}>"{prompt}"</p>
    </Card>
  );
}

// Search 01 — actions coach (21 Oct 2026, revised the same day)
//   Shown over the first profile of every Search visit — Freemium and
//   Premium — until the user taps "Don't show again" (the only act stored,
//   server-side, per user). A scrim covers the screen except the action bar,
//   which is lifted above it; the card sits in the bar's own column, so its
//   position follows the bar (never a fixed Y). Buttons are inert while it
//   shows. "Got it", a scrim tap or Android back close it for this visit
//   only. Rows mirror the buttons' tones.
const SEARCH_COACH_ROWS = [
  { icon: 'x',     tone: 'danger', t: 'Not interested', d: "You won't see this profile again." },
  { icon: 'clock', tone: 'muted',  t: 'Not now',        d: 'This profile can come back at your next Check-In.' },
  { icon: 'heart', tone: 'primary', t: 'Like',          d: "You'd like to meet. If they like you back, you have a date." },
];
function SearchActionsCoach({ onDone }) {
  const chip = {
    danger:  { background: '#fff', color: 'var(--liq-danger)', border: '1.5px solid rgba(251,50,59,0.4)' },
    muted:   { background: '#fff', color: 'var(--liq-fg-muted)', border: '1.5px solid var(--liq-border)' },
    primary: { background: 'var(--su-grad-sunset)', color: '#fff', border: 'none' },
  };
  return (
    <div role="dialog" aria-label="What the three buttons do" style={{ position: 'relative', background: '#fff', borderRadius: 24, padding: '20px 18px 16px', boxShadow: '0 16px 40px rgba(29,17,41,0.28)', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <h3 className="su-underlined" style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 22, lineHeight: 1.15, letterSpacing: '-0.01em', margin: 0, color: 'var(--liq-fg)' }}>Three ways to <em>decide</em></h3>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {SEARCH_COACH_ROWS.map(r => (
          <div key={r.t} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{ width: 38, height: 38, borderRadius: 9999, flex: 'none', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', ...chip[r.tone] }}>
              <Icon name={r.icon} size={18} stroke={r.tone === 'primary' ? 2.4 : 2}/>
            </span>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--liq-fg)', lineHeight: 1.2 }}>{r.t}</div>
              <div style={{ fontSize: 13.5, fontWeight: 500, color: 'var(--liq-fg-muted)', lineHeight: 1.4, textWrap: 'pretty' }}>{r.d}</div>
            </div>
          </div>
        ))}
      </div>
      <div style={{ display: 'flex', gap: 10 }}>
        <Button variant="ghost" size="md" style={{ flex: 1, border: '1px solid var(--liq-border)' }} onClick={() => onDone && onDone('dont_show_again')}>Don't show again</Button>
        <Button variant="violet" size="md" style={{ flex: 1 }} onClick={() => onDone && onDone('got_it')}>Got it</Button>
      </div>
      <span aria-hidden="true" style={{ position: 'absolute', left: '50%', bottom: -7, width: 14, height: 14, background: '#fff', transform: 'translateX(-50%) rotate(45deg)', borderRadius: 3 }}/>
    </div>
  );
}

function ScreenSearchProfile({ profile = SEARCH_V2_PROFILE, viewer = SEARCH_VIEWER, tall = false, tallHeight = 4300, initialModal = null, instant = false, premium = false, likedYou = false, onBack, backLabel = 'All profiles', onAction, tab = 'search', coach = false }) {
  // tab: which TabBar tab owns this profile — 'search' (queue, grid) or
  // 'likes' (opened from Likes received, Search 03: Likes active, no filter).
  const p = profile;
  const [modal, setModal] = React.useState(initialModal);
  const [coaching, setCoaching] = React.useState(coach);
  const act = fn => coaching ? undefined : fn;
  const [msg, setMsg] = React.useState(`Hi ${p.name}, I love the vibe of your profile. Would be great to meet you ${instant ? 'right now' : p.meetDay} :)`);
  const mot = (window.CHECKIN_MOTIVATIONS || []).find(m => m.id === p.motivation);
  const vibeLabel = window.CHECKIN_VIBE_LABEL || {};
  const prompts = p.prompts || [];
  const photos = p.photoColors || [];
  const myVibes = new Set((viewer && viewer.vibes) || []);
  const sharedVibes = (p.vibes || []).filter(id => myVibes.has(id));
  const myInterests = new Set((viewer && viewer.interests) || []);

  const essentials = [
    p.height != null && { icon: 'sliders', label: 'Height', value: `${p.height} cm` },
    p.gender && { icon: 'user', label: 'Gender', value: p.gender },
    p.orientation && { icon: 'heart', label: 'Orientation', value: p.orientation },
    p.datingLanguages && p.datingLanguages.length && { icon: 'globe', local: true, label: 'Dates in', value: p.datingLanguages.join(', ') },
    p.education && { icon: 'book-open', local: true, label: 'Education', value: p.education },
    p.religion && { icon: 'faith', local: true, label: 'Religion', value: p.religion },
    p.politics && { icon: 'vote', local: true, label: 'Politics', value: p.politics },
  ].filter(Boolean);

  return (
    <Phone height={tall ? tallHeight : 844} scroll={!tall}>
      <SearchTopBar instant={instant} premium={premium} filter={tab !== 'likes'} chip={tab !== 'likes'}/>
      {onBack && (
        <div style={{ padding: '0 16px 10px' }}>
          <button onClick={onBack} style={{ display: 'inline-flex', alignItems: 'center', gap: 2, height: 36, padding: '0 14px 0 8px', borderRadius: 9999, border: '1px solid var(--liq-border)', background: '#fff', fontFamily: 'var(--liq-font-sans)', fontSize: 13, fontWeight: 700, color: 'var(--liq-fg)', cursor: 'pointer', whiteSpace: 'nowrap' }}>
            <Icon name="chevron-left" size={18} stroke={2.2}/>{backLabel}
          </button>
        </div>
      )}
      <div data-search-stack style={{ padding: '0 16px 16px', display: 'flex', flexDirection: 'column', gap: 14 }}>

        <div style={{ position: 'relative' }}>
          <PortraitPlaceholder name={p.name} height={460} gradient={photos[0]} radius={28} label={false} photoIndex={0}>
            <div style={{ position: 'absolute', top: 16, left: 16, right: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
              <div style={{ padding: '6px 12px', borderRadius: 9999, background: 'rgba(255,255,255,0.85)', backdropFilter: 'blur(12px)', fontSize: 12, fontWeight: 700, color: 'var(--liq-fg)', display: 'flex', alignItems: 'center', gap: 6 }}>
                <Icon name="map-pin" size={13} stroke={2.2}/>
                {searchDistance(p.dist)} · {p.district}
              </div>
              {likedYou && window.PremiumLikedYouBadge && <window.PremiumLikedYouBadge/>}
            </div>
            <div style={{ position: 'absolute', left: 18, right: 18, bottom: 80, display: 'flex', flexDirection: 'column', gap: 8, color: '#fff', textShadow: '0 2px 12px rgba(0,0,0,0.35)' }}>
              <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 34, letterSpacing: '-0.01em', lineHeight: 1 }}>
                {p.name}{p.age != null ? `, ${p.age}` : ''}
              </div>
              {mot && <div><MotivationPill icon={mot.icon} label={mot.t}/></div>}
            </div>
          </PortraitPlaceholder>
          <Card style={{ position: 'absolute', left: 16, right: 16, bottom: -22, padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 38, height: 38, borderRadius: 12, background: instant ? 'rgba(0,171,85,0.12)' : 'rgba(254,104,57,0.12)', color: instant ? '#0A7A47' : 'var(--liq-orange-500)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Icon name={instant ? 'zap' : 'clock'} size={18} stroke={2.2}/>
            </div>
            {instant ? (
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 12, color: '#0A7A47', fontWeight: 700, display: 'flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                  <span style={{ width: 7, height: 7, borderRadius: 9999, background: '#0A7A47' }}/>Live right now
                </div>
                <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--liq-fg)', whiteSpace: 'nowrap' }}>~{p.travelMin ?? 12} min away</div>
              </div>
            ) : (
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 12, color: 'var(--liq-fg-subtle)', fontWeight: 600, whiteSpace: 'nowrap' }}>You could meet at:</div>
                <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--liq-fg)', whiteSpace: 'nowrap' }}>{p.meetAt} {p.meetDay}</div>
              </div>
            )}
            <div style={{ flex: 'none' }}><ShowUpCount count={p.attended} size="xs"/></div>
          </Card>
        </div>

        <div style={{ height: 18 }}/>

        <SharedVibesRow name={p.name} ids={sharedVibes} label={vibeLabel}/>

        {p.uplift && (
          <Card style={{ padding: 16, background: 'var(--su-grad-blush)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
              <Icon name="sparkles" size={14} stroke={2.4} style={{ color: 'var(--liq-orange-500)' }}/>
              <span style={{ fontSize: 11, fontWeight: 800, color: 'var(--liq-orange-500)', textTransform: 'uppercase', letterSpacing: 0.1 }}>{p.name}'s highlight</span>
            </div>
            <p className="su-underlined" style={{ fontFamily: 'var(--liq-font-serif)', fontStyle: 'italic', fontWeight: 500, fontSize: 18, lineHeight: 1.35, color: 'var(--liq-fg)', margin: 0, textWrap: 'pretty' }}>"{p.uplift}"</p>
          </Card>
        )}

        <Card style={{ padding: 16 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--liq-fg-subtle)', textTransform: 'uppercase', letterSpacing: 0.08, marginBottom: 10 }}>{p.name}'s vibe today</div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {(p.vibes || []).map(id => myVibes.has(id) ? <SharedChip key={id}>{vibeLabel[id] || id}</SharedChip> : <Chip key={id} tone="orange">{vibeLabel[id] || id}</Chip>)}
          </div>
        </Card>

        {photos[1] && <PhotoSlot name={p.name} photoIndex={1} gradient={photos[1]} height={300}/>}
        {prompts[0] && <PromptCard topic={prompts[0].topic} answer={prompts[0].answer}/>}
        {p.voice && <VoicePromptCard name={p.name} topic={p.voice.prompt} duration={p.voice.duration}/>}
        {essentials.length > 0 && <EssentialsBlock name={p.name} rows={essentials}/>}
        {photos[2] && <PhotoSlot name={p.name} photoIndex={2} gradient={photos[2]} height={300}/>}
        {prompts[1] && <PromptCard topic={prompts[1].topic} answer={prompts[1].answer}/>}
        {p.video && <VideoPromptCardV2 name={p.name} prompt={p.video.prompt} duration={p.video.duration} gradient={photos[3]}/>}
        <HowILiveBlock name={p.name} values={p.howILive}/>
        {p.interests && p.interests.length > 0 && <SharedInterestsBlock name={p.name} interests={p.interests} shared={myInterests}/>}
        {prompts[2] && <PromptCard topic={prompts[2].topic} answer={prompts[2].answer}/>}
        {photos[3] && photos[4] ? (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <PhotoSlot name={p.name} photoIndex={3} gradient={photos[3]} height={220} radius={20}/>
            <PhotoSlot name={p.name} photoIndex={4} gradient={photos[4]} height={220} radius={20}/>
          </div>
        ) : photos[3] ? <PhotoSlot name={p.name} photoIndex={3} gradient={photos[3]} height={300}/> : null}
        {photos[5] && <PhotoSlot name={p.name} photoIndex={5} gradient={photos[5]} height={300}/>}

        {p.charms && p.charms.length > 0 && (
          <Card style={{ padding: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--liq-fg-subtle)', textTransform: 'uppercase', letterSpacing: 0.08, marginBottom: 10 }}>Charms from past dates</div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {p.charms.map((b, i) => (
                <div key={i} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '6px 12px', borderRadius: 9999, background: 'var(--liq-bg-raised)', fontSize: 12, fontWeight: 600, color: 'var(--liq-fg)' }}>
                  <Icon name={b.icon} size={13} stroke={2}/>{b.label}
                </div>
              ))}
            </div>
          </Card>
        )}

        <div style={{ height: 80 }}/>
      </div>

      {!tall && (
        <div style={{ position: 'sticky', bottom: 64, left: 0, right: 0, padding: '12px 16px', background: coaching ? 'transparent' : 'linear-gradient(180deg, rgba(255,251,247,0) 0%, rgba(255,251,247,0.95) 35%, rgba(255,251,247,1) 100%)', display: 'flex', flexDirection: 'column', gap: 14, zIndex: coaching ? 11 : 3 }}>
          {coaching && <SearchActionsCoach onDone={() => setCoaching(false)}/>}
          <div style={{ display: 'flex', gap: 10, alignItems: 'center', justifyContent: 'center' }}>
            <ActionBtn icon="x" label="Not interested" tone="danger" onClick={act(() => onAction && onAction('not_interested'))}/>
            <ActionBtn icon="clock" label="Not now" tone="muted" onClick={act(() => onAction && onAction('not_now'))}/>
            <ActionBtn icon={likedYou ? 'check' : 'heart'} label={likedYou ? 'Confirm date' : 'Like'} tone="primary" big onClick={act(() => setModal('confirm'))}/>
          </div>
        </div>
      )}
      {coaching && <div onClick={() => setCoaching(false)} style={{ position: 'absolute', inset: 0, background: 'rgba(29,17,41,0.62)', zIndex: 10 }}/>}
      <TabBar active={instant ? 'instant' : tab} likes={window.LIKES_RECEIVED ? window.likesOpenCount(window.LIKES_RECEIVED) : 0}/>
      {modal === 'confirm' && <LikeConfirmModal name={p.name} day={p.meetDay} instant={instant} mutual={likedYou} cta={likedYou ? 'Confirm date' : 'Continue'} onCancel={() => setModal(null)} onSend={likedYou ? () => { setModal(null); onAction && onAction('confirmed'); } : () => setModal('message')}/>}
      {modal === 'message' && <LikeMessageModal name={p.name} msg={msg} setMsg={setMsg} onCancel={() => setModal(null)} onSkip={() => { setModal(null); onAction && onAction('liked'); }} onSend={() => { setModal(null); onAction && onAction('liked'); }}/>}
    </Phone>
  );
}


Object.assign(window, {
  ShowUpCount, ScreenSearchProfile, SEARCH_V2_PROFILE, SEARCH_V2_PROFILE_MIN, SEARCH_VIEWER,
  searchDistance, SharedChip, SharedVibesRow, SharedInterestsBlock, HowILiveBlock, VideoPromptCardV2,
  SearchTopBar, ActionBtn, ModalShell, LikeConfirmModal, LikeMessageModal,
  SearchActionsCoach, SEARCH_COACH_ROWS,
});
