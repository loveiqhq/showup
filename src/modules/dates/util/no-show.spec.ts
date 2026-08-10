import { noShowReportError } from './no-show';

const at = (iso: string) => new Date(iso);

describe('noShowReportError', () => {
  const scheduledAt = at('2026-07-01T18:00:00Z');

  it('blocks reporting a no-show before the date is due', () => {
    expect(
      noShowReportError(scheduledAt, at('2026-07-01T17:59:00Z')),
    ).not.toBeNull();
  });

  it('allows reporting exactly at the scheduled time', () => {
    expect(noShowReportError(scheduledAt, scheduledAt)).toBeNull();
  });

  it('allows reporting after the scheduled time', () => {
    expect(
      noShowReportError(scheduledAt, at('2026-07-01T18:20:00Z')),
    ).toBeNull();
  });
});
