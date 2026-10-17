/**
 * The 48hrs Check-In flow (SHOWUP-177 epic, stories 178 to 183): the value sets and the pure rules,
 * with no database, so every rule is unit-tested on its own.
 *
 * The value sets mirror `design_handoff_showup/tracking/enums.json` (registry 1.4.25) and are
 * drift-guarded against it in `check-in-flow.spec.ts`:
 *   §30 `check_in_motivation` -- one motivation per Check-In (story 03)
 *   §31 `check_in_vibe`       -- one to five vibes (story 04)
 *   §10 `meet_gender`         -- the gender filter, the §1 gender set (story 05)
 */

/** §30, in list order. Class 1: `non_monogamy` discloses a relationship practice. */
export const CHECK_IN_MOTIVATIONS = [
  'long_term',
  'non_monogamy',
  'short_term',
  'casual',
  'friends',
] as const;
export type CheckInMotivation = (typeof CHECK_IN_MOTIVATIONS)[number];

/** §31, in list order -- three groups (activity · headspace · pacing). Class 0. */
export const CHECK_IN_VIBES = [
  'coffee_chat',
  'casual_drink',
  'grab_food',
  'nature_walk',
  'bouldering_table_tennis',
  'workout',
  'sunset',
  'drinks_music',
  'calm',
  'deep_talk',
  'playful',
  'cozy',
  'spontaneous',
  'curious',
  'quick_hello',
  'low_key',
  'high_energy',
  'no_rush',
  'early_night',
  'meet_now',
] as const;
export type CheckInVibe = (typeof CHECK_IN_VIBES)[number];

/** "Multi-select, at most five, across all groups" (story 04). */
export const MAX_VIBES = 5;

/** §10 `meet_gender` -- the §1 gender set. Class 2: never in a tracking payload. */
export const MEET_GENDERS = ['woman', 'man', 'non_binary', 'other'] as const;
export type MeetGender = (typeof MEET_GENDERS)[number];

/** Age range 18 → 65+, where the top stop is NO upper limit and is stored as null (story 05). */
export const FILTER_AGE_MIN = 18;
export const FILTER_AGE_MAX = 65;

/** Distance 1–50 km in 1 km steps, 10 by default (story 05). */
export const FILTER_DISTANCE_MIN = 1;
export const FILTER_DISTANCE_MAX = 50;
export const FILTER_DISTANCE_DEFAULT = 10;

/** Availability tiers -- the 1st tap and the 2nd tap on a slot (story 02). */
export const SLOT_TIERS = ['preferred', 'available'] as const;
export type SlotTier = (typeof SLOT_TIERS)[number];

/** "100 characters, counted as graphemes, so an emoji is one" (story 06). */
export const UPLIFT_MAX_GRAPHEMES = 100;

/** A Check-In window lasts 48 hours: today and tomorrow. */
export const CHECK_IN_WINDOW_HOURS = 48;

/**
 * The most hour slots a commit can carry: Today from the next full hour to the 6am slot, at most
 * 24, plus Tomorrow's 24. A cap so a malformed body cannot write thousands of rows.
 */
export const MAX_SLOTS = 48;

/** The API's error code for an uplift the server refused for contact info (story 06, state C). */
export const UPLIFT_CONTACT_ERROR_CODE = 'uplift_contact_info';

/** The one string for all three kinds of contact info -- never name which one was found. */
export const UPLIFT_CONTACT_ERROR =
  'No emails, phone numbers or links allowed.';

/** How many user-perceived characters `text` holds: an emoji or a flag is one. */
export function graphemeLength(text: string): number {
  const segmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' });
  return Array.from(segmenter.segment(text)).length;
}

// The link rule's top-level domains, from the reference detector (`screen-checkin-uplift-
// reference.jsx`, `UPLIFT_LINK_RE`). A bare domain such as `myname.de` is a link.
const LINK_TLD_RE =
  /\b[a-z0-9-]+\.(com|de|net|org|io|me|app|co|info|biz|link|ly|gg|tv|eu|at|ch|uk|xyz|online|site)\b/i;

/**
 * Whether `text` carries contact info -- an email, a phone number or a link (story 06). The same
 * rules as the clients' live detector (`upliftContact` in the reference file), which the ticket
 * calls the reference rule set: "the server owns the authoritative one", "the same rules or
 * stricter". The kind found is returned for tests only; the API never names it.
 */
export function upliftContact(text: string): 'email' | 'phone' | 'link' | null {
  const s = text ?? '';
  if (/[^\s@]+@[^\s@]+\.[a-z]{2,}/i.test(s)) return 'email';
  if (/(https?:\/\/|www\.)\S+/i.test(s) || LINK_TLD_RE.test(s)) return 'link';
  // Seven or more digits, with any spaces, dashes, dots, brackets, slashes or a leading `+`.
  const runs = s.match(/\+?\d[\d\s()./-]{5,}\d/g) ?? [];
  if (runs.some((run) => run.replace(/\D/g, '').length >= 7)) return 'phone';
  return null;
}

/**
 * The uplift as stored: trimmed, and null when nothing is left -- "empty is stored as no uplift
 * (null), never `""`" (story 06).
 */
export function normaliseUplift(raw: string | null | undefined): string | null {
  const trimmed = (raw ?? '').trim();
  return trimmed.length === 0 ? null : trimmed;
}

/**
 * The first-Check-In gender filter, from the profile's gender and orientation (story 05's table):
 * straight -> the other of man / woman; gay or lesbian -> own gender; bisexual or pansexual ->
 * woman + man; gender non_binary or other, or orientation other -> all four. Unknown or missing
 * values fall back to all four, so nobody is filtered out by a guess.
 */
export function checkInGenderDefault(
  gender: string | null | undefined,
  orientation: string | null | undefined,
): MeetGender[] {
  const all: MeetGender[] = [...MEET_GENDERS];
  if (gender !== 'woman' && gender !== 'man') return all;
  if (orientation === 'other' || orientation == null) return all;
  if (orientation === 'bisexual' || orientation === 'pansexual') {
    return ['woman', 'man'];
  }
  if (orientation === 'gay' || orientation === 'lesbian') return [gender];
  if (orientation === 'straight') return [gender === 'man' ? 'woman' : 'man'];
  return all;
}

/** One picked hour: it starts at `startsAt` and lasts an hour. */
export interface HourSlot {
  startsAt: Date;
  tier: SlotTier;
}

/** A run of consecutive picked hours of one tier, stored as one availability window. */
export interface SlotWindow {
  start: Date;
  end: Date;
  tier: SlotTier;
}

const HOUR_MS = 60 * 60 * 1000;

/**
 * Slots that are still worth keeping at `now`: an hour that has started is dropped silently --
 * "Today hours that have already started are dropped silently" (story 06, time resolution). The
 * clients already never offer a started hour; this covers the time spent in the flow.
 */
export function upcomingSlots(slots: HourSlot[], now: Date): HourSlot[] {
  return slots.filter((slot) => slot.startsAt.getTime() > now.getTime());
}

/**
 * Consecutive hours of the same tier merged into windows, in time order. 6pm, 7pm and 8pm all
 * preferred become one 6pm-9pm window; a tier change starts a new window, so the backend keeps both
 * tiers "per slot" exactly as picked.
 */
export function slotsToWindows(slots: HourSlot[]): SlotWindow[] {
  const sorted = [...slots].sort(
    (a, b) => a.startsAt.getTime() - b.startsAt.getTime(),
  );
  const windows: SlotWindow[] = [];
  for (const slot of sorted) {
    const last = windows[windows.length - 1];
    if (
      last &&
      last.tier === slot.tier &&
      last.end.getTime() === slot.startsAt.getTime()
    ) {
      last.end = new Date(slot.startsAt.getTime() + HOUR_MS);
    } else {
      windows.push({
        start: slot.startsAt,
        end: new Date(slot.startsAt.getTime() + HOUR_MS),
        tier: slot.tier,
      });
    }
  }
  return windows;
}

/**
 * Why a commit's slots cannot be accepted, or null. Each slot starts on the hour, no two share an
 * hour, and none starts further out than the window could ever cover (now + 48h, plus the 7am day
 * boundary's slack of a day). Started hours are not an error -- they are dropped (see above).
 */
export function slotsError(slots: HourSlot[], now: Date): string | null {
  if (slots.length > MAX_SLOTS)
    return `At most ${MAX_SLOTS} hours can be picked`;
  const seen = new Set<number>();
  const latest = now.getTime() + (CHECK_IN_WINDOW_HOURS + 24) * HOUR_MS;
  for (const slot of slots) {
    const t = slot.startsAt.getTime();
    if (Number.isNaN(t)) return 'Every hour needs a start time';
    if (!startsOnLocalHour(slot.startsAt)) {
      return 'Every hour must start on the hour';
    }
    if (t > latest) return 'An hour is outside the Check-In window';
    if (seen.has(t)) return 'An hour was picked twice';
    seen.add(t);
  }
  return null;
}

/**
 * Whether `date` starts on a whole hour in SOME time zone. UTC offsets are whole hours almost
 * everywhere, but a few are half or quarter hours (India +5:30, Nepal +5:45): a slot that starts on
 * the hour there starts at :30 or :45 in UTC. Any instant on a quarter-hour qualifies.
 */
function startsOnLocalHour(date: Date): boolean {
  return date.getTime() % (15 * 60 * 1000) === 0;
}
