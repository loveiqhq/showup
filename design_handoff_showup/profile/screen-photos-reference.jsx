// screen-photos-reference.jsx — Profile creation 06 · Photos
//
// Design reference for [Profile 06] Photos. This is the code that renders
// spec-sheets/06-photos.png. Read values from HERE, not the PNG.
//
// Extracted verbatim from the Show Up UI kit
// (ui_kits/show-up/screens-extras.jsx). It is a reference implementation,
// not production code: recreate it in the target codebase's own environment
// and patterns. Do not ship this file.
//
// Depends on shared.jsx for StatusBar, AppHeader, StepProgress, NextButton,
// Button, HomeIndicator, Icon and PortraitPlaceholder.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE
// ─────────────────────────────────────────────────────────────
//
// SEVEN STATES, ONE COMPONENT, THREE PROPS:
//   A · empty              <ScreenProfilePhotos state="empty"/>
//   B · some added         <ScreenProfilePhotos state="partial"/>            (default)
//   C · uploading + failed <ScreenProfilePhotos state="uploading"/>
//   D · library blocked    <ScreenProfilePhotos state="denied"/>              access="blocked"
//   E · can still ask      <ScreenProfilePhotos state="denied" access="ask"/>
//   F · source sheet       <ScreenProfilePhotos state="partial" sheet="default"/>
//   G · camera blocked     <ScreenProfilePhotos state="partial" sheet="camera-blocked"/>
// A, B and C share one layout column exactly. D and E replace the grid with
// the access card. F and G overlay the source sheet on an otherwise untouched
// screen — the grid stays visible behind the scrim.
//
// STEP 1 OF 4 OF "THE REAL YOU" — a DIFFERENT group from "The basics". New
// header title, new 4-segment progress bar. Build the group's shell once;
// photos, prompts, media and details all consume it.
//
// THIS IS THE FIRST SCREEN IN THE FLOW THAT SCROLLS. The scroll lives in the
// middle region ONLY: StatusBar → AppHeader → StepProgress → scrolling middle
// (flex: 1, overflow auto) → sticky footer → HomeIndicator. The header, the
// progress bar and the CTA never move. Any fixed Y inside the scrolling
// region is wrong the moment the user drags.
//
// EVERY SLOT IS 158 TALL IN EVERY STATE — filled, empty, uploading, failed.
// That single number is what stops the grid reflowing mid-upload and keeps
// the CTA reachable at the same scroll offset.
//
// THE COUNT ONLY ADVANCES ON A CONFIRMED UPLOAD. In state C three slots look
// occupied and the count reads "1 of 6": an in-flight photo has not been
// added and a failed one never was. Continue still refuses at three in
// flight, and photos_minimum_met fires on the fourth CONFIRMED upload.
//
// CONTINUE IS NEVER DISABLED. Pressing it below four photos answers with the
// toast (2600ms, absolutely positioned at bottom:100% of the footer so the
// footer keeps identical geometry when idle). A dead button cannot say what
// is missing.
//
// ─────────────────────────────────────────────────────────────
// THE PERMISSION SURFACE — SMALLER THAN IT LOOKS, ON PURPOSE
// ─────────────────────────────────────────────────────────────
//
// THE BUILD CONSTRAINT THAT MAKES ALL OF THIS TRUE: present the SYSTEM PICKER
// (iOS PHPickerViewController, Android 13+ photo picker) and DO NOT REQUEST A
// LIBRARY PERMISSION. The picker runs out-of-process, lets the user browse
// their whole library and returns only what they chose. Consequences:
//   - iOS needs NO library access card at all, in any status.
//   - Android 13+ needs none either.
//   - iOS "LIMITED ACCESS" CANNOT HAPPEN, so there is no 'limited' state and
//     no partial-library banner. That banner existed in an earlier draft and
//     was DELETED, not redesigned: it explained a state we cannot enter.
// Build an in-app library browser instead and every one of those states comes
// back. Don't.
//
// WHAT SURVIVES:
//   Library, Android ≤ 12 only — genuinely gated, so D and E apply there and
//     ONLY there. Two modes, because the app always knows its status:
//       access="ask"     can still be asked → the button RE-PROMPTS in-app,
//                        no Settings trip, and the copy names no toggle
//       access="blocked" permanently denied → the button opens the app details
//                        page and the copy NAMES THE ROW to look for
//   Camera, BOTH platforms, always — the one permission that never goes away.
//     Handled in the SOURCE SHEET (F / G), at the moment the user asks for it.
//
// A DENIED CAMERA BLOCKS NOTHING. The library path still works and the step is
// still completable, so camera denial is NEVER a grid replacement and never a
// full-screen card — it is a quiet row in the sheet the user just opened, with
// the library row untouched. The answer arrives where the question was asked.
//
// NEITHER PLATFORM CAN DEEP-LINK TO A SINGLE PERMISSION TOGGLE — iOS opens the
// app's own settings page, Android the app details page — which is exactly why
// blocked copy NAMES THE ROW. Name the platform's own label for that version,
// never the hard-coded string "Photos".
//
// RE-READ THE STATUS ON EVERY FOREGROUND. The most common bug on this screen
// is a user who granted access in Settings returning to the blocked card.
//
// THE OS OWNS the permission alert, the system picker, the camera UI and the
// Settings app. None are drawn and none are built. Never spec a height, a copy
// string or an animation for any of them.
//
// PortraitPlaceholder is PLACEHOLDER ART. Drop real photos in its place.
//
// showToast IS AN ARTBOARD PROP, NOT A PRODUCT STATE. It forces the refusal
// toast open so the spec sheet can show the treatment. In the app the toast
// exists only for 2600ms after a refused Continue, and never on arrival.

// PhotoSlot — one tile in the photo grid.
//
// States:
//   filled            portrait + glassy remove pill (+ "Main" pill on slot 1)
//   empty required    dashed-violet card with its category hint
//   empty optional    quieter dashed-neutral card, "Optional" hint
//   empty + cta       the very first slot when the user has zero photos:
//                     lilac wash + solid violet pip so there is one
//                     obvious place to start
//
// Uniform 158px height so the grid stays clean across every state.
function PhotoSlot({ filled, hint, photoName, photoIndex = 0, optional = false, main = false, cta = false, uploading = false, progress = 0.62, failed = false }) {
  // FAILED — the upload came back an error. Danger-tinted but never a
  // solid red fill, same restraint as the flow's inline error card. The
  // slot keeps its 158 height so the grid does not reflow mid-upload,
  // and Retry is a real 32px control rather than hypertext.
  if (failed) {
    return (
      <div style={{
        height: 158, borderRadius: 20,
        background: 'rgba(251,50,59,0.06)',
        border: '1.5px dashed rgba(251,50,59,0.34)',
        display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center', gap: 9,
        padding: '0 12px',
      }}>
        <div style={{
          width: 34, height: 34, borderRadius: 9999,
          background: 'var(--liq-danger)', color: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 17,
        }}>!</div>
        <span style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 13,
          color: 'var(--liq-danger-fg)', textAlign: 'center',
        }}>Upload failed</span>
        <button style={{
          height: 32, padding: '0 15px', borderRadius: 9999,
          background: '#fff', border: '1.5px solid rgba(251,50,59,0.34)',
          color: 'var(--liq-danger-fg)',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 12.5,
        }}>Retry</button>
      </div>
    );
  }

  // UPLOADING — the picked image is already shown, dimmed, with a
  // determinate ring over it. The user sees WHICH photo is in flight,
  // which a centred spinner on an empty slot would not tell them.
  // Frozen at 62% here for the artboard; it is a real progress value.
  if (uploading) {
    return (
      <div style={{ position: 'relative', height: 158, borderRadius: 20, overflow: 'hidden' }}>
        <PortraitPlaceholder
          name={photoName || 'You'}
          photoIndex={photoIndex}
          height={158}
          radius={20}
          label={false}
        />
        <div style={{
          position: 'absolute', inset: 0,
          background: 'rgba(29,17,41,0.44)',
          display: 'flex', flexDirection: 'column',
          alignItems: 'center', justifyContent: 'center', gap: 10,
        }}>
          <div style={{
            width: 38, height: 38, borderRadius: 9999,
            background: `conic-gradient(#fff 0turn ${progress}turn, rgba(255,255,255,0.30) ${progress}turn 1turn)`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <div style={{ width: 28, height: 28, borderRadius: 9999, background: 'rgba(29,17,41,0.62)' }}/>
          </div>
          <span style={{
            fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 12,
            color: '#fff',
          }}>Uploading…</span>
        </div>
      </div>
    );
  }

  if (filled) {
    return (
      <div style={{ position: 'relative' }}>
        <PortraitPlaceholder
          name={photoName || 'You'}
          photoIndex={photoIndex}
          height={158}
          radius={20}
          label={false}
        >
          <button
            aria-label={`Remove ${hint || 'photo'}`}
            style={{
              position: 'absolute', top: 8, right: 8,
              width: 28, height: 28, borderRadius: 9999,
              background: 'rgba(29,17,41,0.62)', color: '#fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 2px 6px rgba(0,0,0,0.18)', backdropFilter: 'blur(8px)',
            }}
          >
            <Icon name="x" size={14} stroke={2.6}/>
          </button>
          {main && (
            <span style={{
              position: 'absolute', left: 8, bottom: 8,
              padding: '3px 9px', borderRadius: 9999,
              background: 'rgba(255,255,255,0.92)', backdropFilter: 'blur(8px)',
              color: 'var(--liq-fg)',
              fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 10,
              letterSpacing: 0.06, textTransform: 'uppercase',
            }}>Main</span>
          )}
        </PortraitPlaceholder>
      </div>
    );
  }

  const tone = cta
    ? { bg: 'var(--su-grad-lilac, linear-gradient(180deg,#F3E8FF 0%,#E9D5FF 100%))', border: '1.5px dashed rgba(129,42,236,0.46)', pipBg: 'var(--liq-primary-500)', pipFg: '#fff', pipBorder: 'transparent', fg: 'var(--liq-primary-500)' }
    : optional
      ? { bg: 'var(--liq-bg-raised)', border: '1.5px dashed rgba(29,17,41,0.18)', pipBg: '#fff', pipFg: 'var(--liq-fg-subtle)', pipBorder: 'rgba(29,17,41,0.12)', fg: 'var(--liq-fg-subtle)' }
      : { bg: 'var(--liq-bg-raised)', border: '1.5px dashed rgba(129,42,236,0.36)', pipBg: '#fff', pipFg: 'var(--liq-primary-500)', pipBorder: 'rgba(129,42,236,0.20)', fg: 'var(--liq-primary-500)' };

  return (
    <button style={{
      height: 158, borderRadius: 20,
      background: tone.bg,
      border: tone.border,
      display: 'flex', flexDirection: 'column',
      alignItems: 'center', justifyContent: 'center', gap: 10,
      padding: '0 14px',
      transition: 'transform 180ms cubic-bezier(.22,1,.36,1)',
    }}>
      <div style={{
        width: 40, height: 40, borderRadius: 9999, background: tone.pipBg,
        border: `1px solid ${tone.pipBorder}`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: tone.pipFg,
        boxShadow: '0 1px 2px rgba(46,1,71,0.06)',
      }}>
        <Icon name="plus" size={20} stroke={2.4}/>
      </div>
      <span style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 13,
        color: tone.fg, lineHeight: 1.25,
        textAlign: 'center', maxWidth: 130,
      }}>{hint}</span>
    </button>
  );
}

// PhotoCount — the 0→6 scale, stated plainly. One line: what's
// required, what's allowed, and where the user currently stands.
function PhotoCount({ filled = 0, required = 4, max = 6 }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'baseline', justifyContent: 'space-between',
      fontFamily: 'var(--liq-font-sans)',
    }}>
      <span style={{
        fontWeight: 800, fontSize: 10.5, letterSpacing: 0.08,
        textTransform: 'uppercase', color: 'var(--liq-fg-subtle)',
        whiteSpace: 'nowrap',
      }}>
        Photos · {required} required, {max} max
      </span>
      <span style={{
        fontWeight: 700, fontSize: 13, fontVariantNumeric: 'tabular-nums',
        color: filled >= required ? 'var(--liq-success-fg)' : 'var(--liq-fg)',
      }}>
        {filled} of {max}
      </span>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// PhotoSourceSheet — the "+" source choice.
//
// Tapping an empty slot does not open the picker directly; it asks where
// the photo comes from. This sheet is OURS. What it LAUNCHES is the OS's:
// the system photo picker, or the camera UI.
//
// WHY IT EXISTS AT ALL: it is the only place the camera can be offered.
// Library needs no permission (system picker), camera always does, so the
// two paths cannot share one control.
//
// mode:
//   'default'        both rows live
//   'camera-blocked' camera permission permanently denied. The camera row
//                    goes quiet and grows an "Open Settings" action; the
//                    LIBRARY ROW IS UNTOUCHED.
//
// A DENIED CAMERA BLOCKS NOTHING. The library path still works and the user
// can still finish the step, so this is never a grid replacement and never
// a full-screen card. It is a quiet row in the sheet the user just opened —
// the answer arrives exactly where the question was asked.
// ─────────────────────────────────────────────────────────────
function PhotoSourceSheet({ mode = 'default' }) {
  const cameraBlocked = mode === 'camera-blocked';
  return (
    <div style={{
      position: 'absolute', inset: 0, zIndex: 4,
      display: 'flex', flexDirection: 'column', justifyContent: 'flex-end',
    }}>
      {/* Scrim — dismisses on tap. The grid stays visible behind it: the
          user is choosing a source, not leaving the screen. */}
      <div style={{ position: 'absolute', inset: 0, background: 'rgba(29,17,41,0.42)' }}/>
      <div style={{
        position: 'relative',
        background: 'var(--liq-bg)',
        borderRadius: '22px 22px 0 0',
        padding: '10px 16px 16px',
        boxShadow: '0 -8px 30px rgba(46,1,71,0.18)',
      }}>
        <div style={{
          width: 38, height: 4, borderRadius: 999,
          background: 'rgba(29,17,41,0.18)', margin: '0 auto 14px',
        }}/>
        <div style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 15,
          color: 'var(--liq-fg)', letterSpacing: '-0.01em', marginBottom: 12,
        }}>Add a photo</div>

        {/* LIBRARY — no permission, ever. The system picker runs
            out-of-process and hands back what the user chose. */}
        <button style={{
          width: '100%', height: 62, borderRadius: 16,
          background: 'var(--liq-bg-raised)',
          display: 'flex', alignItems: 'center', gap: 13, padding: '0 14px',
          textAlign: 'left',
        }}>
          <span style={{
            flex: 'none', width: 38, height: 38, borderRadius: 9999,
            background: 'var(--liq-primary-500)', color: '#fff',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Icon name="image" size={18} stroke={2.2}/>
          </span>
          <span style={{ flex: 1, minWidth: 0 }}>
            <span style={{
              display: 'block',
              fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15,
              color: 'var(--liq-fg)',
            }}>Choose from library</span>
            <span style={{
              display: 'block',
              fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 12.5,
              color: 'var(--liq-fg-subtle)', marginTop: 1,
            }}>Pick one or more</span>
          </span>
          <Icon name="chevron-right" size={17} stroke={2.2}/>
        </button>

        {/* CAMERA — the one permission that always applies. */}
        <button style={{
          width: '100%', minHeight: 62, borderRadius: 16, marginTop: 10,
          background: cameraBlocked ? 'rgba(167,139,250,0.10)' : 'var(--liq-bg-raised)',
          border: cameraBlocked ? '1px solid rgba(129,42,236,0.16)' : 'none',
          display: 'flex', alignItems: 'center', gap: 13,
          padding: cameraBlocked ? '11px 12px 11px 14px' : '0 14px',
          textAlign: 'left',
        }}>
          <span style={{
            flex: 'none', width: 38, height: 38, borderRadius: 9999,
            background: cameraBlocked ? 'rgba(129,42,236,0.14)' : 'var(--liq-primary-500)',
            color: cameraBlocked ? 'var(--liq-primary-500)' : '#fff',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Icon name={cameraBlocked ? 'lock' : 'camera'} size={cameraBlocked ? 16 : 18} stroke={2.2}/>
          </span>
          <span style={{ flex: 1, minWidth: 0 }}>
            <span style={{
              display: 'block',
              fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15,
              color: 'var(--liq-fg)',
            }}>Take a photo</span>
            <span style={{
              display: 'block',
              fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 12.5,
              lineHeight: 1.35,
              color: 'var(--liq-fg-subtle)', marginTop: 1, textWrap: 'pretty',
            }}>
              {cameraBlocked
                ? 'Camera access is off. Turn on Camera in Settings to use it.'
                : 'Use the camera now'}
            </span>
          </span>
          {cameraBlocked ? (
            <span style={{
              flex: 'none', height: 30, padding: '0 12px', borderRadius: 9999,
              background: '#fff', border: '1.5px solid rgba(129,42,236,0.32)',
              color: 'var(--liq-primary-500)',
              display: 'inline-flex', alignItems: 'center',
              fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 12.5,
            }}>Settings</span>
          ) : (
            <Icon name="chevron-right" size={17} stroke={2.2}/>
          )}
        </button>
      </div>
    </div>
  );
}

// ScreenProfilePhotos — the photos step, scaled 0 → 6.
//
// The count runs from 0 (first landing, nothing uploaded) to 6 (all
// slots used). Four photos are the requirement to continue; slots 5–6
// are headroom, revealed by an explicit "Add more" affordance rather
// than sitting there as two extra chores on first paint.
//
// state (five — two happy, one in-flight, two access):
//   'empty'     0 photos — first slot carries a filled-in CTA treatment
//   'partial'   2 photos — at least one added, drag hint visible (default)
//   'uploading' 1 confirmed, 1 in flight, 1 failed — the gap between
//               picking a photo and seeing it in the slot. OURS, not the OS's.
//   'denied'    LIBRARY permission missing — the grid is replaced by the access
//               card, because six tappable slots that cannot open anything
//               is the worst state in the flow.
//               NARROW CASE, decided 9 Sep 2026: this is reachable on
//               ANDROID ≤ 12 ONLY. We present the SYSTEM PICKER (iOS
//               PHPickerViewController, Android 13+ Photo Picker), which runs
//               out-of-process and needs no permission at all — so on iOS and
//               modern Android there is no library permission to miss and this
//               state cannot happen. Do not delete it (old Android is real),
//               and do not request a library permission to make it appear.
//               TWO CARD MODES, set by the `access` prop, because the app
//               always knows which status it is in:
//                 access="ask"     can still be asked — the button RE-PROMPTS
//                                  in-app, no Settings trip, no toggle named
//                 access="blocked" permanently denied — the button opens the
//                                  app details page and the copy NAMES THE ROW
//                                  to look for (default)
//               Neither platform can deep-link to a single permission toggle,
//               which is exactly why the blocked copy names the row.
//
// THERE IS NO 'limited' STATE, and that is deliberate (9 Sep 2026). iOS
// "Limited access" only exists for an app that reads the library itself. We
// present the system picker, so the user browses their whole library inside
// it and the partial-library condition never arises. The persistent banner
// that used to live here was deleted: it explained a state we cannot enter.
//
// CAMERA IS THE ONE PERMISSION THAT ALWAYS APPLIES, on both platforms. It is
// handled at the moment the user asks for it — in the source sheet (`sheet`
// prop), not as a grid replacement — because a denied camera blocks NOTHING:
// the library path still works and the user can still finish the step.
// THE COUNT ONLY ADVANCES ON A CONFIRMED UPLOAD. In 'uploading' the count
// reads 1 of 6, not 2 — an in-flight photo has not been added yet, and a
// failed one never was.
//
// Tracking (family E · Profile Photos/Media):
//   photo_slot_tapped   { slot_index 0–5, is_optional, action:
//                         "add"|"replace"|"reveal_optional" }
//   photo_added         { slot_index, source, filled_count 0–6, max_slots 6 }
//   photo_removed       { slot_index, filled_count 0–6 }
//   photos_minimum_met  { count } — fires when filled_count first hits 4

function ScreenProfilePhotos({ state = 'partial', access = 'blocked', showToast = false, sheet = null }) {
  const MIN = 4, MAX = 6;
  const denied = state === 'denied';
  const canAsk = denied && access === 'ask';
  const inFlight = state === 'uploading';
  const SLOTS = [
    { hint: 'Portrait',           photoName: 'Leonie', photoIndex: 0 },
    { hint: 'With friends',       photoName: 'Maya',   photoIndex: 0 },
    { hint: 'Full body',          photoName: 'Liv',    photoIndex: 0 },
    { hint: 'Favourite activity', photoName: 'Sofia',  photoIndex: 0 },
    { hint: 'Anything you like',  photoName: 'Anna',   photoIndex: 0, optional: true },
    { hint: 'Anything you like',  photoName: 'Emma',   photoIndex: 0, optional: true },
  ];
  const filledCount = state === 'empty' || denied ? 0 : inFlight ? 1 : 2;
  // Per-slot state for the in-flight artboard: one confirmed, one uploading,
  // one failed, the rest untouched. Everything else reads off filledCount.
  const slotProps = (i) => {
    if (inFlight) {
      if (i === 0) return { filled: true, main: true };
      if (i === 1) return { uploading: true };
      if (i === 2) return { failed: true };
      return {};
    }
    return {
      filled: i < filledCount,
      main: i === 0 && filledCount > 0,
      cta: filledCount === 0 && i === 0,
    };
  };
  const [expanded, setExpanded] = React.useState(false);
  const showExtras = expanded;
  // Continue is always live. Tapping it short of the minimum answers with
  // a toast instead of a dead control — the rule is stated on press,
  // where the user is actually asking about it.
  // showToast forces the refusal toast open for the artboards — in the app it
  // only exists for 2600ms after a refused Continue, so a static sheet cannot
  // show the treatment otherwise. Not a product state.
  const [toast, setToast] = React.useState(showToast);
  const toastTimer = React.useRef(null);
  React.useEffect(() => () => clearTimeout(toastTimer.current), []);
  const onContinue = () => {
    if (filledCount >= MIN) return;
    setToast(true);
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(false), 2600);
  };

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Soft sunset orb backdrop — matches verify steps */}
      <div style={{
        position: 'absolute', top: '-22%', right: '-30%', width: 460, height: 460,
        background: 'radial-gradient(circle, rgba(254,104,57,0.18) 0%, rgba(254,104,57,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>
      <div style={{
        position: 'absolute', top: '-12%', left: '-28%', width: 420, height: 420,
        background: 'radial-gradient(circle, rgba(129,42,236,0.16) 0%, rgba(129,42,236,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>

      {/* Fixed top — status, header, 3-step progress */}
      <div style={{ position: 'relative', zIndex: 3 }}>
        <StatusBar time="4:20"/>
        <AppHeader title="The real you" leading="back"/>
        <div style={{ padding: '4px 24px 14px' }}>
          <StepProgress steps={4} current={1}/>
        </div>
      </div>

      {/* Scrolling middle */}
      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', zIndex: 1, overflow: 'auto',
        padding: '4px 24px 0',
      }}>
        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 32,
          letterSpacing: '-0.018em', lineHeight: 1.08, color: 'var(--liq-fg)',
          margin: '2px 0 8px', textWrap: 'balance',
        }}>
          The <em>messy hair</em>, the loud laugh.
        </h1>

        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
          lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty', maxWidth: 320,
        }}>
          Show who you actually are — not a polished version. Four photos
          to continue, six if you've got them.
        </p>

        {/* ACCESS BLOCKED — no library, so no grid. Six tappable slots
            that cannot open anything is the worst possible state on a
            mandatory step, so the grid is replaced rather than disabled.
            Lilac, not danger: the user made a choice, they did not make
            a mistake. Same recovery vocabulary as ScreenProfileLocation's
            denied variant — one component, two permission screens.

            ONE STATE, ONE ENTRY POINT. It does not matter which
            permission is missing or how many are — the screen says the
            same thing (photos are needed to continue) and offers the same
            single control. Taking a photo with the camera is still
            possible; it is offered where the user taps "+" (source
            choice), not as a second recovery button here. */}
        {denied ? (
          <div style={{
            marginTop: 18, borderRadius: 18,
            background: 'rgba(167,139,250,0.12)',
            border: '1px solid rgba(129,42,236,0.16)',
            padding: '18px 16px',
            display: 'flex', flexDirection: 'column', gap: 14,
          }}>
            <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
              <div style={{
                flex: 'none', width: 34, height: 34, borderRadius: 9999,
                background: 'var(--liq-primary-500)', color: '#fff',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon name="lock" size={17} stroke={2.2}/>
              </div>
              <div>
                <div style={{
                  fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 15,
                  color: 'var(--liq-fg)', letterSpacing: '-0.01em',
                }}>Photo access is needed to continue</div>
                <div style={{
                  fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13.5,
                  lineHeight: 1.45, color: 'var(--liq-neutral-200)',
                  marginTop: 4, textWrap: 'pretty',
                }}>
                  {canAsk ? (
                    <>Allow access so you can pick your photos. Nothing you have
                    entered is lost.</>
                  ) : (
                    <>Show Up can&rsquo;t see your photos, so there is nothing to add.
                    Open Settings, turn on{' '}
                    <b style={{ fontWeight: 800, color: 'var(--liq-fg)' }}>Photos</b>,
                    then come straight back — nothing you have entered is lost.</>
                  )}
                </div>
              </div>
            </div>
            {/* The label tells the truth about where the tap goes, and the
                app already knows: notDetermined can still be asked in-app,
                denied / restricted cannot. Same card, two honest labels. */}
            <Button variant="violet" size="md" fullWidth>
              {canAsk ? 'Allow photo access' : 'Open Settings'}
            </Button>
          </div>
        ) : (
        <React.Fragment>
        {/* The 0→6 scale, up front */}
        <div style={{ marginTop: 16 }}>
          <PhotoCount filled={filledCount} required={MIN} max={MAX}/>
        </div>

        {/* Required four — 2×2 */}
        <div style={{
          display: 'grid', gridTemplateColumns: '1fr 1fr',
          gap: 12, marginTop: 14,
        }}>
          {SLOTS.slice(0, MIN).map((s, i) => {
            const sp = slotProps(i);
            return (
              <PhotoSlot
                key={i}
                {...sp}
                hint={filledCount === 0 && i === 0 ? 'Start with your face' : s.hint}
                photoName={s.photoName}
                photoIndex={s.photoIndex}
              />
            );
          })}
        </div>

        {/* Optional slots 5–6 — revealed on demand */}
        {showExtras ? (
          <div style={{ marginTop: 12 }}>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 8, margin: '2px 0 8px',
              fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 10.5,
              letterSpacing: 0.08, textTransform: 'uppercase',
              color: 'var(--liq-fg-subtle)',
            }}>
              <span>Optional · slots 5 & 6</span>
              <span style={{ flex: 1, height: 1, background: 'var(--liq-border-soft)' }}/>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              {SLOTS.slice(MIN, MAX).map((s, i) => (
                <PhotoSlot
                  key={i}
                  filled={MIN + i < filledCount}
                  optional
                  hint={s.hint}
                  photoName={s.photoName}
                  photoIndex={s.photoIndex}
                />
              ))}
            </div>
          </div>
        ) : (
          /* "Add more" — the only way to reach slots 5–6 before the
             requirement is met. Outlined pill (not hypertext) so it
             reads as a real control at the size of a tap target. */
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: 14 }}>
            <button
              onClick={() => setExpanded(true)}
              style={{
                display: 'inline-flex', alignItems: 'center', gap: 8,
                height: 44, padding: '0 18px', borderRadius: 9999,
                background: '#fff',
                border: '1.5px solid rgba(129,42,236,0.36)',
                color: 'var(--liq-primary-500)',
                fontFamily: 'var(--liq-font-sans)', fontSize: 14.5, fontWeight: 700,
                boxShadow: '0 1px 2px rgba(46,1,71,0.05)',
              }}
            >
              <Icon name="plus" size={16} stroke={2.6}/>
              Add more
            </button>
          </div>
        )}

        {filledCount >= 2 && (
          <div style={{
            marginTop: 12, display: 'flex', justifyContent: 'center',
            alignItems: 'center', gap: 6,
            fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 12,
            color: 'var(--liq-fg-subtle)',
          }}>
            <Icon name="sliders" size={13} stroke={2}/>
            Drag to reorder · the first one is your main photo
          </div>
        )}
        </React.Fragment>
        )}

        <div style={{ height: 24 }}/>
      </div>

      {/* Sticky footer — Verify card + Continue row.
          Canvas-tinted gradient mask so scroll content fades out
          cleanly underneath; keeps Continue always-in-viewport. */}
      <div style={{
        position: 'relative', zIndex: 2, flex: 'none',
        padding: '12px 20px 0',
        background: 'linear-gradient(180deg, rgba(255,251,247,0) 0%, rgba(255,251,247,0.92) 22%, rgba(255,251,247,1) 100%)',
        backdropFilter: 'blur(8px)',
      }}>
        {/* Toast — absolutely positioned above the CTA row so the footer
            keeps identical geometry when it is idle. */}
        <div style={{
          position: 'absolute', left: 20, right: 20, bottom: '100%',
          display: 'flex', justifyContent: 'center',
          pointerEvents: 'none',
          visibility: toast ? 'visible' : 'hidden',
          opacity: toast ? 1 : 0,
          transform: toast ? 'translateY(0)' : 'translateY(6px)',
          transition: 'opacity 200ms, transform 200ms',
        }}>
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 8,
            padding: '10px 14px', borderRadius: 14,
            background: 'var(--liq-fg)', color: '#fff',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 13,
            lineHeight: 1.2, boxShadow: '0 8px 22px rgba(46,1,71,0.22)',
            maxWidth: 300, textWrap: 'pretty',
          }}>
            <Icon name="camera" size={15} stroke={2.2}/>
            {denied
              ? (canAsk ? 'Allow photo access to continue' : 'Turn on Photos in Settings to continue')
              : 'Upload at least 4 photos to continue'}
          </div>
        </div>

        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'flex-end',
          padding: '6px 0 6px',
        }}>
          <div onClick={onContinue}>
            <NextButton label="Continue" color="orange" size={52}/>
          </div>
        </div>
      </div>

      {sheet && <PhotoSourceSheet mode={sheet}/>}

      <div style={{ position: 'relative', zIndex: 3 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}
