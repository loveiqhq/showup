import { DATE_DURATION_MINUTES, reviewWindowError } from './date-review';

const at = (iso: string) => new Date(iso);

describe('reviewWindowError', () => {
  // A date scheduled at 18:00 runs for 30 minutes, so it is finished at 18:30.
  const scheduledAt = at('2026-07-01T18:00:00Z');

  it('blocks a review before the date has started', () => {
    expect(
      reviewWindowError(scheduledAt, at('2026-07-01T17:30:00Z')),
    ).not.toBeNull();
  });

  it('blocks a review while the date is still running', () => {
    expect(
      reviewWindowError(scheduledAt, at('2026-07-01T18:15:00Z')),
    ).not.toBeNull();
  });

  it('blocks a review exactly at the scheduled start', () => {
    expect(reviewWindowError(scheduledAt, scheduledAt)).not.toBeNull();
  });

  it('allows a review once the date has finished', () => {
    expect(
      reviewWindowError(scheduledAt, at('2026-07-01T18:30:00Z')),
    ).toBeNull();
  });

  it('allows a review well after the date', () => {
    expect(
      reviewWindowError(scheduledAt, at('2026-07-01T20:00:00Z')),
    ).toBeNull();
  });

  it('treats a date as 30 minutes long', () => {
    expect(DATE_DURATION_MINUTES).toBe(30);
  });
});
