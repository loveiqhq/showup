import { readFileSync } from 'fs';
import { join } from 'path';

import {
  checkInClosesAt,
  CHECK_IN_MOTIVATIONS,
  CHECK_IN_VIBES,
  checkInGenderDefault,
  FILTER_AGE_MAX,
  FILTER_AGE_MIN,
  FILTER_DISTANCE_DEFAULT,
  FILTER_DISTANCE_MAX,
  FILTER_DISTANCE_MIN,
  graphemeLength,
  HourSlot,
  MAX_SLOTS,
  MEET_GENDERS,
  normaliseUplift,
  slotsError,
  slotsToWindows,
  upcomingSlots,
  upliftContact,
} from './check-in-flow';

/**
 * THE REGISTRY IS THE SOURCE: every value set here is read back from `enums.json`, so a value the
 * registry adds and the server does not know (or the reverse) fails here, not in production.
 */
const REGISTRY = join(
  __dirname,
  '..',
  '..',
  '..',
  '..',
  'design_handoff_showup',
  'tracking',
  'enums.json',
);

interface RegistrySet {
  id: string;
  values?: string[];
  tables?: { rows: Record<string, string>[] }[];
}

function setOf(id: string): RegistrySet {
  const registry = JSON.parse(readFileSync(REGISTRY, 'utf-8')) as {
    sets: RegistrySet[];
  };
  const set = registry.sets.find((s) => s.id === id);
  if (!set) throw new Error(`the registry has no set ${id}`);
  return set;
}

describe('the value sets match the registry (drift guard)', () => {
  it('§30 check_in_motivation, in list order', () => {
    expect([...CHECK_IN_MOTIVATIONS]).toEqual(setOf('e30').values);
  });

  it('§31 check_in_vibe, in list order', () => {
    expect([...CHECK_IN_VIBES]).toEqual(setOf('e31').values);
  });

  it('§10 meet_gender, and its age and distance bounds', () => {
    const rows = setOf('e10').tables!.flatMap((t) => t.rows);
    const row = (id: string) => rows.find((r) => r.filter_id === id)!;
    expect(
      row('meet_gender')
        .values.split('·')
        .map((v) => v.trim()),
    ).toEqual([...MEET_GENDERS]);
    expect(row('age_range').values).toContain(
      `${FILTER_AGE_MIN}–${FILTER_AGE_MAX}`,
    );
    expect(row('distance_km').values).toContain(
      `${FILTER_DISTANCE_MIN}–${FILTER_DISTANCE_MAX}`,
    );
    expect(row('distance_km').values).toContain(
      `default ${FILTER_DISTANCE_DEFAULT}`,
    );
    expect(row('meet_orientation').values).toContain('RETIRED');
  });
});

describe('upliftContact -- the server repeats the clients check', () => {
  it.each([
    ['Text me on 0176 2345 6789 :)', 'phone'],
    ['call +49 (30) 123-4567', 'phone'],
    ['0176.234.5678', 'phone'],
    ['mail me at me@example.com', 'email'],
    ['see https://example.org/x', 'link'],
    ['www.showup', 'link'],
    ['find me on myname.de', 'link'],
  ])('%s is %s', (text, kind) => {
    expect(upliftContact(text)).toBe(kind);
  });

  it.each([
    'have 2x tickets for Eminem today',
    'will take you out on my boat on Wannsee',
    'need a co-pilot for my flight training today',
    'made fresh brownies today, free to share',
    'still looking for someone to join my dance class',
    'I am 31 and live 5 min from the lake',
    'Back by 10.30 for 2 hours',
    '',
  ])('%s is clean', (text) => {
    expect(upliftContact(text)).toBeNull();
  });
});

describe('graphemeLength', () => {
  it('counts an emoji, a flag and a family as one each', () => {
    expect(graphemeLength('ab')).toBe(2);
    expect(graphemeLength('😀')).toBe(1);
    expect(graphemeLength('🇩🇪')).toBe(1);
    expect(graphemeLength('👨‍👩‍👧')).toBe(1);
  });
});

describe('normaliseUplift', () => {
  it('trims, and stores nothing for whitespace', () => {
    expect(normaliseUplift('  hi  ')).toBe('hi');
    expect(normaliseUplift('   \n ')).toBeNull();
    expect(normaliseUplift('')).toBeNull();
    expect(normaliseUplift(null)).toBeNull();
  });
});

describe('checkInGenderDefault -- the story 05 table', () => {
  it.each([
    ['woman', 'straight', ['man']],
    ['man', 'straight', ['woman']],
    ['woman', 'lesbian', ['woman']],
    ['man', 'gay', ['man']],
    ['woman', 'bisexual', ['woman', 'man']],
    ['man', 'pansexual', ['woman', 'man']],
    ['non_binary', 'straight', ['woman', 'man', 'non_binary', 'other']],
    ['other', 'bisexual', ['woman', 'man', 'non_binary', 'other']],
    ['woman', 'other', ['woman', 'man', 'non_binary', 'other']],
    [null, null, ['woman', 'man', 'non_binary', 'other']],
  ])('%s + %s -> %j', (gender, orientation, expected) => {
    expect(checkInGenderDefault(gender, orientation)).toEqual(expected);
  });
});

describe('the hour slots', () => {
  const at = (iso: string) => new Date(iso);
  const slot = (
    iso: string,
    tier: 'preferred' | 'available' = 'preferred',
  ): HourSlot => ({ startsAt: at(iso), tier });

  it('merges consecutive hours of one tier, and splits on a tier change', () => {
    const windows = slotsToWindows([
      slot('2026-10-13T19:00:00Z'),
      slot('2026-10-13T18:00:00Z'),
      slot('2026-10-13T20:00:00Z', 'available'),
      slot('2026-10-13T23:00:00Z'),
    ]);
    expect(
      windows.map((w) => [w.start.toISOString(), w.end.toISOString(), w.tier]),
    ).toEqual([
      ['2026-10-13T18:00:00.000Z', '2026-10-13T20:00:00.000Z', 'preferred'],
      ['2026-10-13T20:00:00.000Z', '2026-10-13T21:00:00.000Z', 'available'],
      ['2026-10-13T23:00:00.000Z', '2026-10-14T00:00:00.000Z', 'preferred'],
    ]);
  });

  it('drops an hour that has started, silently', () => {
    const now = at('2026-10-13T16:20:00Z');
    const kept = upcomingSlots(
      [
        slot('2026-10-13T16:00:00Z'),
        slot('2026-10-13T17:00:00Z'),
        slot('2026-10-14T09:00:00Z'),
      ],
      now,
    );
    expect(kept.map((s) => s.startsAt.toISOString())).toEqual([
      '2026-10-13T17:00:00.000Z',
      '2026-10-14T09:00:00.000Z',
    ]);
  });

  it('refuses a slot off the quarter hour, a repeat, or one past the window', () => {
    const now = at('2026-10-13T16:20:00Z');
    expect(slotsError([slot('2026-10-13T17:10:00Z')], now)).toMatch(
      /on the hour/,
    );
    expect(
      slotsError(
        [slot('2026-10-13T17:00:00Z'), slot('2026-10-13T17:00:00Z')],
        now,
      ),
    ).toMatch(/twice/);
    expect(slotsError([slot('2026-10-17T17:00:00Z')], now)).toMatch(/outside/);
    expect(
      slotsError(
        Array.from({ length: MAX_SLOTS + 1 }, (_, i) =>
          slot(new Date(Date.UTC(2026, 9, 13, 17 + i)).toISOString()),
        ),
        now,
      ),
    ).toMatch(/At most/);
  });

  it('accepts an hour on a half-hour UTC offset (India, +5:30)', () => {
    const now = at('2026-10-13T10:00:00Z');
    expect(slotsError([slot('2026-10-13T12:30:00Z')], now)).toBeNull();
  });
});

describe('checkInClosesAt -- the 48 hours start at Find my date', () => {
  it('closes a Check-In 48 hours after it was committed', () => {
    const committed = new Date('2026-10-21T10:00:00.000Z');
    expect(checkInClosesAt(committed)).toEqual(
      new Date('2026-10-23T10:00:00.000Z'),
    );
  });

  it('outlasts every hour the app offers: Tomorrow ends at most 48 hours after the commit', () => {
    // Committed one second after 7am, the latest Tomorrow hour (the second 6am slot) ends at the
    // second 7am -- 48 hours less a second later. The Check-In is still active through it.
    const committed = new Date('2026-10-21T07:00:01.000Z');
    const lastHourEnds = new Date('2026-10-23T07:00:00.000Z');
    expect(checkInClosesAt(committed).getTime()).toBeGreaterThanOrEqual(
      lastHourEnds.getTime(),
    );
  });
});
