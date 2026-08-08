/**
 * Date-lifecycle analytics events (Epic 11, Families K/M/N — SHOWUP-75). Pure builders so the event
 * names and property shapes are defined and tested in one place. These are SERVER events (the date
 * lifecycle lives on the backend), so the "server row is the one to count". No free text or PII ever
 * goes in the properties — cancellation reasons collapse to a boolean.
 */

export const DATE_CONFIRMED = 'date_confirmed';
export const DATE_COMPLETED = 'date_completed';
export const DATE_CANCELLED = 'date_cancelled';

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
    properties: { date_id: date.id, had_reason: (date.cancelReason ?? null) != null },
  };
}
