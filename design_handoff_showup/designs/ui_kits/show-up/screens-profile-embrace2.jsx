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

// ─────────────────────────────────────────────────────────────
// ConfettiRainStatic — frozen confetti for design review
// ─────────────────────────────────────────────────────────────
// In the final shipped flow, this screen rains confetti from the top of
// the viewport on mount: a one-shot celebratory beat that fires once
// the user lands here, runs ~2.5s, then settles. For the design review
// pass we render the pieces statically — laid out mid-fall — so the
// reviewer can evaluate palette, density and distribution without the
// motion. Wired up to animate later by adding `animation: cr-fall …`
// per-piece with a stagger.
//
// Palette pulls from the Show Up sunset CTA + violet orb so the rain
// reads as "of this canvas" rather than generic party confetti.
function ConfettiRainStatic() {
  // Deterministic layout: hand-tuned so the spread feels organic but
  // doesn't crowd the headline or the CTA. Each piece: [left%, top%,
  // rotation°, shape, color, size].
  const pieces = [
    [  6,  18, -18, 'rect',   '#FE6839', 14],
    [ 14,  34,  42, 'square', '#812AEC', 10],
    [ 22,   9,  12, 'rect',   '#F5B454', 12],
    [ 31,  46, -30, 'circle', '#FE6839',  9],
    [ 40,  22,  68, 'rect',   '#812AEC', 14],
    [ 49,  58, -12, 'square', '#F5B454', 11],
    [ 58,  14,  24, 'rect',   '#FE6839', 13],
    [ 66,  40, -52, 'circle', '#812AEC',  8],
    [ 74,  26,  18, 'rect',   '#F5B454', 12],
    [ 82,  50,  -8, 'square', '#FE6839', 10],
    [ 90,  18,  36, 'rect',   '#812AEC', 13],
    [ 10,  62,  54, 'rect',   '#F5B454', 11],
    [ 27,  72, -22, 'circle', '#FE6839',  9],
    [ 44,  78,  14, 'rect',   '#812AEC', 12],
    [ 60,  68, -40, 'square', '#F5B454', 10],
    [ 76,  74,  30, 'rect',   '#FE6839', 13],
    [ 88,  60, -14, 'circle', '#812AEC',  8],
    [ 18,  50,  62, 'rect',   '#FE6839', 11],
    [ 36,  30, -36, 'square', '#812AEC',  9],
    [ 52,  44,  20, 'rect',   '#F5B454', 12],
    [ 68,  56, -28, 'rect',   '#FE6839', 10],
    [ 84,  36,  46, 'circle', '#F5B454',  9],
    [  4,  44,   8, 'rect',   '#812AEC', 12],
    [ 94,  44, -16, 'square', '#FE6839', 10],
  ];

  return (
    <div aria-hidden="true" style={{
      position: 'absolute', inset: 0,
      pointerEvents: 'none', zIndex: 0,
      overflow: 'hidden',
    }}>
      {pieces.map(([leftPct, topPct, rot, shape, color, size], i) => {
        const base = {
          position: 'absolute',
          left: `${leftPct}%`,
          top: `${topPct}%`,
          transform: `rotate(${rot}deg)`,
          background: color,
        };
        let style;
        if (shape === 'circle') {
          style = { ...base, width: size, height: size, borderRadius: '50%' };
        } else if (shape === 'square') {
          style = { ...base, width: size, height: size, borderRadius: 2 };
        } else { // rect — confetti strip
          style = { ...base, width: size * 0.5, height: size * 1.4, borderRadius: 1.5 };
        }
        return <span key={i} style={style}/>;
      })}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileEmbrace2 — the second bridge screen
// ─────────────────────────────────────────────────────────────
function ScreenProfileEmbrace2({ name = 'Leo', onContinue }) {
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
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 36,
          letterSpacing: '-0.018em', lineHeight: 1.08,
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

        {/* CONFETTI — static placement for reference. In the final
            product these pieces rain down from above the viewport on
            screen mount; here we lay them out frozen mid-fall so the
            user can review density, palette and spread without
            motion. Sits behind the CTA (z-index 0 inside the body
            column) and clipped by the parent overflow:hidden so
            stray pieces don't escape the frame. */}
        <ConfettiRainStatic/>

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
