import { DateStatus } from './date-lifecycle';
import { dueForUnlock, minutesBeforeStart } from './coordinate-unlock';

const NOW = new Date('2026-11-03T16:00:00Z');
const minutesFromNow = (m: number) => new Date(NOW.getTime() + m * 60_000);
const row = (id: string, meetInMinutes: number, over: object = {}) => ({
  id,
  status: DateStatus.Confirmed,
  scheduledAt: minutesFromNow(meetInMinutes),
  coordinateUnlockedAt: null as Date | null,
  ...over,
});

describe('dueForUnlock', () => {
  it('picks a confirmed date whose meet − 1:30 has passed, and skips one still before it', () => {
    expect(dueForUnlock([row('due', 89), row('early', 91)], NOW)).toEqual([
      'due',
    ]);
  });

  it('is due exactly at meet − 1:30', () => {
    expect(dueForUnlock([row('edge', 90)], NOW)).toEqual(['edge']);
  });

  it('never fires twice for a date already stamped', () => {
    expect(
      dueForUnlock([row('done', 30, { coordinateUnlockedAt: NOW })], NOW),
    ).toEqual([]);
  });

  it('skips a date that has already started, and one that is no longer confirmed', () => {
    expect(
      dueForUnlock(
        [
          row('past', -5),
          row('cancelled', 30, { status: DateStatus.Cancelled }),
        ],
        NOW,
      ),
    ).toEqual([]);
  });
});

describe('minutesBeforeStart', () => {
  it('counts from the latest agreed meet time, negative after it', () => {
    expect(minutesBeforeStart(minutesFromNow(90), NOW)).toBe(90);
    expect(minutesBeforeStart(minutesFromNow(0), NOW)).toBe(0);
    expect(minutesBeforeStart(minutesFromNow(-15), NOW)).toBe(-15);
  });

  it('rounds toward zero, so 89.5 minutes is 89 and −0.5 is 0', () => {
    expect(
      minutesBeforeStart(new Date(NOW.getTime() + 89.5 * 60_000), NOW),
    ).toBe(89);
    expect(minutesBeforeStart(new Date(NOW.getTime() - 30_000), NOW)).toBe(0);
  });
});
