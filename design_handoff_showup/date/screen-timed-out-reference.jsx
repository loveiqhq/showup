// Date 09 · Your date has ended — reference implementation (17 Nov 2026)
// Kit source: ui_kits/show-up/screens-extras.jsx → ScreenDateTimedOut · artboard date-timed-out
// Ticket: date/tickets/09-timed-out.md · Sheet: date/spec-sheets/09-timed-out.png
//
// What matters:
// - Build as a FOURTH PAYLOAD of Date 08's PartnerEndedScreen — same anatomy as
//   ScreenDateClosed (screen-partner-end-reference.jsx). Never a second layout.
// - Shown to BOTH people, same screen whatever the check-ins. No verdict.
// - Full screen, no back / close / swipe. Proceed to review is the only exit.
// - CTA placed by the flex spacer, never an absolute Y.
// Uses shared.jsx: Atmosphere, StatusBar, HomeIndicator, Avatar, Button, Icon.

// Date 09 — the +2 h switch (17 Nov 2026).
//
// Nobody ended the date; 2 h after the latest agreed meet time the
// server closes it and BOTH people see this screen, whatever their
// check-ins. Neutral on purpose: no verdict, the hope is conditional.
// Same rules as Date 08: replaces whatever is open, no close / back /
// swipe-down, Proceed to review is the only exit. No last message.
// ─────────────────────────────────────────────────────────────
function ScreenDateTimedOut({ name = 'Leonie', meetTime = '19:00' }) {
  const rows = [
    {
      i: 'heart', accent: 'var(--liq-primary-500)', tint: 'rgba(129,42,236,0.12)',
      t: <>If you met, we hope you had a great time. <strong style={{ color: 'var(--liq-fg)' }}>Either way, tell us how it went in the review.</strong></>,
    },
    {
      i: 'search', accent: 'var(--liq-primary-500)', tint: 'rgba(129,42,236,0.12)',
      t: <>Once the review is done, you're free to <strong style={{ color: 'var(--liq-fg)' }}>search for your next date.</strong></>,
    },
  ];
  return (
    <div className="su-app" style={{ width: 390, height: 844, position: 'relative', overflow: 'hidden', background: 'var(--liq-bg)', display: 'flex', flexDirection: 'column' }}>
      <Atmosphere variant="hero"/>
      <div style={{ position: 'relative', zIndex: 2 }}><StatusBar time="9:00"/></div>
      <div className="su-noscroll" style={{ flex: 1, position: 'relative', zIndex: 1, overflow: 'hidden', padding: '8px 20px 0', display: 'flex', flexDirection: 'column' }}>
        <div style={{ padding: '30px 24px', borderRadius: 28, background: 'var(--su-grad-lilac, linear-gradient(180deg, #F3E8FF 0%, #E9D5FF 100%))', textAlign: 'center' }}>
          <div style={{ position: 'relative', width: 76, height: 76, margin: '0 auto 18px' }}>
            <Avatar name={name} size={76} photoIndex={0}/>
            <div style={{ position: 'absolute', right: -3, bottom: -3, width: 30, height: 30, borderRadius: 9999, background: 'var(--liq-primary-500)', color: '#fff', border: '3px solid #fff', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: 'var(--liq-shadow-md)' }}><Icon name="clock" size={15} stroke={2.6}/></div>
          </div>
          <h2 className="su-underlined" style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 27, color: 'var(--liq-fg)', margin: '0 0 12px', lineHeight: 1.15, letterSpacing: '-0.01em', textWrap: 'balance' }}>Your date has <em>ended.</em></h2>
          <p style={{ fontSize: 14, color: 'var(--liq-fg-muted)', margin: 0, lineHeight: 1.55, textWrap: 'pretty' }}>
            We closed your date with {name} automatically, 2 hours after the meet time ({meetTime}).
          </p>
        </div>
        <div style={{ marginTop: 18, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {rows.map((row, i) => (
            <div key={i} style={{ display: 'flex', gap: 12, padding: 14, borderRadius: 14, background: '#fff', border: '1px solid var(--liq-border-soft)' }}>
              <div style={{ width: 36, height: 36, borderRadius: 12, flex: 'none', background: row.tint, color: row.accent, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name={row.i} size={18} stroke={2}/></div>
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

Object.assign(window, { ScreenDateTimedOut });
