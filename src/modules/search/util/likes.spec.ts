import { LikeCloseReason } from '../../matching/util/match';
import {
  Availability,
  availabilityFrom,
  closeReasonFor,
  compareClosedLikes,
  compareOpenLikes,
  dropAbsent,
} from './likes';

describe('availabilityFrom', () => {
  const at = new Date('2026-10-25T18:00:00Z');

  it('is no_overlap for someone who can never be met, whatever else is true', () => {
    expect(
      availabilityFrom({ reachable: false, hasDate: true, meetAt: at }),
    ).toEqual({ status: Availability.NoOverlap, meetAt: null });
  });

  it('is matched for someone with a date, even with hours left', () => {
    expect(
      availabilityFrom({ reachable: true, hasDate: true, meetAt: at }),
    ).toEqual({ status: Availability.Matched, meetAt: null });
  });

  it('is available at the earliest shared hour', () => {
    expect(
      availabilityFrom({ reachable: true, hasDate: false, meetAt: at }),
    ).toEqual({ status: Availability.Available, meetAt: at });
  });

  it('is no_overlap with no shared hour left', () => {
    expect(
      availabilityFrom({ reachable: true, hasDate: false, meetAt: null }),
    ).toEqual({ status: Availability.NoOverlap, meetAt: null });
  });
});

describe('closeReasonFor', () => {
  it('names the like_close_reason for each closed state, and none while available', () => {
    expect(closeReasonFor(Availability.Matched)).toBe(LikeCloseReason.Matched);
    expect(closeReasonFor(Availability.NoOverlap)).toBe(
      LikeCloseReason.NoOverlap,
    );
    expect(closeReasonFor(Availability.Available)).toBeNull();
  });
});

describe('compareOpenLikes', () => {
  const like = (iso: string, meters: number, senderId: string) => ({
    meetAt: new Date(iso),
    meters,
    senderId,
  });

  it('puts the earliest meet time first -- today before tomorrow, then by time', () => {
    const tomorrow = like('2026-10-26T17:30:00Z', 100, 'a');
    const todayLate = like('2026-10-25T20:00:00Z', 100, 'b');
    const todayEarly = like('2026-10-25T18:00:00Z', 900, 'c');
    expect([tomorrow, todayLate, todayEarly].sort(compareOpenLikes)).toEqual([
      todayEarly,
      todayLate,
      tomorrow,
    ]);
  });

  it('breaks a tie on the nearest, then on the id, so the order never wobbles', () => {
    const far = like('2026-10-25T18:00:00Z', 2000, 'a');
    const nearB = like('2026-10-25T18:00:00Z', 500, 'b');
    const nearA = like('2026-10-25T18:00:00Z', 500, 'a');
    expect([far, nearB, nearA].sort(compareOpenLikes)).toEqual([
      nearA,
      nearB,
      far,
    ]);
  });
});

describe('compareClosedLikes', () => {
  it('leads with the like that closed first, then by id', () => {
    const late = { closedAt: new Date('2026-10-25T12:00:00Z'), senderId: 'a' };
    const earlyB = {
      closedAt: new Date('2026-10-25T09:00:00Z'),
      senderId: 'b',
    };
    const earlyA = {
      closedAt: new Date('2026-10-25T09:00:00Z'),
      senderId: 'a',
    };
    expect([late, earlyB, earlyA].sort(compareClosedLikes)).toEqual([
      earlyA,
      earlyB,
      late,
    ]);
  });
});

describe('dropAbsent', () => {
  it('leaves an absent value out of the JSON rather than sending null', () => {
    const out = dropAbsent({ a: 1, b: undefined, c: null, d: '' });
    expect(out).toEqual({ a: 1, c: null, d: '' });
    expect(Object.keys(out)).not.toContain('b');
  });
});
