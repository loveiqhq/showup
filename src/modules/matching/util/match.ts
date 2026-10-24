import { UserStatus } from '../../users/entities/user.entity';

/**
 * Epic 6 — matching primitives.
 *
 * A "like" is one person's interest in another. A "match" is created when that interest is mutual.
 * Matches are stored as a canonical ordered pair (smaller user id first) so a mutual like from
 * either direction always maps to the same row and can never produce two matches for one couple.
 */

/**
 * Lifecycle of a like. "active" = open; "matched" = it produced a match; "withdrawn" = it closed
 * without a decision, for the `LikeCloseReason` stored with it (Date search epic rule 9).
 */
export enum LikeStatus {
  Active = 'active',
  Matched = 'matched',
  Withdrawn = 'withdrawn',
}

/**
 * Why an open like closed without a decision -- §36 `like_close_reason`, the `reason` on
 * `like_withdrawn`. `matched`: either person's other like became mutual first (1 match = 1 date),
 * shown to the recipient as Too late. `no_overlap`: the two Check-Ins have no meet time left,
 * shown as Out of time.
 */
export enum LikeCloseReason {
  Matched = 'matched',
  NoOverlap = 'no_overlap',
}

/**
 * Accounts that can never be liked or surfaced -- by discovery, the Search queue or a like. One list,
 * so the three can never disagree about who is out.
 */
export const INELIGIBLE_USER_STATUSES = [
  UserStatus.Suspended,
  UserStatus.Deleted,
  UserStatus.DeletionPending,
];

/** Lifecycle of a match. */
export enum MatchStatus {
  Active = 'active',
  Expired = 'expired',
  Cancelled = 'cancelled',
}

/**
 * Canonical ordering of a user pair. Returns the two ids with the smaller one first, so a match
 * between A and B is stored the same way regardless of who liked whom first.
 */
export function orderedUserPair(a: string, b: string): [string, string] {
  return a < b ? [a, b] : [b, a];
}
