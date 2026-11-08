/**
 * Date-lifecycle analytics events (Epic 11, Families K/M/N — SHOWUP-75). Pure builders so the event
 * names and property shapes are defined and tested in one place. These are SERVER events (the date
 * lifecycle lives on the backend), so the "server row is the one to count". No free text or PII ever
 * goes in the properties — cancellation reasons collapse to a boolean.
 */

export const DATE_CONFIRMED = 'date_confirmed';
export const DATE_COMPLETED = 'date_completed';
export const DATE_CANCELLED = 'date_cancelled';
export const COORDINATE_UNLOCKED = 'coordinate_unlocked';
export const DATE_ENDED_IN_CHAT = 'date_ended_in_chat';

export interface DateEventInput {
  id: string;
  venueId?: string | null;
  cancelReason?: string | null;
}

export interface AnalyticsEventSpec {
  eventName: string;
  properties: Record<string, unknown>;
}

/** A date was auto-created (confirmed) for a new match. */
export function dateConfirmedEvent(date: DateEventInput): AnalyticsEventSpec {
  return {
    eventName: DATE_CONFIRMED,
    properties: { date_id: date.id, venue_attached: date.venueId != null },
  };
}

/** Both people confirmed the date happened. */
export function dateCompletedEvent(date: DateEventInput): AnalyticsEventSpec {
  return { eventName: DATE_COMPLETED, properties: { date_id: date.id } };
}

/** A date was cancelled. The reason text is never sent — only whether one was given. */
export function dateCancelledEvent(date: DateEventInput): AnalyticsEventSpec {
  return {
    eventName: DATE_CANCELLED,
    properties: {
      date_id: date.id,
      had_reason: (date.cancelReason ?? null) != null,
    },
  };
}

/**
 * The Date screen's Coordinate card unlocked: meet − 1:30 (Date 01, SHOWUP-193; registry 1.4.32).
 * Server-sent, once per person per date, by the unlock job -- never by the in-place A → B swap on
 * the screen, which sends nothing.
 */
export function coordinateUnlockedEvent(
  minutesBeforeStart: number,
): AnalyticsEventSpec {
  return {
    eventName: COORDINATE_UNLOCKED,
    properties: { minutes_before_start: minutesBeforeStart },
  };
}

/**
 * `date_ended_in_chat` (Date 02, registry 1.4.33, server). Sent once, when either person ends the date
 * from the chat after an "I can't make it today" message: who sent that message, seen from the one who
 * ended it, and the minutes to the latest agreed meet time (negative after it). Never `date_cancelled`.
 */
export function dateEndedInChatEvent(
  dateId: string,
  cantMakeItFrom: 'self' | 'match',
  minutesBeforeStart: number,
): AnalyticsEventSpec {
  return {
    eventName: DATE_ENDED_IN_CHAT,
    properties: {
      date_id: dateId,
      cant_make_it_from: cantMakeItFrom,
      minutes_before_start: minutesBeforeStart,
    },
  };
}
