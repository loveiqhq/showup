// screen-notifications-reference.jsx — Profile creation 09 · Notifications permission ask
//
// Design reference for [Profile 09] Notifications — permission ask. This is the
// code that renders spec-sheets/09-notifications.png. Read values from HERE,
// not the PNG.
//
// Extracted verbatim from the Show Up UI kit
// (ui_kits/show-up/screens-profile-notifications.jsx). It is a reference
// implementation, not production code: recreate it in the target codebase's
// own environment and patterns. Do not ship this file.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// ONE STATE, NO PROPS. <ScreenProfileNotifications/>. There is no denied
// state, no recovery banner and no "asking" state in this file, and that is
// deliberate — see PERMISSION below. onEnable is the only prop and it is a
// callback, not a variant.
//
// THIS IS NOT A STEP. It sits after media (08) and before Stay reachable (10),
// outside both progress bars:
//   - NO AppHeader. No title, no back chevron, no skip, no close.
//   - NO StepProgress.
//   - Nothing to fill in, so nothing to validate.
// Same shape as the 05 bridge, for the same reason. The absence is the design.
//
// IT CARRIES THE AMBIENT BACKDROP AND A SUNSET CTA — the same two named
// exceptions to flow README rules 5 and 7 that the bridge carries: no keyboard
// on this screen, and opting in is a commitment beat. Orange orb top-right,
// violet orb bottom-left, 360-tall peach wash, full-width sunset button.
//
// PERMISSION — the part the file cannot show you. This screen is OUR
// pre-permission surface; the sheet it raises is the OS's and is never drawn,
// never sized and never styled. Profile creation runs once on a FRESH INSTALL,
// so the status here is always "not determined": the CTA raises the OS sheet
// and the flow advances to Stay reachable on granted and denied alike.
// Two skip cases, decided before the screen is pushed, never after it mounts:
//   Android <= 12          no POST_NOTIFICATIONS to ask for — skip silently
//   already determined     restore-from-backup, or killed mid-sheet — skip;
//                          the OS dialog is shown once per install, so the
//                          button would be dead
// A denied permission is recovered on the Stay reachable screen (10), which
// owns the notifications consent row — not here, which is why there is no
// recovery state in this file.
//
// THE SAVED FLOW POSITION ADVANCES WHEN THE SHEET IS RAISED, not when it
// returns. If the app is killed while the OS sheet is up, the relaunch must
// land on Stay reachable — iOS shows that dialog once per install, so a
// relaunch onto this screen is a dead button and there is no skip, no back
// and no close on it. This screen is never a terminal state.
// Full matrix in tickets/09-notifications.md.
//
// LAYOUT — flex column, ONE flex: 1 spacer, nothing absolutely positioned
// except the three decorative backdrop layers. StatusBar (54) → headline
// block (flex: none, padding 40 28 0) → body block (flex: 1, padding 20 28 0)
// → HomeIndicator (28). The five benefit rows are top-anchored; the single
// spacer sits between them and the CTA wrapper (margin-bottom 18) and absorbs
// every device difference. At 375 × 667 it collapses — the rows are what has
// to be checked first at that size.
//
// THE "Premium" TAG on row 2 is a sunset-gradient pill, and it is the only
// place in profile creation where the paid tier is named. It is a label, not
// an upsell: it is not tappable and it opens nothing.
//
// Exposed on window:
//   - ScreenProfileNotifications — main screen
//   - NotifyBenefitRow            — primitive row, reused inside
//   - NOTIFY_BENEFITS             — the five categories

const { useState: useStateNotify } = React;

// ─────────────────────────────────────────────────────────────
// NOTIFY_BENEFITS — the FIVE categories the user is opting in to,
// in this order. Five rows, not six: an earlier draft had a sixth
// ("Match Mate reply") and it was cut. Do not re-add it.
// ─────────────────────────────────────────────────────────────
const NOTIFY_BENEFITS = [
  {
    icon: 'heart',
    title: 'Match alert',
    line: 'Get instant notifications when you receive a match and never miss out on a date.',
  },
  {
    icon: 'sparkles',
    title: 'Like received', tag: 'Premium',
    line: "Don't miss your chance to meet — we surface it the second it arrives.",
  },
  {
    icon: 'message-circle',
    title: 'Meeting details change',
    line: 'Get notified if your date asks — e.g. meet time change, running late.',
  },
  {
    icon: 'clock',
    title: 'Date reminder',
    line: 'A heads-up an hour out — never arrive late.',
  },
  {
    icon: 'x',
    title: 'Date cancelled',
    line: 'Never waste time waiting — get notified, look for someone else instead.',
  },
];

// ─────────────────────────────────────────────────────────────
// NotifyBenefitRow — one icon pip + title + line
//
// Icon sits in a lilac-tinted square pip (same vocabulary as
// MediaSlotCard's leading icon — keeps profile creation visually
// coherent end-to-end). Title is Lora 600 sentence-case with an
// optional small lavender "Premium" tag tucked next to it. Line
// is Manrope 14 in the muted neutral, balanced wrap so each row
// sits a similar height.
// ─────────────────────────────────────────────────────────────
function NotifyBenefitRow({ icon, title, line, tag }) {
  return (
    <div style={{
      display: 'flex', gap: 14, alignItems: 'flex-start',
    }}>
      <div style={{
        flex: 'none',
        width: 40, height: 40, borderRadius: 12,
        background: 'var(--su-grad-lilac)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--liq-primary-500)',
      }}>
        <Icon name={icon} size={20} stroke={1.8}/>
      </div>
      <div style={{ flex: 1, minWidth: 0, paddingTop: 1 }}>
        <div style={{
          display: 'flex', alignItems: 'baseline', gap: 8, flexWrap: 'wrap',
        }}>
          <div style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 16,
            color: 'var(--liq-fg)', lineHeight: 1.2,
            letterSpacing: '-0.005em',
          }}>
            {title}
          </div>
          {tag && (
            <span style={{
              display: 'inline-flex', alignItems: 'center', gap: 6,
              padding: '3px 10px', borderRadius: 9999,
              background: 'var(--su-grad-sunset)',
              color: '#fff',
              fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 10,
              letterSpacing: 0.06, textTransform: 'uppercase',
              transform: 'translateY(-1px)',
            }}>
              {tag}
            </span>
          )}
        </div>
        <div style={{
          marginTop: 3,
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13.5,
          lineHeight: 1.4, color: 'var(--liq-neutral-200)',
          textWrap: 'pretty',
        }}>
          {line}
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileNotifications — main screen
// ─────────────────────────────────────────────────────────────
function ScreenProfileNotifications({ onEnable } = {}) {
  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Ambient backdrop — same recipe as ScreenProfileEmbrace.
          Orange orb top-right, violet orb bottom-left, peach wash
          across the top third. Full-bleed behind status bar + home
          indicator so the warmth is uninterrupted. */}
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
        position: 'absolute', top: 0, left: 0, right: 0, height: 360,
        background: 'linear-gradient(180deg, rgba(255,229,210,0.55) 0%, rgba(255,251,247,0) 70%)',
        zIndex: 0, pointerEvents: 'none',
      }}/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20" dark={false}/>
      </div>

      {/* HEADLINE — dark Lora on the warm-paper canvas, italic-
          underline flourish on a single emphasis word. Matches the
          Embrace screen's typography exactly. */}
      <div style={{
        position: 'relative', zIndex: 1,
        padding: '40px 28px 0',
        flex: 'none',
      }}>
        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 32,
          letterSpacing: '-0.015em', lineHeight: 1.1,
          color: 'var(--liq-fg)', margin: 0, textWrap: 'balance',
        }}>
          Never miss <em>a date</em> with Notifications!
        </h1>
      </div>

      {/* BODY — sits below the headline on the same warm-paper
          canvas. Lead paragraph mirrors the reference's two-beat
          explainer ("no spam, all related to your dates" + "miss a
          date and your Show-up Rate drops"). */}
      <div style={{
        position: 'relative', zIndex: 1,
        padding: '20px 28px 0',
        flex: 1,
        display: 'flex', flexDirection: 'column',
        minHeight: 0,
      }}>
        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15,
          lineHeight: 1.55, color: 'var(--liq-neutral-200)',
          margin: 0, textWrap: 'pretty',
        }}>
          No spam! Every notification is about your dates and helps you to never miss one.
        </p>

        {/* The five rows. Generous gap so each row breathes — the
            list reads as a calm enumeration of what we'll send,
            not a dense table. */}
        <div style={{
          marginTop: 22,
          display: 'flex', flexDirection: 'column', gap: 16,
        }}>
          {NOTIFY_BENEFITS.map((b) => (
            <NotifyBenefitRow key={b.title} {...b}/>
          ))}
        </div>

        <div style={{ flex: 1, minHeight: 12 }}/>

        {/* FOOTER — sunset CTA, full width. Sunset (rather than
            routine orange) because opting in is a commitment beat,
            same vocabulary as the Embrace screen's "Create my
            profile". Pinned at the bottom of the body so the screen
            terminates cleanly above the home indicator. */}
        <div style={{ marginBottom: 18 }}>
          <Button
            variant="sunset"
            size="lg"
            fullWidth
            onClick={onEnable}
          >
            Enable notifications
          </Button>
        </div>
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, {
  ScreenProfileNotifications,
  NotifyBenefitRow,
  NOTIFY_BENEFITS,
});
