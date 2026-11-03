import { SearchProfileDto } from '../../search/dto/search.dto';
import { sharedInOrder, toActiveDate, unreadOf } from './active-date';

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
        msg(PARTNER, '2026-11-03T16:20:00Z', 'cant_find_you'),
      ],
      VIEWER,
      at('2026-11-03T16:01:00Z'),
    );
    expect(unread).toEqual({
      count: 2,
      reason: 'cant_find_you',
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
    meetDay: 'today' as const,
  };

  it('locks coordination more than 90 minutes before and unlocks at meet − 1:30', () => {
    const locked = toActiveDate({ ...base, now: at('2026-11-03T16:29:59Z') });
    expect(locked.coordinateOpen).toBe(false);
    expect(locked.unlockAt).toBe('2026-11-03T16:30:00.000Z');
    expect(
      toActiveDate({ ...base, now: at('2026-11-03T16:30:00Z') }).coordinateOpen,
    ).toBe(true);
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
