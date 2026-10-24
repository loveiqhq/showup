import {
  earliestMeetAt,
  isTimeZone,
  meetDay,
  roundedDistanceKm,
} from './search';

const at = (iso: string) => new Date(iso);
const span = (start: string, end: string) => ({
  start: at(start),
  end: at(end),
});

describe('roundedDistanceKm', () => {
  it('is 0 ("< 1 km") under a kilometre, decided on the metres not the rounding', () => {
    expect(roundedDistanceKm(0)).toBe(0);
    expect(roundedDistanceKm(900)).toBe(0);
    expect(roundedDistanceKm(999.9)).toBe(0);
  });

  it('is whole kilometres from one km up', () => {
    expect(roundedDistanceKm(1000)).toBe(1);
    expect(roundedDistanceKm(1200)).toBe(1);
    expect(roundedDistanceKm(1500)).toBe(2);
    expect(roundedDistanceKm(14_499)).toBe(14);
  });

  it('never turns a bad number into a distance', () => {
    expect(roundedDistanceKm(Number.NaN)).toBe(0);
  });
});

describe('earliestMeetAt', () => {
  const now = at('2026-10-22T15:20:00Z');

  it('is the first whole hour both Check-Ins hold', () => {
    const viewer = [span('2026-10-22T17:00:00Z', '2026-10-22T21:00:00Z')];
    const target = [span('2026-10-22T19:00:00Z', '2026-10-22T23:00:00Z')];
    expect(earliestMeetAt(viewer, target, now)).toEqual(
      at('2026-10-22T19:00:00Z'),
    );
  });

  it('takes the earliest across every pair of spans', () => {
    const viewer = [
      span('2026-10-23T08:00:00Z', '2026-10-23T10:00:00Z'),
      span('2026-10-22T18:00:00Z', '2026-10-22T20:00:00Z'),
    ];
    const target = [
      span('2026-10-23T09:00:00Z', '2026-10-23T12:00:00Z'),
      span('2026-10-22T19:00:00Z', '2026-10-22T22:00:00Z'),
    ];
    expect(earliestMeetAt(viewer, target, now)).toEqual(
      at('2026-10-22T19:00:00Z'),
    );
  });

  it('never offers an hour that has started', () => {
    const both = [span('2026-10-22T15:00:00Z', '2026-10-22T18:00:00Z')];
    expect(earliestMeetAt(both, both, now)).toEqual(at('2026-10-22T16:00:00Z'));
    expect(earliestMeetAt(both, both, at('2026-10-22T16:00:00Z'))).toEqual(
      at('2026-10-22T17:00:00Z'),
    );
  });

  it('is null when only part of an hour overlaps, or nothing is left', () => {
    const viewer = [span('2026-10-22T17:00:00Z', '2026-10-22T19:00:00Z')];
    const target = [span('2026-10-22T18:30:00Z', '2026-10-22T21:00:00Z')];
    expect(earliestMeetAt(viewer, target, now)).toBeNull();
    const both = [span('2026-10-22T15:00:00Z', '2026-10-22T16:00:00Z')];
    expect(earliestMeetAt(both, both, now)).toBeNull();
    expect(earliestMeetAt([], both, now)).toBeNull();
  });

  it("steps from the span's own start, so a half-hour zone keeps its :30 slots", () => {
    // India, +05:30: the 19:00 local slot starts at 13:30 UTC.
    const viewer = [span('2026-10-22T13:30:00Z', '2026-10-22T16:30:00Z')];
    const target = [span('2026-10-22T13:30:00Z', '2026-10-22T15:30:00Z')];
    expect(earliestMeetAt(viewer, target, at('2026-10-22T13:40:00Z'))).toEqual(
      at('2026-10-22T14:30:00Z'),
    );
  });
});

describe('meetDay -- the Check-In day, 7am to 7am, in the viewer zone', () => {
  const berlin = 'Europe/Berlin';

  it('is today for a slot in the block holding the next full hour', () => {
    // 16:20 Berlin (CEST, +2) -> Today is the 7am block starting 22 Oct.
    const now = at('2026-10-22T14:20:00Z');
    expect(meetDay(at('2026-10-22T17:00:00Z'), now, berlin)).toBe('today');
  });

  it('counts 01:00 after midnight as tonight, not tomorrow', () => {
    const now = at('2026-10-22T19:00:00Z'); // 21:00 Berlin
    expect(meetDay(at('2026-10-22T23:00:00Z'), now, berlin)).toBe('today'); // 01:00
    expect(meetDay(at('2026-10-23T05:00:00Z'), now, berlin)).toBe('tomorrow'); // 07:00
  });

  it('rolls to the new day at 06:20, when the next full hour is 7am', () => {
    const now = at('2026-10-23T04:20:00Z'); // 06:20 Berlin
    expect(meetDay(at('2026-10-23T05:00:00Z'), now, berlin)).toBe('today'); // 07:00
    expect(meetDay(at('2026-10-24T05:00:00Z'), now, berlin)).toBe('tomorrow');
  });

  it('reads the wall clock on the night the clocks change', () => {
    // 29 Mar 2026, Berlin springs from 02:00 to 03:00. At 01:30 CET the next full hour is 03:00
    // CEST, still before 7am: Today is the block that started 28 Mar.
    const now = at('2026-03-29T00:30:00Z');
    expect(meetDay(at('2026-03-29T04:00:00Z'), now, berlin)).toBe('today'); // 06:00 CEST
    expect(meetDay(at('2026-03-29T05:00:00Z'), now, berlin)).toBe('tomorrow'); // 07:00 CEST
  });

  it('is computed in the zone the viewer sent', () => {
    const now = at('2026-10-22T20:00:00Z');
    const meet = at('2026-10-23T06:30:00Z');
    expect(meetDay(meet, now, 'Europe/Berlin')).toBe('tomorrow'); // 08:30 Berlin
    expect(meetDay(meet, now, 'America/New_York')).toBe('today'); // 02:30 New York
  });
});

describe('isTimeZone', () => {
  it('accepts IANA zones and refuses anything else', () => {
    expect(isTimeZone('Europe/Berlin')).toBe(true);
    expect(isTimeZone('UTC')).toBe(true);
    expect(isTimeZone('Mars/Olympus')).toBe(false);
    expect(isTimeZone('')).toBe(false);
    expect(isTimeZone(undefined)).toBe(false);
  });
});
