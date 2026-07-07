/**
 * Epic 6 — matching primitives.
 *
 * A "like" is one person's interest in another. A "match" is created when that interest is mutual.
 * Matches are stored as a canonical ordered pair (smaller user id first) so a mutual like from
 * either direction always maps to the same row and can never produce two matches for one couple.
 */

/** Lifecycle of a like. "active" = pending; "matched" = it produced a match. */
export enum LikeStatus {
  Active = 'active',
  Matched = 'matched',
}

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
