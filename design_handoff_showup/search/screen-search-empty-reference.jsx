// screen-search-empty-reference.jsx — Search 01 interim no-results (27 Oct 2026)
// Copied from ui_kits/show-up/screens-search-empty.jsx. Build NORMAL mode only:
// headline + body + "Widen my search" (opens Filters). Leave out ctaSub, the
// filter read-out's one-tap widen and "Adjust filters manually" — they belong
// to the no-results story. Instant mode is out of scope.
// Depends on SearchTopBar / TabBar (screen-search-profile-reference.jsx,
// components/shared.jsx).

// screens-search-empty.jsx — Date Search "no results" empty state
//
// What the user sees when Date Search (or the Instant live feed) runs out
// of people to show. There are two reasons the 1-up / grid view can come up
// dry: the user has worked through everyone currently in range, or their
// filters are simply too tight to surface anyone. Either way the recovery is
// the same — loosen the search so new people appear — so this screen leads
// with a single, one-tap "widen my search" solution rather than dumping the
// user into the full filter editor.
//
// The screen shows the radar coming up dry, a headline + reason, a read-out
// of the current (too-tight) filters, then one sunset CTA that widens
// everything in one tap, plus a quiet "adjust manually" escape hatch.
//
// `instant` swaps the vocabulary throughout: distance is expressed in
// travel-minutes (the unit that matters when the date is imminent), the
// pool is "live right now", and the top bar / tab bar light the Instant
// surface. Everything else is shared with the normal flow.
//
// Reuses SearchTopBar + TabBar from screens-search.jsx and the kit's
// Button / Card / Chip / Icon primitives.
//
// Exposed on window:
//   - ScreenSearchEmpty

// ─────────────────────────────────────────────────────────────
// COPY + FILTER MODEL — keyed by mode. One source of truth so copy
// review doesn't mean spelunking the layout. `before` is the tight
// filter set the user is stuck on; the CTA widens it in one tap.
// ─────────────────────────────────────────────────────────────
const SEARCH_EMPTY_COPY = {
  normal: {
    eyebrow:  '',
    headline: <>Unfortunately there are <em>no people</em> left matching your search filters.</>,
    body:     "Widen your search filters to find more people.",
    cta:      { label: 'Widen my search', icon: 'sliders' },
    ctaSub:   'We will match your settings to receive the best results.',
  },
  instant: {
    eyebrow:  'No one live nearby',
    headline: <>Nobody&rsquo;s <em>live</em> near you right now.</>,
    body:     "The live pool inside your range is empty this minute. Reach further out and we’ll pull in everyone who goes live within a wider radius.",
    cta:      { label: 'Widen the radius', icon: 'compass' },
    ctaSub:   'Reach 10 → 30 min · all intents',
    filters: {
      before: [
        { icon: 'navigation', label: 'Reach',       value: 'Within 10 min',  tight: true },
        { icon: 'user',       label: 'Age',         value: '28 – 32',        tight: true },
        { icon: 'heart',      label: 'Looking for', value: 'Long-term only', tight: true },
      ],
    },
  },
};

// ─────────────────────────────────────────────────────────────
// EmptyRadar — the "came up dry" illustration.
//
// Concentric rings (the search radius) with a single faded glyph at the
// centre and no "nearby" dots — the visual inverse of the location gate's
// live radar. `instant` swaps the centre glyph (compass) and warms the halo
// so the live feed reads distinct from the normal search.
// ─────────────────────────────────────────────────────────────
function EmptyRadar({ instant = false }) {
  const ring = (size, dash, op) => ({
    position: 'absolute', top: '50%', left: '50%',
    width: size, height: size, marginTop: -size / 2, marginLeft: -size / 2,
    borderRadius: '50%',
    border: `1.25px ${dash ? 'dashed' : 'solid'} rgba(129,42,236,${op})`,
  });
  return (
    <div style={{ position: 'relative', width: '100%', height: 184, flex: 'none' }}>
      {/* soft warm halo behind the rings */}
      <div style={{
        position: 'absolute', top: '50%', left: '50%', width: 220, height: 220,
        marginTop: -110, marginLeft: -110, borderRadius: '50%',
        background: `radial-gradient(circle, rgba(254,104,57,${instant ? 0.16 : 0.10}) 0%, rgba(254,104,57,0) 65%)`,
        filter: 'blur(6px)',
      }}/>
      <div style={ring(176, true, 0.10)}/>
      <div style={ring(116, true, 0.16)}/>
      <div style={ring(64,  false, 0.28)}/>
      {/* centre glyph — muted, no result */}
      <div style={{
        position: 'absolute', top: '50%', left: '50%', width: 64, height: 64,
        marginTop: -32, marginLeft: -32, borderRadius: '50%',
        background: 'var(--liq-bg-elevated)',
        border: '1px solid var(--liq-border-soft)',
        boxShadow: 'var(--liq-shadow-md)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--liq-fg-faint)',
      }}>
        <Icon name={instant ? 'compass' : 'search'} size={26} stroke={1.8}/>
      </div>
    </div>
  );
}

// One filter row in the read-out card. `tight` flags the constraint orange.
function FilterRow({ icon, label, value, tight = false, last = false }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 12,
      padding: '12px 0',
      borderBottom: last ? 'none' : '1px solid var(--liq-border-soft)',
    }}>
      <span style={{
        width: 34, height: 34, borderRadius: 11, flex: 'none',
        background: tight ? 'rgba(254,104,57,0.12)' : 'var(--liq-bg-raised)',
        color: tight ? 'var(--liq-orange-500)' : 'var(--liq-primary-500)',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Icon name={icon} size={16} stroke={2}/>
      </span>
      <span style={{
        flex: 1, fontSize: 11, fontWeight: 800, letterSpacing: 0.08,
        textTransform: 'uppercase', color: 'var(--liq-fg-subtle)',
      }}>{label}</span>
      <span style={{
        fontSize: 14, fontWeight: 700,
        color: 'var(--liq-fg)',
        whiteSpace: 'nowrap',
      }}>{value}</span>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenSearchEmpty
//
// Props:
//   instant   live-feed vocabulary (minutes, "live now", Instant chrome)
// ─────────────────────────────────────────────────────────────
function ScreenSearchEmpty({ instant = false }) {
  const c = instant ? SEARCH_EMPTY_COPY.instant : SEARCH_EMPTY_COPY.normal;

  return (
    <Phone height={844}>
      <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        <SearchTopBar instant={instant}/>

        <div style={{ padding: '8px 24px 0', display: 'flex', flexDirection: 'column' }}>
          <EmptyRadar instant={instant}/>

          {c.eyebrow ? (
            <div style={{
              fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 11,
              color: instant ? 'var(--liq-orange-500)' : 'var(--liq-fg-subtle)',
              textTransform: 'uppercase', letterSpacing: 0.12, textAlign: 'center',
              marginTop: 4, marginBottom: 10,
            }}>{c.eyebrow}</div>
          ) : <div style={{ marginTop: 14 }}/>}

          <h1 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
            letterSpacing: '-0.015em', lineHeight: 1.12, color: 'var(--liq-fg)',
            margin: 0, textAlign: 'center', textWrap: 'balance',
          }}>{c.headline}</h1>

          <p style={{
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
            lineHeight: 1.5, color: 'var(--liq-neutral-200)',
            margin: '12px auto 0', maxWidth: 320, textAlign: 'center', textWrap: 'pretty',
          }}>{c.body}</p>
        </div>

        {/* Flexible spacer — pulls the CTA block up so it always clears the
            tab bar without scrolling. */}
        <div style={{ flex: 1, minHeight: 24 }}/>

        {/* Footer — the one-tap solution + manual escape hatch. Sits in normal
            flow directly above the tab bar, always visible. */}
        <div style={{ padding: '0 24px 22px' }}>
          <Button variant="sunset" size="lg" fullWidth
            onClick={() => {}}
            leading={<Icon name={c.cta.icon} size={18} stroke={2.2}/>}>
            {c.cta.label}
          </Button>
          <div style={{
            textAlign: 'center', fontSize: 12, fontWeight: 600, color: 'var(--liq-fg-subtle)',
            margin: '9px 0 0', letterSpacing: 0.01,
          }}>{c.ctaSub}</div>
          <button onClick={() => {}} style={{
            display: 'block', margin: '12px auto 0', padding: 6,
            fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 13,
            color: 'var(--liq-primary-500)', background: 'transparent', border: 'none',
            cursor: 'pointer',
          }}>Adjust filters manually</button>
        </div>

        <TabBar active={instant ? 'instant' : 'search'}/>
      </div>
    </Phone>
  );
}

Object.assign(window, { ScreenSearchEmpty });
