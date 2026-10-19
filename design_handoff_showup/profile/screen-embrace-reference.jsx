// screen-embrace-reference.jsx — Profile creation 05 · Embrace: build your profile
//
// Design reference for [Profile 05] Embrace — build your profile. This is the
// code that renders spec-sheets/05-embrace-build-profile.png. Read values from
// HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit
// (ui_kits/show-up/screens-profile-embrace.jsx). It is a reference
// implementation, not production code: recreate it in the target codebase's
// own environment and patterns. Do not ship this file.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// ONE STATE, ONE PROP. <ScreenProfileEmbrace name="Leo"/>. The only branch is
// whether a first name exists:
//   with a name     "Nice to see you, Leo."
//   without a name  "Glad you're here."
// The second sentence is identical in both. There is no error state, no empty
// state, no loading state — nothing can fail on this screen.
//
// THIS IS NOT A STEP. It is a bridge between "The basics" (name, email, DoB)
// and the profile-building steps (photos, prompts, media). So:
//   - NO AppHeader. No section title, no back chevron, no skip, no close.
//   - NO StepProgress. It belongs to neither progress bar — "The basics" is
//     finished and "Share some details" has not started.
//   - Nothing to fill in, so nothing to validate.
// Adding a header or a progress bar "for consistency" is the single most
// likely mistake on this screen. The absence is the design.
//
// IT IS THE ONE PROFILE SCREEN WITH THE AMBIENT BACKDROP. Flow README rule 5
// ("no ambient backdrop, flat --liq-bg") holds for every screen whose keyboard
// is open. This screen has no keyboard and no input, so it carries the
// first-run Startup screen's atmosphere instead: an orange orb top-right, a
// violet orb bottom-left, and a peach wash down the top 360. That is what
// makes the bridge feel like a beat rather than another form. Named exception,
// this screen and its sibling bridge only.
//
// THE CTA IS SUNSET, NOT ORANGE. Flow README rule 7 reserves --su-grad-sunset
// for commitment beats and this is one: the user is agreeing to build the
// profile. It is also FULL-WIDTH, not the round NextButton the basics use —
// the second and last deliberate departure from the group's chrome.
//
// LAYOUT — flex column, ONE flex: 1 spacer, nothing absolutely positioned
// except the three decorative backdrop layers:
//   StatusBar (54, fixed)
//   headline block   — padding 64 / 28 / 0, flex: none
//   body block       — padding 28 / 28 / 0, flex: 1, column
//     lead p         — Manrope 500 / 15.5 / 1.55, --liq-neutral-200
//     bullet ul      — marginTop 14, gap 8
//     closing p      — marginTop 14, same type as the lead
//     spacer (flex: 1)
//     CTA wrapper    — marginBottom 22
//   HomeIndicator (28, fixed)
// The spacer is the only flexible element. At 390 × 844 it resolves to ≈240;
// at 375 × 667 to ≈65. Never hard-code a Y.
//
// THE BULLET DOTS ARE ELEMENTS, NOT GLYPHS. 7px round --liq-orange-500 with
// marginTop 9 to sit on the first line's optical centre. No "•", no emoji,
// no list-style — brand rule.
//
// ONE EXIT, AND IT IS FORWARD. The CTA goes to photos. There is no back
// (profile creation is mandatory once entered — group rule 4b), no skip, and
// no dismissable chrome. The platform back gesture is suppressed, as on
// screen 01.
//
// THE HEADLINE CARRIES ONE ITALIC EM ("behind") with the shared
// .su-underlined em orange radial wash. One em per headline, never two, and
// the em never breaks across lines.
//
// PROPS
//   name        first name (default "Leo" — the kit's canonical demo user).
//               Empty / whitespace falls back to the no-name headline.
//   onContinue  fired on the CTA tap. The only callback on the screen.

const { useState: useStateEmbrace } = React;

// ─────────────────────────────────────────────────────────────
// BulletRow — Manrope row with a small orange dot in lieu of a bullet
// glyph. Keeps us on brand (no unicode bullets, no emoji) while
// preserving the visual scan-rhythm of the two-item list.
// ─────────────────────────────────────────────────────────────
function BulletRow({ children }) {
  return (
    <li style={{
      display: 'flex', alignItems: 'flex-start', gap: 12,
      padding: 0, margin: 0,
    }}>
      <span aria-hidden style={{
        flex: 'none', width: 7, height: 7, borderRadius: '50%',
        background: 'var(--liq-orange-500)',
        marginTop: 9,
      }}/>
      <span style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 15,
        lineHeight: 1.45, color: 'var(--liq-fg)',
      }}>
        {children}
      </span>
    </li>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileEmbrace — the bridge screen
// ─────────────────────────────────────────────────────────────
function ScreenProfileEmbrace({ name = 'Leo', onContinue }) {
  const cleanName = (name || '').trim();
  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Ambient backdrop — same recipe as the first-run Startup screen.
          Two soft radial orbs (orange top-right, violet bottom-left) plus
          a peach wash across the top third. Sits behind the status bar
          and home indicator so the warmth is full-bleed. Decorative only:
          pointer-events none, aria-hidden by absence of content. */}
      <div style={{
        position: 'absolute', top: '-15%', right: '-25%', width: 520, height: 520,
        background: 'radial-gradient(circle, rgba(254,104,57,0.32) 0%, rgba(254,104,57,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>
      <div style={{
        position: 'absolute', bottom: '-20%', left: '-30%', width: 600, height: 600,
        background: 'radial-gradient(circle, rgba(129,42,236,0.28) 0%, rgba(129,42,236,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0,
        background: 'linear-gradient(180deg, rgba(255,229,210,0.55) 0%, rgba(255,251,247,0) 70%)',
        height: 360, pointerEvents: 'none', zIndex: 0,
      }}/>

      {/* Status bar — sits on top of the orbs, dark ink on warm paper. */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20" dark={false}/>
      </div>

      {/* HEADLINE — dark Lora on the warm-paper canvas. No AppHeader above
          it and no StepProgress: this screen belongs to neither progress
          bar. The 64 top padding is what replaces that chrome. */}
      <div style={{
        position: 'relative', zIndex: 1,
        padding: '64px 28px 0',
        flex: 'none',
      }}>
        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 34,
          letterSpacing: '-0.015em', lineHeight: 1.1,
          color: 'var(--liq-fg)', margin: 0, textWrap: 'balance',
        }}>
          {cleanName
            ? <>Nice to see you, {cleanName}.</>
            : <>Glad you're here.</>
          }
          <br/>
          Time to show the person <em>behind</em> your profile.
        </h1>
      </div>

      {/* BODY — sits below the headline on the same warm-paper canvas. */}
      <div style={{
        position: 'relative', zIndex: 1,
        padding: '28px 28px 0',
        flex: 1,
        display: 'flex', flexDirection: 'column',
      }}>
        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15.5,
          lineHeight: 1.55, color: 'var(--liq-neutral-200)',
          margin: 0, textWrap: 'pretty',
        }}>
          You are wonderful as you are. Share what makes you unique so others
          get a real feel for who they'll meet.
        </p>

        <ul style={{
          listStyle: 'none', padding: 0, margin: '14px 0 0',
          display: 'flex', flexDirection: 'column', gap: 8,
        }}>
          <BulletRow>Upload meaningful photos.</BulletRow>
          <BulletRow>Record a voice or video prompt.</BulletRow>
        </ul>

        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15.5,
          lineHeight: 1.55, color: 'var(--liq-neutral-200)',
          margin: '14px 0 0', textWrap: 'pretty',
        }}>
          More of you means better matches — and more real-life connections.
        </p>

        {/* THE ONLY FLEXIBLE ELEMENT. Absorbs every device difference. */}
        <div style={{ flex: 1 }}/>

        {/* FOOTER — sunset CTA, full width.
            Sunset (rather than the routine orange) because this is a
            commitment beat: "I'm going to build the profile." The button
            inherits --su-grad-sunset so it stays in sync with any Tweaks
            adjustments the user makes to the accent gradient. The trailing
            arrow is an SVG icon, not a text glyph. */}
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
            Upload my photos
          </Button>
        </div>
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, { ScreenProfileEmbrace });
