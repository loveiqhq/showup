/**
 * Epic 7 — pre-date chat rules (SHOWUP-115).
 *
 * The two people on a confirmed date can exchange a small number of last-minute messages in the
 * window shortly before the date. A message is never a blank chat line: the sender always picks one
 * of a fixed set of preset reasons, and may optionally attach free text on top of it. The reason tag
 * is what feeds analytics (Epic 11); the free text stays private between the two participants.
 */

/** The preset reasons a person can pick when opening the chat (from the current design mockup). */
export enum ChatReason {
  ChangeMeetTime = 'change_meet_time',
  ChangeLocation = 'change_location',
  OnMyWay = 'on_my_way',
  RunningLate = 'running_late',
  CantFindYou = 'cant_find_you',
  CantMakeItToday = 'cant_make_it_today',
}

/**
 * How long before the date's scheduled time the chat opens. ~1.5 hours. A tunable product setting —
 * kept as a named constant so it can move to config later without touching the logic.
 */
export const CHAT_OPENS_MINUTES_BEFORE = 90;

/** Max length of the optional free-text note attached to a message. */
export const CHAT_MESSAGE_MAX_LENGTH = 1000;

/** The moment the chat becomes available for a date scheduled at `scheduledAt`. */
export function chatOpensAt(scheduledAt: Date): Date {
  return new Date(scheduledAt.getTime() - CHAT_OPENS_MINUTES_BEFORE * 60_000);
}

/**
 * Whether the chat is open at `now` for a date scheduled at `scheduledAt`. The chat opens ~1.5 hours
 * before and stays open through the date itself (people still message "On my way" / "Can't find you"
 * right up to and during the meeting); it is the date's own status that ends the chat, not the clock.
 */
export function isChatOpen(scheduledAt: Date, now: Date = new Date()): boolean {
  return now.getTime() >= chatOpensAt(scheduledAt).getTime();
}

/** An error message if the chat is not open yet, or null if it is. */
export function chatWindowError(
  scheduledAt: Date,
  now: Date = new Date(),
): string | null {
  if (isChatOpen(scheduledAt, now)) return null;
  const opens = chatOpensAt(scheduledAt);
  return `The chat opens about ${CHAT_OPENS_MINUTES_BEFORE} minutes before the date (from ${opens.toISOString()})`;
}
