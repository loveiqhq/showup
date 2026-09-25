// screen-onboarding-reference.jsx — Show Up tutorial (5 cards)
//
// This is the DESIGN REFERENCE the five tutorial tickets were written from.
// It is the same code that renders the attached spec-sheet PNGs, so every
// number in those sheets exists here as a real value. Read values from this
// file rather than measuring the PNG.
//
// It is NOT the implementation. It is React + inline styles in a browser
// prototype; the app is iOS-first. Port the structure and the values, not
// the file.
//
// What matters, in order:
//   1. ScreenOnboarding() below IS the card shell — progress, eyebrow,
//      headline, body slot, art block, spacer, nav row. Build it once.
//      Cards 02-05 change content only.
//   2. The single <div style={{ flex: 1 }}/> spacer is the whole layout
//      strategy. Nothing is positioned by Y offset.
//   3. ONBOARD_CARDS[] is the content payload: tag, title, body, art,
//      and (card 05 only) artScale.
//   4. Card 05 sets artScale: 0.62 because its text block is 269px tall
//      and does not otherwise fit. On frames shorter than 844 the art
//      must shrink further — it is the only flexible element.
//
// Depends on: StepProgress, Eyebrow, NextButton, Icon, Phone
//   → design_handoff_showup/components/shared.jsx
// Tokens (--liq-*, --su-*): design_handoff_showup/tokens/colors_and_type.css
//
// Card index → ticket:
//   0 → 01 Meet in real life        1 → 02 Match on availability
//   2 → 03 Match means meet         3 → 04 30 minutes
//   4 → 05 Show up, every time (final — terminal CTA)

const { useState } = React;

const ONBOARD_CARDS = [
  {
    tag: 'Meet PEOPLE in real life',
    title: <>We want you to <em>actually</em> meet.</>,
    body: (
      <>
        <span style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
          {[
            ['No texting for weeks', 'date in real life instead'],
            ['No ghosting', 'we penalize unreliability'],
            ['No collecting matches', 'you meet who you match'],
          ].map(([neg, pos]) => (
            <span key={neg} style={{ display: 'flex', alignItems: 'flex-start', gap: 9 }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--liq-orange-500)', flex: 'none', marginTop: 6 }}/>
              <span style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 'var(--liq-size-sm)', lineHeight: 1.4, letterSpacing: '-0.01em', whiteSpace: 'nowrap', color: 'var(--liq-fg)' }}>{neg}<span style={{ color: 'var(--liq-fg-faint)', fontWeight: 500 }}> — </span><span style={{ color: 'var(--liq-primary-500)' }}>{pos}</span></span>
            </span>
          ))}
        </span>
        <span style={{ display: 'block', marginBottom: 10 }}>If you match here, you <em style={{ fontStyle: 'italic', fontWeight: 700 }}>will meet</em> in real life. A match is a committed date — not a maybe.</span>
        <span style={{ display: 'block' }}><strong style={{ fontWeight: 700 }}>Showing up to dates boosts your profile</strong> by highlighting your reliability and increasing your visibility. <strong style={{ fontWeight: 700 }}>Missing dates without fair notice upfront</strong> reduces your visibility for others.</span>
      </>
    ),
    art: 'meet',
  },
  {
    tag: 'Match on availability',
    title: <>Match people who are <em>free to date</em> when you are.</>,
    body: (
      <span style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {[
          ['Visible only when you’re free to date', 'check in and state your available times to meet'],
          ['Synchronised schedules', 'we only show you people to date who are available when you are'],
          ['Different day, different vibe', 'match on what you’re in the mood for right now, not a static bio'],
        ].map(([label, rest]) => (
          <span key={label} style={{ display: 'flex', alignItems: 'flex-start', gap: 9 }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--liq-orange-500)', flex: 'none', marginTop: 6 }}/>
            <span style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 'var(--liq-size-sm)', lineHeight: 1.4, letterSpacing: '-0.01em', color: 'var(--liq-fg)' }}>{label}<span style={{ color: 'var(--liq-fg-faint)', fontWeight: 500 }}> — </span><span style={{ color: 'var(--liq-primary-500)' }}>{rest}</span></span>
          </span>
        ))}
      </span>
    ),
    art: 'clock',
  },
  {
    tag: 'Match means meet',
    title: <>A match is a <em>binding</em> date.</>,
    body: (
      <span style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {[
          ['You decide who you like', 'if you match, you will meet'],
          ['We suggest the time', 'a date and time that works for both of your schedules'],
          ['We pick the place', 'a safe, public spot halfway between you'],
        ].map(([label, rest]) => (
          <span key={label} style={{ display: 'flex', alignItems: 'flex-start', gap: 9 }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--liq-orange-500)', flex: 'none', marginTop: 6 }}/>
            <span style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 'var(--liq-size-sm)', lineHeight: 1.4, letterSpacing: '-0.01em', color: 'var(--liq-fg)' }}>{label}<span style={{ color: 'var(--liq-fg-faint)', fontWeight: 500 }}> — </span><span style={{ color: 'var(--liq-primary-500)' }}>{rest}</span></span>
          </span>
        ))}
      </span>
    ),
    art: 'shield',
  },
  {
    tag: '30 minutes, no pressure',
    title: <>Just <em>thirty minutes</em>.</>,
    body: (
      <span style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {[
          ['Low-pressure 30-minute dates', 'quick, relaxed meetups to see if you click in real life'],
          ['30 minutes up', 'stay if you’re vibing, or leave with a smile — no hard feelings'],
          ['Built-in icebreakers', 'fun, easy prompts to keep the conversation flowing'],
        ].map(([label, rest]) => (
          <span key={label} style={{ display: 'flex', alignItems: 'flex-start', gap: 9 }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--liq-orange-500)', flex: 'none', marginTop: 6 }}/>
            <span style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 'var(--liq-size-sm)', lineHeight: 1.4, letterSpacing: '-0.01em', color: 'var(--liq-fg)' }}>{label}<span style={{ color: 'var(--liq-fg-faint)', fontWeight: 500 }}> — </span><span style={{ color: 'var(--liq-primary-500)' }}>{rest}</span></span>
          </span>
        ))}
      </span>
    ),
    art: 'thirty',
  },
  {
    tag: 'Show up, every time',
    title: <>If you don't show up, <em>there's a cost</em>.</>,
    body: (
      <span style={{ display: 'flex', flexDirection: 'column', gap: 11 }}>
        {[
          'Every profile has a Show-up Rate.',
          'Showing up to dates is reflected positively.',
          'Not showing up is reflected negatively.',
          'A persistently low Show-up Rate reduces your visibility to others.',
          'Miss a date without fair notice and you can’t search for new dates for 24 hours.',
        ].map((t) => (
          <span key={t} style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
            <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--liq-orange-500)', flex: 'none', marginTop: 7 }}/>
            <span style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 14.5, lineHeight: 1.42, color: 'var(--liq-fg)' }}>{t}</span>
          </span>
        ))}
        <span style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5, lineHeight: 1.5,
          color: 'var(--liq-neutral-200)', marginTop: 5, textWrap: 'pretty',
        }}>
          <span style={{ fontWeight: 700, color: 'var(--liq-fg)' }}>Show Up is for reliable people.</span> Life happens. Stay fair and show respect for each other, and your Show-up Rate will reflect it.
        </span>
      </span>
    ),
    art: 'commit',
    artScale: 0.62,
  },
];

function OnboardArt({ kind }) {
  // Painterly editorial illustrations drawn with CSS — no clipart.
  if (kind === 'meet') return (
    <div style={{ position: 'relative', height: 230, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{
        position: 'absolute', width: 220, height: 190,
        background: 'radial-gradient(circle, rgba(254,104,57,0.16) 0%, rgba(129,42,236,0.10) 48%, rgba(0,0,0,0) 70%)',
        filter: 'blur(6px)',
      }}/>
      <svg width="248" height="210" viewBox="0 0 248 210" fill="none" style={{ position: 'relative', overflow: 'visible' }}>
        <defs>
          <linearGradient id="meetSun" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#FE6839"/>
            <stop offset="100%" stopColor="#E0507A"/>
          </linearGradient>
          <linearGradient id="meetVio" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#A855F7"/>
            <stop offset="100%" stopColor="#7118D6"/>
          </linearGradient>
          <linearGradient id="meetHeart" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#FE6839"/>
            <stop offset="55%" stopColor="#D05976"/>
            <stop offset="100%" stopColor="#812AEC"/>
          </linearGradient>
          <clipPath id="meetClipL"><circle cx="60" cy="152" r="41"/></clipPath>
          <clipPath id="meetClipR"><circle cx="188" cy="152" r="41"/></clipPath>
        </defs>

        {/* two people leaning toward one another */}
        <path d="M 90 126 Q 108 98 122 86" stroke="#E9C4D2" strokeWidth="3.5"
          strokeLinecap="round" strokeDasharray="0.5 11" fill="none"/>
        <path d="M 158 126 Q 140 98 126 86" stroke="#E9C4D2" strokeWidth="3.5"
          strokeLinecap="round" strokeDasharray="0.5 11" fill="none"/>

        {/* left avatar — turned inward */}
        <circle cx="60" cy="152" r="41" fill="url(#meetSun)"/>
        <g clipPath="url(#meetClipL)" fill="#FFFBF7">
          <circle cx="66" cy="143" r="14"/>
          <ellipse cx="66" cy="189" rx="25" ry="21"/>
        </g>

        {/* right avatar — turned inward */}
        <circle cx="188" cy="152" r="41" fill="url(#meetVio)"/>
        <g clipPath="url(#meetClipR)" fill="#FFFBF7">
          <circle cx="182" cy="143" r="14"/>
          <ellipse cx="182" cy="189" rx="25" ry="21"/>
        </g>

        {/* a heart blooming where they meet */}
        <path d="M124 96 C102 76 84 63 84 44 C84 30 95 22 106 22 C114 22 121 27 124 34 C127 27 134 22 142 22 C153 22 164 30 164 44 C164 63 146 76 124 96 Z" fill="url(#meetHeart)"/>
        {/* little sparks of chemistry */}
        <path d="M172 24 l2.4 6 6 2.4 -6 2.4 -2.4 6 -2.4 -6 -6 -2.4 6 -2.4 Z" fill="#FBBF7A"/>
        <circle cx="80" cy="30" r="3.2" fill="#C9A6E8"/>
        <circle cx="150" cy="104" r="2.6" fill="#F0A98A"/>
      </svg>
    </div>
  );
  if (kind === 'clock') {
    const circ = (r) => 2 * Math.PI * r;
    return (
    <div style={{ position: 'relative', height: 230, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      {/* soft brand glow */}
      <div style={{
        position: 'absolute', width: 210, height: 210, borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(254,104,57,0.18) 0%, rgba(129,42,236,0.10) 48%, rgba(0,0,0,0) 70%)',
        filter: 'blur(4px)',
      }}/>
      <svg width="200" height="200" viewBox="0 0 200 200" style={{ position: 'relative', overflow: 'visible' }}>
        <defs>
          <linearGradient id="clkHeart" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#FE6839"/>
            <stop offset="100%" stopColor="#812AEC"/>
          </linearGradient>
        </defs>
        {/* minute ticks around the dial */}
        {Array.from({ length: 12 }).map((_, i) => {
          const a = (i / 12) * 2 * Math.PI - Math.PI / 2;
          const r1 = 96, r2 = i % 3 === 0 ? 88 : 92;
          return (
            <line key={i}
              x1={100 + r1 * Math.cos(a)} y1={100 + r1 * Math.sin(a)}
              x2={100 + r2 * Math.cos(a)} y2={100 + r2 * Math.sin(a)}
              stroke="var(--liq-border)" strokeWidth={i % 3 === 0 ? 3 : 1.5} strokeLinecap="round"/>
          );
        })}
        {/* your availability — outer sunset arc */}
        <circle cx="100" cy="100" r="84" stroke="var(--liq-border)" strokeWidth="9" fill="none" opacity="0.5"/>
        <circle cx="100" cy="100" r="84" stroke="#FE6839" strokeWidth="9" fill="none"
          strokeDasharray={circ(84)} strokeDashoffset={circ(84) * (1 - 0.5)} strokeLinecap="round"
          transform="rotate(-125 100 100)"/>
        {/* their availability — inner violet arc, overlapping yours at the top */}
        <circle cx="100" cy="100" r="67" stroke="var(--liq-border)" strokeWidth="9" fill="none" opacity="0.4"/>
        <circle cx="100" cy="100" r="67" stroke="#812AEC" strokeWidth="9" fill="none"
          strokeDasharray={circ(67)} strokeDashoffset={circ(67) * (1 - 0.44)} strokeLinecap="round"
          transform="rotate(-45 100 100)"/>
        {/* the matched moment — a heart where both windows meet */}
        <g transform="translate(100 24)">
          <circle r="14" fill="#FFFBF7"/>
          <path d="M0 6.5 C-6.5 1.5 -11 -2 -11 -6.5 C-11 -9.5 -8 -12 -4.5 -12 C-2 -12 -0.6 -10 0 -8.6 C0.6 -10 2 -12 4.5 -12 C8 -12 11 -9.5 11 -6.5 C11 -2 6.5 1.5 0 6.5 Z" fill="url(#clkHeart)"/>
        </g>
        {/* centre label */}
        <text x="100" y="104" textAnchor="middle" style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 42, fill: 'var(--liq-fg)' }}>48<tspan style={{ fontSize: 22 }}>h</tspan></text>
        <text x="100" y="126" textAnchor="middle" style={{ fontFamily: 'Manrope', fontWeight: 600, fontSize: 10.5, letterSpacing: '0.055em', fill: 'var(--liq-fg-subtle)' }}>WHEN YOU'RE BOTH FREE</text>
      </svg>
    </div>
  );
  }
  if (kind === 'shield') return (
    <div style={{ position: 'relative', height: 230, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      {/* Soft brand glow behind the mark */}
      <div style={{
        position: 'absolute', width: 230, height: 180,
        background: 'radial-gradient(circle, rgba(254,104,57,0.18) 0%, rgba(129,42,236,0.13) 46%, rgba(0,0,0,0) 70%)',
        filter: 'blur(6px)',
      }}/>
      {/* Two interlocked rings — a binding union between two people.
          The violet ring is masked at the top intersection so the sunset
          ring reads OVER it there, while the violet passes over at the
          bottom — a proper woven interlink. */}
      <svg width="234" height="200" viewBox="0 0 234 200" fill="none"
        style={{ position: 'relative', overflow: 'visible' }}>
        <defs>
          <linearGradient id="bindSun" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#FE6839"/>
            <stop offset="100%" stopColor="#E0507A"/>
          </linearGradient>
          <linearGradient id="bindVio" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#A855F7"/>
            <stop offset="100%" stopColor="#7118D6"/>
          </linearGradient>
          <mask id="bindWeave">
            <rect x="0" y="0" width="234" height="200" fill="white"/>
            <circle cx="117" cy="61" r="14" fill="black"/>
          </mask>
        </defs>
        {/* left ring — sunset (drawn first / under) */}
        <circle cx="93" cy="100" r="46" stroke="url(#bindSun)" strokeWidth="15" fill="none"/>
        {/* right ring — violet, woven via mask */}
        <circle cx="141" cy="100" r="46" stroke="url(#bindVio)" strokeWidth="15" fill="none" mask="url(#bindWeave)"/>
        {/* a small heart sealing the union at the lower link */}
        <g transform="translate(117 139)">
          <circle r="17" fill="#FFFBF7"/>
          <path d="M0 8 C-8 2 -13 -2.5 -13 -8 C-13 -11.5 -9.5 -14.5 -5.5 -14.5 C-2.5 -14.5 -0.7 -12 0 -10.5 C0.7 -12 2.5 -14.5 5.5 -14.5 C9.5 -14.5 13 -11.5 13 -8 C13 -2.5 8 2 0 8 Z" fill="url(#bindSun)"/>
        </g>
        {/* spark above the union */}
        <path d="M117 26 l2.4 6 6 2.4 -6 2.4 -2.4 6 -2.4 -6 -6 -2.4 6 -2.4 Z" fill="#C9A6E8"/>
      </svg>
    </div>
  );
  if (kind === 'thirty') return (
    <div style={{ position: 'relative', height: 230, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{
        position: 'absolute', width: 210, height: 210, borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(254,104,57,0.16) 0%, rgba(129,42,236,0.10) 48%, rgba(0,0,0,0) 70%)',
        filter: 'blur(4px)',
      }}/>
      <svg width="200" height="200" viewBox="0 0 200 200" style={{ position: 'relative', overflow: 'visible' }}>
        <defs>
          <linearGradient id="thrRing" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#FE6839"/>
            <stop offset="100%" stopColor="#812AEC"/>
          </linearGradient>
          <linearGradient id="thrHeart" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#FE6839"/>
            <stop offset="100%" stopColor="#D05976"/>
          </linearGradient>
        </defs>
        {/* half-hour progress ring */}
        <circle cx="100" cy="100" r="84" stroke="var(--liq-border)" strokeWidth="4" fill="none"/>
        <circle cx="100" cy="100" r="84" stroke="url(#thrRing)" strokeWidth="6" fill="none"
          strokeDasharray={2 * Math.PI * 84}
          strokeDashoffset={2 * Math.PI * 84 * 0.5}
          strokeLinecap="round"
          transform="rotate(-90 100 100)"/>
        {/* steam curls rising into a little heart — a coffee-length date */}
        <path d="M84 62 C78 54 90 50 84 42" stroke="#C9A6E8" strokeWidth="3.4" strokeLinecap="round" fill="none" opacity="0.85"/>
        <path d="M116 62 C122 54 110 50 116 42" stroke="#F0A98A" strokeWidth="3.4" strokeLinecap="round" fill="none" opacity="0.85"/>
        <path d="M100 56 C96 51 88.5 51 88.5 46 C88.5 43 91 41 93.5 41 C95.5 41 96.8 42.4 100 45.2 C103.2 42.4 104.5 41 106.5 41 C109 41 111.5 43 111.5 46 C111.5 51 104 51 100 56 Z" fill="url(#thrHeart)"/>
        {/* coffee cup */}
        <path d="M74 74 L126 74 L121 104 A14 14 0 0 1 107 116 L93 116 A14 14 0 0 1 79 104 Z" fill="url(#thrRing)"/>
        <path d="M126 80 L133 80 A11 11 0 0 1 133 102 L129 102" stroke="url(#thrRing)" strokeWidth="6" fill="none" strokeLinecap="round"/>
        <ellipse cx="100" cy="74" rx="26" ry="6" fill="#FFFBF7" opacity="0.92"/>
        {/* label */}
        <text x="100" y="152" textAnchor="middle" style={{ fontFamily: 'Lora', fontWeight: 700, fontSize: 32, fill: 'var(--liq-fg)' }}>30<tspan style={{ fontFamily: 'Manrope', fontWeight: 600, fontSize: 15 }}> min</tspan></text>
      </svg>
    </div>
  );
  if (kind === 'commit') return (
    <div style={{ position: 'relative', height: 230, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{
        position: 'absolute', width: 210, height: 210, borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(254,104,57,0.16) 0%, rgba(129,42,236,0.10) 48%, rgba(0,0,0,0) 70%)',
        filter: 'blur(4px)',
      }}/>
      <svg width="200" height="200" viewBox="0 0 200 200" style={{ position: 'relative', overflow: 'visible' }}>
        <defs>
          <linearGradient id="cmtRing" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#00C46A"/>
            <stop offset="100%" stopColor="#0A9E5A"/>
          </linearGradient>
          <linearGradient id="cmtHeart" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#FE6839"/>
            <stop offset="55%" stopColor="#D05976"/>
            <stop offset="100%" stopColor="#812AEC"/>
          </linearGradient>
        </defs>
        {/* the Show-up Rate ring that guards every connection */}
        <circle cx="100" cy="100" r="82" stroke="var(--liq-border)" strokeWidth="9" fill="none"/>
        <circle cx="100" cy="100" r="82" stroke="url(#cmtRing)" strokeWidth="9" fill="none"
          strokeDasharray={2 * Math.PI * 82}
          strokeDashoffset={2 * Math.PI * 82 * 0.08}
          strokeLinecap="round"
          transform="rotate(-90 100 100)"/>
        {/* the connection it protects */}
        <path d="M100 140 C71 115 45 96 45 67 C45 48 60 36 77 36 C89 36 97 43 100 50 C103 43 111 36 123 36 C140 36 155 48 155 67 C155 96 129 115 100 140 Z" fill="url(#cmtHeart)"/>
        {/* kept-your-word seal */}
        <g transform="translate(100 92)">
          <circle r="21" fill="#FFFBF7"/>
          <path d="M-9.5 1 L-3 8 L10.5 -8.5" stroke="#0A9E5A" strokeWidth="5.2" fill="none" strokeLinecap="round" strokeLinejoin="round"/>
        </g>
      </svg>
    </div>
  );
  return null;
}

function ScreenOnboarding({ initialIndex = 0 }) {
  const [i, setI] = useState(initialIndex);
  const card = ONBOARD_CARDS[i];
  const last = i === ONBOARD_CARDS.length - 1;
  return (
    <Phone>
      <div style={{ padding: '8px 24px 0', display: 'flex', flexDirection: 'column', height: '100%' }}>
        <StepProgress steps={5} current={i + 1} style={{ marginBottom: 24 }}/>

        <Eyebrow>{card.tag}</Eyebrow>

        <h2 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 34,
          letterSpacing: '-0.015em', lineHeight: 1.1, color: 'var(--liq-fg)',
          margin: '14px 0 28px', textWrap: 'pretty',
        }}>
          {card.title}
        </h2>

        <div style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 16,
          lineHeight: 1.5, color: 'var(--liq-neutral-200)', margin: 0, textWrap: 'pretty',
        }}>{card.body}</div>

        <div style={{ marginTop: 24, height: card.artScale ? 230 * card.artScale : undefined, display: 'flex', justifyContent: 'center' }}>
          <div style={card.artScale ? { transform: `scale(${card.artScale})`, transformOrigin: 'top center' } : undefined}>
            <OnboardArt kind={card.art}/>
          </div>
        </div>

        <div style={{ flex: 1 }}/>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24, gap: 12 }}>
          <button onClick={() => setI(Math.max(0, i - 1))} style={{
            fontSize: 14, fontWeight: 600, color: i === 0 ? 'transparent' : 'var(--liq-fg-subtle)',
            pointerEvents: i === 0 ? 'none' : 'auto',
          }}>Back</button>
          <NextButton
            label={last ? "I'm ready to show up" : 'Next'}
            color={last ? 'sunset' : 'orange'}
            onClick={() => setI(Math.min(ONBOARD_CARDS.length - 1, i + 1))}
          />
        </div>
      </div>
    </Phone>
  );
}
