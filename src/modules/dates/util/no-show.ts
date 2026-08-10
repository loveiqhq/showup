/**
 * A no-show is a DATE OUTCOME (someone didn't turn up), deliberately separate from the report /
 * safety flow — it is not a "reason" a person picks when reporting a problem.
 *
 * A no-show can only be reported once the date is actually due — you cannot claim someone failed to
 * show up before the meeting time. Returns an error message, or null once reporting is allowed.
 * Pure so it can be unit-tested; the caller supplies "now". (The separate late-vs-no-show grace
 * rule — design build item 04 — is not decided here.)
 */
export function noShowReportError(scheduledAt: Date, now: Date): string | null {
  if (now < scheduledAt) {
    return 'A no-show can only be reported once the date is due';
  }
  return null;
}
