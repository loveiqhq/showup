import type { CheckInMotivation } from '../../check-ins/util/check-in-flow';
import type { HourSpan } from '../util/search';
import type { RankerConfig } from './ranker-config';

/**
 * The Search ranker's rules (Matching 01, SHOWUP-190) as plain functions, each tested on its own
 * against the ticket's worked examples (`ranker.spec.ts`). The service gathers the inputs; nothing
 * here reads a database, a clock or a random source it was not handed.
 *
 * NEVER INPUTS: ethnicity, religion, politics, health and photo data are not parameters of anything
 * in this file, and the service never loads them for ranking. Religion and politics are on the
 * profile; the ranker does not read them.
 */

const HOUR_MS = 3_600_000;

/** `in` the stated preference, `soft` only through the soft range, or `out`. */
export type Reach = 'in' | 'soft' | 'out';

/** The rules that can lift or lower a profile after its score -- the tracking `boosts` values. */
export type Boost = 'liked_you' | 'new_user' | 'not_now';

export const MIN_AGE = 18;

/**
 * Age against one person's own range (filter 5). With the dealbreaker off -- and only once the flag
 * `filters_dealbreakers` is on, which the caller folds into `soft` -- the range widens by
 * `soft_age_years` each way, floor 18; a `max_age` of null (65+) stays unlimited.
 */
export function ageReach(
  age: number,
  range: { minAge: number; maxAge: number | null },
  soft: boolean,
  config: RankerConfig,
): Reach {
  const top = range.maxAge ?? Number.POSITIVE_INFINITY;
  if (age >= range.minAge && age <= top) return 'in';
  if (!soft) return 'out';
  const years = config.soft_range.soft_age_years;
  const low = Math.max(MIN_AGE, range.minAge - years);
  return age >= low && age <= top + years ? 'soft' : 'out';
}

/**
 * Straight-line distance against one person's own radius (filter 6). Soft widens the radius by
 * `soft_distance_pct`. Compared in metres, so 12.5 km on a 10 km radius at 25% is in and 12.6 is not.
 */
export function distanceReach(
  meters: number,
  radiusKm: number,
  soft: boolean,
  config: RankerConfig,
): Reach {
  if (meters <= radiusKm * 1000) return 'in';
  if (!soft) return 'out';
  const widened =
    radiusKm * 1000 * (1 + config.soft_range.soft_distance_pct / 100);
  return meters <= widened ? 'soft' : 'out';
}

/**
 * The four one-way checks of filters 5 and 6 together: out if any is out, a soft match if any is
 * soft. Outside on both age and distance is still ONE soft match (one x0.8, not two).
 */
export function combinedReach(reaches: Reach[]): Reach {
  if (reaches.includes('out')) return 'out';
  return reaches.includes('soft') ? 'soft' : 'in';
}

/**
 * Filter 12: `friends` matches only `friends`. Romantic motivations are never filtered against each
 * other. A Check-In with no motivation (made before the Check-In flow stored one) is not filtered.
 */
export function motivationsCompatible(
  a: CheckInMotivation | null,
  b: CheckInMotivation | null,
): boolean {
  if (a == null || b == null) return true;
  return (a === 'friends') === (b === 'friends');
}

/** Hours of a person's chosen hours still ahead at `now` (filter 1). */
export function remainingHours(spans: readonly HourSpan[], now: Date): number {
  return (
    merge(
      spans.map((s) => ({
        start: Math.max(s.start.getTime(), now.getTime()),
        end: s.end.getTime(),
      })),
    ).reduce((sum, s) => sum + (s.end - s.start), 0) / HOUR_MS
  );
}

/**
 * The shared hours still ahead of two people: when the first starts (0 when one is already running)
 * and how many hours are left in total. Null when they share none. Filter 2 and the Time score read
 * this; the meet hour shown on the card is `earliestMeetAt`, which the caller requires as well.
 */
export function overlapAhead(
  a: readonly HourSpan[],
  b: readonly HourSpan[],
  now: Date,
): { startsInH: number; remainingH: number } | null {
  const shared: Array<{ start: number; end: number }> = [];
  for (const x of a) {
    for (const y of b) {
      const start = Math.max(
        x.start.getTime(),
        y.start.getTime(),
        now.getTime(),
      );
      const end = Math.min(x.end.getTime(), y.end.getTime());
      if (end > start) shared.push({ start, end });
    }
  }
  const merged = merge(shared);
  if (merged.length === 0) return null;
  return {
    startsInH: (merged[0].start - now.getTime()) / HOUR_MS,
    remainingH: merged.reduce((sum, s) => sum + (s.end - s.start), 0) / HOUR_MS,
  };
}

/** Intervals sorted and merged, so an hour two spans both hold is counted once. */
function merge(
  spans: Array<{ start: number; end: number }>,
): Array<{ start: number; end: number }> {
  const sorted = spans
    .filter((s) => s.end > s.start)
    .sort((p, q) => p.start - q.start);
  const out: Array<{ start: number; end: number }> = [];
  for (const s of sorted) {
    const last = out[out.length - 1];
    if (last && s.start <= last.end) last.end = Math.max(last.end, s.end);
    else out.push({ ...s });
  }
  return out;
}

/** What one candidate is scored from. Everything is the two people's own data; nothing sensitive. */
export interface ScoreInputs {
  motivation: [CheckInMotivation | null, CheckInMotivation | null];
  vibes: [readonly string[] | null, readonly string[] | null];
  interests: [readonly string[] | null, readonly string[] | null];
  familyAhead: [readonly string[] | null, readonly string[] | null];
  livingAhead: [readonly string[] | null, readonly string[] | null];
  overlap: { startsInH: number; remainingH: number };
  /** B's past dates: booked (the date time has passed, not cancelled) and attended (both confirmed). */
  pastDates: { booked: number; attended: number };
  meters: number;
  /** The two radii as each person set them -- UNWIDENED, so a soft-distance match scores 0 here. */
  radiiKm: [number, number];
}

/** The score and every part of it, 0-1 each: what `search_feed_ranked.items` carries. */
export interface ScoreParts {
  score: number;
  fit: number;
  motivationFit: number;
  vibeFit: number;
  profileFit: number;
  time: number;
  reliability: number;
  distance: number;
}

const clamp01 = (v: number): number => Math.min(1, Math.max(0, v));

/** `shared / min(count_A, count_B)`; null when either person has none. */
function sharedRatio(
  a: readonly string[] | null,
  b: readonly string[] | null,
): number | null {
  if (!a?.length || !b?.length) return null;
  const theirs = new Set(b);
  const shared = new Set(a.filter((x) => theirs.has(x))).size;
  return shared / Math.min(new Set(a).size, theirs.size);
}

/** 1 if two answers share any value, else 0; null when either person skipped the question. */
function anyShared(
  a: readonly string[] | null,
  b: readonly string[] | null,
): number | null {
  if (!a?.length || !b?.length) return null;
  const theirs = new Set(b);
  return a.some((x) => theirs.has(x)) ? 1 : 0;
}

/**
 * `Score = 0.55 Fit + 0.20 Time + 0.15 Reliability + 0.10 Distance`, every weight from `config`.
 *
 * A value one person never gave counts as `profile_neutral` (0.5): "a skipped question neither helps
 * nor hurts". The ticket says so for Profile; a Check-In without a motivation or vibes (made before
 * the flow stored them) is the same situation and gets the same answer.
 */
export function scoreOf(i: ScoreInputs, config: RankerConfig): ScoreParts {
  const neutral = config.profile_neutral;
  const [ma, mb] = i.motivation;
  const motivationFit = ma && mb ? config.motivation_table[ma][mb] : neutral;
  const vibeFit = sharedRatio(i.vibes[0], i.vibes[1]) ?? neutral;
  // Average over the fields BOTH people answered; none -> neutral.
  const answered = [
    sharedRatio(i.interests[0], i.interests[1]),
    anyShared(i.familyAhead[0], i.familyAhead[1]),
    anyShared(i.livingAhead[0], i.livingAhead[1]),
  ].filter((v): v is number => v != null);
  const profileFit =
    answered.length > 0
      ? answered.reduce((s, v) => s + v, 0) / answered.length
      : neutral;
  const fw = config.fit_weights;
  const fit =
    fw.motivation * motivationFit + fw.vibe * vibeFit + fw.profile * profileFit;

  const soon = clamp01(1 - i.overlap.startsInH / config.time_horizon_h);
  const length = clamp01(i.overlap.remainingH / config.time_full_overlap_h);
  const time =
    config.time_weights.soon * soon + config.time_weights.length * length;

  const r = config.reliability;
  const { booked, attended } = i.pastDates;
  const reliability =
    attended >= 2
      ? r.attended_2plus
      : attended === 1
        ? r.attended_1
        : booked === 0
          ? r.new
          : r.booked_none_attended;

  const radius = Math.min(i.radiiKm[0], i.radiiKm[1]);
  const distance = radius > 0 ? clamp01(1 - i.meters / 1000 / radius) : 0;

  const w = config.weights;
  const score =
    w.fit * fit +
    w.time * time +
    w.reliability * reliability +
    w.distance * distance;
  return {
    score,
    fit,
    motivationFit,
    vibeFit,
    profileFit,
    time,
    reliability,
    distance,
  };
}

/**
 * The multipliers multiply, and the PRODUCT is capped -- never a single rule -- so reductions apply
 * in full (lowest x0.56) and boosts stop at x1.5. On Premium `liked_you` is the liked-you block, not
 * a multiplier, so it is listed in `boosts` and multiplies nothing.
 */
export function multiplierOf(
  boosts: readonly Boost[],
  softMatch: boolean,
  premium: boolean,
  config: RankerConfig,
): number {
  const m = config.multipliers;
  let product = 1;
  if (boosts.includes('liked_you') && !premium) product *= m.liked_you;
  if (boosts.includes('new_user')) product *= m.new_user;
  if (boosts.includes('not_now')) product *= m.not_now;
  if (softMatch) product *= m.soft_match;
  return Math.min(product, m.cap);
}

/** One eligible candidate, scored and multiplied, before its place in the feed is decided. */
export interface Scored<T> {
  candidate: T;
  /** `profiles.id`: the last tie-break, and what tracking names. */
  profileId: string;
  parts: ScoreParts;
  /** `score x multiplier`. Reproducible from `score`, `boosts`, `softMatch` and the config. */
  final: number;
  boosts: Boost[];
  softMatch: boolean;
  /** B has an open like on the viewer. */
  likedYou: boolean;
}

export interface Placed<T> extends Scored<T> {
  /** 1-based. */
  position: number;
  isExplore: boolean;
}

/** Final score descending, then Fit, then the lower `profile_id`: the same inputs, the same order. */
export function compareScored<T>(a: Scored<T>, b: Scored<T>): number {
  return (
    b.final - a.final ||
    b.parts.fit - a.parts.fit ||
    (a.profileId < b.profileId ? -1 : a.profileId > b.profileId ? 1 : 0)
  );
}

/**
 * Steps 4 to 6: the gate, the order and the random picks.
 *
 * 1. Premium only: everyone with an open like on the viewer, first, by final score. The gate does not
 *    apply to this block. (On Freemium the like is a multiplier only -- a fixed place would reveal it.)
 * 2. Fit >= `fit_gate`, by final score.
 * 3. Held back (Fit < gate), by final score. The gate alone never empties Search.
 *
 * Random picks fill positions `first_position`, then every `1 / share`-th after it (5, 10, 15 ...),
 * counted after the liked-you block, each a uniformly random above-gate profile not placed yet; the
 * profiles they displace move down. None left above the gate -> no more picks. `random` returns
 * [0, 1), injected so a test can fix it.
 */
export function orderFeed<T>(
  scored: readonly Scored<T>[],
  premium: boolean,
  random: () => number,
  config: RankerConfig,
): Placed<T>[] {
  const block = premium
    ? scored.filter((s) => s.likedYou).sort(compareScored)
    : [];
  const rest = premium ? scored.filter((s) => !s.likedYou) : [...scored];
  const above = rest
    .filter((s) => s.parts.fit >= config.fit_gate)
    .sort(compareScored);
  const held = rest
    .filter((s) => s.parts.fit < config.fit_gate)
    .sort(compareScored);

  const out: Placed<T>[] = block.map((s, i) => ({
    ...s,
    position: i + 1,
    isExplore: false,
  }));
  const step = Math.max(1, Math.round(1 / config.random.share));
  const first = config.random.first_position;
  const remaining = [...above, ...held];
  const aboveLeft = new Set(above);
  for (let slot = 1; remaining.length > 0; slot++) {
    const isPickSlot = slot >= first && (slot - first) % step === 0;
    let next: Scored<T>;
    let explore = false;
    if (isPickSlot && aboveLeft.size > 0) {
      const pool = remaining.filter((s) => aboveLeft.has(s));
      next =
        pool[Math.min(pool.length - 1, Math.floor(random() * pool.length))];
      explore = true;
    } else {
      next = remaining[0];
    }
    remaining.splice(remaining.indexOf(next), 1);
    aboveLeft.delete(next);
    out.push({ ...next, position: out.length + 1, isExplore: explore });
  }
  return out;
}
