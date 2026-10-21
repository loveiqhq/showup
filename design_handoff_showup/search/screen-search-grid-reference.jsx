// screen-search-grid-reference.jsx — Date search 02 · Premium grid
//
// Design reference for [Search 02] Premium grid. This is the code that
// renders spec-sheets/02-grid.png. Read values from HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/:
// PREMIUM_GRID, PremiumGridCard, PremiumLikedYouBadge, ScreenSearchGrid,
// SearchTopBar, ActionBtn, LikeConfirmModal, ConfirmRefusedSheet from
// screens-search.jsx;
// ScreenSearchProfile from screens-search-profile.jsx; ShowUpCount from
// shared.jsx). A reference implementation, not production code: recreate it
// in the target codebase's own patterns. Do not ship this file.
//
// CONSUMES, never copies:
//   Search 01 — ScreenSearchProfile and everything it builds (the stack,
//     ShowUpCount, searchDistance, LikeMessageModal, ModalShell). This ticket
//     EXTENDS ScreenSearchProfile with five props (below); it does not fork it.
//   components/shared.jsx — Phone, Card, Icon, Button, PortraitPlaceholder,
//     TabBar, Wordmark.
//   Check-In 03 — CHECKIN_MOTIVATIONS (§30) for the motivation line.
//   screens-premium.jsx — PremiumTag (the top-bar label).
//   LikesUndoToast + LIKES_UNDO_MS (6000) are BUILT by this story (G) and
//     reused by Search 03; their code is in screen-search-likes-reference.jsx.
//
// BUILDS: ScreenSearchGrid, PremiumGridCard, PremiumLikedYouBadge, the
// premium / likes / onLikes props on SearchTopBar, the mutual mode of
// LikeConfirmModal, and five props on ScreenSearchProfile:
//   premium     PremiumTag in the top bar
//   onBack      draws the "All profiles" back button under the top bar
//   backLabel   its label (Search 03 passes "Likes received")
//   likedYou    "Liked you" badge on the hero; action bar becomes
//               Not interested · Not now · Confirm date (all three, 21 Oct
//               2026; Not now leaves their like open); Be sure mutual
//   onAction    called with 'not_now' | 'not_interested' | 'liked' |
//               'confirmed' | 'matched' | 'no_overlap' (after the sorry
//               sheet) — the caller returns to the grid and marks the card
//   refuse      (kit demo only) the server's refusal for this confirm; in the
//               build it is the like endpoint's `closed` answer
// Also BUILDS ConfirmRefusedSheet + CONFIRM_REFUSED_COPY and the fly-out
// (useCardFlyOut + GRID_LEAVE_*), all reused by Search 03 — one implementation.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// STATES ON THE SHEET
//   A  <ScreenSearchGrid/>                                              at rest
//   B  <ScreenSearchGrid initialMarks={{ Maya: 'not_now' }} initialGone={['Sofia', 'Emma']}/>   Like / Not interested → gone
//   C  <ScreenSearchGrid initialOpen={1}/>                              card or heart → full profile
//   D  <ScreenSearchGrid initialOpen={1} initialProfileModal="confirm"/> Like on the profile → Be sure
//   E  <ScreenSearchGrid initialOpen={0}/> + Confirm date               a "Liked you" profile → Be sure, mutual
//   F  <ScreenSearchGrid initialClosed={{ Leonie: 'matched' }}/>        confirm refused → card closed in place
//   G  <ScreenSearchGrid initialUndo="Anna" initialGone={['Anna']}/>                         liked-you declined → 6 s Undo toast
//   H  <ScreenSearchGrid initialOpen={0} initialProfileModal="refused" refuse={{ Leonie: 'matched' }}/>   sorry sheet, matched
//   I  <ScreenSearchGrid initialOpen={3} initialProfileModal="refused" refuse={{ Anna: 'no_overlap' }}/>  sorry sheet, no overlap
//   K  <ScreenSearchGrid initialMarks={{ Sofia: 'liked', Maya: 'not_now' }} freezeLeaving={{ name: 'Sofia', t: 440 }}/>  fly-out frozen
//   J  <ScreenSearchGrid initialClosed={{ Sofia: 'matched', Emma: 'no_overlap' }}
//        initialMoved={{ Anna: { meetAt: '18:00', meetDay: 'tomorrow' } }}/>                     availability re-checked
//   Try it: <ScreenSearchGrid recheck={{ Sofia: 'matched', Emma: 'no_overlap' }} refuse={{ Leonie: 'matched' }}/>
//
// EVERY FREEMIUM RULE CARRIES OVER: ShowUpCount (never a %), rounded
// distance (searchDistance), age hidden → name only, §30 motivation label,
// meet time = earliest overlap with today / tomorrow.
//
// CARD: ONE button. A tap anywhere — photo or heart — opens the full
// profile; there is NO shortcut (21 Oct 2026): every decision (Like, Not
// now, Not interested, Confirm date) is made on the profile, after the
// user has seen all of it. The heart is a 36 marker inside the card,
// aria-hidden, not a control. After an action the card
// STAYS, marked: filled sunset heart (liked) · grayscale .9 + opacity .5 +
// a "Not now" / "Not interested" label (no heart). ⚠ 21 Oct 2026: LIKE and
// NOT INTERESTED now REMOVE the card with a fly-out (GRID_LEAVE_*: hold 150,
// 800 to the bottom-centre, scale .12 + fade, 250 FLIP reflow; reduced
// motion = 200 fade). Only Not now stays marked. The next visit applies
// the Search 01 pool rules, so marked cards are gone then.
//
// "LIKED YOU": sunset badge + 2.5 orange ring. Opens like any card; the
// confirm is made on the profile. Be sure in mutual mode, CTA "Confirm
// date", no message step → match (07a). The button is inert until the
// server answers; a REFUSED confirm opens ConfirmRefusedSheet over the
// profile (21 Oct 2026: an apology, the person's photo + name, the timing,
// "Find someone else") and then returns to the grid with the card CLOSED IN
// PLACE (GRID_CLOSED: Too late / Out of time), not a tap target. A plain Like
// is never refused: it silently never becomes mutual.
//
// AVAILABILITY RE-CHECK (21 Oct 2026, MVP): on load the server sends only
// available people. On every return from a profile the client re-reads
// every card's status: matched / no overlap → closed in place (liked or
// not), meet time moved → the new time. No live push in MVP.
// Not interested on a liked-you profile → card marked + the Search 03 Undo
// toast for 6 s; the decline is sent when it ends or the user leaves the
// grid. Other passes keep Search 01's no-undo rule.
//
// FROM THE FULL PROFILE every action returns to the grid with the card
// marked; Like returns after Skip message / Send message. Cancelling a sheet
// stays on the profile. The Search 01 "next profile slides in" does not
// apply when the profile was opened from the grid.
//
// NO HEADER COUNT. No Premium filters in MVP.
//
// LAYOUT — the grid is the only scroll container: pad 4 16 16, two columns
// minmax(0,1fr), gap 12. Card height is fixed (248); the grid's height is
// the queue. Never hard-code a Y.

function ShowUpCount({ count, variant = 'soft', size = 'md' }) {
  const isNew = !(count > 0);
  const t = showUpTone(100);
  const fs = size === 'xs' ? 11 : size === 'sm' ? 12 : 12.5;
  const pad = size === 'xs' ? '4px 9px' : '7px 12px';
  const pal = isNew
    ? (variant === 'dark' ? { bg: 'rgba(255,255,255,0.92)', fg: 'var(--liq-primary-500)' } : { bg: 'rgba(129,42,236,0.10)', fg: 'var(--liq-primary-500)' })
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


// ─────────────────────────────────────────────────────────────
// Premium — shared data + helpers (Search 02 grid · Search 03 Likes received)
// Decided 20 Oct 2026 (search/README.md → Premium). Every Freemium rule
// carries over: ShowUpCount (never a %), rounded distance, age hidden →
// name only, §30 motivation labels, meet time = earliest overlap with
// today / tomorrow. No Premium filters in MVP.
// ─────────────────────────────────────────────────────────────
const PREMIUM_GRID = [
  { name: 'Leonie', age: 28,   dist: 1.2, motivation: 'long_term',    meetAt: '19:00', meetDay: 'today',    attended: 4, likedYou: true,  g: 'linear-gradient(155deg, #FCC8A7 0%, #E89B7A 40%, #B86B5C 100%)' },
  { name: 'Sofia',  age: 31,   dist: 2.4, motivation: 'casual',       meetAt: '20:00', meetDay: 'today',    attended: 2,                  g: 'linear-gradient(155deg, #EAD9F4 0%, #C8A6E8 40%, #8B5CF6 100%)' },
  { name: 'Maya',   age: null, dist: 0.6, motivation: 'non_monogamy', meetAt: '18:00', meetDay: 'tomorrow', attended: 0,                  g: 'linear-gradient(155deg, #DCE9D0 0%, #A5C99F 50%, #5B8C66 100%)' },
  { name: 'Anna',   age: 27,   dist: 1.8, motivation: 'long_term',    meetAt: '18:00', meetDay: 'today',    attended: 9, likedYou: true,  g: 'linear-gradient(155deg, #F1D9EA 0%, #D8A6CC 40%, #8B5C8B 100%)' },
  { name: 'Emma',   age: 32,   dist: 4.0, motivation: 'friends',      meetAt: '21:00', meetDay: 'today',    attended: 1,                  g: 'linear-gradient(155deg, #FCDCDC 0%, #E69CB5 50%, #B85B7C 100%)' },
  { name: 'Liv',    age: 30,   dist: 2.0, motivation: 'short_term',   meetAt: '17:30', meetDay: 'tomorrow', attended: 0,                  g: 'linear-gradient(155deg, #FFE2C5 0%, #FFB58A 40%, #E07A4C 100%)' },
];

// LIKES_RECEIVED lives in screen-search-likes-reference.jsx (Search 03).

const premiumMotivation = id => (window.CHECKIN_MOTIVATIONS || []).find(m => m.id === id);
const premiumName = p => `${p.name}${p.age != null ? `, ${p.age}` : ''}`;
// A grid / likes card opened as the full v2 profile: the card's fields win.
const premiumProfile = c => ({ ...window.SEARCH_V2_PROFILE, name: c.name, age: c.age, dist: c.dist, motivation: c.motivation, meetAt: c.meetAt || '19:00', meetDay: c.meetDay || 'today', attended: c.attended, isNew: !(c.attended > 0) });

function PremiumLikedYouBadge() {
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 4, flex: 'none',
      padding: '4px 9px 4px 7px', borderRadius: 9999, background: 'var(--su-grad-sunset)',
      color: '#fff', fontFamily: 'var(--liq-font-sans)', fontSize: 10.5, fontWeight: 800, letterSpacing: 0.04,
      boxShadow: '0 2px 8px rgba(0,0,0,0.18)',
    }}>
      <Icon name="heart-filled" size={11}/>Liked you
    </span>
  );
}

// ─────────────────────────────────────────────────────────────
// Search 02 — Premium grid card
//   ONE control: a tap anywhere — photo or heart — opens the full v2
//   profile. No shortcut (21 Oct 2026): Like, Not now, Not interested and
//   Confirm date are only ever made on the profile. The heart is a marker
//   inside the card (outline = not liked, filled sunset = liked), not a
//   button of its own.
//   mark: 'liked' (filled heart) · 'not_now' / 'not_interested' (dimmed +
//   label). Marked cards stay for this visit; the next visit applies the
//   Freemium pool rules (Search 01).
//   closed: 'matched' | 'no_overlap' — shown in place: dimmed, label pill,
//   reason line, no heart, no tap target. Two triggers (21 Oct 2026): a
//   refused confirm, AND the availability re-check (H) — the person got a
//   date or ran out of overlap since the grid loaded. Same card for both,
//   liked or not.
// ─────────────────────────────────────────────────────────────
const GRID_CLOSED = {
  matched:    { pill: 'Too late',    line: 'Has a date with someone else.', a11y: n => `Too late — ${n} has a date with someone else.` },
  no_overlap: { pill: 'Out of time', line: 'No time left to meet.',         a11y: n => `Out of time — you and ${n} have no time left to meet.` },
};
function PremiumGridCard({ p, mark, onOpen, instant = false }) {
  const mot = premiumMotivation(p.motivation);
  const closed = GRID_CLOSED[mark];
  const dim = mark === 'not_now' || mark === 'not_interested' || !!closed;
  const liked = mark === 'liked';
  const Tile = closed ? 'div' : 'button';
  return (
    <div style={{ position: 'relative' }}>
      <Tile onClick={closed ? undefined : onOpen} aria-label={closed ? `${p.name}. ${closed.a11y(p.name)}` : liked ? `Open ${p.name}'s profile, liked` : `Open ${p.name}'s profile`} role={closed ? 'group' : undefined} style={{ display: 'block', width: '100%', padding: 0, border: 'none', background: 'none', textAlign: 'left', cursor: closed ? 'default' : 'pointer' }}>
        <PortraitPlaceholder name={p.name} height={248} radius={20} gradient={p.g} label={false}
          style={{ ...(p.likedYou && !dim ? { boxShadow: '0 0 0 2.5px var(--liq-orange-500)' } : null), ...(dim ? { filter: 'grayscale(0.9)', opacity: 0.5 } : null) }}>
          <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(180deg, rgba(20,12,28,0) 42%, rgba(20,12,28,0.66) 100%)', pointerEvents: 'none' }}/>
          {p.likedYou && !dim && <div style={{ position: 'absolute', top: 12, left: 10 }}><PremiumLikedYouBadge/></div>}
          <div style={{ position: 'absolute', left: 12, right: 12, bottom: 12, display: 'flex', flexDirection: 'column', gap: 5, color: '#fff', minWidth: 0 }}>
            {!closed && <div><ShowUpCount count={p.attended} variant="dark" size="xs"/></div>}
            <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 17, lineHeight: 1.1, textShadow: '0 1px 8px rgba(0,0,0,0.3)' }}>{premiumName(p)}</div>
            {closed ? (
              <div style={{ fontSize: 11.5, fontWeight: 700, lineHeight: 1.3, textWrap: 'pretty' }}>{closed.line}</div>
            ) : (<>
              <div style={{ fontSize: 11.5, fontWeight: 700, whiteSpace: 'nowrap' }}>{instant ? `~${p.travelMin || 12} min away` : `${p.meetAt} ${p.meetDay}`} · {searchDistance(p.dist)}</div>
              {mot && <div style={{ fontSize: 11.5, fontWeight: 500, opacity: 0.92, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{mot.t}</div>}
            </>)}
          </div>
          {!dim && (
            <span aria-hidden="true" style={{
              position: 'absolute', top: 8, right: 8, width: 36, height: 36, borderRadius: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center',
              background: liked ? 'var(--su-grad-sunset)' : 'rgba(255,255,255,0.92)', color: liked ? '#fff' : 'var(--liq-orange-500)',
              boxShadow: '0 2px 10px rgba(0,0,0,0.18)', pointerEvents: 'none',
            }}>
              <Icon name={liked ? 'heart-filled' : 'heart'} size={17} stroke={2.4}/>
            </span>
          )}
        </PortraitPlaceholder>
      </Tile>
      {dim && (
        <span style={{
          position: 'absolute', top: 12, right: 10, padding: '5px 10px', borderRadius: 9999,
          background: '#fff', border: '1px solid var(--liq-border)', fontSize: 11, fontWeight: 800, color: 'var(--liq-fg-muted)', whiteSpace: 'nowrap',
        }}>{closed ? closed.pill : mark === 'not_now' ? 'Not now' : 'Not interested'}</span>
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Search 02 — Premium grid
//   initialMarks   { [name]: 'liked' | 'not_now' | 'not_interested' }
//   initialOpen    index → the card opened as the full v2 profile (C)
//   initialProfileModal 'confirm' | 'message' — that profile with a sheet open (D)
//   initialClosed  { [name]: 'matched' | 'no_overlap' } — a refused confirm (F)
//   initialUndo    name → Not interested on that liked-you profile, Undo toast (G)
//   initialGone    names already removed by a final answer (B)
//   freezeLeaving  { name, t } — that card frozen t ms into its fly-out (B2)
//   leaveOnMount   name → plays the fly-out on mount (B3, replay)
//   initialMoved   { [name]: { meetAt, meetDay } } — re-check moved the earliest
//                  overlap (H); the card just shows the new time, no highlight
//   recheck        { [name]: 'matched' | 'no_overlap' } — applied on the next
//                  return from a profile, to demo the re-check (H)
//   AVAILABILITY RE-CHECK (MVP, 21 Oct 2026): on load the server sends only
//   available people. On every return from a profile the client re-reads
//   the status of every card in the grid: gone → closed in place, meet time
//   moved → updated. No live push in MVP.
//   instant        Instant Mode vocabulary (not in Search 02)
//   Not interested on a LIKED-YOU profile gets the Search 03 6 s Undo; the
//   decline is sent when the toast ends or the user leaves the grid. Other
//   passes keep Search 01's no-undo rule.
// ─────────────────────────────────────────────────────────────
// Removal motion (proposal, 21 Oct 2026). t = 0 when the grid is settled
// after the back transition. Hold 150 → fly to the bottom-centre of the
// visible grid, scale .12 + fade, 800 ease-in → the rest close the gap, 250.
// Reduced motion: a 200 fade, then the reflow.
const GRID_LEAVE_HOLD_MS = 150, GRID_LEAVE_MS = 800, GRID_REFLOW_MS = 250;
// Shared fly-out for a card that got a FINAL answer — Search 02 grid and
// Search 03 Likes tab, one implementation (21 Oct 2026). Wrap each card in a
// div with ref={el => refs.current[key] = el}; setLeaving(key) plays hold →
// fly-out → the key joins 'gone' and the rest FLIP into place. snap() before
// any other list change (Undo) makes that change animate too.
function useCardFlyOut({ initialGone = [], initialLeaving = null, freeze = null } = {}) {
  const [gone, setGone] = React.useState(initialGone);
  const [leaving, setLeaving] = React.useState(initialLeaving);
  const refs = React.useRef({});
  const before = React.useRef(null);
  const snap = () => { const r = {}; Object.entries(refs.current).forEach(([n, el]) => { if (el) r[n] = el.getBoundingClientRect(); }); before.current = r; };
  React.useLayoutEffect(() => {
    const prev = before.current; if (!prev) return; before.current = null;
    Object.entries(refs.current).forEach(([n, el]) => {
      if (!el || !prev[n]) return;
      const r = el.getBoundingClientRect(), dx = prev[n].left - r.left, dy = prev[n].top - r.top;
      if (dx || dy) el.animate([{ transform: `translate(${dx}px, ${dy}px)` }, { transform: 'none' }], { duration: GRID_REFLOW_MS, easing: 'cubic-bezier(.2,.7,.2,1)' });
    });
  }, [gone]);
  React.useEffect(() => {
    if (!leaving) return;
    const el = refs.current[leaving]; if (!el) return;
    let anim, t;
    const run = () => {
      const reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      let sc = el.parentElement;
      while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowY)) sc = sc.parentElement;
      const box = (sc || document.documentElement).getBoundingClientRect(), r = el.getBoundingClientRect();
      const dx = box.left + box.width / 2 - (r.left + r.width / 2);
      const dy = box.top + box.height * 0.72 - (r.top + r.height / 2);
      el.style.position = 'relative'; el.style.zIndex = 5;
      anim = el.animate(reduce
        ? [{ opacity: 1 }, { opacity: 0 }]
        : [{ transform: 'none', opacity: 1 }, { transform: `translate(${dx}px, ${dy}px) scale(.12)`, opacity: 0 }],
        { duration: reduce ? 200 : GRID_LEAVE_MS, easing: 'cubic-bezier(.55,0,.8,.2)', fill: 'forwards' });
      if (freeze) { anim.pause(); anim.currentTime = freeze.t; return; }
      anim.onfinish = () => { snap(); setGone(g => [...g, leaving]); setLeaving(null); };
    };
    if (freeze) run(); else t = setTimeout(run, GRID_LEAVE_HOLD_MS);
    // Undo mid-flight (leaving → null): drop the animation and its onfinish,
    // so the restored card stays, at full opacity, with no fill transform.
    return () => { clearTimeout(t); if (anim && anim.playState !== 'finished') { anim.onfinish = null; anim.cancel(); el.style.zIndex = ''; } };
  }, [leaving]);
  return { refs, gone, setGone, leaving, setLeaving, snap };
}

function ScreenSearchGrid({ instant = false, initialMarks = {}, initialOpen = null, initialProfileModal = null, initialClosed = {}, initialUndo = null, initialMoved = {}, recheck = null, refuse = {}, initialGone = [], freezeLeaving = null, leaveOnMount = null }) {
  const [marks, setMarks] = React.useState({ ...initialMarks, ...initialClosed, ...(initialUndo ? { [initialUndo]: 'not_interested' } : null) });
  const pending = React.useRef(recheck);
  const back = () => { setOpen(null); if (pending.current) { const r = pending.current; pending.current = null; setMarks(s => ({ ...s, ...r })); } };
  const grid = PREMIUM_GRID.map(p => initialMoved[p.name] ? { ...p, ...initialMoved[p.name] } : p);
  const [open, setOpen] = React.useState(initialOpen);
  const [likes, setLikes] = React.useState(false);
  const [undo, setUndo] = React.useState(initialUndo);
  // A FINAL answer (Like · Not interested · a confirmed date) takes the card
  // out of the grid: 'leaving' plays the fly-out, then it joins 'gone' and
  // the rest reflow (FLIP). Not now stays, marked, and can be reopened.
  const { refs, gone, setGone, leaving, setLeaving, snap } = useCardFlyOut({ initialGone, initialLeaving: freezeLeaving ? freezeLeaving.name : leaveOnMount, freeze: freezeLeaving });
  const timer = React.useRef(null);
  React.useEffect(() => () => clearTimeout(timer.current), []);
  const leave = name => setLeaving(name);
  const decline = name => {
    setMarks(s => ({ ...s, [name]: 'not_interested' })); setUndo(name); leave(name);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setUndo(null), LIKES_UNDO_MS);
  };
  const restore = () => {
    clearTimeout(timer.current); snap();
    setMarks(s => { const n = { ...s }; delete n[undo]; return n; });
    setGone(g => g.filter(n => n !== undo)); setLeaving(l => l === undo ? null : l); setUndo(null);
  };
  const mark = (name, m) => {
    setMarks(s => ({ ...s, [name]: m === 'confirmed' ? 'liked' : m }));
    if (m === 'liked' || m === 'confirmed' || m === 'not_interested') leave(name);
  };

  if (likes) return <ScreenLikedMe onSearch={() => setLikes(false)}/>;
  if (open != null) {
    const c = grid[open];
    return <ScreenSearchProfile premium instant={instant} profile={premiumProfile(c)} likedYou={c.likedYou} refuse={refuse[c.name]}
      initialModal={open === initialOpen ? initialProfileModal : null}
      onBack={back} backLabel="All profiles" onAction={a => { back(); c.likedYou && a === 'not_interested' ? decline(c.name) : mark(c.name, a); }}/>;
  }
  return (
    <Phone>
      <SearchTopBar premium instant={instant} onLikes={() => setLikes(true)}/>
      <div style={{ padding: '4px 16px 16px', display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1fr)', gap: 12 }}>
        {grid.map((p, i) => gone.includes(p.name) ? null : (
          <div key={p.name} ref={el => { refs.current[p.name] = el; }} style={{ minWidth: 0 }}>
            <PremiumGridCard p={p} instant={instant} mark={marks[p.name]}
              onOpen={leaving === p.name ? undefined : () => { setUndo(null); setOpen(i); }}/>
          </div>
        ))}
      </div>
      <TabBar active={instant ? 'instant' : 'search'} likes={likesOpenCount(LIKES_RECEIVED)} onTab={id => id === 'likes' && setLikes(true)}/>
      {/* zero-height sticky anchor after the TabBar: the toast stays 12 above
          the bar at any scroll position, same as the Likes tab */}
      {undo && <div style={{ position: 'sticky', bottom: 0, height: 0, zIndex: 40 }}><LikesUndoToast name={undo} onUndo={restore}/></div>}
    </Phone>
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

function LikeConfirmModal({ name, onCancel, onSend, instant = false, day = 'today', cta = 'Send like', mutual = false }) {
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
            {mutual ? <>Confirm, and you and {name} have a </> : <>If {name} becomes your match, you two have a </>}<span style={{ fontWeight: 700, color: 'var(--liq-primary-500)' }}>confirmed, binding date{instant ? ' — right now' : ' ' + day}</span>.
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
            leading={<Icon name={mutual ? 'check' : 'send'} size={16} stroke={2.2}/>}>{cta}</Button>
        </div>
      </div>
    </ModalShell>
  );
}

// ─────────────────────────────────────────────────────────────
// Confirm refused — explains why a "Liked you" confirm did not become a
// date (21 Oct 2026). Only for Confirm date: a plain Like is never refused
// (it silently never becomes mutual). Shown over the profile, straight
// after Be sure; the photo + name keep the person real, the copy names the
// timing so it never reads as a fake profile. Button, scrim and swipe-down
// all lead back; the card is then closed in place (Search 02 · 03).
//   reason: 'matched' | 'no_overlap'
// ─────────────────────────────────────────────────────────────
const CONFIRM_REFUSED_COPY = {
  matched: {
    pill: 'Too late',
    head: n => <>We&rsquo;re really sorry, {n} confirmed a date <em>just before you did.</em></>,
    body: n => `A like closes the moment either of you matches, and this time ${n}'s match came first.`,
  },
  no_overlap: {
    pill: 'Out of time',
    head: n => <>Sorry, you and {n} <em>ran out of time.</em></>,
    body: n => `We're sorry this one didn't work out. Your Check-In and ${n}'s no longer share any time, so the like has closed.`,
  },
};
function ConfirmRefusedSheet({ name, reason = 'matched', backLabel = 'Find someone else', onDone }) {
  const c = CONFIRM_REFUSED_COPY[reason];
  return (
    <ModalShell onCancel={onDone}>
      <div role="alertdialog" aria-label={`${c.pill}. ${name}`} style={{ padding: '20px 24px 24px', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 14 }}>
        <div style={{ position: 'relative', width: 96, height: 108 }}>
          <PortraitPlaceholder name={name} height={96} radius={9999} label={false} style={{ width: 96, filter: 'grayscale(0.6)', opacity: 0.85 }}/>
          <span style={{ position: 'absolute', left: '50%', bottom: 0, transform: 'translateX(-50%)', padding: '5px 10px', borderRadius: 9999, background: '#fff', border: '1px solid var(--liq-border)', fontSize: 11, fontWeight: 800, color: 'var(--liq-fg-muted)', whiteSpace: 'nowrap' }}>{c.pill}</span>
        </div>
        <h2 className="su-underlined" style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 24, color: 'var(--liq-fg)', margin: '4px 0 0', letterSpacing: '-0.01em', lineHeight: 1.2, textWrap: 'balance' }}>{c.head(name)}</h2>
        <p style={{ fontSize: 14, color: 'var(--liq-neutral-200)', margin: 0, lineHeight: 1.5, maxWidth: 300, textWrap: 'pretty' }}>{c.body(name)}</p>
        <p style={{ fontSize: 14, fontWeight: 700, color: 'var(--liq-primary-500)', margin: 0, lineHeight: 1.45, maxWidth: 300, textWrap: 'pretty' }}>There are still people ready to meet you. Your date could be one of them.</p>
        <div style={{ alignSelf: 'stretch', marginTop: 6 }}>
          <Button variant="sunset" size="md" fullWidth onClick={onDone}>{backLabel}</Button>
        </div>
      </div>
    </ModalShell>
  );
}


function ScreenSearchProfile({ profile = SEARCH_V2_PROFILE, viewer = SEARCH_VIEWER, tall = false, tallHeight = 4300, initialModal = null, instant = false, premium = false, likedYou = false, onBack, backLabel = 'All profiles', onAction, tab = 'search', refuse = null, refusedLabel = 'Find someone else' }) {
  // refuse: 'matched' | 'no_overlap' — the server refuses this Confirm date
  // (Liked you only): ConfirmRefusedSheet, then onAction(refuse) closes the card.
  // tab: which TabBar tab owns this profile — 'search' (queue, grid) or
  // 'likes' (opened from Likes received, Search 03: Likes active, no filter).
  const p = profile;
  const [modal, setModal] = React.useState(initialModal);
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
        <div style={{ position: 'sticky', bottom: 64, left: 0, right: 0, padding: '12px 16px', background: 'linear-gradient(180deg, rgba(255,251,247,0) 0%, rgba(255,251,247,0.95) 35%, rgba(255,251,247,1) 100%)', display: 'flex', gap: 10, alignItems: 'center', justifyContent: 'center', zIndex: 3 }}>
          <ActionBtn icon="x" label="Not interested" tone="danger" onClick={() => onAction && onAction('not_interested')}/>
          <ActionBtn icon="clock" label="Not now" tone="muted" onClick={() => onAction && onAction('not_now')}/>
          <ActionBtn icon={likedYou ? 'check' : 'heart'} label={likedYou ? 'Confirm date' : 'Like'} tone="primary" big onClick={() => setModal('confirm')}/>
        </div>
      )}
      <TabBar active={instant ? 'instant' : tab} likes={window.LIKES_RECEIVED ? window.likesOpenCount(window.LIKES_RECEIVED) : 0}/>
      {modal === 'confirm' && <LikeConfirmModal name={p.name} day={p.meetDay} instant={instant} mutual={likedYou} cta={likedYou ? 'Confirm date' : 'Continue'} onCancel={() => setModal(null)} onSend={likedYou ? () => { if (refuse) return setModal('refused'); setModal(null); onAction && onAction('confirmed'); } : () => setModal('message')}/>}
      {modal === 'refused' && <ConfirmRefusedSheet name={p.name} reason={refuse || 'matched'} backLabel={refusedLabel} onDone={() => { setModal(null); onAction && onAction(refuse || 'matched'); }}/>}
      {modal === 'message' && <LikeMessageModal name={p.name} msg={msg} setMsg={setMsg} onCancel={() => setModal(null)} onSkip={() => { setModal(null); onAction && onAction('liked'); }} onSend={() => { setModal(null); onAction && onAction('liked'); }}/>}
    </Phone>
  );
}
