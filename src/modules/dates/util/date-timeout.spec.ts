import {
  TIMEOUT_AFTER_MS,
  TIMEOUT_SWEEP_LIMIT,
  dateTimeoutEnabled,
  timeoutDue,
} from './date-timeout';

describe('date timeout rules (SHOWUP-200)', () => {
  const MEET = new Date('2026-11-28T18:00:00Z');

  it('is two hours and a sweep of at most 100 dates', () => {
    expect(TIMEOUT_AFTER_MS).toBe(2 * 60 * 60 * 1000);
    expect(TIMEOUT_SWEEP_LIMIT).toBe(100);
  });

  it('is due at exactly +2 h, not a millisecond before', () => {
    expect(timeoutDue(MEET, new Date(MEET.getTime() + TIMEOUT_AFTER_MS))).toBe(
      true,
    );
    expect(
      timeoutDue(MEET, new Date(MEET.getTime() + TIMEOUT_AFTER_MS - 1)),
    ).toBe(false);
    expect(
      timeoutDue(MEET, new Date(MEET.getTime() + TIMEOUT_AFTER_MS + 1)),
    ).toBe(true);
  });

  it('is on only for true, in any case', () => {
    expect(dateTimeoutEnabled('true')).toBe(true);
    expect(dateTimeoutEnabled('TRUE')).toBe(true);
    for (const off of [undefined, '', 'false', '1', 'yes', 'on']) {
      expect(dateTimeoutEnabled(off)).toBe(false);
    }
  });
});
