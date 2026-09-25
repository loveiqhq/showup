// screens-extras.jsx — Instant mode, Commitment Score, No-show recovery, Profile setup

const { useState: useStateExtras } = React;

// ─────────────────────────────────────────────────────────────
// Instant mode — quick check-in then live feed
// ─────────────────────────────────────────────────────────────
function ScreenInstantConfirm() {
  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Full-bleed ambient atmosphere — same warm wash as the rest of the kit:
          orange orb top-right, violet orb bottom-left, peach wash across the top.
          Sits behind the status bar AND home indicator. */}
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

      {/* Status bar — dark ink, sits on top of the atmosphere */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
      </div>

      <div className="su-noscroll" style={{ flex: 1, position: 'relative', zIndex: 1, padding: '0 24px', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '4px 0' }}>
          <Wordmark size={20}/>
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 5,
            padding: '5px 10px', borderRadius: 9999, background: 'var(--su-grad-sunset)',
            color: '#fff', fontSize: 11, fontWeight: 800, letterSpacing: 0.04,
          }}>
            <Icon name="zap" size={11} stroke={2.4}/>INSTANT
          </span>
        </div>

        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 16, color: 'var(--liq-fg)' }}>
          <h1 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 44,
            lineHeight: 1.05, letterSpacing: '-0.02em', margin: 0, textWrap: 'balance',
          }}>
            Meet someone right now with <em>INSTANT</em> mode.
          </h1>
          <p style={{ fontSize: 16, fontWeight: 500, color: 'var(--liq-neutral-200)', lineHeight: 1.5, margin: 0, textWrap: 'pretty' }}>
            We'll only show you people who are near you, ready to meet in the next 3 hours.
          </p>

          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 8, padding: '14px 16px', background: 'var(--liq-bg-elevated)', borderRadius: 18 }}>
            <div style={{
              width: 44, height: 44, borderRadius: 14, background: 'var(--su-grad-sunset)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff',
            }}><Icon name="users" size={20} stroke={2.2}/></div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, color: 'var(--liq-fg-subtle)', fontWeight: 600 }}>Live in your area</div>
              <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 22, color: 'var(--liq-fg)' }}>
                47 people <span style={{ fontStyle: 'italic', color: 'var(--liq-orange-500)' }}>ready now</span>
              </div>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 16 }}>
          <Button variant="sunset" size="lg" fullWidth
            leading={<Icon name="zap" size={18} stroke={2.4}/>}>
            Meet someone now
          </Button>
          <p style={{ textAlign: 'center', fontSize: 11, color: 'var(--liq-fg-subtle)', margin: 0, lineHeight: 1.5 }}>
            Same accountability as a normal date — Instant cancels lower your Show-up Rate.
          </p>
        </div>
      </div>

      {/* Home indicator — sits on top of the atmosphere */}
      <div style={{ position: 'relative', zIndex: 2 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

function ScreenInstantFeed() {
  const PEOPLE = [
    { name: 'Maya',   age: 29, dist: 0.6, win: '1h 47m', score: 92, intent: 'Casual', vibe: 'Grab a bite', g: 'linear-gradient(155deg, #DCE9D0 0%, #A5C99F 50%, #5B8C66 100%)' },
    { name: 'Sofia',  age: 31, dist: 1.2, win: '1h 35m', score: 88, intent: 'Long-term', vibe: 'Walk & talk', g: 'linear-gradient(155deg, #EAD9F4 0%, #C8A6E8 40%, #8B5CF6 100%)' },
    { name: 'Anna',   age: 27, dist: 1.8, win: '1h 12m', score: 96, intent: 'Long-term', vibe: 'Coffee', g: 'linear-gradient(155deg, #F1D9EA 0%, #D8A6CC 40%, #8B5C8B 100%)' },
    { name: 'Liv',    age: 30, dist: 2.0, win: '0h 58m', score: 89, intent: 'Casual',    vibe: 'Kiosk beer', g: 'linear-gradient(155deg, #FFE2C5 0%, #FFB58A 40%, #E07A4C 100%)' },
  ];
  return (
    <Phone>
      <div style={{ padding: '4px 16px 8px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <Wordmark size={18}/>
        <span style={{
          display: 'inline-flex', alignItems: 'center', gap: 5,
          padding: '5px 10px', borderRadius: 9999, background: 'var(--su-grad-sunset)',
          color: '#fff', fontSize: 11, fontWeight: 800,
        }}>
          <Icon name="zap" size={11} stroke={2.4}/>INSTANT
        </span>
        <div style={{ flex: 1 }}/>
        <div style={{
          display: 'inline-flex', alignItems: 'center', gap: 6,
          padding: '5px 10px', borderRadius: 9999, background: '#fff',
          border: '1px solid var(--liq-border)', fontSize: 12, fontWeight: 700,
          color: 'var(--liq-orange-500)',
        }}>
          <span style={{ width: 7, height: 7, borderRadius: 9999, background: 'var(--liq-orange-500)' }}/>
          Live · 1h 47m
        </div>
      </div>

      <div style={{ padding: '0 16px 16px' }}>
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', padding: '4px 0 14px' }}>
          <h2 style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 24, color: 'var(--liq-fg)', margin: 0, letterSpacing: '-0.01em' }}>
            <em>{PEOPLE.length}</em> near you, ready now
          </h2>
          <button style={{ fontSize: 13, color: 'var(--liq-primary-500)', fontWeight: 600 }}>Stop instant</button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {PEOPLE.map((p, i) => (
            <div key={i} style={{ position: 'relative' }}>
              <PortraitPlaceholder name={p.name} height={140} radius={20} gradient={p.g} label={false}>
                <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'flex-end' }}>
                  <div style={{ flex: 1, padding: 14, color: '#fff' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                      <span style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 22 }}>{p.name}, {p.age}</span>
                      <span style={{
                        padding: '2px 8px', borderRadius: 9999, background: 'rgba(255,255,255,0.85)',
                        color: 'var(--liq-orange-500)', fontSize: 10, fontWeight: 800, letterSpacing: 0.06,
                      }}>{p.win} LEFT</span>
                    </div>
                    <div style={{ fontSize: 12, fontWeight: 500, opacity: 0.95, display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Icon name="map-pin" size={11} stroke={2.2}/>
                      {p.dist} km · {p.intent} · {p.vibe}
                    </div>
                  </div>
                </div>
                <div style={{ position: 'absolute', top: 10, left: 10, padding: '3px 7px', borderRadius: 9999, background: 'rgba(255,255,255,0.85)', display: 'flex', alignItems: 'center', gap: 4, fontSize: 10, fontWeight: 700, color: 'var(--liq-fg)' }}>
                  <Icon name="shield-check" size={11} stroke={2.4} style={{ color: '#0A7A47' }}/>{p.score}%
                </div>
                <button style={{
                  position: 'absolute', top: 10, right: 10, width: 38, height: 38, borderRadius: 9999,
                  background: 'var(--su-grad-sunset)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  boxShadow: 'var(--liq-shadow-violet)',
                }}><Icon name="heart-filled" size={18}/></button>
              </PortraitPlaceholder>
            </div>
          ))}
        </div>

        <Card style={{ padding: 14, marginTop: 14, background: 'var(--su-grad-blush)' }}>
          <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 16, color: 'var(--liq-fg)', marginBottom: 4 }}>Reputation insight</div>
          <div style={{ fontSize: 13, color: 'var(--liq-fg)', lineHeight: 1.5 }}>
            Successfully completed Instant dates earn a <strong>bonus</strong> on your Show-up Rate — spontaneity rewarded.
          </div>
        </Card>
      </div>
      <TabBar active="instant"/>
    </Phone>
  );
}

// ─────────────────────────────────────────────────────────────
// Commitment Score detail screen
// ─────────────────────────────────────────────────────────────
function ScreenScore() {
  return (
    <Phone>
      <AppHeader title="Show-up Rate" leading="back" trailing={<Icon name="more" size={20}/>}/>
      <div style={{ padding: '0 16px 16px', display: 'flex', flexDirection: 'column', gap: 14 }}>

        <Card style={{ padding: 22, background: 'var(--su-grad-lilac)', textAlign: 'center' }}>
          <Eyebrow>Your Show-up Rate</Eyebrow>
          <div style={{ margin: '14px 0 4px', position: 'relative' }}>
            <ScoreRing value={94} size={148} stroke={10}/>
          </div>
          <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontStyle: 'italic', fontSize: 22, color: 'var(--liq-primary-500)', margin: '8px 0 4px' }}>
            One of our <em>most reliable</em>
          </div>
          <div style={{ fontSize: 13, color: 'var(--liq-fg-muted)', lineHeight: 1.5, maxWidth: 260, margin: '0 auto' }}>
            You showed up for <strong>17 of 18</strong> confirmed dates. Keep it up — high scorers get priority placement.
          </div>
        </Card>

        <Card style={{ padding: 16 }}>
          <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 18, color: 'var(--liq-fg)', marginBottom: 14 }}>Last 6 months</div>
          {/* Bar chart */}
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 10, height: 100, marginBottom: 8 }}>
            {[
              { m: 'Nov', v: 80 }, { m: 'Dec', v: 100 }, { m: 'Jan', v: 100 },
              { m: 'Feb', v: 67 }, { m: 'Mar', v: 100 }, { m: 'Apr', v: 100 },
            ].map((b, i) => (
              <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                <div style={{
                  width: '100%', height: `${b.v}%`, borderRadius: '8px 8px 4px 4px',
                  background: b.v >= 90 ? 'var(--su-grad-sunset)' : b.v >= 75 ? 'var(--liq-orange-400)' : 'var(--liq-orange-300)',
                }}/>
                <span style={{ fontSize: 11, color: 'var(--liq-fg-subtle)', fontWeight: 600 }}>{b.m}</span>
              </div>
            ))}
          </div>
        </Card>

        <Card style={{ padding: 16 }}>
          <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 18, color: 'var(--liq-fg)', marginBottom: 14 }}>Recent ratings</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {[
              { name: 'Emma', date: '3 days ago', stars: 5, q: 'Real, warm, and exactly her photos.' },
              { name: 'Tom',  date: '1 week ago', stars: 5, q: 'Showed up early. Easy to talk to.' },
              { name: 'Anna', date: '2 weeks ago', stars: 4, q: 'Good chat, kept to the 30 min.' },
            ].map((r, i) => (
              <div key={i} style={{ display: 'flex', gap: 12 }}>
                <Avatar name={r.name} size={36}/>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                    <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--liq-fg)' }}>{r.name}</span>
                    <span style={{ fontSize: 12, color: 'var(--liq-fg-subtle)' }}>· {r.date}</span>
                  </div>
                  <div style={{ display: 'flex', gap: 1, margin: '2px 0' }}>
                    {Array.from({ length: 5 }).map((_, j) => (
                      <Icon key={j} name="star" size={12}
                        style={{ color: j < r.stars ? 'var(--liq-orange-500)' : 'var(--liq-border)',
                                 fill: j < r.stars ? 'var(--liq-orange-500)' : 'transparent' }}/>
                    ))}
                  </div>
                  <div style={{ fontFamily: 'var(--liq-font-serif)', fontStyle: 'italic', fontSize: 13, color: 'var(--liq-fg)', lineHeight: 1.4 }}>"{r.q}"</div>
                </div>
              </div>
            ))}
          </div>
        </Card>

        <Card style={{ padding: 16, background: 'var(--liq-bg-raised)' }}>
          <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 16, color: 'var(--liq-fg)', marginBottom: 8 }}>How to keep your score up</div>
          <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13, color: 'var(--liq-fg)', lineHeight: 1.6 }}>
            <li>Only check in for hours you can <strong>actually</strong> meet.</li>
            <li>Use Instant mode when your plans firm up — it doesn't bind future hours.</li>
            <li>If life happens, reschedule <em>before</em> the deadline, not after.</li>
          </ul>
        </Card>
      </div>
      <TabBar active="settings"/>
    </Phone>
  );
}

// ─────────────────────────────────────────────────────────────
// No-show penalty / recovery screen
// ─────────────────────────────────────────────────────────────
function ScreenNoShow() {
  return (
    <Phone>
      <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', height: '100%' }}>
        <div style={{
          padding: '32px 24px', borderRadius: 28,
          background: 'var(--su-grad-lilac)', textAlign: 'center',
        }}>
          <div style={{
            width: 64, height: 64, borderRadius: 9999, margin: '0 auto 18px',
            background: '#fff', color: 'var(--liq-orange-500)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: 'var(--liq-shadow-md)',
          }}><Icon name="clock" size={28} stroke={2.2}/></div>
          <h2 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 28,
            color: 'var(--liq-fg)', margin: '0 0 12px', lineHeight: 1.15, letterSpacing: '-0.01em', textWrap: 'balance',
          }}>You didn't <em>show up</em> for Leonie.</h2>
          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.55, textWrap: 'pretty' }}>
            Our community is built on mutual respect for everyone's time. We understand — life happens.
            <strong style={{ color: 'var(--liq-fg)' }}> This first time is on us — no penalty.</strong>
          </p>
        </div>

        <div style={{ marginTop: 22, display: 'flex', flexDirection: 'column', gap: 12 }}>
          {[
            { i: 'shield-check', t: 'Future cancellations and no-shows will lower your Show-up Rate.' },
            { i: 'eye-off',      t: 'A low score reduces your visibility in the community.' },
            { i: 'zap',          t: 'Having trouble with stated availability? Use Instant mode instead.' },
          ].map((row, i) => (
            <div key={i} style={{ display: 'flex', gap: 12, padding: 14, borderRadius: 14, background: '#fff', border: '1px solid var(--liq-border-soft)' }}>
              <div style={{
                width: 36, height: 36, borderRadius: 12, flex: 'none',
                background: 'var(--liq-bg-raised)', color: 'var(--liq-primary-500)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}><Icon name={row.i} size={18} stroke={2}/></div>
              <div style={{ fontSize: 14, color: 'var(--liq-fg)', lineHeight: 1.45, alignSelf: 'center' }}>{row.t}</div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1, minHeight: 16 }}/>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <Button variant="sunset" size="lg" fullWidth>Keep showing up</Button>
          <button style={{ padding: '10px 0', fontSize: 13, fontWeight: 600, color: 'var(--liq-fg-subtle)' }}>Something happened — tell us</button>
        </div>
      </div>
    </Phone>
  );
}

// ─────────────────────────────────────────────────────────────
// Late cancellation — the INTERMEDIATE screen the LATE partner sees.
//
// Scenario: the user told their match they'd be more than 10 minutes
// late. That crosses the kit's 10-minute grace line, so the match was
// free to leave the date with no penalty — and she did. The date never
// happens.
//
// Her side ends on the standard rating flow ("Did Leonie show up? → No",
// no penalty). THIS screen is the other half: the late partner is told,
// plainly but kindly, that the date is closed and — because the delay
// was theirs — a Show-up Rate penalty applies. It mirrors ScreenNoShow's
// anatomy (hero card → consequence rows → CTA) but swaps the lilac
// "this one's on us" tone for a warm blush "this one has a cost" tone.
// ─────────────────────────────────────────────────────────────
function ScreenLateCancelled({ name = 'Leonie', from = 94, to = 90 }) {
  const rows = [
    {
      i: 'clock', accent: 'var(--liq-primary-500)', tint: 'var(--liq-bg-raised)',
      t: <>Ten minutes is the promise we all keep for each other. Show up on time and your rate climbs right back.</>,
    },
    {
      i: 'shield-check', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <>This counts as a missed date. Your <strong style={{ color: 'var(--liq-fg)' }}>Show-up Rate drops from {from}% to {to}%</strong>.</>,
    },
    {
      i: 'shield-check', accent: 'var(--liq-primary-500)', tint: 'var(--liq-bg-raised)',
      t: <>Future delays, cancellations and no-shows will lower your Show-up Rate.</>,
    },
  ];

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)', display: 'flex', flexDirection: 'column',
    }}>
      <Atmosphere variant="hero"/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', zIndex: 1, overflow: 'hidden',
        padding: '8px 20px 0', display: 'flex', flexDirection: 'column',
      }}>
        {/* Hero card — warm blush signals "this one has a cost" (vs. the
            lilac, no-penalty No-show screen). */}
        <div style={{
          padding: '30px 24px', borderRadius: 28,
          background: 'var(--su-grad-blush)', textAlign: 'center',
        }}>
          <div style={{
            width: 64, height: 64, borderRadius: 9999, margin: '0 auto 18px',
            background: '#fff', color: 'var(--liq-orange-500)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: 'var(--liq-shadow-md)',
          }}><Icon name="clock" size={28} stroke={2.2}/></div>

          <h2 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 27,
            color: 'var(--liq-fg)', margin: '0 0 12px', lineHeight: 1.15,
            letterSpacing: '-0.01em', textWrap: 'balance',
          }}>You have missed our <em>10-min</em> waiting window.</h2>

          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.55, textWrap: 'pretty' }}>
            That's past our grace window. {name} decided to
            <strong style={{ color: 'var(--liq-fg)' }}> cancel the date without penalty for her.</strong> The date is now closed. Show up on time on your next date.
          </p>
        </div>

        <div style={{ marginTop: 18, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {rows.map((row, i) => (
            <div key={i} style={{
              display: 'flex', gap: 12, padding: 14, borderRadius: 14,
              background: '#fff', border: '1px solid var(--liq-border-soft)',
            }}>
              <div style={{
                width: 36, height: 36, borderRadius: 12, flex: 'none',
                background: row.tint, color: row.accent,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}><Icon name={row.i} size={18} stroke={2}/></div>
              <div style={{ fontSize: 13.5, color: 'var(--liq-fg)', lineHeight: 1.45, alignSelf: 'center' }}>{row.t}</div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1, minHeight: 14 }}/>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, paddingBottom: 6 }}>
          <Button variant="sunset" size="lg" fullWidth>I'll show up on time</Button>
          <button style={{ padding: '10px 0', fontSize: 13, fontWeight: 600, color: 'var(--liq-fg-subtle)' }}>Something happened — tell us</button>
        </div>
      </div>

      <HomeIndicator/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Partner cancelled on YOU — the receiving side.
//
// Scenario: the match tapped "Cancel this date" from the It's-a-date
// confirmation screen (07). The user is confronted with it and needs a
// screen that (a) is empathetic about the cancellation, (b) reassures
// them their own Show-up Rate is untouched, (c) states plainly that the
// canceller takes the penalty (24h pause, rate drop, no re-match), and
// (d) points them straight back to search to find someone reliable.
//
// The mirror of ScreenLateCancelled: same anatomy (hero → rows → CTA),
// but the tone flips from "this one has a cost" (blush) to "this one's
// not on you" (reassuring lilac), and the CTA is forward-looking —
// straight back into the date search.
// ─────────────────────────────────────────────────────────────
function ScreenPartnerCancelled({ name = 'Leonie' }) {
  const rows = [
    {
      i: 'shield-check', accent: '#0A7A47', tint: 'rgba(0,171,85,0.12)',
      t: <>Your <strong style={{ color: 'var(--liq-fg)' }}>Show-up Rate stays untouched.</strong> A date someone else cancels will <strong style={{ color: 'var(--liq-fg)' }}>never</strong> count against you. You did everything right.</>,
    },
    {
      i: 'heart', accent: 'var(--liq-primary-500)', tint: 'var(--liq-bg-raised)',
      t: <>Show Up is built on <strong style={{ color: 'var(--liq-fg)' }}>reliability</strong> and <strong style={{ color: 'var(--liq-fg)' }}>treasuring each other's time</strong> — real, warm, in-person connection. <strong style={{ color: 'var(--liq-fg)' }}>This isn't how our community behaves.</strong></>,
    },
    {
      i: 'user-x', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <>People who <strong style={{ color: 'var(--liq-fg)' }}>repeatedly cancel or don't show up</strong> are paused, become less visible, and eventually <strong style={{ color: 'var(--liq-fg)' }}>lose access.</strong> {name} carries that — not you.</>,
    },
  ];

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)', display: 'flex', flexDirection: 'column',
    }}>
      <Atmosphere variant="hero"/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', zIndex: 1, overflow: 'hidden',
        padding: '4px 20px 0', display: 'flex', flexDirection: 'column',
      }}>
        {/* Hero card — reassuring lilac (vs. the blush "this one has a
            cost" late-cancel screen). A gentle, caring hand-on-shoulder
            moment: name who backed out, then hold space for the user. */}
        <div style={{
          padding: '26px 24px 24px', borderRadius: 28,
          background: 'var(--su-grad-lilac)', textAlign: 'center',
        }}>
          <div style={{ position: 'relative', width: 72, height: 72, margin: '0 auto 16px' }}>
            <Avatar name={name} size={72} photoIndex={0}/>
            <div style={{
              position: 'absolute', right: -3, bottom: -3,
              width: 28, height: 28, borderRadius: 9999,
              background: 'var(--liq-orange-500)', color: '#fff',
              border: '3px solid #fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: 'var(--liq-shadow-md)',
            }}><Icon name="x" size={15} stroke={2.6}/></div>
          </div>

          <h2 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 26,
            color: 'var(--liq-fg)', margin: '0 0 12px', lineHeight: 1.15,
            letterSpacing: '-0.01em', textWrap: 'balance',
          }}>{name} had to <em>cancel</em> — we're truly sorry.</h2>

          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.55, textWrap: 'pretty' }}>
            You made room in your day and you were ready to show up. <strong style={{ color: 'var(--liq-fg)' }}>Don't be discouraged - someone true as you is just a like away from meeting you.</strong>
          </p>
        </div>

        <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 9 }}>
          {rows.map((row, i) => (
            <div key={i} style={{
              display: 'flex', gap: 12, padding: 13, borderRadius: 14,
              background: '#fff', border: '1px solid var(--liq-border-soft)',
            }}>
              <div style={{
                width: 34, height: 34, borderRadius: 11, flex: 'none',
                background: row.tint, color: row.accent,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}><Icon name={row.i} size={17} stroke={2}/></div>
              <div style={{ fontSize: 13, color: 'var(--liq-fg)', lineHeight: 1.42, alignSelf: 'center' }}>{row.t}</div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1, minHeight: 12 }}/>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, paddingBottom: 6 }}>
          <Button variant="sunset" size="lg" fullWidth leading={<Icon name="search" size={18} stroke={2.4}/>}>Find someone who shows up</Button>
        </div>
      </div>

      <HomeIndicator/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// You didn't show up — your date stopped waiting and closed it.
//
// Scenario: the USER (here "Leonie") was the one who didn't turn up, or
// was very late without a word. Her date waited past the grace window,
// tapped "I'm not waiting any longer" and ended the date. The date is
// already over; this is what Leonie sees the next time she opens the app
// (in-app only — push/SMS handled elsewhere).
//
// Tone: firm accountability, not cruel. Same blush "this one has a cost"
// anatomy as ScreenLateCancelled, but pointed the other way — it names
// the behaviour plainly (no-show / no notice = disrespect for each
// other's time, not accepted here), states the penalty, and then asks
// for information. The single forward CTA hands off into the 10 · Review
// your Date flow, where the details are captured.
// ─────────────────────────────────────────────────────────────
function ScreenNoShowClosed({ name = 'Marco' }) {
  const rows = [
    {
      i: 'user-x', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <><strong style={{ color: 'var(--liq-fg)' }}>Not showing up</strong> to a date — or being <strong style={{ color: 'var(--liq-fg)' }}>very late</strong> without reaching out to your date — shows <strong style={{ color: 'var(--liq-fg)' }}>no respect for each other's time.</strong></>,
    },
    {
      i: 'shield-alert', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <>This <strong style={{ color: 'var(--liq-fg)' }}>counts as a missed date</strong> and results in a <strong style={{ color: 'var(--liq-fg)' }}>penalty</strong>: You <strong style={{ color: 'var(--liq-danger)' }}>cannot search</strong> for a new date for the <strong style={{ color: 'var(--liq-danger)' }}>next 24hrs</strong>. Your <strong style={{ color: 'var(--liq-fg)' }}>Show Up Rate dropped</strong> to <strong style={{ color: 'var(--liq-danger)' }}>2 of 3 dates</strong> attended.</>,
    },
    {
      i: 'eye-off', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <>If you continue to not show up to binding dates, your profile will become <strong style={{ color: 'var(--liq-fg)' }}>less visible</strong> for others and you will eventually <strong style={{ color: 'var(--liq-fg)' }}>lose App access</strong>.</>,
    },
  ];

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)', display: 'flex', flexDirection: 'column',
    }}>
      <Atmosphere variant="hero"/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', zIndex: 1, overflow: 'hidden',
        padding: '8px 20px 0', display: 'flex', flexDirection: 'column',
      }}>
        {/* Hero card — warm blush ("this one has a cost"). The date's
            avatar with a cancel badge names who was left waiting. */}
        <div style={{
          padding: '30px 24px', borderRadius: 28,
          background: 'var(--su-grad-blush)', textAlign: 'center',
        }}>
          <div style={{ position: 'relative', width: 76, height: 76, margin: '0 auto 18px' }}>
            <Avatar name={name} size={76} photoIndex={2}/>
            <div style={{
              position: 'absolute', right: -3, bottom: -3,
              width: 30, height: 30, borderRadius: 9999,
              background: 'var(--liq-orange-500)', color: '#fff',
              border: '3px solid #fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: 'var(--liq-shadow-md)',
            }}><Icon name="clock" size={16} stroke={2.6}/></div>
          </div>

          <h2 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 27,
            color: 'var(--liq-fg)', margin: '0 0 12px', lineHeight: 1.15,
            letterSpacing: '-0.01em', textWrap: 'balance',
          }}>{name} ended the date, stating you <em>didn't show up.</em></h2>

          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.55, textWrap: 'pretty' }}>
            {name} <strong style={{ color: 'var(--liq-fg)' }}>ended the date</strong> after waiting for you.<br/>It's now closed.
          </p>
        </div>

        <div style={{ marginTop: 18, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {rows.map((row, i) => (
            <div key={i} style={{
              display: 'flex', gap: 12, padding: 14, borderRadius: 14,
              background: '#fff', border: '1px solid var(--liq-border-soft)',
            }}>
              <div style={{
                width: 36, height: 36, borderRadius: 12, flex: 'none',
                background: row.tint, color: row.accent,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}><Icon name={row.i} size={18} stroke={2}/></div>
              <div style={{ fontSize: 13.5, color: 'var(--liq-fg)', lineHeight: 1.45, alignSelf: 'center' }}>{row.t}</div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1, minHeight: 14 }}/>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, paddingBottom: 6 }}>
          <Button variant="sunset" size="lg" fullWidth>Proceed to review</Button>
        </div>
      </div>

      <HomeIndicator/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Your date ended it because they didn't feel comfortable / safe.
//
// Scenario: mid-date, the OTHER person tapped "End the date" and picked
// "I don't feel comfortable" or "I don't feel safe". The date is closed
// immediately. This is what the reported user sees the next time they
// open the app.
//
// Tone: serious and unambiguous about the standard — respect is not
// optional here — but explicitly not a verdict. We hold one side of the
// story, we say so, we ask for theirs, and we commit to reviewing both.
// The single forward CTA hands off into the report-detail flow.
//
// One screen serves both exit reasons — "I don't feel comfortable" and
// "I don't feel safe" — so the wording stays deliberately non-escalating.
// ─────────────────────────────────────────────────────────────
function ScreenDateEndedDiscomfort({ name = 'Marco' }) {
  const rows = [
    {
      i: 'hand', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <><strong style={{ color: 'var(--liq-fg)' }}>Respect is non-negotiable</strong> at Show Up. Behaviour that leaves someone feeling <strong style={{ color: 'var(--liq-fg)' }}>uncomfortable or unsafe</strong> is not accepted here.</>,
    },
    {
      i: 'message-circle', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <>So far we only have <strong style={{ color: 'var(--liq-fg)' }}>one side of the story.</strong> In the next step, tell us <strong style={{ color: 'var(--liq-fg)' }}>what happened from your point of view.</strong></>,
    },
    {
      i: 'shield-check', accent: 'var(--liq-orange-500)', tint: 'rgba(254,104,57,0.12)',
      t: <>We <strong style={{ color: 'var(--liq-fg)' }}>review every report</strong> and <strong style={{ color: 'var(--liq-fg)' }}>act on it where it's needed.</strong></>,
    },
  ];

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)', display: 'flex', flexDirection: 'column',
    }}>
      <Atmosphere variant="hero"/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', zIndex: 1, overflow: 'hidden',
        padding: '8px 20px 0', display: 'flex', flexDirection: 'column',
      }}>
        {/* Hero — same blush "this one has a cost" anatomy as the other
            closed-date screens, badged with a raised hand rather than a
            clock: the date wasn't missed, it was stopped. */}
        <div style={{
          padding: '28px 22px', borderRadius: 28,
          background: 'var(--su-grad-blush)', textAlign: 'center',
        }}>
          <div style={{ position: 'relative', width: 76, height: 76, margin: '0 auto 16px' }}>
            <Avatar name={name} size={76} photoIndex={2}/>
            <div style={{
              position: 'absolute', right: -3, bottom: -3,
              width: 30, height: 30, borderRadius: 9999,
              background: 'var(--liq-orange-500)', color: '#fff',
              border: '3px solid #fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: 'var(--liq-shadow-md)',
            }}><Icon name="hand" size={15} stroke={2.6}/></div>
          </div>

          <h2 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 27,
            color: 'var(--liq-fg)', margin: '0 0 12px', lineHeight: 1.15,
            letterSpacing: '-0.01em', textWrap: 'balance',
          }}>{name} ended the date, stating he <em>didn't feel comfortable</em> in the situation.</h2>

          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.55, textWrap: 'pretty' }}>
            The date is now closed.
          </p>
        </div>

        <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {rows.map((row, i) => (
            <div key={i} style={{
              display: 'flex', gap: 12, padding: 14, borderRadius: 14,
              background: '#fff', border: '1px solid var(--liq-border-soft)',
            }}>
              <div style={{
                width: 36, height: 36, borderRadius: 12, flex: 'none',
                background: row.tint, color: row.accent,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}><Icon name={row.i} size={18} stroke={2}/></div>
              <div style={{ fontSize: 13.5, color: 'var(--liq-fg)', lineHeight: 1.45, alignSelf: 'center' }}>{row.t}</div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1, minHeight: 14 }}/>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, paddingBottom: 6 }}>
          <Button variant="sunset" size="lg" fullWidth>Proceed to review</Button>
        </div>
      </div>

      <HomeIndicator/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// The date ended normally — the warm counterpart to the two
// accountability screens above.
//
// Scenario: one of the two tapped "Date finished" with no report
// attached. Nobody was late, nobody was left waiting, nobody felt
// uncomfortable. Both people simply met, in real life, as promised.
//
// Shown ONLY to the person who did NOT end the date: it tells them
// their date has already been closed and it's time for the review.
// Lilac hero rather than blush: this one is a thank-you, not a
// consequence. Single forward CTA into the review, which is also the
// gate back into the date search.
// ─────────────────────────────────────────────────────────────
function ScreenDateClosed() {
  const rows = [
    {
      i: 'heart', accent: 'var(--liq-primary-500)', tint: 'rgba(129,42,236,0.12)',
      t: <>We hope it was a good one. <strong style={{ color: 'var(--liq-fg)' }}>Head into the review</strong> and tell us how it went — it takes a minute.</>,
    },
    {
      i: 'search', accent: 'var(--liq-primary-500)', tint: 'rgba(129,42,236,0.12)',
      t: <>Once the review is done, you're free to <strong style={{ color: 'var(--liq-fg)' }}>search for your next date.</strong></>,
    },
  ];

  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)', display: 'flex', flexDirection: 'column',
    }}>
      <Atmosphere variant="hero"/>

      <div style={{ position: 'relative', zIndex: 2 }}>
        <StatusBar time="4:20"/>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, position: 'relative', zIndex: 1, overflow: 'hidden',
        padding: '8px 20px 0', display: 'flex', flexDirection: 'column',
      }}>
        <div style={{
          padding: '30px 24px', borderRadius: 28,
          background: 'var(--su-grad-lilac, linear-gradient(180deg, #F3E8FF 0%, #E9D5FF 100%))',
          textAlign: 'center',
        }}>
          <div style={{ position: 'relative', width: 76, height: 76, margin: '0 auto 18px' }}>
            <div style={{
              width: 76, height: 76, borderRadius: 9999,
              background: 'rgba(255,255,255,0.72)',
              border: '1px solid rgba(129,42,236,0.16)',
              color: 'var(--liq-primary-500)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 2px 10px rgba(46,1,71,0.08)',
            }}><Icon name="heart-filled" size={34}/></div>
            <div style={{
              position: 'absolute', right: -3, bottom: -3,
              width: 30, height: 30, borderRadius: 9999,
              background: 'var(--liq-success-fg, #00AB55)', color: '#fff',
              border: '3px solid #fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: 'var(--liq-shadow-md)',
            }}><Icon name="check" size={15} stroke={3}/></div>
          </div>

          <h2 className="su-underlined" style={{
            fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 27,
            color: 'var(--liq-fg)', margin: '0 0 12px', lineHeight: 1.15,
            letterSpacing: '-0.01em', textWrap: 'balance',
          }}>Leonie ended the date, stating you both have <em>met.</em></h2>

          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.55, textWrap: 'pretty' }}>
            Thank you for being real and showing up to your date.
          </p>
        </div>

        <div style={{ marginTop: 18, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {rows.map((row, i) => (
            <div key={i} style={{
              display: 'flex', gap: 12, padding: 14, borderRadius: 14,
              background: '#fff', border: '1px solid var(--liq-border-soft)',
            }}>
              <div style={{
                width: 36, height: 36, borderRadius: 12, flex: 'none',
                background: row.tint, color: row.accent,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}><Icon name={row.i} size={18} stroke={2}/></div>
              <div style={{ fontSize: 13.5, color: 'var(--liq-fg)', lineHeight: 1.45, alignSelf: 'center' }}>{row.t}</div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1, minHeight: 14 }}/>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, paddingBottom: 6 }}>
          <Button variant="sunset" size="lg" fullWidth>Proceed to review</Button>
        </div>
      </div>

      <HomeIndicator/>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Profile setup — photos step
//
// 3-step SQPS progress, a 0→6 photo meter, four required photo cards
// in a 2×2 grid, an "Add more" control that reveals optional slots 5–6,
// a Verify-your-profile upsell card, and a sticky footer that keeps the
// Continue CTA in the viewport at all times.
//
// `state` prop: 'empty' (0 photos) · 'partial' (2 photos, default).
// Four photos are required to continue; six is the ceiling.
//
// Layout is split into:
//   1. Fixed top    — status bar + header + 3-step progress
//   2. Scroll body  — eyebrow + headline + sub + 0→6 count + 2×2 grid + add-more
//   3. Sticky foot  — Verify card + Continue row (canvas-tint gradient
//                     mask so scrolling content fades out cleanly).
// ─────────────────────────────────────────────────────────────

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
function PhotoSlot({ filled, hint, photoName, photoIndex = 0, optional = false, main = false, cta = false }) {
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

// ScreenProfilePhotos — the photos step, scaled 0 → 6.
//
// The count runs from 0 (first landing, nothing uploaded) to 6 (all
// slots used). Four photos are the requirement to continue; slots 5–6
// are headroom, revealed by an explicit "Add more" affordance rather
// than sitting there as two extra chores on first paint.
//
// state (two only — nothing added yet, or something added):
//   'empty'   0 photos — first slot carries a filled-in CTA treatment
//   'partial' 2 photos — at least one added, Next greyed until 4 (default)
//
// Tracking (family E · Profile Photos/Media):
//   photo_slot_tapped   { slot_index 0–5, is_optional, action:
//                         "add"|"replace"|"reveal_optional" }
//   photo_added         { slot_index, source, filled_count 0–6, max_slots 6 }
//   photo_removed       { slot_index, filled_count 0–6 }
//   photos_minimum_met  { count } — fires when filled_count first hits 4
function ScreenProfilePhotos({ state = 'partial' }) {
  const MIN = 4, MAX = 6;
  const SLOTS = [
    { hint: 'Portrait',           photoName: 'Leonie', photoIndex: 0 },
    { hint: 'With friends',       photoName: 'Maya',   photoIndex: 0 },
    { hint: 'Full body',          photoName: 'Liv',    photoIndex: 0 },
    { hint: 'Favourite activity', photoName: 'Sofia',  photoIndex: 0 },
    { hint: 'Anything you like',  photoName: 'Anna',   photoIndex: 0, optional: true },
    { hint: 'Anything you like',  photoName: 'Emma',   photoIndex: 0, optional: true },
  ];
  const filledCount = state === 'empty' ? 0 : 2;
  const [expanded, setExpanded] = React.useState(false);
  const showExtras = expanded;
  // Continue is always live. Tapping it short of the minimum answers with
  // a toast instead of a dead control — the rule is stated on press,
  // where the user is actually asking about it.
  const [toast, setToast] = React.useState(false);
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

        {/* The 0→6 scale, up front */}
        <div style={{ marginTop: 16 }}>
          <PhotoCount filled={filledCount} required={MIN} max={MAX}/>
        </div>

        {/* Required four — 2×2 */}
        <div style={{
          display: 'grid', gridTemplateColumns: '1fr 1fr',
          gap: 12, marginTop: 14,
        }}>
          {SLOTS.slice(0, MIN).map((s, i) => (
            <PhotoSlot
              key={i}
              filled={i < filledCount}
              main={i === 0 && filledCount > 0}
              cta={filledCount === 0 && i === 0}
              hint={filledCount === 0 && i === 0 ? 'Start with your face' : s.hint}
              photoName={s.photoName}
              photoIndex={s.photoIndex}
            />
          ))}
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
            Upload at least 4 photos to continue
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

      <div style={{ position: 'relative', zIndex: 3 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Settings — home + a couple of detail pages
// ─────────────────────────────────────────────────────────────
function ScreenSettings() {
  return (
    <Phone>
      <AppHeader title="Settings" leading="back"/>
      <div style={{ padding: '0 16px 16px' }}>
        <Card style={{ padding: 14, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 14 }}>
          <Avatar name="Leo" size={56}/>
          <div style={{ flex: 1 }}>
            <div style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 18, color: 'var(--liq-fg)' }}>Leo, 32</div>
            <div style={{ fontSize: 12, color: 'var(--liq-fg-subtle)' }}>Profile · 65% complete</div>
          </div>
          <ScoreRing value={94} size={42} stroke={4}/>
        </Card>

        {[
          { sect: 'Account', rows: [
            { i: 'user',         t: 'Profile',             s: 'Photos, prompts, video' },
            { i: 'lock',         t: 'Security',            s: 'Email, password, identity' },
            { i: 'shield-check', t: 'Show-up Rate',        s: '94% · history & badges' },
          ]},
          { sect: '48hrs preferences', rows: [
            { i: 'clock',     t: 'Availability',  s: 'Set quick defaults' },
            { i: 'heart',     t: 'Dating intent', s: 'Long-term partner' },
            { i: 'sliders',   t: 'Filters',       s: 'Age, distance, vibe' },
          ]},
          { sect: 'Plan & safety', rows: [
            { i: 'sparkles', t: 'Subscription', s: 'Freemium · upgrade to Premium' },
            { i: 'eye-off',  t: 'Privacy & safety', s: 'Blocked users, share-my-date' },
            { i: 'bell',     t: 'Notifications', s: 'Match alerts, reminders' },
          ]},
        ].map((sect, si) => (
          <div key={si} style={{ marginBottom: 18 }}>
            <div style={{ fontSize: 11, fontWeight: 800, color: 'var(--liq-fg-subtle)', textTransform: 'uppercase', letterSpacing: 0.08, padding: '0 4px 8px' }}>{sect.sect}</div>
            <Card style={{ padding: 0, overflow: 'hidden' }}>
              {sect.rows.map((r, i) => (
                <button key={i} style={{
                  width: '100%', padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 14,
                  borderTop: i > 0 ? '1px solid var(--liq-border-soft)' : 'none',
                  textAlign: 'left',
                }}>
                  <div style={{
                    width: 36, height: 36, borderRadius: 12, flex: 'none',
                    background: 'var(--liq-bg-raised)', color: 'var(--liq-primary-500)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}><Icon name={r.i} size={18} stroke={2}/></div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--liq-fg)' }}>{r.t}</div>
                    <div style={{ fontSize: 12, color: 'var(--liq-fg-subtle)' }}>{r.s}</div>
                  </div>
                  <Icon name="chevron-right" size={18} style={{ color: 'var(--liq-fg-faint)' }}/>
                </button>
              ))}
            </Card>
          </div>
        ))}

        <button style={{
          width: '100%', padding: '12px 0', borderRadius: 14,
          color: 'var(--liq-danger)', fontWeight: 600, fontSize: 14,
          border: '1px solid var(--liq-border-soft)', background: '#fff',
        }}>Sign out</button>
      </div>
      <TabBar active="settings"/>
    </Phone>
  );
}

// ─────────────────────────────────────────────────────────────
// Verify profile — its own full screen in the profile flow, placed
// right after photos. Verification is a real ask with real payoff, so
// it gets a screen of its own instead of an upsell card crowding the
// photo grid. The "face frame" exploration lives in
// `Verify profile.html`.
// ─────────────────────────────────────────────────────────────

function ShieldMedallion() {
  return (
    <div style={{
      width: 132, height: 132, borderRadius: '50%',
      position: 'relative',
      background: 'radial-gradient(circle, rgba(167,139,250,0.28) 0%, rgba(167,139,250,0) 70%)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      <div style={{
        width: 96, height: 96, borderRadius: '50%',
        background: 'var(--su-grad-sunset)',
        boxShadow: '0 14px 30px rgba(129,42,236,0.32), 0 4px 12px rgba(254,104,57,0.28)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: '#fff',
      }}>
        <Icon name="shield-check" size={46} stroke={1.8}/>
      </div>
      {/* tiny orange pip — echoes the dot under the wordmark's "Up." */}
      <div style={{
        position: 'absolute', top: 18, right: 18,
        width: 10, height: 10, borderRadius: '50%',
        background: 'var(--liq-orange-500)',
        boxShadow: '0 0 0 4px var(--liq-bg-elevated)',
      }}/>
    </div>
  );
}

function VerifyBenefitRow({ icon, title, body }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, padding: '2px 0' }}>
      <div style={{
        flex: 'none', width: 40, height: 40, borderRadius: 12,
        background: 'var(--liq-lavender-50)',
        border: '1px solid rgba(129,42,236,0.10)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--liq-primary-500)',
      }}>
        <Icon name={icon} size={20} stroke={1.8}/>
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 15,
          color: 'var(--liq-fg)', lineHeight: 1.3,
        }}>{title}</div>
        <div style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 13.5,
          color: 'var(--liq-fg-muted)', lineHeight: 1.4, marginTop: 2,
          textWrap: 'pretty',
        }}>{body}</div>
      </div>
    </div>
  );
}

// ScreenProfileVerify — verification as its own step in the profile
// flow, not an upsell card competing with the photo grid. The photos
// step stays single-purpose; this screen asks for one thing only.
//
// Anatomy: status bar, back header, sunset shield medallion hero,
// italic-underlined headline, lead paragraph, three benefit rows,
// sunset CTA + muted "Maybe later" escape hatch.
//
// Tracking (family E · Profile):
//   verify_step_viewed   {}
//   verify_started       { entry_point: "profile_flow" }
//   verify_skipped       { entry_point: "profile_flow" }
function ScreenProfileVerify({ onVerify, onSkip }) {
  return (
    <div className="su-app" style={{
      width: 390, height: 844, position: 'relative', overflow: 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Same orb backdrop recipe as the surrounding profile steps */}
      <div style={{
        position: 'absolute', top: '-22%', right: '-30%', width: 460, height: 460,
        background: 'radial-gradient(circle, rgba(254,104,57,0.20) 0%, rgba(254,104,57,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>
      <div style={{
        position: 'absolute', top: '-10%', left: '-28%', width: 420, height: 420,
        background: 'radial-gradient(circle, rgba(129,42,236,0.16) 0%, rgba(129,42,236,0) 65%)',
        filter: 'blur(10px)', zIndex: 0, pointerEvents: 'none',
      }}/>

      <div style={{ position: 'relative', zIndex: 3 }}>
        <StatusBar time="4:20"/>
        <AppHeader title="The real you" leading="back"/>
        <div style={{ padding: '4px 24px 10px' }}>
          <StepProgress steps={4} current={2}/>
        </div>
      </div>

      <div className="su-noscroll" style={{
        flex: 1, minHeight: 0, position: 'relative', zIndex: 1, overflow: 'auto',
        padding: '2px 24px 0',
      }}>
        {/* Hero */}
        <div style={{ display: 'flex', justifyContent: 'center', margin: '6px 0 14px' }}>
          <ShieldMedallion/>
        </div>

        <h1 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 30,
          letterSpacing: '-0.018em', lineHeight: 1.08, color: 'var(--liq-fg)',
          margin: '4px 0 8px', textWrap: 'balance',
        }}>
          Prove to others that your profile is <em>real</em>.
        </h1>

        <p style={{
          fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 14.5,
          lineHeight: 1.45, color: 'var(--liq-neutral-200)', margin: 0,
          textWrap: 'pretty',
        }}>
          Takes only a minute. Verified profiles receive up to{' '}
          <strong style={{ color: 'var(--liq-fg)', fontWeight: 700 }}>3× more matches</strong>.
        </p>

        <div style={{ marginTop: 20, display: 'flex', flexDirection: 'column', gap: 14 }}>
          <VerifyBenefitRow
            icon="camera"
            title="Take a live selfie."
            body="We match your live selfie against your profile photos to verify. The live selfie is not stored."
          />
          <VerifyBenefitRow
            icon="shield-check"
            title="Get a trust badge on your profile."
            body="The badge creates trust and upgrades your profile."
          />
          <VerifyBenefitRow
            icon="sparkles"
            title="Receive more matches."
            body="Verified profiles get up to 3× more matches."
          />
        </div>

        <div style={{ height: 20 }}/>
      </div>

      {/* Footer — primary verify, muted skip */}
      <div style={{
        position: 'relative', zIndex: 2, flex: 'none',
        padding: '10px 24px 6px',
        background: 'linear-gradient(180deg, rgba(255,251,247,0) 0%, rgba(255,251,247,0.92) 30%, rgba(255,251,247,1) 100%)',
      }}>
        <Button variant="sunset" size="lg" fullWidth onClick={onVerify}>Verify now</Button>
        <button
          onClick={onSkip}
          style={{
            marginTop: 4, height: 46, width: '100%', borderRadius: 9999,
            background: 'transparent',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 14.5,
            color: 'var(--liq-fg-subtle)', cursor: 'pointer',
          }}
        >
          Maybe later
        </button>
      </div>

      <div style={{ position: 'relative', zIndex: 3 }}>
        <HomeIndicator/>
      </div>
    </div>
  );
}

Object.assign(window, {
  ScreenInstantConfirm, ScreenInstantFeed, ScreenScore, ScreenNoShow, ScreenLateCancelled, ScreenPartnerCancelled, ScreenNoShowClosed,
  ScreenProfilePhotos, PhotoSlot, PhotoCount,
  ScreenProfileVerify, ShieldMedallion, VerifyBenefitRow,
  ScreenSettings,
});
