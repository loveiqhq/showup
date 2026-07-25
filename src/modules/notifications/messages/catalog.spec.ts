import { renderMessage } from './catalog';

describe('message catalog (EN/DE rendering)', () => {
  it('renders an English date reminder with interpolated params', () => {
    const msg = renderMessage('date_reminder', 'en', {
      time: '8:00 PM',
      venue: 'Café Central',
    });
    expect(msg.title).toMatch(/date/i);
    expect(msg.body).toContain('8:00 PM');
    expect(msg.body).toContain('Café Central');
  });

  it('renders the German variant differently from English', () => {
    const en = renderMessage('date_reminder', 'en', {
      time: '20:00',
      venue: 'X',
    });
    const de = renderMessage('date_reminder', 'de', {
      time: '20:00',
      venue: 'X',
    });
    expect(de.body).not.toBe(en.body);
    expect(de.body).toContain('20:00');
  });

  it('renders a discreet check-in expiry reminder (no location detail)', () => {
    const en = renderMessage('check_in_expiry', 'en', {});
    expect(en.title.length).toBeGreaterThan(0);
    expect(en.body.length).toBeGreaterThan(0);
  });

  it('renders transactional email content (email verification)', () => {
    const en = renderMessage('email_verification', 'en', { code: '123456' });
    expect(en.body).toContain('123456');
    const de = renderMessage('email_verification', 'de', { code: '123456' });
    expect(de.body).toContain('123456');
  });

  it('falls back to English for an unknown locale', () => {
    const en = renderMessage('password_reset', 'en', { code: '999' });
    const unknown = renderMessage('password_reset', 'fr' as unknown as 'en', {
      code: '999',
    });
    expect(unknown.body).toBe(en.body);
  });

  it('throws for an unknown message type', () => {
    expect(() => renderMessage('nope' as never, 'en', {})).toThrow(/template/i);
  });
});
