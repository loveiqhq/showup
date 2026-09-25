// screen-media-reference.jsx — Profile creation, screen 08: Media (video + voice)
//
// THE REFERENCE IMPLEMENTATION FOR [Profile 08]. This is the code that
// renders the spec sheet. Read values from here; never measure the PNG.
// This is not production code — recreate it in the app's own environment
// and patterns.
//
// WHAT THIS SCREEN IS: the last beat of profile creation. Answer ONE short
// prompt as a 10-second video and/or a 15-second voice note, so a match can
// feel the person's energy before meeting. BOTH SLOTS ARE OPTIONAL and the
// step is skippable — "Optional · you can skip this" sits ABOVE the
// headline, and Continue is always enabled.
//
// STEP 3 OF 3 of "The real you" — photos (1) → prompts (2) → media (3).
// Verify profile is OUT of the MVP (13 Sep 2026), so the group's bar is
// steps={3}; this screen is current={3}. The header + progress shell is ONE
// component with the count and index as props; a second copy is a bug.
//
// STATES (props on ScreenProfileMedia):
//   state="empty"                                A · both slots unrecorded
//   state="video-only"                           B · video kept, voice empty
//   state="voice-only"                           C · voice kept, video empty
//   state="both"                                 D · both kept
//   sheet="prompts-video"                        E · prompt list, video
//   sheet="prompts-voice"                        F · prompt list, voice
//   sheetPick={MEDIA_PROMPTS[n]}                 artboard-only: draws the
//                                                sheet's committed CTA state
//   tall                                         artboard-only: the frame
//                                                grows to content height
// SUB-SCREENS (own components, full-bleed, NO AppHeader and NO StepProgress):
//   <VideoRecordingView phase="recording" elapsed={6}/>    G
//   <VideoRecordingView phase="review"    elapsed={9}/>    H · post-Stop
//   <VoiceRecordingView phase="recording" elapsed={4}/>    I
//   <VoiceRecordingView phase="review"    elapsed={14}/>   J · post-Stop
//
// THE 16 SEP 2026 PASS is the substance of the ticket, not polish. Each item
// replaces something an earlier mock showed:
//   · OPTIONAL stated structurally, above the headline, read first
//   · video is 10 seconds, not 15 — shorter is the whole point
//   · no "Upload from library" on either card: captured in the app or not
//     at all, because an uploaded clip is not evidence of a real person
//   · the card's primary CTA is "See the prompts", NOT "Record" — the prompt
//     list is the missing first step; recording is the second
//   · no caption authoring; the chosen prompt IS the caption on the profile
//   · the "what works" inspiration sheet and the manifesto card are gone,
//     and the whole empty state now fits above the fold at 390 × 844
//
// THE PREVIEWED PROMPT IS RANKED, NOT DESIGNED (decision 33). Each empty
// card shows the most-COMPLETED prompt for its medium, published by the
// server and passed in as previewVideo / previewVoice. MEDIA_PREVIEW in this
// file is the COLD-START FALLBACK ONLY. The client never ranks.
//
// WHAT IS NOT OURS: the camera and microphone permission alerts, the Settings
// app, the OS recording indicators and audio-session interruptions. The
// VIEWFINDER IS OURS — unlike screen 06, capture runs through our own session,
// so there is no system camera UI in this flow. Never spec an alert height.
//
// CONTENT LIVES HERE: MEDIA_PROMPTS (the eleven) and MEDIA_PROMPT_OWN are the
// source. Copy them wholesale; store the stable §20 media_prompt_id, never the
// display string.
//
// SHELL COMPONENTS from ../components/shared.jsx:
//   StatusBar · AppHeader · StepProgress · NextButton · SkipLink · Button ·
//   HomeIndicator · Icon
// TOKENS from ../tokens/colors_and_type.css, incl. .su-underlined em.
// The sheet-rise and su-rec-pulse keyframes are inlined so it runs standalone.
//
// EXPORTS: ScreenProfileMedia, MediaSlotCard, RecordedVideoCard,
//          RecordedVoiceCard, PromptCaption, MediaPromptSheet,
//          VideoRecordingView, VoiceRecordingView, MEDIA_PROMPTS,
//          MEDIA_PROMPT_OWN
//
// The final beat in profile creation, *after* photos and written prompts.
// Answer ONE short prompt as a 10-second video and/or a 15-second voice
// note, so a potential match can feel your energy before meeting.
//
// ─────────────────────────────────────────────────────────────
// Revised 16 Sep 2026 — "optional, and here's where to start"
// ─────────────────────────────────────────────────────────────
// Six changes, all aimed at the same two problems: users read this
// step as mandatory, and they stall on "what do I even say".
//
//   1. OPTIONAL is stated structurally, above the headline, before
//      anything else is read. Skip stays on the footer.
//   2. Video is 10 seconds, not 15. Shorter is the whole point.
//   3. No "Upload from library". Media is captured in the app or not
//      at all — an uploaded clip isn't evidence of a real person.
//   4. The card's primary CTA is "See the prompts", not "Record".
//      The prompt list is the missing first step; recording is the
//      second. Each card previews one real prompt so the user knows
//      what they are about to be asked.
//   5. No caption authoring. The chosen prompt IS the caption on the
//      profile — one less text field, and it always reads sensibly.
//   6. The "what works" inspiration sheet and the blush manifesto
//      card are gone. The prompt list does that job better and the
//      whole empty state now fits above the fold on a 390×844.
//
// Exposed on window:
//   - ScreenProfileMedia({ state, sheet, videoPrompt, voicePrompt })
//   - MediaSlotCard, RecordedVideoCard, RecordedVoiceCard — primitives
//   - MediaPromptSheet — the prompt list, per media kind
//   - VideoRecordingView, VoiceRecordingView — full-bleed recording
//   - MEDIA_PROMPTS, MEDIA_PROMPT_OWN

const { useState: useStateMedia } = React;

// ─────────────────────────────────────────────────────────────
// MEDIA_PROMPTS — the shared list, used by both media kinds
//
// Eleven prompts, all answerable in one breath and none of them
// asking the user to be interesting. Every one is about an ordinary
// good moment — that is deliberate: the hardest thing about a video
// is being asked to perform, and "your comfort snack" cannot be
// performed. The list is shared between video and voice; some read
// better spoken, some better filmed, and the user is the judge.
// ─────────────────────────────────────────────────────────────
const MEDIA_PROMPTS = [
  "The little everyday thing that instantly puts me in a good mood",
  "What my ideal sunny morning looks like",
  "A sound that always makes me feel relaxed",
  "My go-to comfort snack when having a good day",
  "How my friends would describe my energy in three words",
  "The kind of weather that brings out the best in me",
  "A song that always makes me want to move",
  "What I usually look like when I'm relaxed and happy",
  "The best simple pleasure in my daily routine",
  "Something cute or funny that made me smile this week",
  "My favorite way to spend an easy 30 minutes outside",
];

// The escape hatch. Always last, always visually distinct — a list
// of eleven is a help, not a cage.
const MEDIA_PROMPT_OWN = "Something else — my own idea";

// One prompt previewed on each empty card. Different per kind so the
// two cards don't read as duplicates, and each is chosen to suit its
// medium: one you'd show, one you'd play.
// COLD-START FALLBACK ONLY. The prompt previewed on each empty card
// is the most-chosen prompt for that medium, ranked from tracking
// (see enums.json §20 and decision 33): the list answers "what do
// people actually want to talk about", and the card should show that
// answer rather than a designer's guess. These two are what a new
// install shows until the ranking has a sample — chosen to suit
// their medium, one you'd show and one you'd play.
const MEDIA_PREVIEW = {
  video: MEDIA_PROMPTS[7],
  voice: MEDIA_PROMPTS[2],
};

// ─────────────────────────────────────────────────────────────
// PromptCaption — the chosen prompt, shown read-only under a
// recorded artefact.
//
// Replaces the old 50-char caption input (removed 16 Sep 2026). The
// prompt the user answered is a better caption than anything they
// would type under time pressure, it is already written in the
// product's voice, and it can never be left blank.
// ─────────────────────────────────────────────────────────────
function PromptCaption({ prompt }) {
  return (
    <div style={{
      borderTop: '1px dashed var(--liq-border-soft)',
      paddingTop: 10,
      display: 'flex', flexDirection: 'column', gap: 3,
    }}>
      <span style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 10.5,
        letterSpacing: 0.08, textTransform: 'uppercase',
        color: 'var(--liq-fg-subtle)',
      }}>
        Prompt · shown on your profile
      </span>
      <span style={{
        fontFamily: 'var(--liq-font-serif)', fontStyle: 'italic', fontSize: 15,
        lineHeight: 1.35, color: 'var(--liq-fg)', textWrap: 'pretty',
      }}>
        {prompt}
      </span>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// MediaSlotCard — empty state for a media slot
//
// White card: icon pip + title + one-line hint, then a previewed
// prompt in the lilac wash, then a single sunset CTA.
//
// The CTA is "See the prompts", NOT "Record" (changed 16 Sep 2026).
// "Record" asks for a performance with no brief; the prompt list is
// the brief, so it comes first. There is no upload path on either
// card — media is captured in the app or not at all.
//
// Both cards are deliberately compact: the whole empty state has to
// sit above the fold on a 390×844 with nothing cut off.
// ─────────────────────────────────────────────────────────────
function MediaSlotCard({ kind = 'video', onChoose, preview }) {
  const shown = preview || MEDIA_PREVIEW[kind];
  const cfg = kind === 'video'
    ? { icon: 'video', title: 'A 10-second video',
        hint: 'Filmed here in the app. Ten seconds, one prompt.',
        preview: shown }
    : { icon: 'mic', title: 'A 15-second voice note',
        hint: 'Just your voice, answering one prompt.',
        preview: shown };

  return (
    <div style={{
      background: 'var(--liq-bg-elevated)',
      border: '1px solid var(--liq-border-soft)',
      borderRadius: 20,
      boxShadow: 'var(--liq-shadow-md)',
      padding: '14px 14px 13px',
      display: 'flex', flexDirection: 'column', gap: 10,
    }}>
      <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
        <div style={{
          flex: 'none', width: 42, height: 42, borderRadius: 13,
          background: 'var(--su-grad-lilac)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'var(--liq-primary-500)',
        }}>
          <Icon name={cfg.icon} size={20} stroke={1.8}/>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 17,
            color: 'var(--liq-fg)', lineHeight: 1.2, letterSpacing: '-0.01em',
          }}>
            {cfg.title}
          </div>
          <div style={{
            marginTop: 3,
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13,
            lineHeight: 1.4, color: 'var(--liq-neutral-200)', textWrap: 'pretty',
          }}>
            {cfg.hint}
          </div>
        </div>
      </div>

      {/* Previewed prompt — the answer to "where do I start". One real
          prompt, verbatim, so the list is understood before it opens. */}
      <div style={{
        borderRadius: 14,
        background: 'var(--su-grad-lilac)',
        border: '1px solid rgba(129,42,236,0.10)',
        padding: '8px 12px 10px',
      }}>
        <div style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 9.5,
          letterSpacing: 0.08, textTransform: 'uppercase',
          color: 'var(--liq-primary-500)',
        }}>
          One of 11 prompts
        </div>
        <div style={{
          marginTop: 2,
          fontFamily: 'var(--liq-font-serif)', fontStyle: 'italic', fontSize: 14.5,
          lineHeight: 1.3, color: 'var(--liq-fg)', textWrap: 'pretty',
        }}>
          {cfg.preview}
        </div>
      </div>

      <Button variant="sunset" size="md" fullWidth onClick={onChoose}>
        See the prompts
      </Button>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// RecordedVideoCard — filled state for the video slot
//
// Renders a 16:9 thumbnail tile with a centered play affordance and
// a corner duration pill. Below, the same row of controls as the
// voice card so the two filled states feel like siblings.
// ─────────────────────────────────────────────────────────────
function RecordedVideoCard({ duration = '0:09', thumb = 'ph_you_0', prompt = MEDIA_PROMPTS[7], onPlay, onRetake, onDelete }) {
  const thumbUrl = (window.__resources && window.__resources[thumb])
    || `https://i.pravatar.cc/600?u=${thumb}`;
  return (
    <div style={{
      background: 'var(--liq-bg-elevated)',
      border: '1px solid var(--liq-border-soft)',
      borderRadius: 20,
      boxShadow: 'var(--liq-shadow-md)',
      padding: 12,
      display: 'flex', flexDirection: 'column', gap: 10,
    }}>
      <div onClick={onPlay} style={{
        position: 'relative',
        borderRadius: 14, overflow: 'hidden',
        aspectRatio: '16 / 10',
        background: `center/cover no-repeat url(${thumbUrl}), var(--liq-bg-raised)`,
        cursor: 'pointer',
      }}>
        {/* Soft scrim so the play glyph reads on any photo */}
        <div style={{
          position: 'absolute', inset: 0,
          background: 'linear-gradient(180deg, rgba(29,17,41,0.10) 0%, rgba(29,17,41,0.32) 100%)',
        }}/>

        {/* Centered play button */}
        <div style={{
          position: 'absolute', inset: 0,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <div style={{
            width: 56, height: 56, borderRadius: 9999,
            background: 'rgba(255,255,255,0.92)',
            backdropFilter: 'blur(8px)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: 'var(--liq-fg)',
            boxShadow: '0 8px 24px rgba(29,17,41,0.20)',
          }}>
            <span style={{ marginLeft: 4 }}>
              <Icon name="play" size={22} stroke={0} style={{ fill: 'var(--liq-fg)' }}/>
            </span>
          </div>
        </div>

        {/* Duration pill */}
        <div style={{
          position: 'absolute', left: 10, bottom: 10,
          padding: '4px 10px', borderRadius: 9999,
          background: 'rgba(29,17,41,0.78)', backdropFilter: 'blur(8px)',
          color: '#fff', fontFamily: 'var(--liq-font-sans)',
          fontVariantNumeric: 'tabular-nums', fontWeight: 700, fontSize: 12,
          letterSpacing: 0.02,
        }}>
          {duration}
        </div>

        {/* Saved chip top-right */}
        <div style={{
          position: 'absolute', right: 10, top: 10,
          padding: '4px 10px 4px 6px', borderRadius: 9999,
          background: 'rgba(255,255,255,0.92)', backdropFilter: 'blur(8px)',
          color: 'var(--liq-success-fg)', fontFamily: 'var(--liq-font-sans)',
          fontWeight: 700, fontSize: 11, letterSpacing: 0.02,
          display: 'inline-flex', alignItems: 'center', gap: 4,
        }}>
          <span style={{
            width: 14, height: 14, borderRadius: 9999,
            background: 'var(--liq-success)', color: '#fff',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <svg width="8" height="8" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
          </span>
          Saved
        </div>
      </div>

      <PromptCaption prompt={prompt}/>

      <RecordedControls label="Your video" onRetake={onRetake} onDelete={onDelete}/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// RecordedVoiceCard — filled state for the voice slot
//
// A waveform strip with a play/pause pip on the left, time readout
// in the middle (`0:08 / 0:14` tabular), and the same controls row
// underneath. The waveform is purely decorative SVG bars whose
// heights are deterministic per index so re-renders don't dance.
// ─────────────────────────────────────────────────────────────
function RecordedVoiceCard({ duration = '0:14', played = '0:08', prompt = MEDIA_PROMPTS[2], onPlay, onRetake, onDelete }) {
  // Deterministic waveform — sin-based so playback never jumps.
  const bars = React.useMemo(() => Array.from({ length: 48 }, (_, i) => {
    const v = Math.abs(Math.sin(i * 0.7) * 0.55 + Math.sin(i * 0.31) * 0.4) + 0.1;
    return Math.min(1, v);
  }), []);
  const progress = 0.55; // matches played/duration roughly

  return (
    <div style={{
      background: 'var(--liq-bg-elevated)',
      border: '1px solid var(--liq-border-soft)',
      borderRadius: 20,
      boxShadow: 'var(--liq-shadow-md)',
      padding: 14,
      display: 'flex', flexDirection: 'column', gap: 12,
    }}>
      <div style={{
        background: 'var(--su-grad-lilac)',
        borderRadius: 14,
        padding: '14px 14px',
        display: 'flex', alignItems: 'center', gap: 14,
      }}>
        {/* Play pip */}
        <button onClick={onPlay} style={{
          flex: 'none',
          width: 44, height: 44, borderRadius: 9999,
          background: 'var(--liq-primary-500)',
          boxShadow: 'var(--liq-shadow-violet)',
          color: '#fff',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <span style={{ marginLeft: 3 }}>
            <Icon name="play" size={18} stroke={0} style={{ fill: '#fff' }}/>
          </span>
        </button>

        {/* Waveform */}
        <div style={{
          flex: 1, height: 36, display: 'flex',
          alignItems: 'center', gap: 2,
        }}>
          {bars.map((v, i) => {
            const played = i / bars.length < progress;
            return (
              <span key={i} style={{
                display: 'inline-block', flex: '1 1 0',
                height: `${Math.round(v * 100)}%`, minHeight: 3,
                borderRadius: 2,
                background: played ? 'var(--liq-primary-500)' : 'rgba(129,42,236,0.28)',
              }}/>
            );
          })}
        </div>
      </div>

      {/* Time + controls — time on left so it pairs with the waveform above */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 4px',
      }}>
        <div style={{
          fontFamily: 'var(--liq-font-sans)', fontVariantNumeric: 'tabular-nums',
          fontWeight: 700, fontSize: 13, color: 'var(--liq-fg-subtle)',
          letterSpacing: 0.02,
        }}>
          <span style={{ color: 'var(--liq-fg)' }}>{played}</span>
          <span> / {duration}</span>
        </div>

        <div style={{ display: 'flex', gap: 8 }}>
          <ControlPip icon="refresh" label="Retake" onClick={onRetake}/>
          <ControlPip icon="trash"   label="Delete" tone="danger" onClick={onDelete}/>
        </div>
      </div>

      <PromptCaption prompt={prompt}/>
    </div>
  );
}

// Shared controls row used by the video filled state. Lives under
// the thumb. Same vocabulary as the voice card's controls row.
function RecordedControls({ label, onRetake, onDelete }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '2px 6px 2px 8px',
    }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 13.5,
        color: 'var(--liq-fg)',
      }}>
        <span style={{
          width: 18, height: 18, borderRadius: 9999,
          background: 'var(--liq-success)', color: '#fff',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none"
            stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
        </span>
        <span>{label}</span>
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <ControlPip icon="refresh" label="Retake" onClick={onRetake}/>
        <ControlPip icon="trash"   label="Delete" tone="danger" onClick={onDelete}/>
      </div>
    </div>
  );
}

// Pill-shaped secondary action with leading icon. Used inside the
// filled-state controls rows. Two tones: neutral (warm-ink on white)
// and danger (red-on-white) for delete.
function ControlPip({ icon, label, tone = 'neutral', onClick }) {
  const colors = tone === 'danger'
    ? { fg: 'var(--liq-danger-fg)', bg: '#fff', border: 'rgba(251,50,59,0.30)' }
    : { fg: 'var(--liq-fg)',         bg: '#fff', border: 'var(--liq-border)' };
  return (
    <button onClick={onClick} style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      height: 32, padding: '0 12px',
      borderRadius: 9999,
      background: colors.bg,
      border: `1px solid ${colors.border}`,
      color: colors.fg,
      fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 12.5,
      letterSpacing: 0.01,
    }}>
      <Icon name={icon} size={13} stroke={2}/>
      <span>{label}</span>
    </button>
  );
}

// ─────────────────────────────────────────────────────────────
// MediaPromptSheet — the eleven prompts, per media kind
//
// Replaces the old "Show me what works" inspiration sheet (removed
// 16 Sep 2026). Examples of other people's videos answered "what
// does good look like"; the real question was "what am I supposed
// to talk about", and a list of prompts answers it directly.
//
// Rows are radio-select, not tap-to-launch: the user should be able
// to read all eleven and change their mind before the camera opens.
// The chosen prompt becomes the caption on the profile, so this is
// also where the profile copy is decided.
// ─────────────────────────────────────────────────────────────
function MediaPromptSheet({ kind = 'video', selected, onPick, onClose }) {
  const [pick, setPick] = useStateMedia(selected === undefined ? null : selected);
  const limit = kind === 'video' ? '10 seconds' : '15 seconds';

  const Row = ({ label, isOwn }) => {
    const on = pick === label;
    return (
      <button onClick={() => { setPick(label); onPick && onPick(label); }}
        style={{
          width: '100%', textAlign: 'left',
          display: 'flex', alignItems: 'center', gap: 12,
          padding: '11px 14px 12px',
          borderRadius: 16,
          background: on ? 'var(--su-grad-lilac)' : '#fff',
          border: on
            ? '1.5px solid var(--liq-primary-500)'
            : isOwn
              ? '1.5px dashed var(--liq-border)'
              : '1.5px solid var(--liq-border-soft)',
          transition: 'background 160ms, border-color 160ms',
        }}
      >
        <span style={{
          flex: 'none', width: 22, height: 22, borderRadius: 9999,
          background: on ? 'var(--liq-primary-500)' : 'transparent',
          border: on ? 'none' : '1.5px solid rgba(29,17,41,0.20)',
          color: '#fff',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        }}>
          {on && (
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
          )}
        </span>
        <span style={{ flex: 1, minWidth: 0 }}>
          <span style={{
            display: 'block',
            fontFamily: isOwn ? 'var(--liq-font-sans)' : 'var(--liq-font-serif)',
            fontWeight: isOwn ? 700 : 500,
            fontStyle: isOwn ? 'normal' : 'italic',
            fontSize: isOwn ? 14 : 15,
            lineHeight: 1.3, color: 'var(--liq-fg)', textWrap: 'pretty',
          }}>
            {label}
          </span>
          {isOwn && (
            <span style={{
              display: 'block', marginTop: 2,
              fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 12,
              lineHeight: 1.35, color: 'var(--liq-fg-subtle)',
            }}>
              Say or show whatever you like — your own words, your own idea.
            </span>
          )}
        </span>
      </button>
    );
  };

  return (
    <div style={{
      width: '100%',
      background: 'var(--liq-bg)',
      borderTopLeftRadius: 32, borderTopRightRadius: 32,
      padding: '8px 0 0',
      boxShadow: '0 -20px 60px rgba(46,1,71,0.18), 0 -4px 12px rgba(46,1,71,0.08)',
      animation: 'sheet-rise 360ms cubic-bezier(.22,1,.36,1) both',
      position: 'relative',
      maxHeight: 660,
      display: 'flex', flexDirection: 'column',
    }}>
      <div style={{
        margin: '0 auto', width: 40, height: 4, borderRadius: 4,
        background: 'rgba(29,17,41,0.18)',
      }}/>
      <button onClick={onClose} aria-label="Dismiss" style={{
        position: 'absolute', top: 16, right: 16,
        width: 36, height: 36, borderRadius: '50%',
        background: 'rgba(29,17,41,0.04)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--liq-fg)',
      }}>
        <Icon name="x" size={20} stroke={1.8}/>
      </button>

      <div style={{ padding: '14px 24px 12px' }}>
        <h2 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 25,
          letterSpacing: '-0.018em', lineHeight: 1.12, color: 'var(--liq-fg)',
          margin: '4px 0 6px', paddingRight: 36, textWrap: 'balance',
        }}>
          Pick one to <em>answer</em>.
        </h2>
        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13.5,
          lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty',
        }}>
          {kind === 'video'
            ? 'Ten seconds is short on purpose. Pick the easiest one — nobody is marking this.'
            : 'Fifteen seconds, just your voice. Pick the easiest one — nobody is marking this.'}
        </p>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, overflow: 'auto',
        padding: '0 24px 4px',
        display: 'flex', flexDirection: 'column', gap: 8,
        maskImage: 'linear-gradient(180deg, #000 0%, #000 94%, transparent 100%)',
        WebkitMaskImage: 'linear-gradient(180deg, #000 0%, #000 94%, transparent 100%)',
      }}>
        {MEDIA_PROMPTS.map((p, i) => <Row key={i} label={p}/>)}
        <Row label={MEDIA_PROMPT_OWN} isOwn/>
        <div style={{ height: 8 }}/>
      </div>

      {/* Sticky commit row. The CTA names the medium and the length,
          so nothing about what happens next is a surprise. */}
      <div style={{
        flex: 'none', padding: '12px 24px 14px',
        borderTop: '1px solid var(--liq-border-soft)',
        background: 'var(--liq-bg)',
      }}>
        <Button variant={pick ? 'sunset' : 'ghost'} size="lg" fullWidth
          leading={pick ? <span style={{
            width: 8, height: 8, borderRadius: 9999,
            background: '#fff', display: 'inline-block',
          }}/> : undefined}
        >
          {pick
            ? (kind === 'video' ? 'Film ' : 'Record ') + limit
            : 'Choose a prompt to continue'}
        </Button>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// VideoRecordingView — focused full-bleed sub-screen
//
// When the user taps Record on the video slot, the chrome falls away
// and we present a full-bleed camera viewfinder with a 0:00 → 0:10
// countdown ring, a centered red shutter, and a small "Cancel" pip.
// Mock-only — no real getUserMedia. The viewfinder is a warm ink
// surface with the user's selfie portrait as a stand-in.
// ─────────────────────────────────────────────────────────────
function VideoRecordingView({ elapsed = 6, max = 10, prompt = MEDIA_PROMPTS[7], phase = 'recording' }) {
  const review = phase === 'review';
  const ratio = review ? 0 : Math.min(1, elapsed / max);
  const sec = (s) => `0:${String(s).padStart(2, '0')}`;
  const selfie = (window.__resources && window.__resources['ph_you_0'])
    || 'https://i.pravatar.cc/600?u=ph_you_0';

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: '#0F0518',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Viewfinder — selfie portrait full-bleed, very slightly darkened */}
      <div style={{
        position: 'absolute', inset: 0,
        background: `center/cover no-repeat url(${selfie})`,
        filter: 'saturate(0.95) brightness(0.92)',
      }}/>
      {/* Vignette */}
      <div style={{
        position: 'absolute', inset: 0,
        background: 'radial-gradient(ellipse at 50% 30%, rgba(15,5,24,0) 0%, rgba(15,5,24,0.45) 80%, rgba(15,5,24,0.65) 100%)',
      }}/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20" dark={true}/>
      </div>

      {/* Top: cancel + recording dot */}
      <div style={{
        position: 'relative', zIndex: 2,
        padding: '4px 20px 0',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <button style={{
          height: 36, padding: '0 14px',
          borderRadius: 9999,
          background: 'rgba(255,255,255,0.16)',
          backdropFilter: 'blur(20px)',
          color: '#fff',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 13.5,
          letterSpacing: 0.01,
        }}>
          Cancel
        </button>

        {review ? (
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 7,
            height: 36, padding: '0 14px 0 12px',
            borderRadius: 9999,
            background: 'rgba(255,255,255,0.18)',
            backdropFilter: 'blur(20px)',
            color: '#fff',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 12,
            letterSpacing: 0.08, textTransform: 'uppercase',
          }}>
            <Icon name="check" size={13} stroke={2.6}/>
            {sec(elapsed)} recorded
          </div>
        ) : (
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 8,
            height: 36, padding: '0 14px 0 12px',
            borderRadius: 9999,
            background: 'rgba(251,50,59,0.92)',
            backdropFilter: 'blur(20px)',
            color: '#fff',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 12,
            letterSpacing: 0.08, textTransform: 'uppercase',
          }}>
            <span style={{
              width: 8, height: 8, borderRadius: 9999, background: '#fff',
              animation: 'su-rec-pulse 1.2s ease-in-out infinite',
            }}/>
            REC
          </div>
        )}
      </div>

      {/* Middle: empty while filming; the play affordance in review */}
      <div style={{
        flex: 1, position: 'relative', zIndex: 2,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        {review && (
          <button aria-label="Play your clip" style={{
            width: 88, height: 88, borderRadius: 9999,
            background: 'rgba(255,255,255,0.22)',
            backdropFilter: 'blur(24px)',
            border: '1.5px solid rgba(255,255,255,0.55)',
            color: '#fff',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            paddingLeft: 6,
            boxShadow: '0 18px 44px rgba(0,0,0,0.35)',
          }}>
            <svg width="34" height="34" viewBox="0 0 24 24" fill="currentColor">
              <polygon points="7 4 20 12 7 20 7 4"/>
            </svg>
          </button>
        )}
      </div>

      {/* Lower third: caption, progress, shutter */}
      <div style={{
        position: 'relative', zIndex: 2,
        padding: '0 24px 14px',
        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 18,
      }}>
        <div style={{
          maxWidth: 280, textAlign: 'center',
          fontFamily: 'var(--liq-font-serif)', fontStyle: 'italic', fontSize: 18,
          lineHeight: 1.35, color: '#fff',
          textShadow: '0 2px 12px rgba(0,0,0,0.45)',
          textWrap: 'balance',
        }}>
          {prompt}
        </div>

        {/* Time bar */}
        <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div style={{
            height: 6, borderRadius: 9999,
            background: 'rgba(255,255,255,0.20)', overflow: 'hidden',
          }}>
            <div style={{
              height: '100%', width: `${ratio * 100}%`,
              background: 'var(--liq-orange-500)',
              borderRadius: 9999,
              boxShadow: '0 0 12px rgba(254,104,57,0.6)',
              transition: 'width 280ms cubic-bezier(.22,1,.36,1)',
            }}/>
          </div>
          <div style={{
            display: 'flex', justifyContent: 'space-between',
            fontFamily: 'var(--liq-font-sans)', fontVariantNumeric: 'tabular-nums',
            fontWeight: 700, fontSize: 12, color: 'rgba(255,255,255,0.85)',
            letterSpacing: 0.04,
          }}>
            <span>{review ? '0:00' : sec(elapsed)}</span>
            <span>{review ? sec(elapsed) : sec(max)}</span>
          </div>
        </div>

        {review ? (
          /* Review actions — retake sits left as a quiet glass pill so
             "Use this clip" stays the obvious way forward. */
          <div style={{ width: '100%', display: 'flex', gap: 10, alignItems: 'stretch' }}>
            <button style={{
              flex: 'none', display: 'inline-flex', alignItems: 'center', gap: 7,
              height: 52, padding: '0 18px', borderRadius: 9999,
              background: 'rgba(255,255,255,0.18)',
              backdropFilter: 'blur(20px)',
              border: '1px solid rgba(255,255,255,0.40)',
              color: '#fff',
              fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14.5,
            }}>
              <Icon name="refresh" size={16} stroke={2}/>
              Retake
            </button>
            <div style={{ flex: 1, minWidth: 0 }}>
              <Button variant="sunset" size="lg" fullWidth trailing={<Icon name="check" size={17} stroke={2.4}/>}>
                Use this clip
              </Button>
            </div>
          </div>
        ) : (
          /* Shutter — large red record ring; tap to stop. */
          <button aria-label="Stop recording" style={{
            width: 84, height: 84, borderRadius: 9999,
            background: 'rgba(255,255,255,0.16)',
            backdropFilter: 'blur(20px)',
            border: '4px solid #fff',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            marginTop: 4,
          }}>
            <span style={{
              width: 30, height: 30, borderRadius: 8,
              background: '#FB323B',
              boxShadow: '0 0 18px rgba(251,50,59,0.65)',
            }}/>
          </button>
        )}
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator dark={true}/>
      </div>

      <style>{`
        @keyframes su-rec-pulse {
          0%, 100% { opacity: 1; }
          50%      { opacity: 0.35; }
        }
      `}</style>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// VoiceRecordingView — focused full-bleed sub-screen for voice
//
// Same idea but on-canvas: a centered live waveform that pulses to
// the user's voice (simulated as a sin wave for the mock), the
// 0:00 → 0:15 progress under it, and a red stop button.
// ─────────────────────────────────────────────────────────────
function VoiceRecordingView({ elapsed = 4, max = 15, prompt = MEDIA_PROMPTS[2], phase = 'recording' }) {
  const review = phase === 'review';
  const ratio = review ? 0 : Math.min(1, elapsed / max);
  const sec = (s) => `0:${String(s).padStart(2, '0')}`;

  // Center-anchored live waveform — heights peak at the middle, fade
  // toward the edges so it reads as "live mic" rather than "scrubbing".
  const bars = React.useMemo(() => Array.from({ length: 48 }, (_, i) => {
    const center = (i - 24) / 24; // -1 → 1
    const envelope = Math.max(0.1, 1 - center * center * 0.85);
    const v = (Math.abs(Math.sin(i * 0.93) + Math.cos(i * 0.5) * 0.7) / 1.7) * envelope;
    return Math.min(1, 0.15 + v * 0.85);
  }), []);

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Atmosphere — same orbs but a touch more saturated, focused mood */}
      <div style={{
        position: 'absolute', top: '-25%', right: '-30%', width: 520, height: 520,
        background: 'radial-gradient(circle, rgba(254,104,57,0.24) 0%, rgba(254,104,57,0) 65%)',
        filter: 'blur(10px)', zIndex: 0,
      }}/>
      <div style={{
        position: 'absolute', bottom: '-22%', left: '-30%', width: 520, height: 520,
        background: 'radial-gradient(circle, rgba(129,42,236,0.22) 0%, rgba(129,42,236,0) 65%)',
        filter: 'blur(10px)', zIndex: 0,
      }}/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
      </div>

      <div style={{
        position: 'relative', zIndex: 2,
        padding: '4px 20px 0',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <button style={{
          height: 36, padding: '0 14px',
          borderRadius: 9999,
          background: 'rgba(29,17,41,0.06)',
          color: 'var(--liq-fg)',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 13.5,
        }}>
          Cancel
        </button>
        {review ? (
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 7,
            height: 36, padding: '0 14px 0 12px',
            borderRadius: 9999,
            background: 'var(--liq-success)',
            color: '#fff',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 12,
            letterSpacing: 0.08, textTransform: 'uppercase',
          }}>
            <Icon name="check" size={13} stroke={2.6}/>
            {sec(elapsed)} recorded
          </div>
        ) : (
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 8,
            height: 36, padding: '0 14px 0 12px',
            borderRadius: 9999,
            background: 'rgba(251,50,59,0.92)',
            color: '#fff',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 12,
            letterSpacing: 0.08, textTransform: 'uppercase',
          }}>
            <span style={{
              width: 8, height: 8, borderRadius: 9999, background: '#fff',
              animation: 'su-rec-pulse 1.2s ease-in-out infinite',
            }}/>
            REC
          </div>
        )}
      </div>

      {/* Hero: prompt, then big waveform */}
      <div style={{
        flex: 1, position: 'relative', zIndex: 2,
        padding: '36px 24px 0',
        display: 'flex', flexDirection: 'column', alignItems: 'center',
        gap: 28,
      }}>
        <div style={{
          maxWidth: 300, textAlign: 'center',
          fontFamily: 'var(--liq-font-serif)', fontStyle: 'italic', fontSize: 22,
          lineHeight: 1.25, color: 'var(--liq-fg)',
          letterSpacing: '-0.015em',
          textWrap: 'balance',
        }}>
          {prompt}
        </div>

        {/* Waveform — live while recording; the finished take in review,
            with a play button anchoring it as something you listen to. */}
        <div style={{
          width: '100%',
          display: 'flex', alignItems: 'center', gap: review ? 14 : 4,
        }}>
          {review && (
            <button aria-label="Play your recording" style={{
              flex: 'none', width: 64, height: 64, borderRadius: 9999,
              background: 'var(--su-grad-sunset, var(--liq-orange-500))',
              color: '#fff', paddingLeft: 4,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 12px 28px rgba(254,104,57,0.35)',
            }}>
              <svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor">
                <polygon points="7 4 20 12 7 20 7 4"/>
              </svg>
            </button>
          )}
          <div style={{
            flex: 1, minWidth: 0, height: review ? 120 : 160,
            display: 'flex', alignItems: 'center', gap: 4,
          }}>
            {bars.map((v, i) => (
              <span key={i} style={{
                display: 'inline-block', flex: '1 1 0',
                height: `${Math.round(v * 100)}%`, minHeight: 6,
                borderRadius: 3,
                background: i < bars.length * ratio
                  ? 'var(--liq-orange-500)'
                  : 'var(--liq-primary-500)',
                opacity: review ? 0.5 : (i < bars.length * ratio ? 1 : 0.85),
              }}/>
            ))}
          </div>
        </div>

        {/* Hint */}
        <div style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 13,
          color: 'var(--liq-fg-subtle)', letterSpacing: 0.02,
        }}>
          {review ? 'Hear it back before you keep it' : 'Listening · keep going'}
        </div>
      </div>

      {/* Lower third: progress + stop */}
      <div style={{
        position: 'relative', zIndex: 2,
        padding: '0 24px 14px',
        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 18,
      }}>
        <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div style={{
            height: 6, borderRadius: 9999,
            background: 'rgba(29,17,41,0.10)', overflow: 'hidden',
          }}>
            <div style={{
              height: '100%', width: `${ratio * 100}%`,
              background: 'var(--liq-orange-500)',
              borderRadius: 9999,
              boxShadow: '0 0 12px rgba(254,104,57,0.45)',
              transition: 'width 280ms cubic-bezier(.22,1,.36,1)',
            }}/>
          </div>
          <div style={{
            display: 'flex', justifyContent: 'space-between',
            fontFamily: 'var(--liq-font-sans)', fontVariantNumeric: 'tabular-nums',
            fontWeight: 700, fontSize: 12, color: 'var(--liq-fg-subtle)',
            letterSpacing: 0.04,
          }}>
            <span>{review ? '0:00' : sec(elapsed)}</span>
            <span>{review ? sec(elapsed) : sec(max)}</span>
          </div>
        </div>

        {review ? (
          <div style={{ width: '100%', display: 'flex', gap: 10, alignItems: 'stretch' }}>
            <button style={{
              flex: 'none', display: 'inline-flex', alignItems: 'center', gap: 7,
              height: 52, padding: '0 18px', borderRadius: 9999,
              background: '#fff',
              border: '1px solid var(--liq-border)',
              color: 'var(--liq-fg)',
              fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14.5,
            }}>
              <Icon name="refresh" size={16} stroke={2}/>
              Retake
            </button>
            <div style={{ flex: 1, minWidth: 0 }}>
              <Button variant="sunset" size="lg" fullWidth trailing={<Icon name="check" size={17} stroke={2.4}/>}>
                Use this recording
              </Button>
            </div>
          </div>
        ) : (
          <button aria-label="Stop recording" style={{
            width: 84, height: 84, borderRadius: 9999,
            background: '#fff',
            border: '4px solid var(--liq-border)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: 'var(--liq-shadow-md)',
          }}>
            <span style={{
              width: 30, height: 30, borderRadius: 8,
              background: '#FB323B',
              boxShadow: '0 0 18px rgba(251,50,59,0.45)',
            }}/>
          </button>
        )}
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>

      <style>{`
        @keyframes su-rec-pulse {
          0%, 100% { opacity: 1; }
          50%      { opacity: 0.35; }
        }
      `}</style>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileMedia — main "Show your face" screen
//
// Props:
//   state          'empty'        — both slots unrecorded (default)
//                  'video-only'   — video recorded, voice empty
//                  'voice-only'   — voice recorded, video empty
//                  'both'         — both recorded
//   sheet          'prompts-video' | 'prompts-voice' — prompt list docked
//   sheetPick      artboard-only: a MEDIA_PROMPTS entry preselected in the
//                  docked sheet, so the committed CTA state is drawable.
//                  Nothing is ever preselected in the product.
//   videoPrompt /  the prompt each recorded artefact answers; it is
//   voicePrompt    also the caption shown on the profile
//   previewVideo / the prompt previewed on each EMPTY card — supplied
//   previewVoice   by the server as the most-chosen prompt for that
//                  medium; falls back to MEDIA_PREVIEW when the
//                  ranking has no sample yet
//   tall           true — dev-handoff mode: the frame grows to the
//                  content height and nothing scrolls
//
// Continue is ALWAYS enabled and Skip sits beside it: this step is
// optional, and "Optional" is stated above the headline so it is read
// before anything else. Gating Continue here would punish users for
// not wanting to be on camera, and the brand's not that.
// ─────────────────────────────────────────────────────────────
function ScreenProfileMedia({ state = 'empty', sheet, sheetPick, videoPrompt, voicePrompt, previewVideo, previewVoice, tall = false } = {}) {
  const hasVideo = state === 'video-only' || state === 'both';
  const hasVoice = state === 'voice-only' || state === 'both';

  return (
    <div className="su-app" style={{
      width: 390, height: tall ? 'auto' : 844,
      minHeight: tall ? 844 : undefined,
      position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      <style>{`@keyframes sheet-rise {
        from { transform: translateY(28px); opacity: 0.85; }
        to   { transform: translateY(0);    opacity: 1; }
      }`}</style>

      {/* Ambient atmosphere — same orb pattern as the rest of profile creation */}
      <div style={{
        position: 'absolute', top: '-20%', right: '-25%', width: 460, height: 460,
        background: 'radial-gradient(circle, rgba(254,104,57,0.18) 0%, rgba(254,104,57,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>
      <div style={{
        position: 'absolute', bottom: '-12%', left: '-28%', width: 420, height: 420,
        background: 'radial-gradient(circle, rgba(129,42,236,0.16) 0%, rgba(129,42,236,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
        <AppHeader title="The real you" leading="back"/>
      </div>

      <div className="su-noscroll" style={{
        flex: tall ? 'none' : 1, position: 'relative', zIndex: 1,
        overflow: tall ? 'visible' : 'auto',
        padding: '4px 24px 0',
      }}>
        {/* "The real you": photos (1) → prompts (2) → media (3). Verify
            profile was cut from the MVP on 13 Sep 2026, so the bar is 3
            segments. Media is the last step, so it reads full. */}
        <StepProgress steps={3} current={3} style={{ marginBottom: 22 }}/>

        {/* OPTIONAL, stated before the headline. The old build put it
            in the subhead under a 32px display line, where it was the
            fourth thing read; users took the step as mandatory. */}
        <div style={{
          display: 'inline-flex', alignItems: 'center', gap: 7,
          padding: '5px 12px 5px 9px', borderRadius: 9999,
          background: 'rgba(129,42,236,0.10)',
          color: 'var(--liq-primary-500)',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 11,
          letterSpacing: 0.07, textTransform: 'uppercase',
          marginBottom: 10,
        }}>
          <Icon name="check" size={12} stroke={2.6}/>
          Optional · you can skip this
        </div>

        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
          letterSpacing: '-0.018em', lineHeight: 1.08, color: 'var(--liq-fg)',
          margin: '0 0 4px', textWrap: 'balance',
        }}>
          Show your face. Let them <em>hear you</em>.
        </h1>

        {/* Two media slots, stacked. Video sits above voice because
            it's the higher-signal artefact for a date — the user
            sees a person, not just hears one. */}
        <div style={{
          marginTop: 14, display: 'flex', flexDirection: 'column', gap: 12,
        }}>
          {hasVideo
            ? <RecordedVideoCard
                duration="0:09"
                prompt={videoPrompt === undefined ? MEDIA_PROMPTS[7] : videoPrompt}
              />
            : <MediaSlotCard kind="video" preview={previewVideo}/>
          }
          {hasVoice
            ? <RecordedVoiceCard
                duration="0:14" played="0:08"
                prompt={voicePrompt === undefined ? MEDIA_PROMPTS[2] : voicePrompt}
              />
            : <MediaSlotCard kind="voice" preview={previewVoice}/>
          }
        </div>

        <div style={{ height: 18 }}/>
      </div>

      {/* Footer — Skip on the left, Continue on the right.
          Continue is always active (step is optional). When both slots
          are filled it pops to the sunset gradient as a small reward. */}
      <div style={{
        position: 'relative', zIndex: 2, flex: 'none',
        padding: '12px 24px 14px',
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      }}>
        <SkipLink/>

        <NextButton
          label="Continue"
          color="orange"
          size={52}
        />
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>

      {/* Sheet overlay — the prompt list, per kind */}
      {(sheet === 'prompts-video' || sheet === 'prompts-voice' || sheet === 'prompts') && (
        <>
          <div style={{
            position: 'absolute', inset: 0, zIndex: 4,
            background: 'rgba(29,17,41,0.42)',
            backdropFilter: 'blur(1.5px)',
          }}/>
          <div style={{
            position: 'absolute', left: 0, right: 0, bottom: 0, zIndex: 5,
          }}>
            <MediaPromptSheet kind={sheet === 'prompts-voice' ? 'voice' : 'video'} selected={sheetPick}/>
            <div style={{ background: 'var(--liq-bg)' }}>
              <HomeIndicator/>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

Object.assign(window, {
  ScreenProfileMedia,
  MediaSlotCard,
  RecordedVideoCard,
  RecordedVoiceCard,
  PromptCaption,
  MediaPromptSheet,
  VideoRecordingView,
  VoiceRecordingView,
  MEDIA_PROMPTS,
  MEDIA_PROMPT_OWN,
});
