import {
  ArrivalEntry,
  arrivalOf,
  minutesVsStart,
  retentionCutoff,
  secondsSince,
} from './arrival';
import { minutesBeforeStart } from './coordinate-unlock';
import {
  ARRIVAL_CONFIRMED,
  ARRIVAL_UNDONE,
  arrivalConfirmedEvent,
  arrivalUndoneEvent,
} from '../../analytics/events/date-events';

describe('arrival rules (Date 03)', () => {
  const meet = new Date('2026-11-10T18:00:00Z');
  const at = (ms: number) => new Date(meet.getTime() + ms);

  describe('minutesVsStart', () => {
    it('is negative before the meet time, truncated toward zero', () => {
      expect(minutesVsStart(meet, at(-90_000))).toBe(-1);
    });
    it('is 0 within the first minute after', () => {
      expect(minutesVsStart(meet, at(59_000))).toBe(0);
      expect(minutesVsStart(meet, meet)).toBe(0);
      expect(minutesVsStart(meet, at(-30_000))).toBe(0);
    });
    it('counts whole minutes after', () => {
      expect(minutesVsStart(meet, at(61 * 60_000))).toBe(61);
    });
    it('is the inverse of minutesBeforeStart', () => {
      expect(minutesVsStart(meet, at(-90_000))).toBe(
        -minutesBeforeStart(meet, at(-90_000)),
      );
      expect(minutesVsStart(meet, at(-5 * 60_000))).toBe(
        -minutesBeforeStart(meet, at(-5 * 60_000)),
      );
    });
  });

  describe('secondsSince', () => {
    it('counts whole seconds', () => {
      expect(secondsSince(meet, at(5_900))).toBe(5);
    });
    it('is never negative', () => {
      expect(secondsSince(meet, at(-5_000))).toBe(0);
    });
  });

  describe('arrivalOf', () => {
    const checkedInAt = new Date('2026-11-10T17:50:00Z');
    const committedAt = new Date('2026-11-10T17:55:00Z');
    it('is null with no partner row', () => {
      expect(arrivalOf(null, null)).toBeNull();
    });
    it('is null while the partner has not committed', () => {
      expect(arrivalOf({ checkedInAt, committedAt: null }, null)).toBeNull();
    });
    it('shows a committed row nobody has read past', () => {
      expect(arrivalOf({ checkedInAt, committedAt }, null)).toEqual({
        checkedInAt: checkedInAt.toISOString(),
      });
    });
    it('shows it when the viewer read before the commit', () => {
      expect(
        arrivalOf(
          { checkedInAt, committedAt },
          new Date(committedAt.getTime() - 1),
        ),
      ).toEqual({ checkedInAt: checkedInAt.toISOString() });
    });
    it('hides it when the viewer read at or after the commit', () => {
      expect(arrivalOf({ checkedInAt, committedAt }, committedAt)).toBeNull();
      expect(
        arrivalOf(
          { checkedInAt, committedAt },
          new Date(committedAt.getTime() + 1),
        ),
      ).toBeNull();
    });
  });

  describe('retentionCutoff', () => {
    it('is one calendar year earlier', () => {
      expect(
        retentionCutoff(new Date('2026-11-10T12:00:00Z')).toISOString(),
      ).toBe('2025-11-10T12:00:00.000Z');
    });
    it('clamps 29 February to 28 February', () => {
      expect(
        retentionCutoff(new Date('2028-02-29T09:30:00Z')).toISOString(),
      ).toBe('2027-02-28T09:30:00.000Z');
    });
  });

  describe('events', () => {
    const banned = /lat|lon|accur|dist|place|address/;
    it('arrival_confirmed carries exactly its four properties', () => {
      const e = arrivalConfirmedEvent('d1', ArrivalEntry.ChatChip, -4, true);
      expect(e).toEqual({
        eventName: ARRIVAL_CONFIRMED,
        properties: {
          date_id: 'd1',
          entry: 'chat_chip',
          minutes_vs_start: -4,
          is_first: true,
        },
      });
      expect(ARRIVAL_CONFIRMED).toBe('arrival_confirmed');
      expect(Object.keys(e.properties).some((k) => banned.test(k))).toBe(false);
    });
    it('arrival_undone carries exactly its three properties', () => {
      const e = arrivalUndoneEvent('d2', ArrivalEntry.Report, 12);
      expect(e).toEqual({
        eventName: ARRIVAL_UNDONE,
        properties: {
          date_id: 'd2',
          entry: 'report',
          seconds_since_confirm: 12,
        },
      });
      expect(ARRIVAL_UNDONE).toBe('arrival_undone');
      expect(Object.keys(e.properties).some((k) => banned.test(k))).toBe(false);
    });
    it('names the three entries', () => {
      expect(Object.values(ArrivalEntry)).toEqual([
        'date_screen',
        'chat_chip',
        'report',
      ]);
    });
  });
});
