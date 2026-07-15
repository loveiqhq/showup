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
