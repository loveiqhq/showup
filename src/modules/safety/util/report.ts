/**
 * Epic 12 — Reporting primitives (pure logic, no I/O) for SHOWUP-78.
 *
 * A report is one person flagging another. The reason a person may pick depends on WHERE they are:
 * during an active date (safety), or in the review-your-date flow (how the date went). The reason set
 * is deliberately not final yet — new reasons can be added to the enums + the map below.
 */

/** Where the report is being made from. */
export enum ReportContext {
  ActiveDate = 'active_date',
  DateReview = 'date_review',
}

/** The fixed set of reasons a person can pick. */
export enum ReportReason {
  // Active date (safety):
  FeltInDanger = 'felt_in_danger',
  FeltUncomfortable = 'felt_uncomfortable',
  // Review your date:
  ProfileMismatch = 'profile_mismatch',
  ShowedUpLate = 'showed_up_late',
}

/** How late the other person was (only used with the "showed up late" reason). */
export enum LatenessBucket {
  Late5 = 'late_5',
  Late10 = 'late_10',
  Late15 = 'late_15',
  Late15Plus = 'late_15_plus',
}

/** How far along a report is, from newly submitted through to resolved. */
export enum ReportStatus {
  Submitted = 'submitted',
  Reviewing = 'reviewing',
  Resolved = 'resolved',
  Dismissed = 'dismissed',
}

/** Which reasons are valid in which context. */
export const REASONS_BY_CONTEXT: Record<ReportContext, ReportReason[]> = {
  [ReportContext.ActiveDate]: [
    ReportReason.FeltInDanger,
    ReportReason.FeltUncomfortable,
  ],
  [ReportContext.DateReview]: [
    ReportReason.ProfileMismatch,
    ReportReason.ShowedUpLate,
  ],
};

/** Whether a reason is allowed to be picked in a given context. */
export function isValidReasonForContext(
  context: ReportContext,
  reason: ReportReason,
): boolean {
  return REASONS_BY_CONTEXT[context].includes(reason);
}

/** Whether a lateness bucket is meaningful for this reason (only "showed up late"). */
export function latenessAppliesTo(reason: ReportReason): boolean {
  return reason === ReportReason.ShowedUpLate;
}
