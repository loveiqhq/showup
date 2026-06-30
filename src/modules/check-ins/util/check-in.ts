/** Lifecycle state of a check-in. "available" is the live/checked-in state. */
export enum CheckInStatus {
  Available = 'available',
  Cancelled = 'cancelled',
  Expired = 'expired',
}

/** A check-in must be available for at least this long and at most this long. */
export const MIN_WINDOW_MINUTES = 15;
export const MAX_WINDOW_HOURS = 48;

/**
 * Validates an availability window. Returns an error message, or null if the window is valid.
 * Pure (no DB) so it can be unit-tested; callers supply the current time.
 */
export function availabilityWindowError(
  start: Date,
  end: Date,
  now: Date,
): string | null {
  if (end <= start) return 'Availability end must be after the start';
  if (end <= now) return 'Availability window must be in the future';
  const minutes = (end.getTime() - start.getTime()) / 60_000;
  if (minutes < MIN_WINDOW_MINUTES) {
    return `Availability window must be at least ${MIN_WINDOW_MINUTES} minutes`;
  }
  if (minutes > MAX_WINDOW_HOURS * 60) {
    return `Availability window cannot exceed ${MAX_WINDOW_HOURS} hours`;
  }
  return null;
}

/** The fields needed to decide whether a check-in is currently active. */
export interface CheckInWindow {
  status: CheckInStatus;
  availabilityStart: Date;
  availabilityEnd: Date;
}

/**
 * A check-in is active right now if it is still "available" and the current time falls inside its
 * window. This is the single source of truth for "active", used by both the matching query and the
 * fetch-my-check-in endpoint — so a check-in past its window reads as inactive immediately (lazy
 * expiry), even before a background job marks it expired.
 */
export function isActiveCheckIn(c: CheckInWindow, now: Date): boolean {
  return (
    c.status === CheckInStatus.Available &&
    now >= c.availabilityStart &&
    now < c.availabilityEnd
  );
}
