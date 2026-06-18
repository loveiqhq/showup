import { normalizePhone } from './phone';

describe('normalizePhone', () => {
  it('normalizes a valid number to E.164 (stripping spaces)', () => {
    expect(normalizePhone('+49 170 1234567')).toBe('+491701234567');
    expect(normalizePhone('+491701234567')).toBe('+491701234567');
  });

  it('rejects invalid numbers', () => {
    expect(() => normalizePhone('not a phone')).toThrow();
    expect(() => normalizePhone('+49 1')).toThrow();
    expect(() => normalizePhone('12345')).toThrow();
  });
});
