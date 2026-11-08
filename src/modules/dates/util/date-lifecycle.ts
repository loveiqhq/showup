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
  /** Ended from the chat after an "I can't make it today" message (Date 02): no review, no flow 10. */
  EndedInChat = 'ended_in_chat',
}

/**
 * A date that never happened: cancelled, or ended from the chat before anyone met (Date 02). Search
 * reads neither as a past date -- it hides no pair from each other and books nobody a date.
 */
export const NEVER_HAPPENED: readonly DateStatus[] = [
  DateStatus.Cancelled,
  DateStatus.EndedInChat,
];

/** For each stage, the stages it is allowed to move to. Empty = terminal. */
const ALLOWED_TRANSITIONS: Record<DateStatus, DateStatus[]> = {
  [DateStatus.Confirmed]: [
    DateStatus.Cancelled,
    DateStatus.Completed,
    DateStatus.NoShowReported,
    DateStatus.EndedInChat,
  ],
  [DateStatus.NoShowReported]: [DateStatus.Disputed, DateStatus.Completed],
  [DateStatus.Disputed]: [DateStatus.Completed, DateStatus.Cancelled],
  [DateStatus.Cancelled]: [],
  [DateStatus.Completed]: [],
  [DateStatus.EndedInChat]: [],
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
