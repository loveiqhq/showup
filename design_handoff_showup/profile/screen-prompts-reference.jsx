// screen-prompts-reference.jsx — Profile creation, screen 07: Prompts
//
// THE REFERENCE IMPLEMENTATION FOR [Profile 07]. This is the code that
// renders the attached spec sheet. Read values from here; never measure
// the PNG. This is not production code — recreate it in the app's own
// environment and patterns.
//
// WHAT THIS SCREEN IS: the first screen in the flow that asks the user to
// write something about themselves. Up to 3 prompts (topic + answer,
// 160 characters max). ONE IS ENOUGH TO CONTINUE.
//
// STEP 2 OF 3 of "The real you" — photos (1) → prompts (2) → media (3).
// Verify profile is OUT of the MVP, so this group's bar is steps={3}.
// The photos screen changes to steps={3} current={1} with this ticket.
// The header + progress shell is ONE component with the count and index
// as props; a second copy is a bug.
//
// STATES (props):
//   prompts={[]}                                 A · 0 of 3
//   prompts={[one]}                              B · 1 of 3
//   prompts={three}                              C · 3 of 3
//   sheet="topic"                                D · browse all topics
//   sheet="write" sheetVariant="empty"           E
//   sheet="write" sheetVariant="mid"             F
//   sheet="write" sheetVariant="cap"             G · 160/160
//   sheet="write" sheetVariant="nudge"           H · empty Save press
//   showToast                                    artboard-only: draws the
//                                                refusal toast open
//
// THE CONVERSION PASS (13 Sep 2026) is the substance of the ticket, not
// polish. Each item replaces something an earlier mock showed:
//   · three suggested topics ON the screen — the topic sheet is now the
//     "Browse all 15 topics" escape hatch, not the critical path
//   · a worked example above the field (NOT a placeholder — a placeholder
//     vanishes at the first keystroke, when it is still wanted)
//   · a floor ("One good sentence is enough") instead of a ceiling; the
//     counter appears at 100 characters, not at 0
//   · 160/160 is AMBER and Save stays live — reaching the cap is not a
//     validation failure
//   · Save is never disabled (flow rule 2c); an empty press explains
//   · drafts survive dismissing the sheet
//
// WHAT IS NOT OURS: the keyboard (the sheets are drawn without one so the
// artboard shows true content height), dictation, paste and autocorrect.
// Never spec a keyboard height.
//
// CONTENT LIVES HERE: TOPIC_GROUPS (15 topics in 3 groups) and
// PROMPT_EXAMPLES (one example per topic) are the source. Copy them
// wholesale; do not retype them, and store a stable topic id rather than
// the display string.
//
// SHELL COMPONENTS from ../components/shared.jsx:
//   StatusBar · AppHeader · StepProgress · NextButton · Button ·
//   HomeIndicator · Icon
// TOKENS from ../tokens/colors_and_type.css, incl. .su-underlined em.
// The sheet-rise and su-shake keyframes are inlined so it runs standalone.
//
// EXPORTS: ScreenProfilePrompts, TopicPickerSheet, WritePromptSheet,
//          SuggestionCard, FilledPromptCard, TOPIC_GROUPS,
//          SUGGESTED_TOPICS, PROMPT_EXAMPLES, SAMPLE_PROMPTS
//
const { useState: useStatePrompts } = React;

const PROMPT_MAX = 160;      // hard cap — quiet, not an error
const PROMPT_COUNTER_FROM = 100; // counter appears here, not at 0

// ─────────────────────────────────────────────────────────────
// TOPIC_GROUPS — the same 15 topics, in three named groups.
//
// Grouping is the whole point: a flat 15 is a scroll, three groups
// of five is a scan. The group names are answers to "what kind of
// thing do you want to say", which is the question the user is
// actually holding.
// ─────────────────────────────────────────────────────────────
const TOPIC_GROUPS = [
  {
    label: 'Dating me',
    topics: [
      "On a first date, I usually…",
      "The easiest way to get me out the door is…",
      "My ideal 30-minute date looks like…",
      "I'd cross town for…",
      "A spontaneous plan with me usually involves…",
    ],
  },
  {
    label: 'Me in real life',
    topics: [
      "What 30 minutes with me feels like…",
      "In real life, I'm way more…",
      "The unsexy truth about me is…",
      "My default Sunday energy is…",
      "A weird or specific habit of mine…",
    ],
  },
  {
    label: 'Opinions & obsessions',
    topics: [
      "I'll talk for hours about…",
      "A random topic I know way too much about…",
      "The hill I'm willing to die on…",
      "A green flag that always catches my attention…",
      "My hot take on…",
    ],
  },
];

const PROMPT_TOPICS = TOPIC_GROUPS.flatMap(g => g.topics);

// The three surfaced on the screen itself. One from each group, and
// deliberately the three easiest to answer without thinking — the
// job of a suggestion is to be answerable, not to be the best topic.
const SUGGESTED_TOPICS = [
  "On a first date, I usually…",
  "A weird or specific habit of mine…",
  "I'll talk for hours about…",
];

// ─────────────────────────────────────────────────────────────
// PROMPT_EXAMPLES — one worked example per topic, shown in the
// write sheet above the field.
//
// Written short on purpose: every example is under 120 characters,
// so the example itself says "this length is fine". An example that
// fills the box would undo the floor framing.
//
// Fallback covers any topic without a written example.
// ─────────────────────────────────────────────────────────────
const PROMPT_EXAMPLES = {
  "On a first date, I usually…": "…talk too fast about something I care about, then apologise for it. Don't let me apologise.",
  "The easiest way to get me out the door is…": "…say the words \"there's a table free at 7\". I'll be there at 6:55.",
  "My ideal 30-minute date looks like…": "Coffee, a bench, and the good half of a conversation. No menus, no agenda.",
  "I'd cross town for…": "A proper conversation. An old cinema. The 8pm walk after a long day.",
  "A spontaneous plan with me usually involves…": "A train, a vague idea of a destination, and somewhere that does chips.",
  "What 30 minutes with me feels like…": "Fast. I ask a lot of questions and I actually wait for the answers.",
  "In real life, I'm way more…": "…quiet at the start and much louder by minute ten. Give me the ten.",
  "The unsexy truth about me is…": "I go to bed at 10 and I'm not sorry. Breakfast dates are my best work.",
  "My default Sunday energy is…": "Long walk, loud kitchen, three podcasts I won't finish.",
  "A weird or specific habit of mine…": "I read the last page of a book first. It has never once ruined it.",
  "I'll talk for hours about…": "Why every good city has a bad river, and why we keep building next to them.",
  "A random topic I know way too much about…": "Competitive dog agility. I have opinions about the weave poles.",
  "The hill I'm willing to die on…": "Showing up. Cancelling last minute isn't a scheduling problem, it's an answer.",
  "A green flag that always catches my attention…": "Being kind to someone who can't do anything for you. Every time.",
  "My hot take on…": "…brunch: it's just a queue with eggs in it.",
};
const PROMPT_EXAMPLE_FALLBACK = "Say the specific thing, not the safe one. Two lines is plenty.";

function exampleFor(topic) {
  return PROMPT_EXAMPLES[topic] || PROMPT_EXAMPLE_FALLBACK;
}

// Demo answers for the filled artboards.
const SAMPLE_PROMPTS = [
  {
    topic: "On a first date, I usually…",
    answer: "Talk about anything real. Not jobs, not pets, not the weather. The thing actually on your mind this week. Bring it. I'll listen.",
  },
  {
    topic: "The hill I'm willing to die on…",
    answer: "Showing up. Cancelling last minute isn't a scheduling problem, it's an answer.",
  },
  {
    topic: "I'd cross town for…",
    answer: "A proper conversation. An old cinema. The 8pm walk after a long day.",
  },
];

const MID_DRAFT = "Talk about anything real. Not jobs, not pets, not the weather.";
const FULL_DRAFT = "Talk about anything real. Not jobs, not pets, not the weather. The thing actually on your mind this week. Bring it. I will listen for the entire thirty minutes!";

// ─────────────────────────────────────────────────────────────
// SuggestionCard — a topic, on the screen, tappable
//
// The single biggest change in this revision. It is a topic first
// and a control second: the question is set in Lora at reading
// size, and the affordance is a small violet "Write this" row
// underneath rather than a chevron, so the card reads as an
// invitation rather than a menu item.
// ─────────────────────────────────────────────────────────────
function SuggestionCard({ topic, onClick }) {
  return (
    <button onClick={onClick} style={{
      width: '100%', textAlign: 'left',
      padding: '14px 16px 12px',
      borderRadius: 18,
      background: '#fff',
      border: '1.5px solid rgba(129,42,236,0.28)',
      display: 'flex', flexDirection: 'column', gap: 8,
      transition: 'transform 180ms cubic-bezier(.22,1,.36,1), border-color 180ms, background 180ms',
    }}
      onMouseDown={e => { e.currentTarget.style.transform = 'scale(0.985)'; e.currentTarget.style.background = 'var(--liq-lavender-50)'; }}
      onMouseUp={e => { e.currentTarget.style.transform = 'scale(1)'; e.currentTarget.style.background = '#fff'; }}
      onMouseLeave={e => { e.currentTarget.style.transform = 'scale(1)'; e.currentTarget.style.background = '#fff'; }}
    >
      <span style={{
        fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 16.5,
        lineHeight: 1.25, letterSpacing: '-0.008em', color: 'var(--liq-fg)',
        textWrap: 'pretty',
      }}>{topic}</span>
      <span style={{
        display: 'inline-flex', alignItems: 'center', gap: 7,
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 12.5,
        letterSpacing: 0.01, color: 'var(--liq-primary-500)',
      }}>
        <span style={{
          width: 20, height: 20, borderRadius: '50%',
          background: 'var(--liq-lavender-50)',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon name="plus" size={13} stroke={2.6}/>
        </span>
        Write this
      </span>
    </button>
  );
}

// ─────────────────────────────────────────────────────────────
// BrowseAllButton — the escape hatch to the full 15.
//
// Deliberately quieter than a suggestion card: it is the slower
// path, and it should look like it.
// ─────────────────────────────────────────────────────────────
function BrowseAllButton({ onClick, label = "Browse all 15 topics" }) {
  return (
    <button onClick={onClick} style={{
      width: '100%', height: 48,
      borderRadius: 9999,
      background: 'transparent',
      border: '1.5px solid var(--liq-border)',
      padding: '0 20px',
      display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
      transition: 'background 180ms',
    }}
      onMouseDown={e => e.currentTarget.style.background = 'rgba(29,17,41,0.035)'}
      onMouseUp={e => e.currentTarget.style.background = 'transparent'}
      onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
    >
      <span style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14,
        color: 'var(--liq-neutral-200)',
      }}>{label}</span>
    </button>
  );
}

// ─────────────────────────────────────────────────────────────
// FilledPromptCard — lilac wash card with topic + answer
// ─────────────────────────────────────────────────────────────
function FilledPromptCard({ topic, answer, onEdit }) {
  return (
    <div style={{
      position: 'relative',
      padding: '16px 18px',
      borderRadius: 18,
      background: 'var(--su-grad-lilac)',
      border: '1px solid rgba(129,42,236,0.10)',
    }}>
      <button
        onClick={onEdit}
        aria-label="Edit prompt"
        style={{
          position: 'absolute', top: 12, right: 12,
          width: 30, height: 30, borderRadius: '50%',
          background: 'rgba(255,255,255,0.7)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'var(--liq-primary-500)',
        }}
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
          stroke="currentColor" strokeWidth="1.8"
          strokeLinecap="round" strokeLinejoin="round">
          <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
          <path d="M15 5l4 4"/>
        </svg>
      </button>

      <div style={{
        fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 16,
        lineHeight: 1.25, color: 'var(--liq-fg)',
        letterSpacing: '-0.005em',
        paddingRight: 32,
      }}>
        {topic}
      </div>
      <div style={{
        marginTop: 6,
        fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13.5,
        lineHeight: 1.5, color: 'var(--liq-fg)',
        textWrap: 'pretty',
      }}>
        {answer}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// PromptSheetChrome — drag handle + close X, shared by both sheets
// ─────────────────────────────────────────────────────────────
function PromptSheetChrome({ onClose }) {
  return (
    <>
      <div style={{
        margin: '0 auto', width: 40, height: 4, borderRadius: 4,
        background: 'rgba(29,17,41,0.18)',
      }}/>
      <button
        onClick={onClose}
        aria-label="Dismiss"
        style={{
          position: 'absolute', top: 16, right: 16,
          width: 36, height: 36, borderRadius: '50%',
          background: 'rgba(29,17,41,0.04)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'var(--liq-fg)',
        }}
      >
        <Icon name="x" size={20} stroke={1.8}/>
      </button>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// TopicPickerSheet — all 15, in three groups
//
// No longer the default path: this is what "Browse all 15 topics"
// opens. Topics already used are rendered disabled rather than
// hidden, so the list never changes length or order between visits.
// ─────────────────────────────────────────────────────────────
function TopicPickerSheet({ used = [], onPick, onClose }) {
  return (
    <div style={{
      width: '100%',
      background: 'var(--liq-bg)',
      borderTopLeftRadius: 32,
      borderTopRightRadius: 32,
      padding: '8px 0 0',
      boxShadow: '0 -20px 60px rgba(46,1,71,0.18), 0 -4px 12px rgba(46,1,71,0.08)',
      animation: 'sheet-rise 360ms cubic-bezier(.22,1,.36,1) both',
      position: 'relative',
      maxHeight: 600,
      display: 'flex', flexDirection: 'column',
    }}>
      <PromptSheetChrome onClose={onClose}/>

      <div style={{ padding: '14px 24px 12px' }}>
        <h2 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26,
          letterSpacing: '-0.018em', lineHeight: 1.1, color: 'var(--liq-fg)',
          margin: '6px 0 6px', textWrap: 'balance',
        }}>
          Choose a <em>topic</em>.
        </h2>
        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14,
          lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty',
        }}>
          Pick something you'd want a match to actually know.
        </p>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, overflow: 'auto',
        padding: '0 24px 4px',
        display: 'flex', flexDirection: 'column', gap: 8,
        maskImage: 'linear-gradient(180deg, #000 0%, #000 88%, transparent 100%)',
        WebkitMaskImage: 'linear-gradient(180deg, #000 0%, #000 88%, transparent 100%)',
      }}>
        {TOPIC_GROUPS.map((group, gi) => (
          <div key={gi} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{
              fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 10.5,
              letterSpacing: '0.08em', textTransform: 'uppercase',
              color: 'var(--liq-fg-subtle)',
              margin: gi === 0 ? '2px 0 0 2px' : '12px 0 0 2px',
            }}>{group.label}</div>
            {group.topics.map((t, i) => {
              const isUsed = used.indexOf(t) !== -1;
              return (
                <button key={i}
                  disabled={isUsed}
                  onClick={() => !isUsed && onPick && onPick(t)}
                  style={{
                    width: '100%', minHeight: 50,
                    borderRadius: 9999,
                    background: isUsed ? 'rgba(29,17,41,0.03)' : '#fff',
                    border: isUsed
                      ? '1.5px solid var(--liq-border)'
                      : '1.5px solid var(--liq-primary-500)',
                    padding: '10px 20px',
                    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                    gap: 12,
                    opacity: isUsed ? 0.55 : 1,
                    transition: 'background 180ms, transform 180ms',
                  }}
                  onMouseDown={e => { if (isUsed) return; e.currentTarget.style.transform = 'scale(0.985)'; e.currentTarget.style.background = 'var(--liq-lavender-50)'; }}
                  onMouseUp={e => { if (isUsed) return; e.currentTarget.style.transform = 'scale(1)'; e.currentTarget.style.background = '#fff'; }}
                  onMouseLeave={e => { if (isUsed) return; e.currentTarget.style.transform = 'scale(1)'; e.currentTarget.style.background = '#fff'; }}
                >
                  <span style={{
                    fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 14.5,
                    color: 'var(--liq-fg)', textAlign: 'left', lineHeight: 1.3,
                  }}>{t}</span>
                  <span style={{
                    flex: 'none',
                    color: isUsed ? 'var(--liq-fg-subtle)' : 'var(--liq-primary-500)',
                    fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 10.5,
                    letterSpacing: '0.06em', textTransform: 'uppercase',
                    display: 'inline-flex', alignItems: 'center',
                  }}>
                    {isUsed ? 'Used' : <Icon name="plus" size={16} stroke={2.4}/>}
                  </span>
                </button>
              );
            })}
          </div>
        ))}
        <div style={{ height: 18 }}/>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// WritePromptSheet — write your answer
//
// Variants:
//   'empty'   → example visible, empty field, floor hint, no counter.
//   'mid'     → ≈63-char draft. Violet focus border. Still no counter
//               (under 100) — the field is calm while writing.
//   'cap'     → at 160/160. Amber cap line, amber counter, amber
//               border. NOT the flow's danger card: the user wrote
//               to the end of the box, which is not a failure.
//               ('error' is accepted as a legacy alias.)
//   'nudge'   → Save pressed on an empty field. Inline hint, field
//               shakes. Save is never disabled (flow rule 2c).
// ─────────────────────────────────────────────────────────────
function WritePromptSheet({ variant = 'empty', topic = "On a first date, I usually…", onSave, onClose }) {
  const atCap = variant === 'cap' || variant === 'error';
  const initial = atCap
    ? FULL_DRAFT.slice(0, PROMPT_MAX)
    : variant === 'mid'
      ? MID_DRAFT
      : '';
  const [value, setValue] = useStatePrompts(initial);
  const [nudge, setNudge] = useStatePrompts(variant === 'nudge');
  const [exampleOpen, setExampleOpen] = useStatePrompts(true);
  const len = value.length;
  const isCap = len >= PROMPT_MAX;
  const isEmpty = len === 0;
  const showCounter = len >= PROMPT_COUNTER_FROM;

  const borderColor = isCap
    ? 'var(--liq-orange-500)'
    : nudge && isEmpty
      ? 'var(--liq-danger)'
      : isEmpty
        ? 'var(--liq-border)'
        : 'var(--liq-primary-500)';
  const halo = isCap
    ? '0 0 0 4px rgba(254,104,57,0.12)'
    : nudge && isEmpty
      ? '0 0 0 4px rgba(251,50,59,0.10)'
      : isEmpty
        ? 'none'
        : '0 0 0 4px rgba(129,42,236,0.10)';

  return (
    <div style={{
      width: '100%',
      background: 'var(--liq-bg)',
      borderTopLeftRadius: 32,
      borderTopRightRadius: 32,
      padding: '8px 24px 16px',
      boxShadow: '0 -20px 60px rgba(46,1,71,0.18), 0 -4px 12px rgba(46,1,71,0.08)',
      animation: 'sheet-rise 360ms cubic-bezier(.22,1,.36,1) both',
      position: 'relative',
      minHeight: 560,
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Inlined so the sheet runs standalone (design-canvas focus,
          spec sheet, preview page). Same keyframes as every other
          screen in the flow. */}
      <style>{`@keyframes su-shake {
        10%, 90% { transform: translateX(-2px); }
        20%, 80% { transform: translateX(3px); }
        30%, 50%, 70% { transform: translateX(-5px); }
        40%, 60% { transform: translateX(5px); }
      }`}</style>
      <PromptSheetChrome onClose={onClose}/>

      <h2 className="su-underlined" style={{
        fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 23,
        letterSpacing: '-0.018em', lineHeight: 1.18, color: 'var(--liq-fg)',
        margin: '14px 0 10px', paddingRight: 40,
        textWrap: 'balance',
      }}>
        {topic}
      </h2>

      {/* Worked example — the answer to "I don't know what to write".
          Dismissible, because a user who already knows does not need
          someone else's sentence in their eyeline. Kept above the
          field, not inside it as a placeholder: a placeholder
          disappears at the first keystroke, which is exactly when
          the user still wants it. */}
      {exampleOpen && (
        <div style={{
          display: 'flex', alignItems: 'flex-start', gap: 10,
          padding: '10px 12px 11px 14px',
          borderRadius: 14,
          background: 'var(--liq-lavender-50)',
          border: '1px solid rgba(129,42,236,0.14)',
          marginBottom: 12,
        }}>
          <div style={{ flex: 1 }}>
            <div style={{
              fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 10,
              letterSpacing: '0.08em', textTransform: 'uppercase',
              color: 'var(--liq-primary-500)', marginBottom: 3,
            }}>For example</div>
            <div style={{
              fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13,
              lineHeight: 1.45, color: 'var(--liq-neutral-200)', textWrap: 'pretty',
            }}>{exampleFor(topic)}</div>
          </div>
          <button
            onClick={() => setExampleOpen(false)}
            aria-label="Hide example"
            style={{
              flex: 'none', width: 22, height: 22, borderRadius: '50%',
              background: 'rgba(129,42,236,0.10)', color: 'var(--liq-primary-500)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            <Icon name="x" size={13} stroke={2.2}/>
          </button>
        </div>
      )}

      {/* Field. Border tracks state: idle → focus → cap (amber) or
          empty-submit (danger). Note the cap colour is the orange
          token, not the danger token — deliberate. */}
      <div style={{
        position: 'relative',
        background: '#fff',
        border: `1.5px solid ${borderColor}`,
        boxShadow: halo,
        borderRadius: 16,
        padding: '14px 16px 34px',
        transition: 'border-color 180ms, box-shadow 180ms',
        animation: (nudge && isEmpty) ? 'su-shake 480ms cubic-bezier(.36,.07,.19,.97) both' : undefined,
      }}>
        <textarea
          value={value}
          onChange={e => { setValue(e.target.value.slice(0, PROMPT_MAX)); if (nudge) setNudge(false); }}
          placeholder="Say it like you'd tell it to a friend…"
          rows={4}
          style={{
            width: '100%', minHeight: 116, border: 'none', outline: 'none',
            background: 'transparent', resize: 'none',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15.5,
            lineHeight: 1.5, color: 'var(--liq-fg)',
          }}
        />
        {/* Counter appears at 100, not at 0. Below that it is not
            information, it is a target. */}
        {showCounter && (
          <div style={{
            position: 'absolute', right: 14, bottom: 10,
            fontFamily: 'var(--liq-font-sans)', fontVariantNumeric: 'tabular-nums',
            fontWeight: 700, fontSize: 12, letterSpacing: 0.02,
            color: isCap ? 'var(--liq-orange-500)' : 'var(--liq-fg-subtle)',
          }}>
            {len}/{PROMPT_MAX}
          </div>
        )}
      </div>

      {/* Status line. One reserved row, three messages:
            calm  → the floor ("one good sentence is enough")
            cap   → amber, factual, no blame
            nudge → danger, only after an empty Save press */}
      <div aria-live="polite" style={{
        minHeight: 34, marginTop: 10,
        display: 'flex', alignItems: 'flex-start', gap: 8,
      }}>
        {nudge && isEmpty ? (
          <>
            <span style={{
              flex: 'none', width: 18, height: 18, borderRadius: '50%',
              background: 'var(--liq-danger)', color: '#fff',
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 12,
              lineHeight: 1, marginTop: 1,
            }}>!</span>
            <span style={{
              fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13.5,
              lineHeight: 1.4, color: 'var(--liq-danger-fg)',
            }}>Write a few words to save this prompt.</span>
          </>
        ) : isCap ? (
          <span style={{
            fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 13,
            lineHeight: 1.4, color: 'var(--liq-orange-500)',
          }}>That's the full 160 — short and specific lands harder anyway.</span>
        ) : (
          <span style={{
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13,
            lineHeight: 1.4, color: 'var(--liq-fg-subtle)',
          }}>One good sentence is enough.</span>
        )}
      </div>

      {/* Save — sunset CTA, full width, never disabled. An empty
          press produces the nudge above rather than doing nothing. */}
      <div style={{ marginTop: 10 }}>
        <Button variant="sunset" size="lg" fullWidth
          onClick={() => { if (isEmpty) { setNudge(true); return; } onSave && onSave(value); }}
        >Save</Button>
      </div>

      {/* Drafts survive dismissal — the sheet reopens with whatever
          was typed. Stated here because it is invisible in a mock. */}
      <div style={{ flex: 1 }}/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfilePrompts — main screen
//
// Props:
//   prompts        array of {topic, answer}
//   sheet          "topic" | "write" | undefined
//   sheetVariant   'empty' | 'mid' | 'cap' | 'nudge'
//   sheetTopic     topic shown inside the write sheet
// ─────────────────────────────────────────────────────────────
function ScreenProfilePrompts({ prompts = [], sheet, sheetVariant = 'empty', sheetTopic, showToast = false }) {
  const count = prompts.length;
  const canContinue = count >= 1;
  const [toastOpen, setToast] = React.useState(false);
  const toast = toastOpen || showToast; // showToast is artboard-only
  const toastTimer = React.useRef(null);
  React.useEffect(() => () => clearTimeout(toastTimer.current), []);
  const onContinue = () => {
    if (canContinue) return;
    setToast(true);
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(false), 2600);
  };

  // "The real you" is photos → prompts → media. Verify profile is out
  // of the MVP, so this bar is 3 segments and prompts is segment 2.
  const used = prompts.map(p => p.topic);
  const suggestions = SUGGESTED_TOPICS.filter(t => used.indexOf(t) === -1)
    .slice(0, count === 0 ? 3 : 2);

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      <style>{`@keyframes sheet-rise {
        from { transform: translateY(28px); opacity: 0.85; }
        to   { transform: translateY(0);    opacity: 1; }
      }`}</style>

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
        flex: 1, position: 'relative', zIndex: 1, overflow: 'auto',
        padding: '4px 24px 0',
      }}>
        <StepProgress steps={3} current={2} style={{ marginBottom: 20 }}/>

        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
          letterSpacing: '-0.018em', lineHeight: 1.08, color: 'var(--liq-fg)',
          margin: '0 0 8px', textWrap: 'balance',
        }}>
          Your space to share something <em>personal</em>.
        </h1>

        {/* The rule, stated as a floor. "One is enough" is the
            sentence that matters; the maximum is an afterthought. */}
        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
          lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty', maxWidth: 330,
        }}>
          One is enough to continue. Add up to 3 if you're enjoying yourself.
        </p>

        {count > 0 && (
          <div style={{ marginTop: 18, display: 'flex', flexDirection: 'column', gap: 12 }}>
            {prompts.map((p, i) => (
              <FilledPromptCard key={i} topic={p.topic} answer={p.answer}/>
            ))}
          </div>
        )}

        {count < 3 && (
          <>
            <div style={{
              marginTop: count === 0 ? 20 : 22, marginBottom: 10,
              fontFamily: 'var(--liq-font-sans)', fontWeight: 800, fontSize: 10.5,
              letterSpacing: '0.08em', textTransform: 'uppercase',
              color: 'var(--liq-fg-subtle)',
            }}>
              {count === 0 ? 'Start with one of these' : 'Add another · optional'}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {suggestions.map((t, i) => (
                <SuggestionCard key={i} topic={t}/>
              ))}
              <BrowseAllButton/>
            </div>
          </>
        )}

        <div style={{
          marginTop: 14, display: 'flex', justifyContent: 'flex-start',
          padding: '0 2px',
        }}>
          <span style={{
            fontFamily: 'var(--liq-font-sans)', fontSize: 13, fontWeight: 600,
            color: count >= 1 ? 'var(--liq-success-fg)' : 'var(--liq-fg-subtle)',
            whiteSpace: 'nowrap',
          }}>
            {count}/3 prompts{count >= 1 ? ' · enough to continue' : ''}
          </span>
        </div>

        <div style={{ height: 20 }}/>
      </div>

      <div style={{
        position: 'relative', zIndex: 2, flex: 'none',
        padding: '12px 24px 14px',
        display: 'flex', justifyContent: 'flex-end', alignItems: 'center',
        gap: 14,
      }}>
        <div style={{
          position: 'absolute', left: 24, right: 24, bottom: '100%',
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
            <Icon name="edit" size={15} stroke={2.2}/>
            Write 1 prompt to continue
          </div>
        </div>

        <div onClick={onContinue}>
          <NextButton label="Continue" color="orange" size={52}/>
        </div>
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>

      {sheet && (
        <>
          <div style={{
            position: 'absolute', inset: 0, zIndex: 4,
            background: 'rgba(29,17,41,0.42)',
            backdropFilter: 'blur(1.5px)',
          }}/>
          <div style={{
            position: 'absolute', left: 0, right: 0, bottom: 0,
            zIndex: 5,
          }}>
            {sheet === 'topic'
              ? <TopicPickerSheet used={used}/>
              : <WritePromptSheet variant={sheetVariant} topic={sheetTopic}/>
            }
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
  ScreenProfilePrompts,
  TopicPickerSheet,
  WritePromptSheet,
  FilledPromptCard,
  SuggestionCard,
  BrowseAllButton,
  PROMPT_TOPICS,
  TOPIC_GROUPS,
  SUGGESTED_TOPICS,
  PROMPT_EXAMPLES,
  SAMPLE_PROMPTS,
  PROMPT_MAX,
});
