// ═════════════════════════════════════════════════════════════
// Date 05 — Change meet time and place · REFERENCE IMPLEMENTATION
// Extracted from ui_kits/show-up/screens-date-change.jsx and
// screens-date-chat.jsx (10 Nov 2026, incl. failure states K–N). The kit is the source of truth; this
// file is regenerated from it, never edited by hand.
//
// What this ticket builds (spec sheet frames A–N):
//   A TimeChangeSheet          — full-screen time picker: later today, the match's availability, 15-min slots
//   B PlaceChangeSheet         — full-screen place picker: public venues only (Google Places)
//   ChangeFullSheet            — the shared picker shell: list scrolls, frosted footer pinned (BindingNote + 2 buttons)
//   C ChangeSuggestionCard     — the structured card in the thread: pending · agreed · replaced · expired
//   D/G ChangeTag + BindingNote — the purple time + place card: your own waiting suggestion, or "Changed · was …"
//   E/J CoordinateModule unread.request — the orange "Change request" card. ONLY the `request` branch and the
//                                locked card's two tiles (built hidden by Date 01) are this ticket.
//   F the receiver's answer in the thread (I agree / Suggest another)
//   H expiry: system line + card marked Expired
//   changeDeadline()           — binding meet time − 30 min. Counts from the BINDING time, never the suggested one.
//   K–N ChangeError · ChangeRetryLine · placesState — Suggest failed, I agree failed, Places failed, no place found
//
// Kit frame props:
//   A ScreenChatChange initialSheet="time" initialPick="19:30" · B … initialSheet="place" initialPick="lenz"
//   C ScreenChatChange stage="sent" · D ScreenMatchCoord coordOpen change={[time pending by you, place pending by you]}
//   E ScreenMatchCoord coordOpen coordUnread={{ time, request: {kind:'time',…} }} change={{ …by:'them' }}
//   F ScreenChatChange role="receiver" · G ScreenMatchCoord coordOpen change={{ kind:'time', status:'agreed' }}
//   H ScreenChatChange stage="expired" · I ScreenMatchCoord earlyChange
//   J ScreenMatchCoord earlyChange coordUnread={{ time, request }} change={{ …by:'them' }}
//   K ScreenChatChange initialSheet="time" initialPick="19:30" sendError · L … coordUnread={{ request: { …, agreeError: true } }}
//   M ScreenChatChange initialSheet="place" placesState="error" · N … initialSheet="place" initialQuery="sushi"
//
// Demo data, not behaviour: CHANGE_SLOTS and CHANGE_PLACES are fixtures. The slot list and the
// place list come from the server (availability · Google Places) — never computed on the client.
//
// Consumed, not built here:
//   ScreenMatchCoord, the time + place card, CoordinateModule (locked / available / unread) → Date 01
//   ChatHeader, ThreadScroll, ChatBubble, SystemLine, PersistentComposer, CHAT_REASONS, ReasonBadge → Date 02
//   Phone · Card · Icon · Avatar → design_handoff_showup/components/shared.jsx
//
// Read values from here, not from the PNG.
// ═════════════════════════════════════════════════════════════

const CHANGE_ORIGINAL = { time: '19:00', place: { name: 'Cafe Krone', type: 'Café', address: 'Oranienstrasse 42, Kreuzberg' } };
const CHANGE_AVAIL_UNTIL = '22:00';
const CHANGE_SLOTS = ['19:15', '19:30', '19:45', '20:00', '20:15', '20:30', '20:45', '21:00', '21:15', '21:30'];
const CHANGE_PLACES = [
  { id: 'lenz',   name: 'Bar Lenz',        type: 'Bar',        address: 'Lausitzer Platz 9, Kreuzberg',  dist: '350 m' },
  { id: 'nove',   name: 'Osteria Nove',    type: 'Restaurant', address: 'Wiener Str. 14, Kreuzberg',      dist: '600 m' },
  { id: 'fraeu',  name: 'Café Fräulein',   type: 'Café',       address: 'Manteuffelstr. 30, Kreuzberg',  dist: '800 m' },
  { id: 'kanal',  name: 'Kanal Bar',       type: 'Bar',        address: 'Paul-Lincke-Ufer 21, Kreuzberg', dist: '1.1 km' },
  { id: 'linde',  name: 'Lindenblatt',     type: 'Restaurant', address: 'Skalitzer Str. 82, Kreuzberg',  dist: '1.4 km' },
];

const changeSheetBtn = {
  primary: { width: '100%', height: 56, borderRadius: 18, border: 'none', cursor: 'pointer', background: 'var(--su-grad-sunset)', color: '#fff', fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 16.5, boxShadow: 'var(--liq-shadow-cta)' },
  secondary: { width: '100%', height: 50, borderRadius: 16, cursor: 'pointer', background: '#fff', border: '1px solid var(--liq-border-soft)', fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14.5, color: 'var(--liq-fg-muted)' },
};

// Binding rule — shown in the picker, on the sent card and on the main screen
// while a suggestion is waiting.
// Answer deadline = binding meet time − 30 min (decided 3 Nov 2026). Counts
// from the BINDING time, never the suggested one.
function changeDeadline(t) {
  const [h, m] = t.split(':').map(Number);
  const x = h * 60 + m - 30;
  return `${String(Math.floor(x / 60)).padStart(2, '0')}:${String(x % 60).padStart(2, '0')}`;
}

// youDecide: the match suggested it, so the binding holds until YOU agree.
function BindingNote({ name = 'Leonie', time = CHANGE_ORIGINAL.time, place = CHANGE_ORIGINAL.place.name, compact = false, youDecide = false }) {
  return (
    <div style={{
      display: 'flex', gap: 10, alignItems: 'flex-start',
      padding: compact ? '9px 11px' : '12px 14px', borderRadius: 14,
      background: 'rgba(245,158,11,0.09)', border: '1px solid rgba(217,119,6,0.28)',
    }}>
      <Icon name="alert-circle" size={compact ? 15 : 17} stroke={2.2} style={{ color: '#b45309', flex: 'none', marginTop: 1 }}/>
      <div style={{ fontSize: compact ? 12 : 13, lineHeight: 1.45, color: '#78350f', textWrap: 'pretty' }}>
        <strong style={{ fontWeight: 700 }}>{time} at {place} stays binding unless {youDecide ? 'you agree' : `${name} agrees`} by {changeDeadline(time)}.</strong> Not showing up there counts as a no-show.
      </div>
    </div>
  );
}

// Failure states (10 Nov 2026) — Date 02's patterns. ChangeError: the danger box
// (as Couldn't end the date) · ChangeRetryLine: the tappable red line (as Couldn't send).
function ChangeError({ children }) {
  return (
    <div role="alert" style={{ display: 'flex', alignItems: 'flex-start', gap: 9, padding: '11px 13px', borderRadius: 14, background: 'rgba(251,50,59,0.08)', border: '1px solid rgba(251,50,59,0.20)' }}>
      <Icon name="alert-circle" size={17} stroke={2.2} style={{ color: 'var(--liq-danger-fg)', flex: 'none', marginTop: 1 }}/>
      <span style={{ fontSize: 13.5, fontWeight: 600, lineHeight: 1.4, color: 'var(--liq-fg)', textWrap: 'pretty' }}>{children}</span>
    </div>
  );
}
function ChangeRetryLine({ onRetry, children, align = 'flex-start' }) {
  return (
    <button role="alert" onClick={onRetry} style={{ alignSelf: align, minHeight: 44, display: 'inline-flex', alignItems: 'center', gap: 6, padding: '0 2px', background: 'transparent', border: 'none', textAlign: 'left', fontFamily: 'var(--liq-font-sans)', fontSize: 12, fontWeight: 600, lineHeight: 1.35, color: 'var(--liq-danger-fg)', cursor: 'pointer' }}>
      <Icon name="alert-circle" size={14} stroke={2.4} style={{ flex: 'none' }}/>
      <span style={{ textWrap: 'pretty' }}>{children}</span>
    </button>
  );
}

// Full-screen picker shell — the list scrolls underneath; the binding note and
// both buttons are pinned at the bottom, always above the fold, overlapping
// the list on a frosted footer.
function ChangeFullSheet({ onClose, footer, children }) {
  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 10, background: 'var(--liq-bg)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 'none', height: 62 }}></div>
      <div className="su-noscroll" style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '0 20px 280px' }}>
        {children}
      </div>
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 0, padding: '14px 20px 30px',
        background: 'rgba(255,251,247,0.94)', backdropFilter: 'blur(18px)', WebkitBackdropFilter: 'blur(18px)',
        borderTop: '1px solid var(--liq-border-soft)', boxShadow: '0 -10px 28px rgba(29,17,41,0.08)',
        display: 'flex', flexDirection: 'column', gap: 10,
      }}>{footer}</div>
    </div>
  );
}

function ChangeSheetHead({ icon, title, sub }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', marginBottom: 16 }}>
      <h3 style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 24, letterSpacing: '-0.01em', margin: '4px 0 6px', color: 'var(--liq-fg)', lineHeight: 1.15 }}>{title}</h3>
      <p style={{ alignSelf: 'stretch', fontSize: 13.5, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.45, textAlign: 'left', textWrap: 'pretty', padding: '12px 14px', borderRadius: 14, background: 'rgba(129,42,236,0.07)', border: '1px solid rgba(129,42,236,0.14)' }}>{sub}</p>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Time picker — later today only, capped at the match's availability.
// ─────────────────────────────────────────────────────────────
function TimeChangeSheet({ name = 'Leonie', current = CHANGE_ORIGINAL.time, initial = null, onSend, onClose, sendError = false }) {
  const [sel, setSel] = React.useState(initial);
  const [err, setErr] = React.useState(sendError);
  const slots = CHANGE_SLOTS.filter(t => t > current);
  return (
    <ChangeFullSheet onClose={onClose} footer={<>
      {err ? <ChangeError>Couldn't send your suggestion. Check your connection and try again.</ChangeError> : <BindingNote name={name}/>}
      <button disabled={!sel} onClick={() => { if (!sel) return; if (err) { setErr(false); return; } onSend(sel); }} style={{ ...changeSheetBtn.primary, opacity: sel ? 1 : 0.45, boxShadow: sel ? changeSheetBtn.primary.boxShadow : 'none' }}>
        {sel ? `Suggest ${sel}` : 'Pick a time'}
      </button>
      <button onClick={onClose} style={changeSheetBtn.secondary}>Cancel</button>
    </>}>
      <div>
        <ChangeSheetHead icon="clock" title="Suggest a new time"
          sub={<><span style={{ display: 'block' }}>Your current meet time: <strong style={{ color: 'var(--liq-fg)' }}>{current}</strong></span><span style={{ display: 'block' }}>Choose a time within {name}'s availability. Latest possible meet time: {changeDeadline(CHANGE_AVAIL_UNTIL)}.</span><span style={{ display: 'block', marginTop: 6 }}>{name} has to agree by {changeDeadline(current)}, so you both still arrive on time. If not, {current} stays binding.</span></>}/>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0,1fr))', gap: 6, marginBottom: 16 }}>
          {slots.map(t => {
            const on = t === sel;
            return (
              <button key={t} onClick={() => setSel(t)} style={{
                height: 44, borderRadius: 12, cursor: 'pointer',
                background: on ? 'var(--liq-primary-500)' : '#fff',
                color: on ? '#fff' : 'var(--liq-fg)',
                border: on ? 'none' : '1px solid var(--liq-border)',
                fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15, fontVariantNumeric: 'tabular-nums',
              }}>{t}</button>
            );
          })}
        </div>
      </div>
    </ChangeFullSheet>
  );
}

// ─────────────────────────────────────────────────────────────
// Place picker — public places only (Google Maps places: cafés, bars,
// restaurants). Search returns places, never street addresses.
// ─────────────────────────────────────────────────────────────
// placesState: 'ok' · 'error' (Places request failed) · 'empty' falls out of the query.
function PlaceChangeSheet({ name = 'Leonie', current = CHANGE_ORIGINAL.place, initial = null, onSend, onClose, sendError = false, placesState = 'ok', initialQuery = '' }) {
  const [sel, setSel] = React.useState(initial);
  const [q, setQ] = React.useState(initialQuery);
  const [err, setErr] = React.useState(sendError);
  const [loadErr, setLoadErr] = React.useState(placesState === 'error');
  const list = loadErr ? [] : CHANGE_PLACES.filter(p => !q || (p.name + p.type).toLowerCase().includes(q.toLowerCase()));
  const picked = CHANGE_PLACES.find(p => p.id === sel);
  return (
    <ChangeFullSheet onClose={onClose} footer={<>
      {err ? <ChangeError>Couldn't send your suggestion. Check your connection and try again.</ChangeError> : <BindingNote name={name}/>}
      <button disabled={!picked} onClick={() => { if (!picked) return; if (err) { setErr(false); return; } onSend(picked); }} style={{ ...changeSheetBtn.primary, opacity: picked ? 1 : 0.45, boxShadow: picked ? changeSheetBtn.primary.boxShadow : 'none' }}>
        {picked ? `Suggest ${picked.name}` : 'Pick a place'}
      </button>
      <button onClick={onClose} style={changeSheetBtn.secondary}>Cancel</button>
    </>}>
      <div>
        <ChangeSheetHead icon="map-pin" title="Suggest a new place"
          sub={<><span style={{ display: 'block' }}>Your current meet place: <strong style={{ color: 'var(--liq-fg)' }}>{current.name}</strong></span><span style={{ display: 'block' }}>Choose a public place: café, bar or restaurant.</span><span style={{ display: 'block', marginTop: 6 }}>{name} has to agree by {changeDeadline(CHANGE_ORIGINAL.time)}, so you both still arrive on time. If not, {current.name} stays binding.</span></>}/>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, height: 48, padding: '0 14px', borderRadius: 14, background: '#fff', border: '1.5px solid var(--liq-border)', marginBottom: 12 }}>
          <Icon name="search" size={18} stroke={2.2} style={{ color: 'var(--liq-fg-subtle)', flex: 'none' }}/>
          <input value={q} onChange={e => setQ(e.target.value)} placeholder="Search cafés, bars, restaurants"
            style={{ flex: 1, minWidth: 0, border: 'none', outline: 'none', background: 'transparent', fontFamily: 'var(--liq-font-sans)', fontSize: 15, fontWeight: 500, color: 'var(--liq-fg)' }}/>
        </div>
        {loadErr && (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, padding: '28px 12px', textAlign: 'center' }}>
            <p role="alert" style={{ margin: 0, maxWidth: 290, fontSize: 15, lineHeight: 1.45, fontWeight: 500, color: 'var(--liq-fg-muted)', textWrap: 'pretty' }}>Couldn't load places. Check your connection and try again.</p>
            <button onClick={() => setLoadErr(false)} style={{ height: 44, padding: '0 22px', borderRadius: 9999, cursor: 'pointer', background: '#fff', border: '1px solid var(--liq-border)', fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14.5, color: 'var(--liq-fg)' }}>Try again</button>
          </div>
        )}
        {!loadErr && q && list.length === 0 && (
          <div style={{ padding: '28px 12px', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--liq-fg)', textWrap: 'pretty' }}>No cafés, bars or restaurants found for “{q}”.</span>
            <span style={{ fontSize: 13.5, color: 'var(--liq-fg-muted)' }}>Try another name.</span>
          </div>
        )}
        {!loadErr && list.length > 0 && <div style={{ fontSize: 11.5, fontWeight: 800, letterSpacing: 0.08, textTransform: 'uppercase', color: 'var(--liq-fg-subtle)', margin: '0 2px 8px' }}>{q ? 'Results' : 'Nearby public places'}</div>}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
          {list.map(p => {
            const on = p.id === sel;
            return (
              <button key={p.id} onClick={() => setSel(p.id)} style={{
                display: 'flex', alignItems: 'center', gap: 12, width: '100%', textAlign: 'left', padding: '11px 12px', borderRadius: 16, cursor: 'pointer',
                background: on ? 'rgba(129,42,236,0.07)' : '#fff',
                border: on ? '1.5px solid var(--liq-primary-500)' : '1px solid var(--liq-border-soft)',
              }}>
                <div style={{ width: 40, height: 40, borderRadius: 12, flex: 'none', background: 'var(--liq-bg-raised)', color: 'var(--liq-primary-500)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Icon name="map-pin" size={18} stroke={2.2}/>
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 7, flexWrap: 'wrap' }}>
                    <span style={{ fontWeight: 700, fontSize: 15, color: 'var(--liq-fg)' }}>{p.name}</span>
                    <span style={{ fontSize: 10.5, fontWeight: 800, letterSpacing: 0.05, textTransform: 'uppercase', color: 'var(--liq-primary-500)' }}>{p.type}</span>
                  </div>
                  <div style={{ fontSize: 12.5, color: 'var(--liq-fg-muted)', marginTop: 2 }}>{p.address} · {p.dist}</div>
                </div>
                <div style={{ width: 22, height: 22, borderRadius: 9999, flex: 'none', border: on ? 'none' : '1.5px solid var(--liq-border)', background: on ? 'var(--liq-primary-500)' : '#fff', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  {on && <Icon name="check" size={13} stroke={3}/>}
                </div>
              </button>
            );
          })}
        </div>
      </div>
    </ChangeFullSheet>
  );
}

// ─────────────────────────────────────────────────────────────
// Suggestion card — the structured message in the thread.
// status: 'pending' · 'agreed' · 'replaced' (a newer suggestion superseded it)
//         · 'expired' (not agreed by binding time − 30 min)
// ─────────────────────────────────────────────────────────────
function ChangeSuggestionCard({ msg, mine, name = 'Leonie', onAgree, onCounter, onRetry }) {
  const s = msg.suggestion;
  const isTime = s.kind === 'time';
  const value = isTime ? s.value : s.value.name;
  const was = isTime ? s.was : s.was.name;
  const replaced = s.status === 'replaced';
  const expired = s.status === 'expired';
  const deadline = changeDeadline(msg.bindTime);
  return (
    <div style={{ display: 'flex', justifyContent: mine ? 'flex-end' : 'flex-start', opacity: replaced || expired ? 0.55 : 1 }}>
      <div style={{ width: '84%', borderRadius: 20, background: '#fff', border: '1.5px solid rgba(129,42,236,0.22)', boxShadow: 'var(--liq-shadow-sm)', overflow: 'hidden' }}>
        <div style={{ padding: '12px 14px 13px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, fontWeight: 800, letterSpacing: 0.06, textTransform: 'uppercase', color: 'var(--liq-primary-500)' }}>
            <Icon name={isTime ? 'clock' : 'map-pin'} size={13} stroke={2.4}/>
            {mine ? 'You suggested' : `${name} suggested`} · {isTime ? 'Meet time' : 'Meeting place'}
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginTop: 6, flexWrap: 'wrap' }}>
            <span style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 24, color: 'var(--liq-fg)', lineHeight: 1.1 }}>{value}</span>
            <span style={{ fontSize: 13, color: 'var(--liq-fg-subtle)', textDecoration: 'line-through' }}>{was}</span>
          </div>
          {!isTime && <div style={{ fontSize: 12.5, color: 'var(--liq-fg-muted)', marginTop: 3 }}>{s.value.type} · {s.value.address}</div>}
        </div>
        <div style={{ padding: '0 14px 13px', display: 'flex', flexDirection: 'column', gap: 8 }}>
          {s.status === 'agreed' && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 700, color: 'var(--liq-success-fg)' }}>
              <Icon name="check" size={15} stroke={2.8}/> Agreed{s.agreedAt ? ` · ${s.agreedAt}` : ''}
            </div>
          )}
          {replaced && <div style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--liq-fg-muted)' }}>Replaced by a new suggestion</div>}
          {expired && <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12.5, fontWeight: 700, color: 'var(--liq-fg-muted)' }}><Icon name="clock" size={13} stroke={2.4}/> Expired · not agreed by {deadline}</div>}
          {s.status === 'pending' && mine && (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12.5, fontWeight: 700, color: 'var(--liq-fg-muted)' }}>
                <Icon name="clock" size={13} stroke={2.4}/> Waiting for {name}
              </div>
              <BindingNote name={name} time={msg.bindTime} place={msg.bindPlace} compact/>
            </>
          )}
          {s.status === 'pending' && !mine && (
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 9, padding: '9px 11px', borderRadius: 12, background: 'rgba(245,158,11,0.09)' }}>
              <Icon name="clock" size={15} stroke={2.4} style={{ color: '#b45309', flex: 'none', marginTop: 1 }}/>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
                <span style={{ fontSize: 13.5, fontWeight: 700, color: '#78350f' }}>Answer by {deadline}</span>
                <span style={{ fontSize: 12.5, fontWeight: 500, color: '#92400e', lineHeight: 1.4, textWrap: 'pretty' }}>Until you agree, <strong style={{ fontWeight: 700, color: '#78350f' }}>{msg.bindTime} at {msg.bindPlace} stays binding.</strong> Not being there counts as a no-show.</span>
              </div>
            </div>
          )}
          {s.status === 'pending' && !mine && (
            <div style={{ display: 'flex', gap: 8 }}>
              <button onClick={onCounter} style={{ flex: 1, height: 44, borderRadius: 9999, cursor: 'pointer', background: '#fff', border: '1px solid var(--liq-border)', fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 13.5, color: 'var(--liq-fg)' }}>Suggest another</button>
              <button onClick={onAgree} style={{ flex: 1, height: 44, borderRadius: 9999, cursor: 'pointer', border: 'none', background: 'var(--su-grad-sunset)', color: '#fff', fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14, boxShadow: 'var(--liq-shadow-cta)' }}>I agree</button>
            </div>
          )}
          {s.status === 'pending' && !mine && msg.agreeError && (
            <ChangeRetryLine onRetry={onRetry}>Couldn't agree. Check your connection and try again.</ChangeRetryLine>
          )}
        </div>
        <div style={{ padding: '7px 14px', borderTop: '1px solid var(--liq-border-soft)', fontSize: 11, fontWeight: 600, color: 'var(--liq-fg-subtle)', textAlign: mine ? 'right' : 'left' }}>{msg.time}</div>
      </div>
    </div>
  );
}

// Main-screen line under the time / place value while a change is live.
// Pending: the BINDING value stays big above this; the suggestion is shown
// small, with who still has to agree (3 Nov 2026). Agreed: new value is big,
// the old one struck through here.
function ChangeTag({ change, name = 'Leonie' }) {
  const agreed = change.status === 'agreed';
  const was = change.kind === 'time' ? change.was : change.was.name;
  const value = change.kind === 'time' ? change.value : change.value.name;
  if (!agreed) {
    const mine = change.by !== 'them';
    // Same amber box as the "Answer by" deadline note — one look for "pending".
    return (
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 9, padding: '9px 11px', borderRadius: 12, background: 'rgba(245,158,11,0.09)', marginTop: 8, width: '100%', boxSizing: 'border-box' }}>
        <Icon name="clock" size={15} stroke={2.4} style={{ color: '#b45309', flex: 'none', marginTop: 1 }}/>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
          <span style={{ fontSize: 13.5, fontWeight: 700, color: '#78350f' }}>{mine ? `Waiting for ${name} to agree` : 'Waiting for you to agree'}</span>
          <span style={{ fontSize: 12.5, fontWeight: 500, color: '#92400e', lineHeight: 1.4, textWrap: 'pretty' }}>
            {mine ? 'You suggested' : `${name} suggested`} <strong style={{ fontWeight: 700, color: '#78350f' }}>{value}</strong>
          </span>
        </div>
      </div>
    );
  }
  // Agreed: the purple card shows only the latest reality. One quiet hint that
  // it changed — never a chain, always the ORIGINAL value; hidden after the
  // meet time (3 Nov 2026). No "Agreed" pill: everything on purple is agreed.
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 4, width: '100%', fontSize: 12.5, color: 'var(--liq-fg-muted)' }}>
      <Icon name="refresh" size={12} stroke={2.4} style={{ flex: 'none' }}/>
      <span><strong style={{ fontWeight: 700, color: 'var(--liq-fg)' }}>Changed</strong> · was <span style={{ textDecoration: 'line-through' }}>{was}</span></span>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenChatChange — the chat with structured time/place suggestions.
// role 'sender' = you suggest · 'receiver' = Leonie suggested, you answer.
// stage (sender): 'start' · 'sent' — initialSheet opens a picker on load.
// ─────────────────────────────────────────────────────────────
const CHANGE_SEED = [
  { id: 1, reason: 'onway', text: 'Looking forward to tonight!', mine: false, time: '17:20' },
];
function changeMsg(kind, value, mine, time, status = 'pending') {
  return {
    id: Date.now() + Math.random(), mine, time,
    bindTime: CHANGE_ORIGINAL.time, bindPlace: CHANGE_ORIGINAL.place.name,
    suggestion: { kind, value, was: kind === 'time' ? CHANGE_ORIGINAL.time : CHANGE_ORIGINAL.place, status },
  };
}
// Failure frames (10 Nov 2026): sendError (picker footer) · agreeError (receiver card) ·
// placesState 'error' · initialQuery for the empty search.
function ScreenChatChange({ name = 'Leonie', time = '17:42', role = 'sender', stage = 'start', kind = 'time', initialSheet = null, initialPick = null, seed = null, sendError = false, agreeError = false, placesState = 'ok', initialQuery = '' }) {
  const initial = React.useMemo(() => {
    const base = seed || CHANGE_SEED;
    const v = kind === 'place' ? CHANGE_PLACES[0] : '19:30';
    if (role === 'receiver') return [...base, { ...changeMsg(kind, v, false, '17:38'), agreeError }];
    if (stage === 'sent') return [...base, changeMsg(kind, v, true, '17:41')];
    if (stage === 'expired') return [...base, changeMsg(kind, v, true, '17:41', 'expired'),
      { id: 'exp', system: true, expired: true, text: `No answer by ${changeDeadline(CHANGE_ORIGINAL.time)} — ${CHANGE_ORIGINAL.time} at ${CHANGE_ORIGINAL.place.name} stays.` }];
    return base;
  }, []);
  const [messages, setMessages] = React.useState(initial);
  const [sheet, setSheet] = React.useState(initialSheet);
  const [draftKey, setDraftKey] = React.useState(null);
  const [draftText, setDraftText] = React.useState('');

  // Header shows the BINDING values: only an agreed suggestion moves them.
  const latest = (kind) => [...messages].reverse().find(m => m.suggestion && m.suggestion.kind === kind && m.suggestion.status === 'agreed');
  const tMsg = latest('time'), pMsg = latest('place');
  const headerTime = tMsg ? tMsg.suggestion.value : CHANGE_ORIGINAL.time;
  const headerPlace = pMsg ? pMsg.suggestion.value.name : CHANGE_ORIGINAL.place.name;

  function send(kind, value) {
    setMessages(ms => [...ms.map(m => (m.suggestion && m.suggestion.kind === kind && m.suggestion.status === 'pending') ? { ...m, suggestion: { ...m.suggestion, status: 'replaced' } } : m), changeMsg(kind, value, true, time)]);
    setSheet(null);
  }
  function agree(id) {
    let label = '';
    setMessages(ms => {
      const next = ms.map(m => {
        if (m.id !== id) return m;
        const s = m.suggestion;
        label = s.kind === 'time' ? `Meet time is now ${s.value}` : `Meeting place is now ${s.value.name}`;
        return { ...m, suggestion: { ...s, status: 'agreed', agreedAt: time } };
      });
      return [...next, { id: Date.now(), system: true, text: label }];
    });
  }
  function pick(key) {
    if (key === 'time' || key === 'place') { setSheet(key); return; }
    setDraftKey(key); setDraftText(reasonFor(key).prefill);
  }
  function sendText() {
    if (!draftText.trim()) return;
    setMessages(m => [...m, { id: Date.now(), reason: draftKey, text: draftText.trim(), mine: true, time, read: false }]);
    setDraftKey(null); setDraftText('');
  }

  return (
    <Phone time={time}>
      <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', height: '100%' }}>
        <ChatHeader name={name} when={`Today · ${headerTime}`} place={headerPlace}/>
        <ThreadScroll style={{ overflow: 'auto', padding: '14px 14px 8px', display: 'flex', flexDirection: 'column', gap: 12, background: '#fff' }}>
          {messages.map((m, i) => m.system
            ? (m.expired
              ? <SystemLine key={m.id} icon="clock">{m.text}</SystemLine>
              : <SystemLine key={m.id} icon="check" tone="success">{m.text}</SystemLine>)
            : m.suggestion
              ? <ChangeSuggestionCard key={m.id} msg={m} mine={m.mine} name={name}
                  onAgree={() => agree(m.id)} onCounter={() => setSheet(m.suggestion.kind)} onRetry={() => agree(m.id)}/>
              : <ChatBubble key={m.id} msg={m} mine={m.mine} name={name}
                  showAvatar={!m.mine && (i === messages.length - 1 || messages[i + 1]?.mine)}/>
          )}
        </ThreadScroll>
        <PersistentComposer selectedKey={draftKey} text={draftText}
          onPick={pick} onText={setDraftText} onClear={() => { setDraftKey(null); setDraftText(''); }} onSend={sendText}/>
        {sheet === 'time' && <TimeChangeSheet name={name} current={CHANGE_ORIGINAL.time} initial={initialPick} sendError={sendError} onSend={v => send('time', v)} onClose={() => setSheet(null)}/>}
        {sheet === 'place' && <PlaceChangeSheet name={name} initial={initialPick} sendError={sendError} placesState={placesState} initialQuery={initialQuery} onSend={v => send('place', v)} onClose={() => setSheet(null)}/>}
      </div>
    </Phone>
  );
}

Object.assign(window, {
  ScreenChatChange, TimeChangeSheet, PlaceChangeSheet, ChangeSuggestionCard, ChangeTag, BindingNote, ChangeError, ChangeRetryLine,
  CHANGE_ORIGINAL, CHANGE_PLACES,
});

// ═════════════════════════════════════════════════════════════
// From screens-date-chat.jsx — Date 01's CoordinateModule. This ticket owns
// the `unread.request` branch (frames E, J, L) and wires the locked card's
// Change meet time · Change location tiles (frame I) to the pickers above.
// ═════════════════════════════════════════════════════════════
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
          {req && req.agreeError && (
            // I agree failed (10 Nov 2026) — Date 02's tappable red line; tapping retries.
            <div style={{ marginTop: 4, display: 'flex' }}><ChangeRetryLine onRetry={onAgree}>Couldn't agree. Check your connection and try again.</ChangeRetryLine></div>
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

// ═════════════════════════════════════════════════════════════
// From screens-date-chat.jsx — ScreenMatchCoord: how the `change` prop is
// resolved into binding values (excerpt, top of the component).
// ═════════════════════════════════════════════════════════════
// function ScreenMatchCoord({ coordOpen = false, coordUnread = null, tall = false, initialReportOpen = false, reportStep = 'menu', reportEnded = false, matchMateTab = 'topics', dateComplete = false, initialWrapOpen = false, wrapStep = 'menu', initialCancelStep = null, checkInFlow = false, initialCheckInPopup = false, change = null, intentShared = true, rideAction = false, earlyChange = true, loadFailed = false, initialProfileOpen = false }) {
//   // intentShared: only what matches is shown — different motivations → no intent row (2 Nov 2026).
//   // rideAction: Order Ride waits for a ride-app API; hidden until it exists (2 Nov 2026).
//   // change: { kind: 'time'|'place', value, was, status: 'pending'|'agreed', by: 'you'|'them' }
//   // change may also be an array — one time + one place suggestion can be open at once (rule 3).
//   const changes = Array.isArray(change) ? change : change ? [change] : [];
//   const timeChange = changes.find(c => c.kind === 'time') || null;
//   const placeChange = changes.find(c => c.kind === 'place') || null;
//   // Purple card = what's REAL (binding). A suggestion from the match lives only
//   // on the orange Coordinate card; the purple card shows just your own waiting
//   // suggestion + the binding note (3 Nov 2026).
//   const shownTag = (c) => c && (c.status === 'agreed' || c.by !== 'them');
//   const pendingChange = changes.find(c => c.status === 'pending' && c.by !== 'them') || null;
//   // Big values = BINDING: a pending suggestion is shown small by ChangeTag (3 Nov 2026).
//   const showTime = timeChange && timeChange.status === 'agreed' ? timeChange.value : '19:00';
//   const showPlace = placeChange && placeChange.status === 'agreed' ? placeChange.value : { name: 'Cafe Krone', address: 'Oranienstrasse 42, Kreuzberg' };

// ─── The time + place card (excerpt from ScreenMatchCoord's render) ───
//         {/* Time + place — read-only now; changing them happens in Coordinate */}
//         <Card style={{ padding: '13px 14px', background: '#E8DCF5' }}>
//           {/* Card header — who + when, with a heart mark */}
//           <div style={{ display: 'flex', alignItems: 'center', gap: 9, marginBottom: 13 }}>
//             <div style={{
//               width: 26, height: 26, borderRadius: 9, flex: 'none',
//               background: 'var(--liq-primary-500)', color: '#fff',
//               display: 'flex', alignItems: 'center', justifyContent: 'center',
//             }}>
//               <Icon name="heart-filled" size={14} stroke={2}/>
//             </div>
//             <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 20, color: 'var(--liq-fg)', lineHeight: 1.15 }}>
//               You and Leonie are meeting <em style={{ color: 'var(--liq-primary-500)' }}>today</em>
//             </div>
//           </div>
//           <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
//             <div style={{
//               width: 40, height: 40, borderRadius: 13, flex: 'none',
//               background: '#fff', color: 'var(--liq-primary-500)',
//               display: 'flex', alignItems: 'center', justifyContent: 'center',
//             }}>
//               <Icon name="clock" size={19} stroke={2.2}/>
//             </div>
//             <div style={{ flex: 1, display: 'flex', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
//               <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--liq-primary-500)', letterSpacing: 0.02 }}>Meeting time:</div>
//               <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 20, color: 'var(--liq-fg)' }}>{showTime}</div>
//               {shownTag(timeChange) && <ChangeTag change={timeChange} name="Leonie"/>}
//             </div>
//           </div>
//           <div style={{ height: 1, background: 'rgba(29,17,41,0.10)', margin: '10px 0' }}/>
//           <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
//             <div style={{
//               width: 40, height: 40, borderRadius: 13, flex: 'none',
//               background: '#fff', color: 'var(--liq-primary-500)',
//               display: 'flex', alignItems: 'center', justifyContent: 'center',
//             }}>
//               <Icon name="map-pin" size={19} stroke={2.2}/>
//             </div>
//             <div style={{ flex: 1 }}>
//               <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
//                 <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--liq-primary-500)', letterSpacing: 0.02 }}>Meeting place:</div>
//                 <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 20, color: 'var(--liq-fg)' }}>{showPlace.name}</div>
//               </div>
//               <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 3 }}>
//                 <div style={{ fontSize: 12.5, color: 'var(--liq-fg-muted)', lineHeight: 1.35 }}>{showPlace.address}</div>
//                 <CopyAddress address={`${showPlace.name}, ${showPlace.address}`}/>
//               </div>
//               {shownTag(placeChange) && <ChangeTag change={placeChange} name="Leonie"/>}
//             </div>
//           </div>
//           {pendingChange && (
//             <div style={{ marginTop: 12 }}><BindingNote name="Leonie" compact
//               time={timeChange && timeChange.status === 'agreed' ? timeChange.value : undefined}
//               place={placeChange && placeChange.status === 'agreed' ? placeChange.value.name : undefined}/></div>
//           )}
//         </Card>
