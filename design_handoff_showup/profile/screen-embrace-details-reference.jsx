// screen-embrace-details-reference.jsx — Profile creation 13 · Embrace 2: add profile details
//
// Design reference for [Profile 13] Embrace 2 — add profile details. This is
// the code that renders spec-sheets/13-embrace-details.png. Read values from
// HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit
// (ui_kits/show-up/screens-profile-embrace2.jsx), 5 Oct 2026. It is a
// reference implementation, not production code: recreate it in the target
// codebase's own environment and patterns. Do not ship this file.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// ONE STATE, ONE CONTENT PROP. <ScreenProfileEmbrace2 name="Leo"/>. The only
// branch is the name: "You are doing great, Leo." / "You are doing great."
//
// THE CONFETTI IS THE POINT OF THE SCREEN, AND IT IS NOT A STATE. The user has
// just finished the core profile; the screen celebrates that once, on arrival,
// then gets out of the way. ConfettiRain below carries every number:
//   CONFETTI_PIECES      the 32-row table — position, delay, fall, spin, sway,
//                        shape, colour, size. Deterministic; do not randomise
//   CONFETTI_TOTAL_MS    3000 — the layer unmounts here; it never loops
//   CONFETTI_FALL_EASE   cubic-bezier(.3,.4,.6,1)
//   confettiSwayMs(i)    700 / 820 / 940 / 1060 by index
//   keyframes            su-cf-fall (translateY −40 → height + 40, opacity
//                        1 → 0 over the last 20%) and su-cf-sway (sway ±px,
//                        rotate rot0 → rot0 + spin, rotateY 0 → 180°)
// t = 0 is when the push transition has SETTLED, not mount. Reduced motion
// renders nothing. Transform and opacity only.
//
// THE confetti AND frozenAt PROPS ARE FOR THE SPEC SHEET, NOT THE APP.
// 'frozen' pauses every piece at frozenAt ms (frame B is t = 900); 'off'
// renders nothing. The app needs exactly one behaviour: play once.
//
// THIS IS NOT A STEP. No AppHeader, no StepProgress, no back, no skip — same
// as the first bridge (screen 05). It belongs to neither progress bar.
//
// SAME SHELL AS SCREEN 05, DIFFERENT PAYLOAD. Backdrop, sunset CTA and the
// no-chrome rule are shared. What differs is listed in the ticket: the violet
// glow is centred rather than in the corner, the headline has no em (same
// 34 / 1.1 / −0.015em as 05 — one bridge headline size, decided 5 Oct 2026),
// there are no bullets, and this screen has the confetti.
//
// ─────────────────────────────────────────────────────────────

// screens-profile-embrace2.jsx — Profile creation: second "embrace" transition
//
// The first ScreenProfileEmbrace sits between "the basics" (name, email,
// DoB) and the first profile-building step (photos). This second embrace
// sits *between* the profile-building work the user has just done (photos,
// prompts, media) and the next phase: optional background details that
// flesh out a profile (height, work, prompts beyond the first three,
// values, etc.).
//
// Same template as the first embrace and ScreenProfileNotifications:
//   - warm-paper canvas, peach wash + orange/violet orbs
//   - dark Lora headline (here without italic-flourish — the screenshot
//     reference has a pure-typographic headline, no emphasis underline)
//   - Manrope lead body
//   - empty middle (room for the violet orb to breathe through)
//   - full-width sunset CTA pinned to the bottom with a trailing arrow
//
// Voice note:
//   The reference's "You are doing great Leo." is on-brand-adjacent —
//   "doing great" is a notch more cheerleader than Show Up usually
//   permits, but the underlying intent (a one-beat reward before asking
//   for more) is right. We honour the reference copy verbatim because
//   it was provided by the user as the literal screen content.
//
// Props:
//   name        the user's first name (default: "Leo" — matches the kit's
//               canonical demo user). Falls back to a generic line if
//               empty.
//   onContinue  fired on the CTA tap.
//   confetti    'play' (default) · 'frozen' · 'off' — see ConfettiRain
//   frozenAt    ms, only with confetti="frozen" (spec sheet: 900)

// ─────────────────────────────────────────────────────────────
// ConfettiRain — one-shot celebratory rain, plays once on arrival
// ─────────────────────────────────────────────────────────────
// Added 5 Oct 2026 with the Profile 13 ticket (replaces the static
// placeholder ConfettiRainStatic). The intent: the user has just finished
// the core profile, and the screen celebrates that before asking for more.
//
// TIMELINE (t = 0 when the screen is fully on screen — the push transition
// has settled; in the kit that is mount):
//   - 32 pieces, each starts 40px ABOVE the top edge and falls to 40px
//     BELOW the bottom edge, so no piece is ever born or killed on screen
//   - per-piece delay 0–700ms, per-piece fall 1950–2600ms
//   - the last piece leaves the screen at t ≈ 2930ms; the layer is
//     UNMOUNTED at CONFETTI_TOTAL_MS (3000). It plays once, never loops
//   - fall easing cubic-bezier(.3,.4,.6,1); opacity 1 until 80% of the
//     fall, 0 at 100% — pieces fade as they leave, never pop
//   - while falling, each piece sways ±sway px, spins rot0 → rot0 + spin,
//     and flips on Y (the paper-flutter read) — alternate, ease-in-out,
//     700–1060ms per swing
//
// LAYERING: full-screen, absolute inset 0, zIndex 0, after the backdrop
// in DOM order — ABOVE the orbs, BELOW the content (zIndex 1). Confetti
// passes behind the headline and the CTA, never over them.
//
// GPU properties only: transform + opacity. Nothing animates top/left/
// width/height. pointer-events none, aria-hidden — it is decoration.
//
// REDUCED MOTION: renders nothing. Not a static scatter — frozen pieces
// on a still screen read as debris, not celebration.
//
// MODES (kit/spec only):
//   play    default — plays once on mount, then unmounts
//   frozen  pauses every piece at frozenAt ms, for the spec sheet's
//           mid-fall frame. Uses the same keyframes with a negative
//           delay, so the frozen frame IS the animation at that moment
//   off     renders nothing (also what reduced motion resolves to)
//
// PALETTE: tokens only — the sunset CTA's two ends plus one step lighter
// each, so the rain reads as "of this canvas", not party confetti.
const CONFETTI_TOTAL_MS = 3000;
const CONFETTI_FALL_EASE = 'cubic-bezier(.3,.4,.6,1)';
const CF_O5 = '#FE6839'; // --liq-orange-500
const CF_O4 = '#FF9450'; // --liq-orange-400
const CF_V5 = '#812AEC'; // --liq-primary-500
const CF_V4 = '#A855F7'; // --liq-primary-400
// [left %, delay ms, fall ms, rot0 °, spin °, sway px, shape, colour, size px]
const CONFETTI_PIECES = [
  [ 4,   0, 2300, -18,  540, 14, 'strip',  CF_O5, 14],
  [12, 260, 2100,  42, -420, 18, 'square', CF_V5, 10],
  [19,  80, 2500,  12,  600, 12, 'strip',  CF_O4, 12],
  [26, 420, 2000, -30, -360, 20, 'dot',    CF_V4,  9],
  [33, 140, 2400,  68,  480, 16, 'strip',  CF_V5, 14],
  [40, 560, 2200, -12, -540, 14, 'square', CF_O4, 11],
  [47,  40, 2600,  24,  420, 22, 'strip',  CF_O5, 13],
  [54, 320, 2050, -52, -600, 12, 'dot',    CF_V5,  8],
  [61, 180, 2350,  18,  360, 18, 'strip',  CF_O4, 12],
  [68, 480, 2150,  -8, -480, 16, 'square', CF_O5, 10],
  [75, 100, 2450,  36,  540, 14, 'strip',  CF_V4, 13],
  [82, 380, 2000,  54, -420, 20, 'strip',  CF_O4, 11],
  [89, 220, 2300, -22,  600, 12, 'dot',    CF_O5,  9],
  [96, 600, 2100,  14, -360, 18, 'strip',  CF_V5, 12],
  [ 8, 660, 2250, -40,  480, 16, 'square', CF_O4, 10],
  [16, 360, 2550,  30, -540, 14, 'strip',  CF_O5, 13],
  [23,  20, 1950, -14,  420, 22, 'dot',    CF_V5,  8],
  [30, 500, 2400,  62, -600, 12, 'strip',  CF_O4, 11],
  [37, 240, 2150, -36,  360, 18, 'square', CF_V4,  9],
  [44, 700, 2050,  20, -480, 16, 'strip',  CF_O5, 12],
  [51, 160, 2500, -28,  540, 14, 'strip',  CF_V5, 10],
  [58, 440, 2200,  46, -420, 20, 'dot',    CF_O4,  9],
  [65,  60, 2350,   8,  600, 12, 'strip',  CF_O5, 12],
  [72, 620, 2000, -16, -360, 18, 'square', CF_V5, 10],
  [79, 300, 2450,  58,  480, 16, 'strip',  CF_V4, 13],
  [86, 520, 2100, -44, -540, 14, 'strip',  CF_O4, 11],
  [93, 120, 2300,  26,  420, 22, 'dot',    CF_V5,  8],
  [ 2, 400, 2150,  -6, -600, 12, 'strip',  CF_V4, 12],
  [49, 680, 2250,  34,  360, 18, 'strip',  CF_O5, 11],
  [63, 280, 2600, -24, -480, 16, 'square', CF_O4,  9],
  [35, 580, 2050,  50,  540, 14, 'dot',    CF_O5,  9],
  [85, 200, 2400, -10, -420, 20, 'strip',  CF_V5, 12],
];
const confettiSwayMs = (i) => 700 + (i % 4) * 120; // 700 · 820 · 940 · 1060

function ConfettiRain({ mode = 'play', frozenAt = 900, height = 844 }) {
  const reduced = typeof window !== 'undefined' && window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const [done, setDone] = React.useState(false);
  React.useEffect(() => {
    if (mode !== 'play') return;
    const t = setTimeout(() => setDone(true), CONFETTI_TOTAL_MS);
    return () => clearTimeout(t);
  }, [mode]);
  if (mode === 'off' || done || (mode === 'play' && reduced)) return null;
  const frozen = mode === 'frozen';
  const fall = height + 40;

  return (
    <div aria-hidden="true" style={{
      position: 'absolute', inset: 0, zIndex: 0,
      pointerEvents: 'none', overflow: 'hidden',
    }}>
      <style>{`
        @keyframes su-cf-fall{0%{transform:translate3d(0,-40px,0);opacity:1}80%{opacity:1}100%{transform:translate3d(0,var(--cf-fall),0);opacity:0}}
        @keyframes su-cf-sway{0%{transform:translateX(calc(var(--cf-sway) * -1)) rotate(var(--cf-r0)) rotateY(0deg)}100%{transform:translateX(var(--cf-sway)) rotate(var(--cf-r1)) rotateY(180deg)}}
      `}</style>
      {CONFETTI_PIECES.map(([left, delay, dur, rot0, spin, sway, shape, color, size], i) => {
        const swayMs = confettiSwayMs(i);
        const offset = frozen ? delay - frozenAt : delay;
        const shapeStyle = shape === 'dot'
          ? { width: size, height: size, borderRadius: '50%' }
          : shape === 'square'
            ? { width: size, height: size, borderRadius: 2 }
            : { width: size * 0.5, height: size * 1.4, borderRadius: 1.5 };
        return (
          <span key={i} style={{
            position: 'absolute', top: 0, left: `${left}%`,
            '--cf-fall': `${fall}px`,
            transform: 'translate3d(0,-40px,0)',
            animation: `su-cf-fall ${dur}ms ${CONFETTI_FALL_EASE} ${offset}ms both`,
            animationPlayState: frozen ? 'paused' : 'running',
            willChange: 'transform, opacity',
          }}>
            <span style={{
              display: 'block', background: color, ...shapeStyle,
              '--cf-sway': `${sway}px`, '--cf-r0': `${rot0}deg`, '--cf-r1': `${rot0 + spin}deg`,
              animation: `su-cf-sway ${swayMs}ms ease-in-out ${offset}ms infinite alternate both`,
              animationPlayState: frozen ? 'paused' : 'running',
            }}/>
          </span>
        );
      })}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileEmbrace2 — the second bridge screen
// ─────────────────────────────────────────────────────────────
function ScreenProfileEmbrace2({ name = 'Leo', onContinue, confetti = 'play', frozenAt = 900 }) {
  const cleanName = (name || '').trim();

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Ambient backdrop — same recipe as the first embrace, but the
          violet orb is pulled centre-bottom (matching the reference) so
          the empty middle of the screen carries a soft lavender glow
          rather than just a corner accent. The orange orb stays in the
          top corner so the peach wash + warm top edge still reads. */}
      <div style={{
        position: 'absolute', top: '-18%', right: '-20%', width: 480, height: 480,
        background: 'radial-gradient(circle, rgba(254,104,57,0.30) 0%, rgba(254,104,57,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>
      <div style={{
        position: 'absolute', bottom: '-10%', left: '-10%', right: '-10%',
        height: 540,
        background: 'radial-gradient(ellipse at 50% 70%, rgba(129,42,236,0.30) 0%, rgba(129,42,236,0) 60%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0,
        background: 'linear-gradient(180deg, rgba(255,229,210,0.55) 0%, rgba(255,251,247,0) 60%)',
        height: 320, pointerEvents: 'none', zIndex: 0,
      }}/>

      {/* CONFETTI — full-screen, above the backdrop, below the content.
          Plays once on arrival, then unmounts. See ConfettiRain. */}
      <ConfettiRain mode={confetti} frozenAt={frozenAt}/>

      {/* Status bar */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20" dark={false}/>
      </div>

      {/* HEADLINE — dark Lora on the warm-paper canvas. No italic
          flourish; the reference copy has no emphasis word, and the
          headline reads as a calm complete sentence, not a manifesto
          fragment. */}
      <div style={{
        position: 'relative', zIndex: 1,
        padding: '64px 28px 0',
        flex: 'none',
      }}>
        <h1 style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 34,
          letterSpacing: '-0.015em', lineHeight: 1.1,
          color: 'var(--liq-fg)', margin: 0, textWrap: 'balance',
        }}>
          {cleanName
            ? <>You are doing great, {cleanName}.</>
            : <>You are doing great.</>
          }
        </h1>
      </div>

      {/* BODY — lead paragraph only. No bullets, no list. The screen's
          job here is to celebrate + transition, then get out of the way
          so the empty middle (and its violet glow) carries the breath
          before the next ask. */}
      <div style={{
        position: 'relative', zIndex: 1,
        padding: '20px 28px 0',
        flex: 1,
        display: 'flex', flexDirection: 'column',
      }}>
        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 16,
          lineHeight: 1.55, color: 'var(--liq-neutral-200)',
          margin: 0, textWrap: 'pretty', maxWidth: 330,
        }}>
          You'll see on others' profiles exactly what you choose to
          share on yours. Let's add a few more details!
        </p>


        <div style={{ flex: 1 }}/>

        {/* FOOTER — sunset CTA, full width. Sunset because adding the
            optional details is a small commitment beat, same vocabulary
            as the first embrace's "Create my profile". */}
        <div style={{ marginBottom: 22 }}>
          <Button
            variant="sunset"
            size="lg"
            fullWidth
            onClick={onContinue}
            trailing={
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" strokeWidth="2"
                strokeLinecap="round" strokeLinejoin="round">
                <path d="M5 12h14M13 5l7 7-7 7"/>
              </svg>
            }
          >
            Add profile details
          </Button>
        </div>
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, { ScreenProfileEmbrace2 });
