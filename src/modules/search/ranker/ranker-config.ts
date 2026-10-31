import {
  CHECK_IN_MOTIVATIONS,
  CheckInMotivation,
} from '../../check-ins/util/check-in-flow';

/**
 * Every number the Search ranker uses (Matching 01, SHOWUP-190), as ONE versioned object.
 *
 * The keys are the ticket's own, snake_case and all, so the stored JSON is the ticket's config block
 * verbatim and a reader can diff the two. "Any change to any value -> new `ranker_version`": the
 * version is stamped on every `search_feed_ranked`, so a training record always names the numbers
 * that ordered it. `ranker_configs` enforces that in the database (a stored config cannot be edited,
 * only replaced by a new version), and the active row is read at run time, so changing a weight
 * never needs a deploy.
 */
export interface RankerConfig {
  ranker_version: string;
  filters: { min_remaining_h: number; min_overlap_h: number };
  soft_range: { soft_age_years: number; soft_distance_pct: number };
  weights: { fit: number; time: number; reliability: number; distance: number };
  fit_weights: { motivation: number; vibe: number; profile: number };
  motivation_table: Record<
    CheckInMotivation,
    Record<CheckInMotivation, number>
  >;
  reliability: {
    new: number;
    attended_1: number;
    attended_2plus: number;
    booked_none_attended: number;
  };
  time_weights: { soon: number; length: number };
  time_horizon_h: number;
  time_full_overlap_h: number;
  profile_neutral: number;
  fit_gate: number;
  multipliers: {
    liked_you: number;
    new_user: number;
    new_user_checkins: number;
    not_now: number;
    soft_match: number;
    cap: number;
  };
  random: { share: number; first_position: number };
}

/** The launch numbers, 26-27 Oct 2026 (`matching-plan.md`), exactly as the ticket's config block. */
export const LAUNCH_RANKER_CONFIG: RankerConfig = {
  ranker_version: 'launch-2026-10',
  filters: { min_remaining_h: 2, min_overlap_h: 1 },
  soft_range: { soft_age_years: 3, soft_distance_pct: 25 },
  weights: { fit: 0.55, time: 0.2, reliability: 0.15, distance: 0.1 },
  fit_weights: { motivation: 0.7, vibe: 0.2, profile: 0.1 },
  // Symmetric. The `friends` cells against a romantic value are never reached (filter 12) and are
  // kept anyway, as the ticket asks, so the table stays whole if that filter ever changes.
  motivation_table: {
    long_term: {
      long_term: 1.0,
      non_monogamy: 0.4,
      short_term: 0.5,
      casual: 0.6,
      friends: 0.2,
    },
    non_monogamy: {
      long_term: 0.4,
      non_monogamy: 1.0,
      short_term: 0.6,
      casual: 0.7,
      friends: 0.2,
    },
    short_term: {
      long_term: 0.5,
      non_monogamy: 0.6,
      short_term: 1.0,
      casual: 0.9,
      friends: 0.1,
    },
    casual: {
      long_term: 0.6,
      non_monogamy: 0.7,
      short_term: 0.9,
      casual: 1.0,
      friends: 0.3,
    },
    friends: {
      long_term: 0.2,
      non_monogamy: 0.2,
      short_term: 0.1,
      casual: 0.3,
      friends: 1.0,
    },
  },
  reliability: {
    new: 0.9,
    attended_1: 0.9,
    attended_2plus: 1.0,
    booked_none_attended: 0.5,
  },
  time_weights: { soon: 0.7, length: 0.3 },
  time_horizon_h: 48,
  time_full_overlap_h: 4,
  profile_neutral: 0.5,
  fit_gate: 0.3,
  multipliers: {
    liked_you: 1.3,
    new_user: 1.3,
    new_user_checkins: 2,
    not_now: 0.7,
    soft_match: 0.8,
    cap: 1.5,
  },
  random: { share: 0.2, first_position: 5 },
};

/**
 * A stored config, checked before it can order anyone's feed: every value present, finite and in
 * range, and the motivation table whole and symmetric. Returns why it was refused, never throws --
 * a bad row must not take Search down (the caller keeps the last good config).
 *
 * `random.first_position` is at least 4: "never 1-3" is a rule of the ticket, not a number in the
 * config, so no config can break it.
 */
export function parseRankerConfig(
  raw: unknown,
): { config: RankerConfig } | { error: string } {
  const problems: string[] = [];
  const obj = (v: unknown, path: string): Record<string, unknown> => {
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      return v as Record<string, unknown>;
    }
    problems.push(`${path} is not an object`);
    return {};
  };
  const num = (
    parent: Record<string, unknown>,
    key: string,
    path: string,
    min: number,
    max: number,
  ): void => {
    const v = parent[key];
    if (typeof v !== 'number' || !Number.isFinite(v) || v < min || v > max) {
      problems.push(`${path}.${key} must be a number in [${min}, ${max}]`);
    }
  };

  const root = obj(raw, 'config');
  if (
    typeof root.ranker_version !== 'string' ||
    root.ranker_version.trim() === ''
  ) {
    problems.push('ranker_version must be a non-empty string');
  }
  const filters = obj(root.filters, 'filters');
  num(filters, 'min_remaining_h', 'filters', 0, 48);
  num(filters, 'min_overlap_h', 'filters', 0, 48);
  const soft = obj(root.soft_range, 'soft_range');
  num(soft, 'soft_age_years', 'soft_range', 0, 47);
  num(soft, 'soft_distance_pct', 'soft_range', 0, 400);
  const weights = obj(root.weights, 'weights');
  for (const k of ['fit', 'time', 'reliability', 'distance']) {
    num(weights, k, 'weights', 0, 1);
  }
  const fit = obj(root.fit_weights, 'fit_weights');
  for (const k of ['motivation', 'vibe', 'profile']) {
    num(fit, k, 'fit_weights', 0, 1);
  }
  const table = obj(root.motivation_table, 'motivation_table');
  for (const a of CHECK_IN_MOTIVATIONS) {
    const row = obj(table[a], `motivation_table.${a}`);
    for (const b of CHECK_IN_MOTIVATIONS) {
      num(row, b, `motivation_table.${a}`, 0, 1);
    }
  }
  if (problems.length === 0) {
    for (const a of CHECK_IN_MOTIVATIONS) {
      for (const b of CHECK_IN_MOTIVATIONS) {
        const t = table as Record<string, Record<string, number>>;
        if (t[a][b] !== t[b][a]) {
          problems.push(`motivation_table is not symmetric at ${a}/${b}`);
        }
      }
    }
  }
  const rel = obj(root.reliability, 'reliability');
  for (const k of [
    'new',
    'attended_1',
    'attended_2plus',
    'booked_none_attended',
  ]) {
    num(rel, k, 'reliability', 0, 1);
  }
  const time = obj(root.time_weights, 'time_weights');
  num(time, 'soon', 'time_weights', 0, 1);
  num(time, 'length', 'time_weights', 0, 1);
  num(root, 'time_horizon_h', 'config', 1, 168);
  num(root, 'time_full_overlap_h', 'config', 0.25, 48);
  num(root, 'profile_neutral', 'config', 0, 1);
  num(root, 'fit_gate', 'config', 0, 1);
  const mult = obj(root.multipliers, 'multipliers');
  for (const k of ['liked_you', 'new_user', 'not_now', 'soft_match']) {
    num(mult, k, 'multipliers', 0.01, 10);
  }
  num(mult, 'new_user_checkins', 'multipliers', 0, 100);
  num(mult, 'cap', 'multipliers', 1, 10);
  const random = obj(root.random, 'random');
  num(random, 'share', 'random', 0.01, 1);
  num(random, 'first_position', 'random', 4, 1000);

  return problems.length > 0
    ? { error: problems.join('; ') }
    : { config: raw as RankerConfig };
}
