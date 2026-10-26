import { LikeCloseReason } from '../../matching/util/match';

/**
 * The rules of Search 02's availability re-check and Search 03's Likes list, kept apart from the
 * queries so they are tested with no database.
 */

/** Whether two people can still meet, as the grid and the Likes tab re-check it (Search 02 ⑲). */
export enum Availability {
  Available = 'available',
  Matched = 'matched',
  NoOverlap = 'no_overlap',
}

/** One person's availability for the viewer, with the earliest hour when there is one. */
export interface AvailabilityOf {
  status: Availability;
  meetAt: Date | null;
}

/**
 * One person's status from what is known about them:
 *
 * - someone who can never be met -- gone, hidden, blocked either way, an invisible profile -- is
 *   `no_overlap`: the card says there is no time left to meet, which is true, and never why;
 * - someone with a date (a match in their current Check-In, or one still locking them) is `matched`;
 * - otherwise `available` at the earliest hour both still hold, or `no_overlap` without one.
 */
export function availabilityFrom(opts: {
  reachable: boolean;
  hasDate: boolean;
  meetAt: Date | null;
}): AvailabilityOf {
  if (!opts.reachable) return { status: Availability.NoOverlap, meetAt: null };
  if (opts.hasDate) return { status: Availability.Matched, meetAt: null };
  return opts.meetAt
    ? { status: Availability.Available, meetAt: opts.meetAt }
    : { status: Availability.NoOverlap, meetAt: null };
}

/** The reason a like closes with when its sender is in this state; null while they are available. */
export function closeReasonFor(status: Availability): LikeCloseReason | null {
  switch (status) {
    case Availability.Matched:
      return LikeCloseReason.Matched;
    case Availability.NoOverlap:
      return LikeCloseReason.NoOverlap;
    default:
      return null;
  }
}

/**
 * The Likes list's order for open likes (Search 03 ④): "earliest meet time first -- today before
 * tomorrow, then by time". One instant says both. Then the nearest, then a stable tie-break.
 */
export function compareOpenLikes(
  a: { meetAt: Date; meters: number; senderId: string },
  b: { meetAt: Date; meters: number; senderId: string },
): number {
  return (
    a.meetAt.getTime() - b.meetAt.getTime() ||
    a.meters - b.meters ||
    a.senderId.localeCompare(b.senderId)
  );
}

/** Closed cards go last, the one that closed first leading. */
export function compareClosedLikes(
  a: { closedAt: Date; senderId: string },
  b: { closedAt: Date; senderId: string },
): number {
  return (
    a.closedAt.getTime() - b.closedAt.getTime() ||
    a.senderId.localeCompare(b.senderId)
  );
}

/** Drops undefined keys, so an absent value is absent from the JSON rather than `null`. */
export function dropAbsent<T extends object>(value: T): T {
  return Object.fromEntries(
    Object.entries(value).filter(([, v]) => v !== undefined),
  ) as T;
}
