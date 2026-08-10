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
 * How long, in minutes, a user may say they need to get ready before heading out.
 * These bounds are intentionally kept here as named constants so they can be tuned (or later
 * moved to runtime config) without touching any caller — the product rule is "min 15, max 60,
 * but flexible". The default is used when the client does not send an explicit value.
 */
export const MIN_PREPARATION_MINUTES = 15;
export const MAX_PREPARATION_MINUTES = 60;
export const DEFAULT_PREPARATION_MINUTES = 30;

/**
 * Validates the user's chosen preparation ("time to get ready") in whole minutes. Returns an error
 * message, or null if valid. Pure so it can be unit-tested and reused by the matching feasibility
 * math (prep time is subtracted from a user's usable window along with travel time).
 */
export function preparationTimeError(minutes: number): string | null {
  if (!Number.isInteger(minutes)) {
    return 'Preparation time must be a whole number of minutes';
  }
  if (minutes < MIN_PREPARATION_MINUTES) {
    return `Preparation time must be at least ${MIN_PREPARATION_MINUTES} minutes`;
  }
  if (minutes > MAX_PREPARATION_MINUTES) {
    return `Preparation time cannot exceed ${MAX_PREPARATION_MINUTES} minutes`;
  }
  return null;
}

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

/** A time span with a start and end, used for overlap checks between availability windows. */
export interface TimeWindow {
  availabilityStart: Date;
  availabilityEnd: Date;
}

/**
 * Whether a proposed window [start, end) overlaps any of the user's existing windows. A person may
 * hold any number of availability windows in a day (no limit), but they must never overlap in time.
 * Windows are half-open, so two that only touch at an edge (11:00–13:00 then 13:00–14:00) do NOT
 * overlap. Pure (no DB) so it can be unit-tested; the service supplies the user's still-live windows.
 */
export function overlapsAnyWindow(
  start: Date,
  end: Date,
  existing: TimeWindow[],
): boolean {
  return existing.some(
    (w) => start < w.availabilityEnd && w.availabilityStart < end,
  );
}

/**
 * Validates that a check-in carries a meeting location. Location is required to become available:
 * without it a person can neither be shown to others nor run the date search (that search is centred
 * on their own check-in location). Returns an error message, or null when both coordinates are set.
 */
export function locationError(
  latitude?: number | null,
  longitude?: number | null,
): string | null {
  if (latitude == null || longitude == null) {
    return 'A meeting location (latitude and longitude) is required to check in';
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
