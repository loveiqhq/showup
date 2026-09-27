// screen-reachability-reference.jsx — Profile creation 10 · Stay reachable
//
// Design reference for [Profile 10] Stay reachable — MVP. Read values from
// HERE, not the PNG. Extracted verbatim from the Show Up UI kit
// (ui_kits/show-up/screens-profile-reachability.jsx). Reference, not
// production code — recreate it in the app's own patterns. Do not ship it.
//
// ─────────────────────────────────────────────────────────────
// WHAT MATTERS HERE — read before the kit's own header below
// ─────────────────────────────────────────────────────────────
//
// BUILD scope="mvp". ONLY. The file renders two scopes:
//   <ScreenProfileReachability scope="mvp"/>                       state A
//   <ScreenProfileReachability scope="mvp" initialInterest={{ai_call:true, whatsapp:true}}/>  state B
//   <ScreenProfileReachability scope="mvp" confirmOff="notifications"/>  state C
//   <ScreenProfileReachability scope="mvp" confirmOff="notifications" afterDenial/>  state D
// scope="full" (the concierge-call card, the calendar card, the phone field,
// WhatsApp / SMS / email as live toggles) is the LATER scope — backlog ticket
// 10-reachability-later.md. It is kept here so nothing is lost. Do not build it.
//
// The kit header below describes the FULL scope and is superseded for MVP on
// three points: push defaults ON (opt-out, decided 25 Sep 2026); there is no skip;
// and the phone / calendar / concierge items do not exist in MVP.
//
// KNOWN KIT SHORTCUTS — do not copy them:
//   - ConsentToggle moves its thumb with justify-content, so the transform
//     transition never runs. Build a real switch whose thumb animates.
//   - ConsentToggle is 51x31. Its hit target must be >= 44x44 (pad it).
//   - The OS sheet and the Settings app are never drawn here — they are the OS's.
// ─────────────────────────────────────────────────────────────

// screens-profile-reachability.jsx — "Stay reachable for a match" consent
//
// Sits inside Profile creation, right after the Notifications permission
// screen. Show Up's whole promise is that a match means a real date at a
// real time and place — so the moment a match lands, the app has to be
// able to *reach* the user, even when they're not in the app. This screen
// collects the three channels that make that possible:
//
//   1. Phone number  — so our AI concierge ("Sunny") can call the user
//                       when a match lands and read out the time + place.
//   2. Calendar       — so the confirmed date drops straight into their
//                       calendar with a reminder.
//   3. Notifications  — push alerts for matches, reminders and changes.
//
// ─────────────────────────────────────────────────────────────
// GDPR / legal orientation  (this is the reason the screen looks the
// way it does — the design IS the compliance)
// ─────────────────────────────────────────────────────────────
//  • GRANULAR CONSENT. Each purpose is a separate toggle. The user can
//    say yes to calendar and no to phone calls — consent is not bundled
//    (GDPR Art. 7(2), Art. 6(1)(a); EDPB Guidelines 05/2020 §42–44).
//  • OPT-IN, NOT PRE-TICKED. Every toggle defaults to OFF. Consent must
//    be an affirmative act — no pre-checked boxes (Recital 32; Planet49).
//  • SPECIFIC + INFORMED. Each card states exactly what data is used and
//    for what single purpose, in plain language, before the toggle.
//  • FREELY GIVEN. The primary CTA works with zero toggles on, and a
//    "Skip for now" escape is always present. Access to the app is not
//    conditional on any of these consents.
//  • RIGHT TO WITHDRAW. The footer states — and it is true in Settings —
//    that any of these can be turned off at any time, as easily as on.
//  • PURPOSE LIMITATION. Footer is explicit: data is used only to
//    coordinate dates, never for marketing or sold on.
//  • TRANSPARENCY. A Privacy Policy link sits in the footer.
//
// ─────────────────────────────────────────────────────────────
// Visual orientation — same template family as ScreenProfileNotifications
// and ScreenProfileLocation: warm-paper canvas, orange/violet orbs, peach
// top wash, dark Lora headline with a single italic-underline flourish,
// Manrope body. The difference: consent CARDS (not plain rows), each with
// an iOS-style toggle, so the affirmative action reads unmistakably.
//
// Exposed on window:
//   - ScreenProfileReachability — main screen
//   - ConsentToggle             — the iOS-style switch primitive
//   - ReachConsentCard          — one consent card
//   - REACH_CONSENTS            — the three consent definitions

const { useState: useStateReach } = React;

// ─────────────────────────────────────────────────────────────
// ConsentToggle — iOS-style switch in brand colors. Off = neutral
// track (the GDPR default); on = success green (learned "active"
// convention). 51×31 like the
// system control so it reads as a real permission switch.
// ─────────────────────────────────────────────────────────────
function ConsentToggle({ on, onChange, label }) {
  return (
    <button
      role="switch"
      aria-checked={on}
      aria-label={label}
      onClick={() => onChange && onChange(!on)}
      style={{
        flex: 'none',
        width: 51, height: 31, borderRadius: 9999,
        padding: 2,
        display: 'flex', alignItems: 'center',
        justifyContent: on ? 'flex-end' : 'flex-start',
        background: on ? 'var(--liq-success)' : 'rgba(29,17,41,0.14)',
        boxShadow: on ? '0 4px 12px rgba(0,171,85,0.32)' : 'inset 0 0 0 1px rgba(29,17,41,0.04)',
        transition: 'background 220ms cubic-bezier(.22,1,.36,1)',
        cursor: 'pointer',
      }}
    >
      <span style={{
        width: 27, height: 27, borderRadius: 9999, background: '#fff',
        boxShadow: '0 2px 5px rgba(20,12,28,0.28)',
        transition: 'transform 220ms cubic-bezier(.22,1,.36,1)',
      }}/>
    </button>
  );
}

// ─────────────────────────────────────────────────────────────
// REACH_CONSENTS — the three channels. `key` drives state,
// `purpose` is the GDPR-required specific-purpose sentence,
// `legal` is the fine-print legal basis / data-handling line.
// ─────────────────────────────────────────────────────────────
// The two channels that carry their OWN distinct purpose (a live call; a
// calendar write) keep full cards. The rest are ordinary alert channels that
// all deliver the SAME information — so their shared purpose is stated once,
// centrally, and each channel is just a labelled toggle.
const REACH_PRIMARY = [
  {
    key: 'phone',
    icon: 'phone-call',
    title: 'AI concierge call',
    purpose: 'Our AI concierge gives you a quick call to notify you about dates happening, meet time or location changes and date cancellations. No speaking, just listening. No marketing calls, ever - promised!',
    legal: '',
    hasPhone: true,
  },
  {
    key: 'calendar',
    icon: 'calendar',
    title: 'Add dates to your calendar',
    purpose: 'We automatically add your dates to your calendar, incl. time, place and a reminder. We never read out your calendar.',
    legal: '',
  },
];

// Alert channels — all deliver the same content, differ only in medium.
const NOTIFY_CHANNELS = [
  { key: 'notifications', icon: 'bell',           title: 'Push notifications', recommended: true },
  { key: 'whatsapp',      icon: 'whatsapp',        title: 'WhatsApp' },
  { key: 'sms',           icon: 'message-circle',  title: 'SMS' },
  { key: 'email',         icon: 'mail',            title: 'Email' },
];

// MVP scope (25 Sep 2026): push is the only live channel. These rows are a
// DEMAND TEST — checkboxes that record interest only. No delivery logic, no
// phone-number field, no dialog. Default unchecked.
const INTEREST_CHANNELS = [
  { key: 'ai_call',  icon: 'phone-call',     title: 'Phone call from our AI assistant', sub: 'A short automated call when something changes.' },
  { key: 'whatsapp', icon: 'whatsapp',       title: 'WhatsApp' },
  { key: 'sms',      icon: 'message-circle', title: 'SMS' },
];

// Kept for the deactivation-confirmation dialog's key→meta lookup.
const REACH_CONSENTS = [...REACH_PRIMARY, ...NOTIFY_CHANNELS];

// ─────────────────────────────────────────────────────────────
// PhoneInputInline — revealed under the phone card when its toggle
// is on. Country-code prefix + number. Purely for the design pass;
// the real product validates E.164 and verifies via the existing
// code-entry screen.
// ─────────────────────────────────────────────────────────────
function PhoneInputInline({ value, onChange }) {
  const [focus, setFocus] = useStateReach(false);
  return (
    <div style={{
      marginTop: 14,
      display: 'flex', alignItems: 'center', gap: 10,
      height: 50, padding: '0 6px 0 14px',
      borderRadius: 12, background: '#fff',
      border: `1.5px solid ${focus ? 'var(--liq-primary-500)' : 'var(--liq-border)'}`,
      transition: 'border-color 180ms',
    }}>
      <span style={{
        display: 'flex', alignItems: 'center', gap: 6,
        paddingRight: 10, borderRight: '1px solid var(--liq-border)',
        fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 15,
        color: 'var(--liq-fg)', whiteSpace: 'nowrap',
      }}>
        <span style={{ fontSize: 16 }}>🇩🇪</span> +49
      </span>
      <input
        value={value || ''}
        onChange={e => onChange && onChange(e.target.value)}
        onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
        placeholder="151 234 5678"
        inputMode="tel"
        style={{
          flex: 1, minWidth: 0, border: 'none', outline: 'none', background: 'transparent',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 16, color: 'var(--liq-fg)',
        }}
      />
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ReachConsentCard — icon pip + title + toggle, purpose sentence,
// a folded legal-basis line, and (phone only) the revealed input.
// The whole card lifts to a soft violet tint + violet hairline when
// its consent is on, so granted state is legible at a glance.
// ─────────────────────────────────────────────────────────────
function ReachConsentCard({ icon, title, purpose, legal, on, onToggle, hasPhone, phone, onPhone }) {
  return (
    <div style={{
      borderRadius: 18,
      background: on ? 'rgba(167,139,250,0.10)' : 'var(--liq-bg-elevated)',
      border: `1px solid ${on ? 'rgba(129,42,236,0.28)' : 'var(--liq-border-soft)'}`,
      boxShadow: on ? 'none' : 'var(--liq-shadow-md)',
      padding: 16,
      transition: 'background 220ms, border-color 220ms',
    }}>
      <div style={{ display: 'flex', gap: 13, alignItems: 'flex-start' }}>
        <div style={{
          flex: 'none',
          width: 40, height: 40, borderRadius: 12,
          background: 'var(--su-grad-lilac)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'var(--liq-primary-500)',
        }}>
          <Icon name={icon} size={20} stroke={1.8}/>
        </div>

        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            display: 'flex', alignItems: 'center', gap: 10,
          }}>
            <div style={{
              flex: 1, minWidth: 0,
              fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 16.5,
              color: 'var(--liq-fg)', lineHeight: 1.2, letterSpacing: '-0.005em',
              textWrap: 'balance',
            }}>
              {title}
            </div>
            <ConsentToggle on={on} onChange={onToggle} label={title}/>
          </div>

          <div style={{
            marginTop: 6,
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13,
            lineHeight: 1.42, color: 'var(--liq-neutral-200)', textWrap: 'pretty',
          }}>
            {purpose}
          </div>

          {/* Legal-basis fine print — always visible, muted, framed with
              a lock so the user clocks the register. This is the "informed"
              half of informed consent. */}
          {legal && <div style={{
            marginTop: 9, display: 'flex', gap: 7, alignItems: 'flex-start',
            color: 'var(--liq-fg-subtle)',
          }}>
            <div style={{ flex: 'none', marginTop: 1 }}>
              <Icon name="lock" size={12} stroke={1.9}/>
            </div>
            <div style={{
              fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 11.5,
              lineHeight: 1.4, textWrap: 'pretty',
            }}>
              {legal}
            </div>
          </div>}
        </div>
      </div>

      {hasPhone && on && (
        <PhoneInputInline value={phone} onChange={onPhone}/>
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// NotifyChannelRow — a single alert channel: icon + name + toggle.
// No per-channel copy; the shared purpose lives once in the card head.
// ─────────────────────────────────────────────────────────────
function NotifyChannelRow({ icon, title, on, onToggle, first, recommended }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 12,
      padding: '12px 0',
      borderTop: first ? 'none' : '1px solid var(--liq-border-soft)',
    }}>
      <div style={{
        flex: 'none', width: 34, height: 34, borderRadius: 10,
        background: recommended ? 'var(--liq-primary-500)' : 'var(--su-grad-lilac)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: recommended ? '#fff' : 'var(--liq-primary-500)',
      }}>
        <Icon name={icon} size={17} stroke={1.8}/>
      </div>
      <div style={{
        flex: 1, minWidth: 0,
        display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap',
        fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 15,
        color: 'var(--liq-fg)',
      }}>
        {title}
        {recommended && (
          <span style={{
            flex: 'none',
            padding: '2px 8px', borderRadius: 999,
            background: 'var(--liq-primary-500)', color: '#fff',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 700,
            fontSize: 10.5, letterSpacing: '0.03em', textTransform: 'uppercase',
          }}>Recommended</span>
        )}
      </div>
      <ConsentToggle on={on} onChange={onToggle} label={title}/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// NotifyChannelsCard — the shared-purpose alert group. States the
// what-gets-sent ONCE at the top, then lists the channels as plain
// toggle rows. Same lift-on-active treatment as ReachConsentCard,
// triggered when any channel in the group is on.
// ─────────────────────────────────────────────────────────────
// InterestCheckRow — MVP demand-test row: icon + name (+ sub-line) +
// checkbox. The whole row is the hit target (≥44pt). Checking never
// opens a dialog or asks for data; it only records interest.
function InterestCheckRow({ icon, title, sub, on, onToggle, first }) {
  return (
    <button type="button" role="checkbox" aria-checked={on} onClick={onToggle} style={{
      width: '100%', minHeight: 44, display: 'flex', alignItems: 'center', gap: 12,
      padding: '12px 0', background: 'none', border: 'none', textAlign: 'left', cursor: 'pointer',
      borderTop: first ? 'none' : '1px solid var(--liq-border-soft)',
    }}>
      <div style={{
        flex: 'none', width: 34, height: 34, borderRadius: 10, background: 'var(--su-grad-lilac)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--liq-primary-500)',
      }}>
        <Icon name={icon} size={17} stroke={1.8}/>
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 15, color: 'var(--liq-fg)', lineHeight: 1.3, textWrap: 'pretty' }}>{title}</div>
        {sub && <div style={{ marginTop: 2, fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 12.5, lineHeight: 1.4, color: 'var(--liq-neutral-200)', textWrap: 'pretty' }}>{sub}</div>}
      </div>
      <span style={{
        flex: 'none', width: 22, height: 22, borderRadius: 7,
        border: on ? '1.5px solid var(--liq-primary-500)' : '1.5px solid var(--liq-border)',
        background: on ? 'var(--liq-primary-500)' : '#fff',
        display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'all 160ms',
      }}>{on && <Icon name="check" size={14} stroke={3} style={{ color: '#fff' }}/>}</span>
    </button>
  );
}

function NotifyChannelsCard({ channels, consents, onToggle, mvp = false, interest, onInterest }) {
  const anyOn = channels.some(c => consents[c.key]);
  return (
    <div style={{
      borderRadius: 18,
      background: anyOn ? 'rgba(167,139,250,0.10)' : 'var(--liq-bg-elevated)',
      border: `1px solid ${anyOn ? 'rgba(129,42,236,0.28)' : 'var(--liq-border-soft)'}`,
      boxShadow: anyOn ? 'none' : 'var(--liq-shadow-md)',
      padding: 16,
      transition: 'background 220ms, border-color 220ms',
    }}>
      {/* Shared purpose — stated once for the whole group. */}
      <div style={{ display: 'flex', gap: 13, alignItems: 'flex-start' }}>
        <div style={{
          flex: 'none', width: 40, height: 40, borderRadius: 12,
          background: 'var(--su-grad-lilac)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'var(--liq-primary-500)',
        }}>
          <Icon name="bell" size={20} stroke={1.8}/>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 16.5,
            color: 'var(--liq-fg)', lineHeight: 1.2, letterSpacing: '-0.005em',
            textWrap: 'balance',
          }}>
            Get notifications
          </div>
          <div style={{
            marginTop: 6,
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13,
            lineHeight: 1.42, color: 'var(--liq-neutral-200)', textWrap: 'pretty',
          }}>
            We’ll let you know about new matches, meet time or location
            changes and date cancellations.{!mvp && ' Pick any channels you like — standard message rates may apply.'}
          </div>
        </div>
      </div>

      {/* Channel toggle rows. */}
      <div style={{ marginTop: 8 }}>
        {channels.map((c, i) => (
          <NotifyChannelRow
            key={c.key}
            icon={c.icon} title={c.title} recommended={c.recommended}
            on={consents[c.key]}
            onToggle={() => onToggle(c.key)}
            first={i === 0}
          />
        ))}
      </div>

      {/* MVP demand test — interest checkboxes, no function behind them. */}
      {mvp && (
        <div style={{ marginTop: 10, paddingTop: 18, borderTop: '1px solid var(--liq-border-soft)' }}>
          <div style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14, lineHeight: 1.35, color: 'var(--liq-fg)', textWrap: 'pretty' }}>
            More ways to reach you are coming soon.
          </div>
          <div style={{ marginTop: 3, fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13, lineHeight: 1.42, color: 'var(--liq-neutral-200)', textWrap: 'pretty' }}>
            Tell us which ones you’d like.
          </div>
          <div style={{ marginTop: 6 }}>
            {INTEREST_CHANNELS.map((c, i) => (
              <InterestCheckRow key={c.key} icon={c.icon} title={c.title} sub={c.sub}
                on={!!interest[c.key]} onToggle={() => onInterest(c.key)} first={i === 0}/>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ScreenProfileReachability — main screen
//
// Props:
//   initialConsents  { phone, calendar, notifications } — seed toggles
//                    (default all false — the compliant default)
//   initialPhone     seed phone value
//   onSave           fired on "Save preferences"
//   onSkip           fired on "Skip for now"
//   scope            'full' (backlog reference, default) | 'mvp' (build target:
//                    push only, ON by default + interest checkboxes)
//   initialInterest  { ai_call, whatsapp, sms } — MVP seed, default all false
// ─────────────────────────────────────────────────────────────
function ScreenProfileReachability({
  initialConsents,
  initialPhone = '',
  onSave, onSkip,
  flow = false,
  confirmOff = null,
  scope = 'full',
  initialInterest,
  afterDenial = false,   // MVP: dialog opened by an OS "Don't allow" — primary reads Open Settings
} = {}) {
  const mvp = scope === 'mvp';
  const [consents, setConsents] = useStateReach(
    initialConsents || (mvp
      ? { notifications: true }
      : { phone: false, calendar: false, notifications: false, whatsapp: false, sms: false, email: false })
  );
  const [interest, setInterest] = useStateReach(initialInterest || { ai_call: false, whatsapp: false, sms: false });
  const toggleInterest = (key) => setInterest(s => ({ ...s, [key]: !s[key] }));
  const [phone, setPhone] = useStateReach(initialPhone);
  // `pending` holds the key of the consent the user just tried to switch OFF.
  // While set, the deactivation-confirmation dialog is shown. Turning a
  // consent ON is immediate; turning one OFF is a deliberate act that costs
  // the user reachability, so we interrupt with an "Are you sure?" step.
  const [pending, setPending] = useStateReach(confirmOff);

  const toggle = (key) => {
    if (consents[key]) { setPending(key); return; }   // switching OFF → confirm
    setConsents(c => ({ ...c, [key]: true }));          // switching ON  → immediate
  };
  const confirmDeactivate = () => {
    setConsents(c => ({ ...c, [pending]: false }));
    setPending(null);
  };
  const keepActive = () => setPending(null);

  return (
    <div className="su-app" style={{
      width: 390, height: flow ? 'auto' : 844, minHeight: flow ? 844 : undefined,
      position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Ambient backdrop — identical recipe to the Notifications /
          Location permission screens so the profile flow reads as one
          continuous space. */}
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

      {/* HEADLINE — dark Lora with italic-underline flourish. */}
      <div style={{
        position: 'relative', zIndex: 1,
        padding: '32px 28px 0', flex: 'none',
      }}>
        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 31,
          letterSpacing: '-0.015em', lineHeight: 1.1,
          color: 'var(--liq-fg)', margin: 0, textWrap: 'balance',
        }}>
          <em>Never miss</em> a date and avoid getting a penalty!
        </h1>
        <p style={{
          margin: '14px 0 0',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
          lineHeight: 1.5, color: 'var(--liq-neutral-200)', textWrap: 'pretty',
        }}>
          <b>Activate notifications</b> so you <b>never miss a date!</b> <b>Missing</b> a date <b>lowers</b> your <b>Show-up Rate</b>, which <b>will</b> lead to a <b>temporary</b> ban or <b>permanent</b> <b>suspension</b> from the <b>app</b>.
        </p>
      </div>

      {/* SCROLLABLE BODY — three consent cards. The list can run taller
          than the viewport once the phone field is revealed, so it
          scrolls; the CTA footer stays pinned below. */}
      <div className="su-noscroll" style={{
        position: 'relative', zIndex: 1,
        flex: 1, minHeight: 0, overflow: flow ? 'visible' : 'auto',
        padding: '20px 24px 8px',
        display: 'flex', flexDirection: 'column', gap: 12,
      }}>
        {!mvp && REACH_PRIMARY.map((c) => (
          <ReachConsentCard
            key={c.key}
            icon={c.icon} title={c.title} purpose={c.purpose} legal={c.legal}
            hasPhone={c.hasPhone}
            on={consents[c.key]}
            onToggle={() => toggle(c.key)}
            phone={phone} onPhone={setPhone}
          />
        ))}

        <NotifyChannelsCard
          channels={mvp ? NOTIFY_CHANNELS.filter(c => c.key === 'notifications') : NOTIFY_CHANNELS}
          consents={consents}
          onToggle={toggle}
          mvp={mvp} interest={interest} onInterest={toggleInterest}
        />

        {/* GDPR footer note — purpose limitation, right to withdraw,
            and the Privacy Policy link. Sits in the muted register so
            it reads as reassurance, not another card. */}
        <div style={{
          marginTop: 4, padding: '2px 4px',
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 11.5,
          lineHeight: 1.5, color: 'var(--liq-fg-subtle)', textWrap: 'pretty',
        }}>
          We use this only to coordinate your dates — never for marketing,
          and we never sell your data. Turn any of these off whenever you
          like in <span style={{ color: 'var(--liq-fg)', fontWeight: 600 }}>Settings</span>.{' '}
          <a href="#" onClick={e => e.preventDefault()} style={{
            color: 'var(--liq-primary-500)', fontWeight: 600,
            textDecoration: 'underline', textUnderlineOffset: 2,
          }}>
            Read our Privacy Policy
          </a>.
        </div>
      </div>

      {/* FOOTER — sunset CTA + skip. The CTA is enabled with zero
          consents on (freely-given: the app never gates on these);
          its label just shifts to acknowledge whichever state you're in. */}
      <div style={{
        position: 'relative', zIndex: 1,
        padding: '10px 24px 6px', flex: 'none',
        background: 'linear-gradient(180deg, rgba(255,251,247,0) 0%, var(--liq-bg) 34%)',
      }}>
        <Button variant="sunset" size="lg" fullWidth onClick={onSave}>
          Save preferences
        </Button>
      </div>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>

      {/* DEACTIVATION-CONFIRMATION DIALOG
          Shown when the user tries to switch a consent OFF. Turning off any
          channel means we may not be able to reach them when a match lands —
          which risks a missed date and a Show Up-rate penalty. So we make the
          user confirm the trade-off before we drop the consent. Modal, centred
          alert dialog over a dimmed scrim — the standard iOS destructive
          confirmation pattern, in Show Up's warm-paper register. */}
      {pending && (() => {
        const c = REACH_CONSENTS.find(x => x.key === pending) || {};
        return (
          <div
            role="dialog" aria-modal="true" aria-label="Confirm deactivation"
            style={{
              position: 'absolute', inset: 0, zIndex: 40,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              padding: '0 26px',
            }}
          >
            {/* Scrim */}
            <div
              onClick={keepActive}
              style={{
                position: 'absolute', inset: 0,
                background: 'rgba(29,17,41,0.44)',
                backdropFilter: 'blur(3px)',
                animation: 'su-mismatch-in 200ms ease-out both',
              }}
            />

            {/* Alert card */}
            <div style={{
              position: 'relative', width: '100%', maxWidth: 320,
              borderRadius: 26, background: 'var(--liq-bg-elevated)',
              boxShadow: '0 24px 60px rgba(20,12,28,0.34)',
              padding: '26px 24px 20px',
              animation: 'su-sheet-pop 260ms cubic-bezier(.22,1,.36,1) both',
            }}>
              <div style={{
                width: 52, height: 52, borderRadius: 16, margin: '0 auto 16px',
                background: 'rgba(251,50,59,0.10)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: 'var(--liq-danger)',
              }}>
                <Icon name="alert-circle" size={24} stroke={1.9}/>
              </div>

              <h2 style={{
                margin: 0, textAlign: 'center',
                fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 23,
                letterSpacing: '-0.01em', lineHeight: 1.15, color: 'var(--liq-fg)',
              }}>
                <em>Are you sure?</em>
              </h2>

              <p style={{
                margin: '10px 0 0', textAlign: 'center',
                fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14,
                lineHeight: 1.5, color: 'var(--liq-neutral-200)', textWrap: 'pretty',
              }}>
                <b>Please remember:</b><br/>
                <b>Missing</b> a date <b>lowers</b> your <b>Show-up Rate</b>, which <b>will</b> lead to a <b>temporary</b> ban or <b>permanent</b> <b>suspension</b> from the <b>app</b>.
                Keep notifications active to avoid any chances of missing a date.
              </p>

              {c.title && <div style={{
                margin: '16px 0 4px', padding: '10px 14px',
                borderRadius: 12, background: 'var(--liq-bg-raised)',
                display: 'flex', alignItems: 'center', gap: 10,
              }}>
                <div style={{
                  flex: 'none', width: 30, height: 30, borderRadius: 9,
                  background: 'var(--su-grad-lilac)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  color: 'var(--liq-primary-500)',
                }}>
                  <Icon name={c.icon} size={16} stroke={1.8}/>
                </div>
                <span style={{
                  fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 13.5,
                  color: 'var(--liq-fg)',
                }}>{c.title}</span>
              </div>}

              <div style={{
                marginTop: 18, display: 'flex', flexDirection: 'column', gap: 10,
              }}>
                <Button variant="sunset" size="lg" fullWidth onClick={keepActive}>
                  {afterDenial ? 'Open Settings' : 'Keep active'}
                </Button>
                <button
                  onClick={confirmDeactivate}
                  style={{
                    height: 50, width: '100%', borderRadius: 9999,
                    background: 'transparent',
                    fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15,
                    color: 'var(--liq-danger)', cursor: 'pointer',
                  }}
                >
                  Confirm deactivation
                </button>
              </div>
            </div>
          </div>
        );
      })()}
    </div>
  );
}

Object.assign(window, {
  ScreenProfileReachability,
  ConsentToggle,
  ReachConsentCard,
  NotifyChannelRow,
  NotifyChannelsCard,
  REACH_PRIMARY,
  NOTIFY_CHANNELS,
  INTEREST_CHANNELS,
  InterestCheckRow,
  REACH_CONSENTS,
});
