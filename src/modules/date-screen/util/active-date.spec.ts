import { SearchProfileDto } from '../../search/dto/search.dto';
import { EndReason } from '../../dates/util/date-end';
import { DateStatus } from '../../dates/util/date-lifecycle';
import {
  endedOutcomeOf,
  sharedInOrder,
  toActiveDate,
  unreadOf,
} from './active-date';

const VIEWER = 'viewer';
const PARTNER = 'partner';
const at = (iso: string) => new Date(iso);

const partner = (over: Partial<SearchProfileDto> = {}): SearchProfileDto => ({
  userId: PARTNER,
  name: 'Leonie',
  age: 28,
  distanceKm: 1,
  district: 'Neukölln',
  meetAt: '2026-11-03T18:00:00.000Z',
  meetDay: 'today',
  attendedCount: 4,
  motivation: 'long_term',
  vibes: ['coffee_chat', 'nature_walk', 'fun_playful'],
  photos: [{ url: 'https://cdn/p1.jpg' }, { url: 'https://cdn/p2.jpg' }],
  prompts: [],
  essentials: {},
  howILive: {},
  interests: ['reading', 'hiking', 'brunch'],
  ...over,
});

describe('sharedInOrder', () => {
  it('keeps what both have, in the match’s order', () => {
    expect(sharedInOrder(['a', 'b', 'c'], ['c', 'a'])).toEqual(['a', 'c']);
  });

  it('is empty when the viewer has none', () => {
    expect(sharedInOrder(['a'], [])).toEqual([]);
  });
});

describe('unreadOf', () => {
  const msg = (senderId: string, iso: string, reason = 'running_late') => ({
    senderId,
    reason,
    body: `body ${iso}`,
    createdAt: at(iso),
  });

  it('counts only the other person’s messages newer than the read time, and previews the newest', () => {
    const unread = unreadOf(
      [
        msg(PARTNER, '2026-11-03T16:00:00Z'),
        msg(VIEWER, '2026-11-03T16:05:00Z'),
        msg(PARTNER, '2026-11-03T16:10:00Z', 'on_my_way'),
        msg(PARTNER, '2026-11-03T16:20:00Z', 'cant_find'),
      ],
      VIEWER,
      at('2026-11-03T16:01:00Z'),
    );
    expect(unread).toEqual({
      count: 2,
      reason: 'cant_find',
      body: 'body 2026-11-03T16:20:00Z',
      sentAt: '2026-11-03T16:20:00.000Z',
    });
  });

  it('treats a never-read thread as all unread', () => {
    expect(
      unreadOf([msg(PARTNER, '2026-11-03T16:00:00Z')], VIEWER, null)?.count,
    ).toBe(1);
  });

  it('is null when nothing from the other person is unread', () => {
    expect(
      unreadOf(
        [
          msg(VIEWER, '2026-11-03T16:30:00Z'),
          msg(PARTNER, '2026-11-03T16:00:00Z'),
        ],
        VIEWER,
        at('2026-11-03T16:00:00Z'),
      ),
    ).toBeNull();
  });
});

const MEET = new Date('2026-11-03T18:00:00Z');
const MEET_ISO = '2026-11-03T18:00:00.000Z';

describe('endedOutcomeOf (Date 02)', () => {
  const ended = {
    id: 'd1',
    status: DateStatus.EndedInChat,
    userAId: VIEWER,
    userBId: PARTNER,
    endedById: VIEWER as string | null,
    scheduledAt: MEET,
    aEndSeenAt: null as Date | null,
    bEndSeenAt: null as Date | null,
  };

  it('tells the ender they ended it, and the match that the ender did', () => {
    expect(endedOutcomeOf(ended, VIEWER, 'Leonie')).toEqual({
      dateId: 'd1',
      name: 'Leonie',
      endedByYou: true,
      reason: 'chat',
      meetAt: MEET_ISO,
    });
    expect(endedOutcomeOf(ended, PARTNER, 'Max')).toEqual({
      dateId: 'd1',
      name: 'Max',
      endedByYou: false,
      reason: 'chat',
      meetAt: MEET_ISO,
    });
  });

  it('is null once that person has seen it, or for a date that did not end in the chat', () => {
    expect(
      endedOutcomeOf({ ...ended, aEndSeenAt: new Date() }, VIEWER, 'Leonie'),
    ).toBeNull();
    expect(
      endedOutcomeOf({ ...ended, aEndSeenAt: new Date() }, PARTNER, 'Max'),
    ).not.toBeNull();
    expect(
      endedOutcomeOf(
        { ...ended, status: DateStatus.Cancelled },
        VIEWER,
        'Leonie',
      ),
    ).toBeNull();
  });
});

describe('endedOutcomeOf (Date 06, R3)', () => {
  const ended = {
    id: 'd1',
    status: DateStatus.Ended,
    userAId: VIEWER,
    userBId: PARTNER,
    endedById: VIEWER as string | null,
    scheduledAt: MEET,
    endReason: EndReason.Met as EndReason | null,
    aEndSeenAt: null as Date | null,
    bEndSeenAt: null as Date | null,
  };

  it('reports every reason to the ender, with no partner screen', () => {
    for (const reason of Object.values(EndReason)) {
      expect(
        endedOutcomeOf({ ...ended, endReason: reason }, VIEWER, 'Leonie'),
      ).toEqual({
        dateId: 'd1',
        name: 'Leonie',
        endedByYou: true,
        reason,
        meetAt: MEET_ISO,
      });
    }
  });

  it('gives the match the Date 08 screen from end_reason alone', () => {
    const screen = (reason: EndReason) =>
      endedOutcomeOf({ ...ended, endReason: reason }, PARTNER, 'Max')
        ?.partnerScreen;
    expect(screen(EndReason.Met)).toBe('date_closed_positive');
    expect(screen(EndReason.PartnerNoShow)).toBe('you_no_showed');
    expect(screen(EndReason.Uncomfortable)).toBe('date_ended_discomfort');
  });

  it('is reported until that person marks it seen, each side on its own', () => {
    expect(
      endedOutcomeOf({ ...ended, aEndSeenAt: new Date() }, VIEWER, 'L'),
    ).toBeNull();
    expect(
      endedOutcomeOf({ ...ended, aEndSeenAt: new Date() }, PARTNER, 'M'),
    ).not.toBeNull();
    expect(
      endedOutcomeOf({ ...ended, bEndSeenAt: new Date() }, PARTNER, 'M'),
    ).toBeNull();
  });

  it('is null for a date with no end reason stored', () => {
    expect(
      endedOutcomeOf({ ...ended, endReason: null }, VIEWER, 'L'),
    ).toBeNull();
  });
});

describe('endedOutcomeOf (Date 09, timed out)', () => {
  const timedOut = {
    id: 'd9',
    status: DateStatus.TimedOut,
    userAId: VIEWER,
    userBId: PARTNER,
    endedById: null as string | null,
    scheduledAt: MEET,
    endReason: EndReason.TimedOut as EndReason | null,
    aEndSeenAt: null as Date | null,
    bEndSeenAt: null as Date | null,
  };

  it('reads the same to both people: nobody ended it, both get Date 09', () => {
    for (const [viewer, name] of [
      [VIEWER, 'Leonie'],
      [PARTNER, 'Max'],
    ] as const) {
      expect(endedOutcomeOf(timedOut, viewer, name)).toEqual({
        dateId: 'd9',
        name,
        endedByYou: false,
        reason: 'timed_out',
        partnerScreen: 'date_timed_out',
        meetAt: MEET_ISO,
      });
    }
  });

  it('is null once that person has seen it, each side on its own', () => {
    expect(
      endedOutcomeOf({ ...timedOut, aEndSeenAt: new Date() }, VIEWER, 'L'),
    ).toBeNull();
    expect(
      endedOutcomeOf({ ...timedOut, aEndSeenAt: new Date() }, PARTNER, 'M'),
    ).not.toBeNull();
  });
});

describe('endedOutcomeOf (Date 07, cancelled)', () => {
  const cancelled = {
    id: 'date-1',
    status: DateStatus.Cancelled,
    userAId: VIEWER,
    userBId: PARTNER,
    endedById: null as string | null,
    scheduledAt: MEET,
    cancelledById: VIEWER as string | null,
    penaltyPoints: 10 as number | null,
    aEndSeenAt: null,
    bEndSeenAt: null,
  };

  it('reports it to both people: endedByYou for the canceller only', () => {
    expect(endedOutcomeOf(cancelled, VIEWER, 'Leonie')).toEqual({
      dateId: 'date-1',
      name: 'Leonie',
      endedByYou: true,
      reason: 'cancelled',
      meetAt: MEET_ISO,
    });
    expect(endedOutcomeOf(cancelled, PARTNER, 'Max')).toEqual({
      dateId: 'date-1',
      name: 'Max',
      endedByYou: false,
      reason: 'cancelled',
      meetAt: MEET_ISO,
    });
  });

  it('sends no partnerScreen, and is reported until each side has seen it', () => {
    expect(
      endedOutcomeOf(cancelled, PARTNER, 'Max')?.partnerScreen,
    ).toBeUndefined();
    expect(
      endedOutcomeOf({ ...cancelled, bEndSeenAt: new Date() }, PARTNER, 'M'),
    ).toBeNull();
    expect(
      endedOutcomeOf({ ...cancelled, bEndSeenAt: new Date() }, VIEWER, 'L'),
    ).not.toBeNull();
  });

  it('ignores a cancel from before Date 07 (no penalty stored)', () => {
    expect(
      endedOutcomeOf({ ...cancelled, penaltyPoints: null }, PARTNER, 'M'),
    ).toBeNull();
  });
});

describe('toActiveDate', () => {
  const base = {
    dateId: 'date-1',
    scheduledAt: at('2026-11-03T18:00:00Z'),
    venue: { name: 'Cafe Krone', address: 'Oranienstrasse 42, Kreuzberg' },
    partner: partner(),
    distanceMeters: 1200 as number | null,
    viewer: {
      motivation: 'long_term' as string | null,
      vibes: ['fun_playful', 'coffee_chat'],
      interests: ['hiking', 'reading', 'cooking'],
    },
    unread: null,
    checkedIn: false,
    arrival: null,
    chatTutorialSeen: false,
    coordinateUnlockedAt: null as Date | null,
    anyCommittedCheckIn: false,
    meetDay: 'today' as const,
    endsEnabled: true,
    cancelPenaltyPoints: 10,
  };

  it('locks coordination more than 90 minutes before and unlocks at meet − 1:30', () => {
    const locked = toActiveDate({ ...base, now: at('2026-11-03T16:29:59Z') });
    expect(locked.coordinateOpen).toBe(false);
    expect(locked.unlockAt).toBe('2026-11-03T16:30:00.000Z');
    expect(
      toActiveDate({ ...base, now: at('2026-11-03T16:30:00Z') }).coordinateOpen,
    ).toBe(true);
  });

  it('opens before meet − 1:30 once an arrival check-in is committed (Date 03, 16 Nov)', () => {
    const early = at('2026-11-03T15:00:00Z');
    const stamp = at('2026-11-03T14:59:00Z');
    expect(
      toActiveDate({ ...base, now: early, coordinateUnlockedAt: stamp })
        .coordinateOpen,
    ).toBe(true);
    expect(
      toActiveDate({ ...base, now: early, coordinateUnlockedAt: null })
        .coordinateOpen,
    ).toBe(false);
  });

  it('carries the ends switch and the cancel penalty from its source', () => {
    const now = at('2026-11-03T15:00:00Z');
    const on = toActiveDate({ ...base, now });
    expect(on.endsEnabled).toBe(true);
    expect(on.cancelPenaltyPoints).toBe(10);
    const off = toActiveDate({
      ...base,
      now,
      endsEnabled: false,
      cancelPenaltyPoints: 15,
    });
    expect(off.endsEnabled).toBe(false);
    expect(off.cancelPenaltyPoints).toBe(15);
  });

  it('stays open from a committed check-in alone, when its stamp was lost', () => {
    const early = at('2026-11-03T15:00:00Z');
    expect(
      toActiveDate({ ...base, now: early, anyCommittedCheckIn: true })
        .coordinateOpen,
    ).toBe(true);
    expect(toActiveDate({ ...base, now: early }).coordinateOpen).toBe(false);
  });

  it('carries only the shared vibes and interests, in the match’s order', () => {
    const d = toActiveDate({ ...base, now: at('2026-11-03T15:00:00Z') });
    expect(d.sharedVibes).toEqual(['coffee_chat', 'fun_playful']);
    expect(d.sharedInterests).toEqual(['reading', 'hiking']);
  });

  it('sends both motivations, so the app shows the intent line only when they are equal', () => {
    const d = toActiveDate({
      ...base,
      viewer: { ...base.viewer, motivation: 'casual' },
      now: at('2026-11-03T15:00:00Z'),
    });
    expect(d.motivations).toEqual({ you: 'casual', them: 'long_term' });
  });

  it('builds the match summary from the partner’s profile, first photo only', () => {
    const d = toActiveDate({ ...base, now: at('2026-11-03T15:00:00Z') });
    expect(d.match).toEqual({
      userId: PARTNER,
      name: 'Leonie',
      age: 28,
      district: 'Neukölln',
      distanceKm: 1,
      photoUrl: 'https://cdn/p1.jpg',
      attendedCount: 4,
    });
    expect(d.place).toEqual({
      name: 'Cafe Krone',
      address: 'Oranienstrasse 42, Kreuzberg',
    });
    expect(d.meetAt).toBe('2026-11-03T18:00:00.000Z');
    expect(d.meetDay).toBe('today');
    expect(d.checkedIn).toBe(false);
  });

  it('carries the match’s arrival as a time only, and null when there is none', () => {
    const arrival = { checkedInAt: '2026-11-03T17:50:00.000Z' };
    const d = toActiveDate({
      ...base,
      arrival,
      checkedIn: true,
      now: at('2026-11-03T17:55:00Z'),
    });
    expect(d.arrival).toEqual(arrival);
    expect(d.checkedIn).toBe(true);
    expect(
      toActiveDate({ ...base, now: at('2026-11-03T17:55:00Z') }).arrival,
    ).toBeNull();
  });

  it('leaves out what the partner hid or never gave, and an unknown distance', () => {
    const d = toActiveDate({
      ...base,
      partner: partner({ age: undefined, district: undefined, photos: [] }),
      distanceMeters: null,
      venue: null,
      now: at('2026-11-03T15:00:00Z'),
    });
    expect(d.match).toEqual({
      userId: PARTNER,
      name: 'Leonie',
      attendedCount: 4,
    });
    expect(d.place).toBeNull();
    expect(JSON.stringify(d)).not.toContain('"age"');
  });

  it('rounds a known distance under 1 km to 0, as Search does', () => {
    const d = toActiveDate({
      ...base,
      distanceMeters: 400,
      now: at('2026-11-03T15:00:00Z'),
    });
    expect(d.match.distanceKm).toBe(0);
  });
});
