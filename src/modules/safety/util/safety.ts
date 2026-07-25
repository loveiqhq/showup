/**
 * Epic 12 — Safety & Moderation primitives (pure logic, no I/O).
 *
 * Kept separate from the services so the decision rules — who is hidden from discovery, how the
 * two-directional block list is combined, and when a verification attempt passes — can be unit
 * tested without a database.
 */

/**
 * The safety standing carried by an account, profile, or photo (SHOWUP-79).
 *  - active         : approved / in good standing
 *  - pending_review : waiting to be checked (still visible)
 *  - rejected       : content turned down by staff
 *  - limited        : partially restricted
 *  - banned         : shut out entirely
 */
export enum ModerationStanding {
  Active = 'active',
  PendingReview = 'pending_review',
  Rejected = 'rejected',
  Limited = 'limited',
  Banned = 'banned',
}

/** What a moderation standing can be attached to (SHOWUP-79). */
export enum ModerationSubjectType {
  User = 'user',
  Profile = 'profile',
  Photo = 'photo',
}

/**
 * How a block came to be (SHOWUP-77). Recorded on each block so staff can trace *why* it happened.
 * `manual` is a direct block by the user; the rest are the app flows that also create a block:
 *  - not_interested   : tapped "Not interested" during search (Epic 6)
 *  - felt_unsafe       : "I feel unsafe" during a date → AI safety call + date ended (Epic 7/8)
 *  - not_as_claimed    : "My date is not who they claimed to be" → chose to leave (Epic 7/8)
 *  - no_show           : "I waited, they did not show up" → chose to leave (Epic 7/8)
 *  - reported_other    : "Something else" → chose to leave (Epic 7/8)
 *  - date_cancelled    : a date was cancelled before it happened (Epic 7)
 *  - ended_date        : "End date" (heading out early / no connection) (Epic 7/8)
 *  - date_review       : "My date was not my vibe" in the date review (Epic 8)
 */
export enum BlockSource {
  Manual = 'manual',
  NotInterested = 'not_interested',
  FeltUnsafe = 'felt_unsafe',
  NotAsClaimed = 'not_as_claimed',
  NoShow = 'no_show',
  ReportedOther = 'reported_other',
  DateCancelled = 'date_cancelled',
  EndedDate = 'ended_date',
  DateReview = 'date_review',
}

/** Standings that must be left out of discovery and matching (SHOWUP-79). */
export const HIDDEN_FROM_DISCOVERY: ModerationStanding[] = [
  ModerationStanding.Limited,
  ModerationStanding.Banned,
];

/** Whether a user with this standing may be surfaced to others. */
export function isDiscoverable(standing: ModerationStanding): boolean {
  return !HIDDEN_FROM_DISCOVERY.includes(standing);
}

/**
 * Combine the two directions of blocking into one de-duplicated list: people the viewer has blocked
 * plus people who have blocked the viewer. Either direction must hide the other person, so the feed
 * and matching filter against the union.
 */
export function dedupeBlockIds(
  blockedByViewer: string[],
  blockedTheViewer: string[],
): string[] {
  return [...new Set([...blockedByViewer, ...blockedTheViewer])];
}

/** The result of a verification attempt (SHOWUP-110). */
export enum VerificationOutcome {
  Verified = 'verified',
  Rejected = 'rejected',
}

/** The signals a verification provider returns for a single attempt. */
export interface VerificationSignals {
  /** The live check confirmed a real, present human (not a photo/replay). */
  livenessPassed: boolean;
  /** The live selfie matched the person's own profile photos. */
  faceMatched: boolean;
}

/** A person is verified only when the live check AND the face match both pass. */
export function evaluateVerification(
  signals: VerificationSignals,
): VerificationOutcome {
  return signals.livenessPassed && signals.faceMatched
    ? VerificationOutcome.Verified
    : VerificationOutcome.Rejected;
}
