import { isAnalyticsAllowed } from './consent';

describe('isAnalyticsAllowed (analytics consent gate)', () => {
  it('allows tracking when the user has explicitly consented', () => {
    expect(isAnalyticsAllowed(true, { defaultWhenUnset: false })).toBe(true);
  });

  it('blocks tracking when the user has explicitly declined', () => {
    expect(isAnalyticsAllowed(false, { defaultWhenUnset: true })).toBe(false);
  });

  it('falls back to the configured default when consent is unset — opt-in posture (default false) blocks', () => {
    expect(isAnalyticsAllowed(undefined, { defaultWhenUnset: false })).toBe(
      false,
    );
    expect(isAnalyticsAllowed(null, { defaultWhenUnset: false })).toBe(false);
  });

  it('falls back to the configured default when consent is unset — opt-out posture (default true) allows', () => {
    expect(isAnalyticsAllowed(undefined, { defaultWhenUnset: true })).toBe(
      true,
    );
  });
});
