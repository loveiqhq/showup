import { localParts } from '../../search/util/search';

const MINUTE_MS = 60_000;
const SLOT_MS = 15 * MINUTE_MS;
/** A new meet time must leave this much of the match's free time after it. */
const MIN_REMAINING_MS = 30 * MINUTE_MS;

export interface AvailabilityRun {
  start: Date;
  end: Date;
}

/** Local wall clock minus the instant, i.e. the zone's UTC offset at that instant, in ms. */
function offsetMs(instant: Date, zone: string): number {
  const p = localParts(instant, zone);
  const wall = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return wall - Math.floor(instant.getTime() / 1000) * 1000;
}

/**
 * The instant of the NEXT local midnight after `instant` in `zone`. Built from the wall clock, not
 * "+24 h": the day the clocks change is 23 or 25 hours long. Two passes settle the offset, because
 * the offset at midnight can differ from the one at the guess.
 */
export function nextLocalMidnight(instant: Date, zone: string): Date {
  const p = localParts(instant, zone);
  const wallMidnight = Date.UTC(p.year, p.month - 1, p.day + 1);
  let guess = wallMidnight - offsetMs(instant, zone);
  guess = wallMidnight - offsetMs(new Date(guess), zone);
  return new Date(guess);
}

/** The local day `instant` falls in, in `zone`: its midnight and the next one, as instants. */
export function localDay(
  instant: Date,
  zone: string,
): { start: Date; end: Date } {
  const end = nextLocalMidnight(instant, zone);
  // An instant 26 h before the next midnight is always in the previous day (days are 23 to 25 h).
  const dayBefore = new Date(end.getTime() - 26 * 60 * 60_000);
  return { start: nextLocalMidnight(dayBefore, zone), end };
}

/**
 * The meet times a person can suggest instead of `bindingAt` (R6): 15-minute starts `s` with
 * `s > bindingAt`, inside one of the match's availability runs with at least 30 minutes of it left
 * (`run.start <= s <= run.end - 30 min`), and before local midnight of the binding time's day in
 * `tz`. The union over all runs, ascending, each start once.
 */
export function changeSlots(
  bindingAt: Date,
  runs: readonly AvailabilityRun[],
  tz: string,
): Date[] {
  const midnight = nextLocalMidnight(bindingAt, tz).getTime();
  const starts = new Set<number>();
  for (const run of mergeRuns(runs)) {
    const first = Math.max(run.start, bindingAt.getTime() + 1);
    const last = Math.min(run.end - MIN_REMAINING_MS, midnight - 1);
    for (
      // Quarter-hours of the UTC epoch, which are also local quarter-hours: every IANA offset in
      // use is a multiple of 15 minutes.
      let s = Math.ceil(first / SLOT_MS) * SLOT_MS;
      s <= last;
      s += SLOT_MS
    ) {
      starts.add(s);
    }
  }
  return [...starts].sort((a, b) => a - b).map((ms) => new Date(ms));
}

/**
 * Runs that overlap or touch are one stretch of free time: a person free 17:00 to 20:00 (first
 * tap) and 20:00 to 22:00 (second tap) is free from 17:00 to 22:00, so 19:45 is a slot even though
 * neither run alone leaves 30 minutes after it.
 */
function mergeRuns(
  runs: readonly AvailabilityRun[],
): Array<{ start: number; end: number }> {
  const sorted = runs
    .map((r) => ({ start: r.start.getTime(), end: r.end.getTime() }))
    .sort((a, b) => a.start - b.start);
  const merged: Array<{ start: number; end: number }> = [];
  for (const run of sorted) {
    const last = merged[merged.length - 1];
    if (last && run.start <= last.end) last.end = Math.max(last.end, run.end);
    else merged.push(run);
  }
  return merged;
}
