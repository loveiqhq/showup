/**
 * Date search, story 01 (SHOWUP-184): the rules the Search payload is built from, as plain
 * functions so each is tested on its own (`search.spec.ts`).
 *
 * Three of them exist on the phones too and must agree with them: the 7am Check-In day
 * (`CheckInRules.kt` / `.swift`, `checkInDays`), the rounded distance (`searchDistance`) and the
 * meet hour. The server computes the payload so the phone never needs an exact distance or the
 * other person's hours -- "Hidden fields never reach the client" and "Distance arrives rounded".
 */

/** One run of committed hours (a `check_ins` row): `[start, end)`. */
export interface HourSpan {
  start: Date;
  end: Date;
}

const HOUR_MS = 3_600_000;

/** "A Check-In day runs 7am → 7am" (Check-In 02). The same constant as `CHECK_IN_DAY_START`. */
export const CHECK_IN_DAY_START_HOUR = 7;

/**
 * Distance as the hero shows it: `0` for "< 1 km", otherwise whole kilometres (`~N km`). Never the
 * exact value -- this is all the client receives.
 *
 * Under one km is decided on the metres, not on the rounding: 900 m rounds to 1 km and is still
 * "< 1 km".
 */
export function roundedDistanceKm(meters: number): number {
  if (!(meters >= 1000)) return 0;
  return Math.round(meters / 1000);
}

/**
 * The earliest hour both people can meet: the first whole committed hour that both Check-Ins hold
 * and that has not started. Null when there is none -- "no overlap left", which closes a like
 * (`no_overlap`, Search 03).
 *
 * WHOLE HOURS COUNTED FROM EACH SPAN'S OWN START, not from the UTC hour. Every span is a run of
 * hours the phone laid out on its local clock, so in a zone with a half-hour offset (India,
 * Newfoundland) every span starts at :30 UTC. Stepping from the span's start keeps the slot the
 * person actually picked.
 *
 * AN HOUR THAT HAS STARTED IS NEVER A MEET TIME, as on the Check-In grid ("Today's first slot is the
 * next full hour"): 19:00 at 19:20 is gone; the next candidate is 20:00, if both still hold it.
 */
export function earliestMeetAt(
  viewer: readonly HourSpan[],
  target: readonly HourSpan[],
  now: Date,
): Date | null {
  let best: number | null = null;
  for (const a of viewer) {
    for (const b of target) {
      const from = Math.max(a.start.getTime(), b.start.getTime());
      const to = Math.min(a.end.getTime(), b.end.getTime());
      if (to - from < HOUR_MS) continue;
      // Hours step from the later span's start, which is the one that bounds the overlap.
      let slot = from;
      if (slot <= now.getTime()) {
        slot += Math.ceil((now.getTime() - slot + 1) / HOUR_MS) * HOUR_MS;
      }
      if (slot + HOUR_MS > to) continue;
      if (best == null || slot < best) best = slot;
    }
  }
  return best == null ? null : new Date(best);
}

/** Whether `zone` is an IANA time zone this runtime knows. */
export function isTimeZone(zone: string | null | undefined): zone is string {
  if (!zone) return false;
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: zone });
    return true;
  } catch {
    return false;
  }
}

interface LocalParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
}

function localParts(instant: Date, zone: string): LocalParts {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: zone,
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: 'numeric',
    minute: 'numeric',
    second: 'numeric',
    hourCycle: 'h23',
  }).formatToParts(instant);
  const get = (type: Intl.DateTimeFormatPartTypes): number =>
    Number(parts.find((p) => p.type === type)?.value);
  return {
    year: get('year'),
    month: get('month'),
    day: get('day'),
    hour: get('hour'),
    minute: get('minute'),
    second: get('second'),
  };
}

/**
 * The Check-In day an instant falls in, as a sortable day number: the local date of its 7am, so
 * 01:00 belongs to the evening before. Read from the WALL CLOCK, not by subtracting seven hours: on
 * the night the clocks change, 7am local is not seven hours after midnight.
 */
function checkInDayNumber(instant: Date, zone: string): number {
  const p = localParts(instant, zone);
  const day = Date.UTC(p.year, p.month - 1, p.day) / (24 * HOUR_MS);
  return p.hour >= CHECK_IN_DAY_START_HOUR ? day : day - 1;
}

/**
 * `today` or `tomorrow` for the meet card and the Be sure line, in the VIEWER's zone, by the
 * Check-In's own day: Today is the 7am → 7am block holding the next full hour (`checkInDays` on the
 * phones). So 01:00 tonight is `today`, and at 06:20 the next full hour is 7am and Today is the new
 * day.
 *
 * Only these two can occur: both Check-Ins cover at most the viewer's Today and Tomorrow, because
 * the viewer committed no later than now. A later day (a clock gone wrong) still reads `tomorrow`
 * rather than a word the copy does not have.
 */
export function meetDay(
  meetAt: Date,
  now: Date,
  zone: string,
): 'today' | 'tomorrow' {
  const p = localParts(now, zone);
  const intoHour = (p.minute * 60 + p.second) * 1000 + now.getMilliseconds();
  const nextFullHour = new Date(now.getTime() - intoHour + HOUR_MS);
  return checkInDayNumber(meetAt, zone) <= checkInDayNumber(nextFullHour, zone)
    ? 'today'
    : 'tomorrow';
}

/** The Essentials rows, in the ticket's order, each one a §1 field. */
export const ESSENTIAL_FIELDS = [
  'height',
  'gender',
  'orientation',
  'dating_language',
  'education',
  'religion',
  'politics',
] as const;
