import { readFileSync } from 'fs';
import { join } from 'path';

import { LAUNCH_RANKER_CONFIG, parseRankerConfig } from './ranker-config';
import {
  ageReach,
  combinedReach,
  distanceReach,
  motivationsCompatible,
  multiplierOf,
  orderFeed,
  overlapAhead,
  remainingHours,
  ScoreInputs,
  scoreOf,
  Scored,
} from './ranker';

const C = LAUNCH_RANKER_CONFIG;
const at = (iso: string) => new Date(iso);
const span = (start: string, end: string) => ({
  start: at(start),
  end: at(end),
});

/** Worked example 1's A and B, as the ticket states them. */
const example1: ScoreInputs = {
  motivation: ['long_term', 'casual'],
  // A 3 vibes, B 4, 2 shared.
  vibes: [
    ['coffee_chat', 'sunset', 'no_rush'],
    ['coffee_chat', 'sunset', 'grab_food', 'workout'],
  ],
  // A 6 interests, B 8, 3 shared.
  interests: [
    ['hiking', 'cooking', 'reading', 'yoga', 'music', 'travel'],
    ['hiking', 'cooking', 'reading', 'art', 'film', 'gaming', 'dance', 'tech'],
  ],
  familyAhead: [['want_kids'], ['want_kids', 'open']],
  // B skipped `living_ahead`: it drops out of the Profile average.
  livingAhead: [['city'], null],
  overlap: { startsInH: 6, remainingH: 3 },
  pastDates: { booked: 1, attended: 1 },
  meters: 4000,
  radiiKm: [10, 8],
};

describe('worked example 1 -- the full sum', () => {
  const parts = scoreOf(example1, C);

  it('gives the motivation, vibe and profile parts of Fit', () => {
    expect(parts.motivationFit).toBeCloseTo(0.6, 3);
    expect(parts.vibeFit).toBeCloseTo(0.667, 3);
    expect(parts.profileFit).toBeCloseTo(0.75, 3);
  });

  it('gives Fit 0.628, Time 0.838 and Score 0.698, each within 0.001', () => {
    expect(Math.abs(parts.fit - 0.628)).toBeLessThanOrEqual(0.001);
    expect(Math.abs(parts.time - 0.838)).toBeLessThanOrEqual(0.001);
    expect(Math.abs(parts.score - 0.698)).toBeLessThanOrEqual(0.001);
  });

  it('gives Reliability 0.9 for one date attended and Distance 0.5 on the smaller radius', () => {
    expect(parts.reliability).toBe(0.9);
    expect(parts.distance).toBeCloseTo(0.5, 6);
  });
});

describe('worked example 2 -- the cap', () => {
  it('caps liked-you x new-user (x1.69) at x1.5 on Freemium: final 1.047', () => {
    const m = multiplierOf(['liked_you', 'new_user'], false, false, C);
    expect(m).toBe(1.5);
    expect(scoreOf(example1, C).score * m).toBeCloseTo(1.047, 3);
  });

  it('caps the product, never a single rule, and nothing is ever above x1.5', () => {
    expect(multiplierOf(['liked_you'], false, false, C)).toBeCloseTo(1.3, 6);
    expect(multiplierOf(['new_user'], false, false, C)).toBeCloseTo(1.3, 6);
    for (const boosts of [
      ['liked_you', 'new_user'],
      ['liked_you', 'new_user', 'not_now'],
    ] as const) {
      for (const soft of [false, true]) {
        expect(multiplierOf([...boosts], soft, false, C)).toBeLessThanOrEqual(
          1.5,
        );
      }
    }
  });

  it('applies no x1.3 for liked-you on Premium: the block is its reward', () => {
    expect(multiplierOf(['liked_you'], false, true, C)).toBe(1);
    expect(multiplierOf(['liked_you', 'new_user'], false, true, C)).toBeCloseTo(
      1.3,
      6,
    );
  });
});

describe('worked example 3 -- reductions are not capped', () => {
  it('applies Not now x soft match in full: x0.56, final 0.391, and B aged 37 on 25-35 is soft', () => {
    expect(ageReach(37, { minAge: 25, maxAge: 35 }, true, C)).toBe('soft');
    const m = multiplierOf(['not_now'], true, false, C);
    expect(m).toBeCloseTo(0.56, 6);
    expect(scoreOf(example1, C).score * m).toBeCloseTo(0.391, 3);
  });

  it('counts two Not nows as one: the multiplier is flat', () => {
    expect(multiplierOf(['not_now', 'not_now'], false, false, C)).toBeCloseTo(
      0.7,
      6,
    );
  });
});

describe('worked example 4 -- soft range edges', () => {
  it('widens 25-35 to 22-38 with the dealbreaker off: 38 soft, 39 out, 22 soft', () => {
    const range = { minAge: 25, maxAge: 35 };
    expect(ageReach(38, range, true, C)).toBe('soft');
    expect(ageReach(39, range, true, C)).toBe('out');
    expect(ageReach(22, range, true, C)).toBe('soft');
    expect(ageReach(21, range, true, C)).toBe('out');
    expect(ageReach(30, range, true, C)).toBe('in');
  });

  it('keeps 65+ unlimited and widens only the bottom: 22 soft on 25-65+', () => {
    const range = { minAge: 25, maxAge: null };
    expect(ageReach(22, range, true, C)).toBe('soft');
    expect(ageReach(80, range, true, C)).toBe('in');
  });

  it('never widens below 18', () => {
    expect(ageReach(17, { minAge: 18, maxAge: 30 }, true, C)).toBe('out');
    expect(ageReach(18, { minAge: 20, maxAge: 30 }, true, C)).toBe('soft');
  });

  it('widens a 10 km radius to 12.5 km: 12.5 soft, 12.6 out', () => {
    expect(distanceReach(12_500, 10, true, C)).toBe('soft');
    expect(distanceReach(12_600, 10, true, C)).toBe('out');
    expect(distanceReach(10_000, 10, true, C)).toBe('in');
  });

  it('scores a soft-distance match 0 on Distance, on the unwidened radius', () => {
    expect(
      scoreOf({ ...example1, meters: 12_500, radiiKm: [10, 20] }, C).distance,
    ).toBe(0);
  });

  it('with the dealbreaker on, 36 and 10.1 km are not eligible', () => {
    expect(ageReach(36, { minAge: 25, maxAge: 35 }, false, C)).toBe('out');
    expect(distanceReach(10_100, 10, false, C)).toBe('out');
    expect(ageReach(22, { minAge: 25, maxAge: null }, false, C)).toBe('out');
  });

  it('is one soft match when outside on both age and distance, and out when either is out', () => {
    expect(combinedReach(['soft', 'soft', 'in', 'in'])).toBe('soft');
    expect(combinedReach(['in', 'in', 'in', 'in'])).toBe('in');
    expect(combinedReach(['soft', 'out', 'in', 'in'])).toBe('out');
  });
});

/** A scored candidate for the ordering tests: only what ordering reads. */
function scored(
  id: string,
  final: number,
  fit: number,
  likedYou = false,
): Scored<string> {
  return {
    candidate: id,
    profileId: id,
    parts: {
      score: final,
      fit,
      motivationFit: 0,
      vibeFit: 0,
      profileFit: 0,
      time: 0,
      reliability: 0,
      distance: 0,
    },
    final,
    boosts: likedYou ? ['liked_you'] : [],
    softMatch: false,
    likedYou,
  };
}

/** A random source that never picks: always the top of the pool, which is the natural order. */
const first = () => 0;

describe('worked example 5 -- the gate', () => {
  it('places Fit 0.25 at final 0.9 after an above-gate profile at final 0.4', () => {
    const feed = orderFeed(
      [scored('held', 0.9, 0.25), scored('above', 0.4, 0.5)],
      false,
      first,
      C,
    );
    expect(feed.map((p) => p.candidate)).toEqual(['above', 'held']);
  });

  it('puts a held-back profile who liked the viewer in the Premium block regardless', () => {
    const feed = orderFeed(
      [scored('above', 0.4, 0.5), scored('held-liker', 0.2, 0.1, true)],
      true,
      first,
      C,
    );
    expect(feed.map((p) => p.candidate)).toEqual(['held-liker', 'above']);
  });

  it('is never empty because of the gate alone', () => {
    const feed = orderFeed(
      [scored('a', 0.5, 0.1), scored('b', 0.6, 0.2)],
      false,
      first,
      C,
    );
    expect(feed.map((p) => p.candidate)).toEqual(['b', 'a']);
  });

  it('gives a Freemium liker no fixed place: they rank by final score like anyone', () => {
    const feed = orderFeed(
      [scored('top', 0.9, 0.5), scored('liker', 0.3, 0.5, true)],
      false,
      first,
      C,
    );
    expect(feed.map((p) => p.candidate)).toEqual(['top', 'liker']);
  });
});

describe('worked example 6 -- random picks', () => {
  // Twelve above-gate profiles, p01 best ... p12 worst.
  const twelve = Array.from({ length: 12 }, (_, i) =>
    scored(`p${String(i + 1).padStart(2, '0')}`, 1 - i * 0.05, 0.5),
  );

  it('fills positions 5 and 10 with random picks and 1-4 with the top four', () => {
    const last = () => 0.999; // always the worst left in the pool, so a pick is visible
    const feed = orderFeed(twelve, false, last, C);
    expect(feed.slice(0, 4).map((p) => p.candidate)).toEqual([
      'p01',
      'p02',
      'p03',
      'p04',
    ]);
    expect(feed.filter((p) => p.isExplore).map((p) => p.position)).toEqual([
      5, 10,
    ]);
    expect(feed[4].candidate).toBe('p12');
    // The displaced move down: p05 is now 6th.
    expect(feed[5].candidate).toBe('p05');
    expect(feed).toHaveLength(12);
  });

  it('never picks in positions 1-3, and only from above the gate, whatever the random source does', () => {
    const mixed = [
      ...twelve.slice(0, 6),
      ...Array.from({ length: 6 }, (_, i) => scored(`h${i}`, 2, 0.1)),
    ];
    for (let seed = 0; seed < 50; seed++) {
      let s = seed;
      const rng = () => (s = (s * 9301 + 49297) % 233280) / 233280;
      const feed = orderFeed(mixed, false, rng, C);
      for (const p of feed.filter((x) => x.isExplore)) {
        expect(p.position).toBeGreaterThanOrEqual(5);
        expect(p.parts.fit).toBeGreaterThanOrEqual(C.fit_gate);
      }
      expect(new Set(feed.map((p) => p.candidate)).size).toBe(12);
    }
  });

  it('stops picking once no above-gate profile is left', () => {
    const feed = orderFeed(
      [
        ...twelve.slice(0, 4),
        ...Array.from({ length: 8 }, (_, i) => scored(`h${i}`, 0.5, 0.1)),
      ],
      false,
      () => 0.999,
      C,
    );
    expect(feed.filter((p) => p.isExplore)).toHaveLength(0);
  });

  it('counts the slots after the Premium liked-you block', () => {
    const likers = [scored('l1', 0.3, 0.5, true), scored('l2', 0.2, 0.5, true)];
    const feed = orderFeed([...likers, ...twelve], true, () => 0.999, C);
    expect(feed.slice(0, 2).map((p) => p.candidate)).toEqual(['l1', 'l2']);
    expect(feed.filter((p) => p.isExplore).map((p) => p.position)).toEqual([
      7, 12,
    ]);
  });
});

describe('ties', () => {
  it('break by Fit, then the lower profile_id, so the same inputs give the same order', () => {
    const feed = orderFeed(
      [scored('b', 0.5, 0.4), scored('c', 0.5, 0.6), scored('a', 0.5, 0.4)],
      false,
      first,
      C,
    );
    expect(feed.map((p) => p.candidate)).toEqual(['c', 'a', 'b']);
  });
});

describe('score parts', () => {
  it('scores Profile 0.5 when no field is answered by both', () => {
    const parts = scoreOf(
      {
        ...example1,
        interests: [['hiking'], null],
        familyAhead: [null, ['open']],
        livingAhead: [null, null],
      },
      C,
    );
    expect(parts.profileFit).toBe(0.5);
  });

  it('clamps Time and Distance to 0-1', () => {
    const far = scoreOf(
      {
        ...example1,
        overlap: { startsInH: 60, remainingH: 1 },
        meters: 50_000,
      },
      C,
    );
    expect(far.time).toBeGreaterThanOrEqual(0);
    expect(far.distance).toBe(0);
    const now = scoreOf(
      { ...example1, overlap: { startsInH: 0, remainingH: 9 }, meters: 0 },
      C,
    );
    expect(now.time).toBe(1);
    expect(now.distance).toBe(1);
  });

  it('values Reliability from past dates: new 0.9, 1 attended 0.9, 2+ 1.0, booked none attended 0.5', () => {
    const rel = (booked: number, attended: number) =>
      scoreOf({ ...example1, pastDates: { booked, attended } }, C).reliability;
    expect(rel(0, 0)).toBe(0.9);
    expect(rel(1, 1)).toBe(0.9);
    expect(rel(3, 2)).toBe(1.0);
    expect(rel(2, 0)).toBe(0.5);
  });

  it('reads every weight from the config: a different weight gives a different score', () => {
    const heavy = { ...C, weights: { ...C.weights, distance: 0.5, fit: 0.15 } };
    expect(scoreOf(example1, heavy).score).not.toBeCloseTo(
      scoreOf(example1, C).score,
      3,
    );
  });
});

describe('filters as functions', () => {
  it('filter 1: counts the chosen hours still ahead', () => {
    const now = at('2026-10-31T12:30:00Z');
    expect(
      remainingHours(
        [span('2026-10-31T12:00:00Z', '2026-10-31T14:00:00Z')],
        now,
      ),
    ).toBe(1.5);
    expect(
      remainingHours(
        [
          span('2026-10-31T12:00:00Z', '2026-10-31T14:00:00Z'),
          span('2026-11-01T18:00:00Z', '2026-11-01T19:00:00Z'),
        ],
        now,
      ),
    ).toBe(2.5);
    expect(
      remainingHours(
        [span('2026-10-31T09:00:00Z', '2026-10-31T11:00:00Z')],
        now,
      ),
    ).toBe(0);
  });

  it('filter 2: measures the shared hours ahead, and none when they do not meet', () => {
    const now = at('2026-10-31T12:00:00Z');
    const a = [span('2026-10-31T18:00:00Z', '2026-10-31T22:00:00Z')];
    expect(
      overlapAhead(
        a,
        [span('2026-10-31T20:00:00Z', '2026-10-31T23:00:00Z')],
        now,
      ),
    ).toEqual({
      startsInH: 8,
      remainingH: 2,
    });
    expect(
      overlapAhead(
        a,
        [span('2026-10-31T13:00:00Z', '2026-10-31T17:00:00Z')],
        now,
      ),
    ).toBeNull();
    // Already running: starts in 0.
    expect(
      overlapAhead(
        [span('2026-10-31T11:00:00Z', '2026-10-31T14:00:00Z')],
        [span('2026-10-31T10:00:00Z', '2026-10-31T13:00:00Z')],
        now,
      ),
    ).toEqual({ startsInH: 0, remainingH: 1 });
  });

  it('filter 12: friends matches only friends; romantic values never filter each other', () => {
    expect(motivationsCompatible('friends', 'friends')).toBe(true);
    expect(motivationsCompatible('friends', 'casual')).toBe(false);
    expect(motivationsCompatible('long_term', 'friends')).toBe(false);
    expect(motivationsCompatible('long_term', 'casual')).toBe(true);
    expect(motivationsCompatible('non_monogamy', 'short_term')).toBe(true);
  });
});

describe('parseRankerConfig', () => {
  it('accepts the launch config', () => {
    expect(parseRankerConfig(LAUNCH_RANKER_CONFIG)).toEqual({
      config: LAUNCH_RANKER_CONFIG,
    });
  });

  it('refuses a missing value, a value out of range, an asymmetric table and an early random slot', () => {
    const bad = (patch: (c: Record<string, any>) => void) => {
      const c = JSON.parse(JSON.stringify(LAUNCH_RANKER_CONFIG)) as Record<
        string,
        any
      >;
      patch(c);
      return parseRankerConfig(c);
    };
    expect(bad((c) => delete c.weights.fit)).toHaveProperty('error');
    expect(bad((c) => (c.fit_gate = 2))).toHaveProperty('error');
    expect(
      bad((c) => (c.motivation_table.casual.friends = 0.9)),
    ).toHaveProperty('error');
    expect(bad((c) => (c.random.first_position = 3))).toHaveProperty('error');
    expect(bad((c) => (c.ranker_version = ''))).toHaveProperty('error');
    expect(parseRankerConfig(null)).toHaveProperty('error');
  });
});

describe('never inputs', () => {
  /** A source file, or the part of it between two markers, without its comment lines. */
  const code = (file: string, from?: string, to?: string): string => {
    let src = readFileSync(join(__dirname, file), 'utf8');
    if (from) {
      const start = src.indexOf(from);
      src = src.slice(start, to ? src.indexOf(to, start) : undefined);
    }
    return src
      .split('\n')
      .filter((line) => !/^\s*(\/\/|\*|\/\*)/.test(line))
      .join('\n');
  };

  it('reads no ethnicity, religion, politics, health or photo data anywhere in the ranker', () => {
    const rules = code('ranker.ts');
    const pool = code(
      '../search.service.ts',
      'private async scoredPool(',
      'private async pastDatePartners(',
    );
    // The markers were found: the pool is the whole filter-and-score step, not an empty string.
    expect(pool).toContain('scoreOf(');
    for (const src of [rules, pool]) {
      expect(src).not.toMatch(/religion|politic|ethnic|health|photo/i);
    }
  });
});
