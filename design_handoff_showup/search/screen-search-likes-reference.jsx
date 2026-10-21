// screen-search-likes-reference.jsx — Date search 03 · Likes received (Premium)
//
// Design reference for [Search 03] Likes received. This is the code that
// renders spec-sheets/03-likes-received.png. Read values from HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit (ui_kits/show-up/screens-search.jsx:
// LIKES_RECEIVED, likesOrder, likesOpenCount, LikesUndoToast,
// LikesReceivedCard, ScreenLikedMe). A reference implementation, not
// production code: recreate it in the target codebase's own patterns.
// Do not ship this file.
//
// CONSUMES, never copies:
//   Search 02 (screen-search-grid-reference.jsx) — ConfirmRefusedSheet, the
//     fly-out (GRID_LEAVE_* timings; shared in the kit as useCardFlyOut),
//     the availability status call, premiumMotivation,
//     premiumName, premiumProfile, the mutual mode of LikeConfirmModal, and
//     the premium / onBack / backLabel / likedYou / onAction props on
//     ScreenSearchProfile.
//   Search 01 — ScreenSearchProfile, ShowUpCount, searchDistance.
//   components/shared.jsx — Phone, Card, Chip, Icon, Button, PortraitPlaceholder.
//   Check-In 03 — CHECKIN_MOTIVATIONS (§30).  screens-premium.jsx — PremiumTag.
//
// BUILDS: ScreenLikedMe, LikesReceivedCard (open + closed), LikesUndoToast,
// likesOrder, likesOpenCount, the LIKES TAB in TabBar (likes badge,
// components/shared.jsx), the tab prop on ScreenSearchProfile, and
// the filter + chip props on SearchTopBar (both false on the Likes tab).
//
// ─────────────────────────────────────────────────────────────
// STATES ON THE SHEET
//   A  <ScreenLikedMe/>                                  the Likes tab
//   B  <ScreenLikedMe initialOpen={0}/>                  View profile → full profile
//   C  <ScreenLikedMe initialModal={0}/>                 Confirm date on the profile → Be sure
//   D  <ScreenLikedMe initialUndo="Anna"/>               Not interested on the profile → Undo toast
//   E  <ScreenLikedMe initialClosed={{ Anna: 'matched' }}/>     refused: Too late
//   F  <ScreenLikedMe initialClosed={{ Anna: 'no_overlap' }}/>  Out of time
//   G  <ScreenLikedMe empty/>                            no likes yet
//   H  <ScreenLikedMe initialUndo="Anna" freezeLeaving={{ name: 'Anna', t: 440 }}/>  fly-out frozen
//   I  <ScreenLikedMe initialModal={0} initialSheet="refused" refuse={{ Leonie: 'matched' }}/>  sorry sheet
//   J  <ScreenLikedMe initialNotNow={['Anna']}/>        Not now → labelled
//   Try it: <ScreenLikedMe recheck={{ Leonie: 'no_overlap' }}/> — any return re-checks
//
// WHAT MATTERS HERE
//   • A TAB in every user's TabBar, badge = open likes. This list is the
//     Premium destination; Freemium gets the upsell (own story). No back.
//   • Order: earliest meet time first (today, then tomorrow, then by time);
//     closed cards last. Sorted on load, never re-sorted in place.
//   • The card has ONE action, View profile (the photo is the same
//     target). No decision from the list (21 Oct 2026). The confirm happens
//     on the profile: Be sure (mutual, no message step) → the server decides; the
//     first confirm to arrive wins. Refused → the SORRY SHEET (Search 02's
//     ConfirmRefusedSheet, "Back to likes") → back to the list, the card
//     closed IN PLACE with its reason (matched / no_overlap). 21 Oct 2026.
//   • Not interested (on the profile): back on the list, the card FLIES OUT
//     (useCardFlyOut, Search 02's motion: hold 150, 800, 250 reflow) and the toast shows for
//     LIKES_UNDO_MS (6 s). The decline is SENT when the toast ends or the
//     user leaves the screen. Undo puts the card back in its place.
//   • Not now (on the profile): back on the list, the card gets a "Not now"
//     label — NO dimming (the like is still open). This visit only.
//   • AVAILABILITY RE-CHECK on every return from a profile (Search 02 ⑲):
//     matched / no_overlap → the card closes in place; the badge drops.
//   • Closed cards stay until the next visit after they were seen.
//   • The screen is a flex column at min-height 100% and the list is
//     flex: 1, so the TabBar sits at the bottom of a short list. No card
//     height, list height or Y position is fixed.
// ─────────────────────────────────────────────────────────────

// Messages are the liker's optional Like message (≤ 100, Search 01).
// The same people as the grid's "Liked you" cards (Leonie, Anna), so the
// Likes tab badge (2 = open likes only) and this list agree. Clara's like
// closed: she matched someone else, so she is out of the grid too.
// closed: 'matched' (Too late) | 'no_overlap' (Out of time).
// Clara's kit photo: PHOTOS.Clara in components/shared.jsx (added 21 Oct 2026).
const LIKES_RECEIVED = [
  { name: 'Leonie', age: 28, dist: 1.2, attended: 4, motivation: 'long_term', meetAt: '19:00', meetDay: 'today', msg: "I love that you're into cooking too. Coffee at Bonanza?" },
  { name: 'Clara',  age: 29, dist: 3.1, attended: 5, motivation: 'long_term', closed: 'matched' },
  { name: 'Anna',   age: 27, dist: 1.8, attended: 9, motivation: 'long_term', meetAt: '18:00', meetDay: 'today' },
];
// Order (decided 20 Oct 2026): earliest meet time first — today before
// tomorrow, then by time. Closed cards go last. The server sorts; the
// client renders in the order it receives.
const likesOrder = list => [...list].sort((a, b) =>
  (a.closed ? 1 : 0) - (b.closed ? 1 : 0) ||
  (a.meetDay === b.meetDay ? 0 : a.meetDay === 'today' ? -1 : 1) ||
  String(a.meetAt).localeCompare(String(b.meetAt)));
// The Likes tab badge counts OPEN likes only: closed cards are not counted.
const likesOpenCount = list => list.filter(p => !p.closed).length;


// ─────────────────────────────────────────────────────────────
// Search 03 — Likes received (Premium). Its own tab: LIKES in the TabBar,
// badge = open likes. No back button. (Revised 20 Oct 2026.)
//   Confirm date (or the photo / View profile) → the FULL PROFILE. The
//     user confirms there: Confirm date → Be sure (mutual, no message step)
//     → the server decides. Won → match (07a). Lost → back to the list,
//     the card closed in place.
//   Not interested → the card leaves and an Undo toast shows for 6 s. The
//     decline is sent when the toast ends (or the user leaves the screen);
//     Undo puts the card back where it was. The liker is not told.
//   closed → 'matched' "Too late" · 'no_overlap' "Out of time". Kept until
//     the next visit after it was seen. Never counted by the badge.
//   empty → no likes yet · Back to search
// ─────────────────────────────────────────────────────────────
const LIKES_UNDO_MS = 6000;
function LikesUndoToast({ name, onUndo }) {
  return (
    <div role="status" style={{
      position: 'absolute', left: 16, right: 16, bottom: 78, zIndex: 40, minHeight: 52,
      padding: '4px 4px 4px 16px', borderRadius: 16, background: 'rgba(29,17,41,0.92)',
      boxShadow: '0 8px 24px rgba(20,12,28,0.22)', color: '#fff',
      display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12,
    }}>
      <span style={{ fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 600, lineHeight: 1.35, minWidth: 0 }}>{name}'s like declined.</span>
      <button onClick={onUndo} style={{
        height: 44, padding: '0 14px', border: 'none', background: 'none', cursor: 'pointer', flex: 'none',
        fontFamily: 'var(--liq-font-sans)', fontSize: 14, fontWeight: 800, color: 'var(--liq-orange-500)',
      }}>Undo</button>
    </div>
  );
}

// ── BUILT BY SEARCH 02, consumed here (copied for reading; one implementation) ──
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

function LikesReceivedCard({ p, onOpen, notNow = false }) {
  const mot = premiumMotivation(p.motivation);
  if (p.closed) {
    const late = p.closed === 'matched';
    return (
      <Card style={{ padding: 14, display: 'flex', gap: 14, background: 'var(--liq-bg-raised)', boxShadow: 'none', border: '1px solid var(--liq-border)' }}>
        <div style={{ width: 88, flex: 'none' }}>
          <PortraitPlaceholder name={p.name} height={120} radius={16} label={false} style={{ width: 88, filter: 'grayscale(0.92)', opacity: 0.7 }}/>
        </div>
        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6, justifyContent: 'center' }}>
          <span style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 17, color: 'var(--liq-fg-subtle)' }}>{premiumName(p)}</span>
          <span style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 15.5, color: 'var(--liq-fg)', lineHeight: 1.25, textWrap: 'pretty' }}>{late ? `Too late — ${p.name} has a date with someone else.` : `Out of time — you and ${p.name} have no time left to meet.`}</span>
          <span style={{ fontSize: 12.5, color: 'var(--liq-neutral-200)', lineHeight: 1.45, textWrap: 'pretty' }}>{late ? 'A like closes as soon as either of you matches. Confirm sooner next time.' : 'A like closes when your Check-Ins no longer overlap. Confirm sooner next time.'}</span>
        </div>
      </Card>
    );
  }
  return (
    <Card style={{ padding: 14, display: 'flex', gap: 14 }}>
      <div style={{ width: 88, flex: 'none' }}>
        <button onClick={onOpen} aria-label={`Open ${p.name}'s profile${notNow ? ', not now' : ''}`} style={{ padding: 0, border: 'none', background: 'none', display: 'block', cursor: 'pointer' }}>
          <PortraitPlaceholder name={p.name} height={120} radius={16} label={false} style={{ width: 88 }}/>
        </button>
      </div>
      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 7 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, flexWrap: 'wrap' }}>
          <span style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 17, color: 'var(--liq-fg)' }}>{premiumName(p)}</span>
          <span style={{ fontSize: 11.5, fontWeight: 600, color: 'var(--liq-fg-subtle)' }}>· {searchDistance(p.dist)}</span>
          {notNow && <span style={{ marginLeft: 'auto', alignSelf: 'center', padding: '4px 9px', borderRadius: 9999, background: '#fff', border: '1px solid var(--liq-border)', fontSize: 11, fontWeight: 800, color: 'var(--liq-fg-muted)', whiteSpace: 'nowrap' }}>Not now</span>}
        </div>
        <div><ShowUpCount count={p.attended} size="xs"/></div>
        {p.msg && <p style={{ fontFamily: 'var(--liq-font-serif)', fontStyle: 'italic', fontSize: 13.5, color: 'var(--liq-fg)', margin: 0, lineHeight: 1.4, textWrap: 'pretty' }}>"{p.msg}"</p>}
        <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
          <Chip size="xs" tone="orange" selected leading={<Icon name="clock" size={12}/>}>{p.meetAt} {p.meetDay}</Chip>
          {mot && <Chip size="xs" tone="lavender" leading={<Icon name={mot.icon} size={12}/>}>{mot.t}</Chip>}
        </div>
        <div style={{ display: 'flex', gap: 8, marginTop: 2, flexWrap: 'wrap' }}>
          <Button size="sm" variant="sunset" onClick={onOpen}>View profile</Button>
        </div>
      </div>
    </Card>
  );
}

// initialUndo   name → that card declined, Undo toast showing (state D)
// initialClosed { name: 'matched' | 'no_overlap' } → a confirm refused by
//               the server: the card shown closed, in place (E, F)
// initialModal  index → that profile opened with Be sure showing (C)
// initialNotNow  names marked Not now this visit (label only, no dimming)
// recheck        { name: 'matched' | 'no_overlap' } applied on the next return
//               from a profile (availability re-check, as Search 02)
// freezeLeaving  { name, t } — that card frozen t ms into its fly-out
function ScreenLikedMe({ onSearch, empty = false, initialModal = null, initialOpen = null, initialUndo = null, initialClosed = {}, refuse = {}, initialSheet = 'confirm', initialNotNow = [], recheck = null, freezeLeaving = null }) {
  const { refs, gone, setGone, leaving, setLeaving, snap } = useCardFlyOut({ initialGone: initialUndo && !freezeLeaving ? [initialUndo] : [], initialLeaving: freezeLeaving ? freezeLeaving.name : null, freeze: freezeLeaving });
  const [closed, setClosed] = React.useState(initialClosed);
  const [notNow, setNotNow] = React.useState(initialNotNow);
  const pending = React.useRef(recheck);
  const back = () => { setOpen(null); if (pending.current) { const r = pending.current; pending.current = null; setClosed(s => ({ ...s, ...r })); } };
  const [undo, setUndo] = React.useState(initialUndo);
  const [open, setOpen] = React.useState(initialModal != null ? initialModal : initialOpen);
  const timer = React.useRef(null);
  React.useEffect(() => () => clearTimeout(timer.current), []);
  // Sorted once, on load. A refused confirm closes its card IN PLACE: it
  // does not move to the end until the next load.
  const all = likesOrder(LIKES_RECEIVED).map(p => closed[p.name] ? { ...p, closed: closed[p.name] } : p);
  const list = empty ? [] : all.filter(p => !gone.includes(p.name));
  const remove = name => setGone(g => [...g, name]);
  // Not interested: the card flies out (the Search 02 motion), the decline is
  // sent when the toast ends. Undo puts it back in its slot.
  const decline = name => {
    setLeaving(name); setUndo(name);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setUndo(null), LIKES_UNDO_MS);
  };
  const restore = () => { clearTimeout(timer.current); snap(); setGone(g => g.filter(n => n !== undo)); setLeaving(l => l === undo ? null : l); setUndo(null); };
  const badge = empty ? 0 : likesOpenCount(all.filter(p => !gone.includes(p.name)));
  const tabs = <TabBar active="likes" likes={badge} onTab={id => id === 'search' && onSearch && onSearch()}/>;

  if (open != null) {
    const c = LIKES_RECEIVED[open];
    return <ScreenSearchProfile premium likedYou tab="likes" profile={premiumProfile(c)} backLabel="All likes" refuse={refuse[c.name]} refusedLabel="Back to likes"
      initialModal={initialModal != null ? initialSheet : null}
      onBack={back}
      onAction={a => { back(); if (a === 'not_now') return setNotNow(n => n.includes(c.name) ? n : [...n, c.name]); if (GRID_CLOSED[a]) return setClosed(s => ({ ...s, [c.name]: a })); a === 'not_interested' ? decline(c.name) : remove(c.name); }}/>;
  }
  return (
    <Phone>
      {/* flex column, min-height 100%: the list takes the free space, so the
          TabBar lands at the bottom on a short list with no magic number. */}
      <div style={{ minHeight: '100%', display: 'flex', flexDirection: 'column' }}>
      <SearchTopBar premium filter={false} chip={false}/>
      {list.length ? (
        <div style={{ flex: 1, padding: '8px 16px 88px', display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6, paddingBottom: 6 }}>
            <h2 className="su-underlined" style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 24, color: 'var(--liq-fg)', margin: 0, letterSpacing: '-0.01em', lineHeight: 1.2 }}>
              People who want to <em>meet you</em>
            </h2>
            <p style={{ fontSize: 14, color: 'var(--liq-neutral-200)', margin: 0, lineHeight: 1.5 }}>Open a profile, then decide. Confirm and you two have a binding date.</p>
          </div>
          {list.map(p => {
            const i = LIKES_RECEIVED.findIndex(q => q.name === p.name);
            return <div key={p.name} ref={el => { refs.current[p.name] = el; }}><LikesReceivedCard p={p} notNow={!p.closed && notNow.includes(p.name)} onOpen={leaving === p.name ? undefined : () => { setUndo(null); setOpen(i); }}/></div>;
          })}
        </div>
      ) : (
        <div style={{ flex: 1, padding: '72px 32px 0', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 14 }}>
          <div style={{ width: 88, height: 88, borderRadius: 9999, background: 'var(--su-grad-blush)', color: 'var(--liq-orange-500)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon name="heart" size={36} stroke={2}/>
          </div>
          <h2 className="su-underlined" style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 24, color: 'var(--liq-fg)', margin: '6px 0 0', letterSpacing: '-0.01em', lineHeight: 1.2 }}>
            No likes <em>yet</em>
          </h2>
          <p style={{ fontSize: 14, color: 'var(--liq-neutral-200)', margin: 0, lineHeight: 1.5, maxWidth: 290, textWrap: 'pretty' }}>
            When someone likes you, they show up here — with their message, if they wrote one.
          </p>
          <div style={{ marginTop: 8 }}>
            <Button variant="sunset" size="md" onClick={onSearch}>Back to search</Button>
          </div>
        </div>
      )}
      {tabs}
      </div>
      {undo && <LikesUndoToast name={undo} onUndo={restore}/>}
    </Phone>
  );
}

Object.assign(window, { ScreenLikedMe, LikesReceivedCard, LikesUndoToast, LIKES_RECEIVED, likesOrder, likesOpenCount, LIKES_UNDO_MS });
