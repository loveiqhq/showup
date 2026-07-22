/**
 * Epic 7 — date lifecycle state machine (SHOWUP-55).
 *
 * A date is created already "confirmed" (the app proposes a fixed time & venue on a match, so there
 * is no "scheduled/awaiting confirmation" stage). From there it can only move in ways that make
 * sense; every other move is refused. Callers validate with `transitionError` before changing state.
 */
export enum DateStatus {
  Confirmed = 'confirmed',
  Cancelled = 'cancelled',
  Completed = 'completed',
  NoShowReported = 'no_show_reported',
  Disputed = 'disputed',
}

/** For each stage, the stages it is allowed to move to. Empty = terminal. */
const ALLOWED_TRANSITIONS: Record<DateStatus, DateStatus[]> = {
  [DateStatus.Confirmed]: [
    DateStatus.Cancelled,
    DateStatus.Completed,
    DateStatus.NoShowReported,
  ],
  [DateStatus.NoShowReported]: [DateStatus.Disputed, DateStatus.Completed],
  [DateStatus.Disputed]: [DateStatus.Completed, DateStatus.Cancelled],
  [DateStatus.Cancelled]: [],
  [DateStatus.Completed]: [],
};

/** Whether a date may move from `from` to `to`. */
export function canTransition(from: DateStatus, to: DateStatus): boolean {
  return ALLOWED_TRANSITIONS[from]?.includes(to) ?? false;
}

/** An error message if the move is not allowed, or null if it is. */
export function transitionError(
  from: DateStatus,
  to: DateStatus,
): string | null {
  if (from === to) return `The date is already ${to}`;
  if (!canTransition(from, to)) {
    return `A date cannot move from ${from} to ${to}`;
  }
  return null;
}
