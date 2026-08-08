/**
 * Post-date review timing (Epic 7). A ShowUp date is a fixed-length real-world meet-up; the
 * duration is kept here as a named constant so it can be tuned in one place.
 */
export const DATE_DURATION_MINUTES = 30;

/**
 * A person may only review a date ("confirm it happened" + rate) AFTER it has finished — never
 * before or during. A date finishes DATE_DURATION_MINUTES after its scheduled start. Returns an
 * error message, or null once the review window is open. Pure (no DB) so it can be unit-tested;
 * the caller supplies "now".
 */
export function reviewWindowError(scheduledAt: Date, now: Date): string | null {
  const finishedAt = new Date(
    scheduledAt.getTime() + DATE_DURATION_MINUTES * 60_000,
  );
  if (now < finishedAt) {
    return 'You can only review a date once it has finished';
  }
  return null;
}
