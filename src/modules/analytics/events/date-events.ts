/**
 * Date-lifecycle analytics events (Epic 11, Families K/M/N — SHOWUP-75). Pure builders so the event
 * names and property shapes are defined and tested in one place. These are SERVER events (the date
 * lifecycle lives on the backend), so the "server row is the one to count". No free text or PII ever
 * goes in the properties — cancellation reasons collapse to a boolean.
 */

import type { ArrivalEntry } from '../../dates/util/arrival';

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
 * Server-sent, once per person per date -- never by the in-place A → B swap on the screen, which sends
 * nothing. `cause` is `time` from the unlock job and `arrival_check_in` from the first committed
 * check-in that opens the chat early (registry 1.4.38).
 */
export function coordinateUnlockedEvent(
  minutesBeforeStart: number,
  cause: 'time' | 'arrival_check_in',
): AnalyticsEventSpec {
  return {
    eventName: COORDINATE_UNLOCKED,
    properties: { minutes_before_start: minutesBeforeStart, cause },
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

export const ARRIVAL_CONFIRMED = 'arrival_confirmed';
export const ARRIVAL_UNDONE = 'arrival_undone';

/**
 * `arrival_confirmed` (Date 03, registry 1.4.34, server). Once per stored check-in: the entry it came
 * from, whole minutes to the latest agreed meet time (negative before it), and whether the match has
 * no check-in yet. Never a coordinate, an accuracy or a place.
 */
export function arrivalConfirmedEvent(
  dateId: string,
  entry: ArrivalEntry,
  minutesVsStart: number,
  isFirst: boolean,
): AnalyticsEventSpec {
  return {
    eventName: ARRIVAL_CONFIRMED,
    properties: {
      date_id: dateId,
      entry,
      minutes_vs_start: minutesVsStart,
      is_first: isFirst,
    },
  };
}

/** `arrival_undone` (Date 03): the check-in was taken back before Close; seconds since it was made. */
export function arrivalUndoneEvent(
  dateId: string,
  entry: ArrivalEntry,
  secondsSinceConfirm: number,
): AnalyticsEventSpec {
  return {
    eventName: ARRIVAL_UNDONE,
    properties: {
      date_id: dateId,
      entry,
      seconds_since_confirm: secondsSinceConfirm,
    },
  };
}

export const CHANGE_SUGGESTED = 'change_suggested';
export const CHANGE_AGREED = 'change_agreed';
export const CHANGE_EXPIRED = 'change_expired';

/**
 * `change_suggested` (Date 05, registry 1.4.28, server). On the accepted write only; the binding
 * values do not change. `offset_min` is time only (suggested minus the binding time, always > 0),
 * `from_search` place only; the other kind's field is null. Never a place name, address, coordinate
 * or distance.
 */
export function changeSuggestedEvent(p: {
  dateId: string;
  suggestionId: string;
  kind: 'time' | 'place';
  entry: string;
  replaces: 'none' | 'own' | 'theirs';
  offsetMin: number | null;
  fromSearch: boolean | null;
  minutesToDeadline: number;
  minutesBeforeStart: number;
}): AnalyticsEventSpec {
  return {
    eventName: CHANGE_SUGGESTED,
    properties: {
      date_id: p.dateId,
      suggestion_id: p.suggestionId,
      kind: p.kind,
      entry: p.entry,
      replaces: p.replaces,
      offset_min: p.offsetMin,
      from_search: p.fromSearch,
      minutes_to_deadline: p.minutesToDeadline,
      minutes_before_start: p.minutesBeforeStart,
    },
  };
}

/** `change_agreed`: `I agree` accepted. A tap the server rejects fires nothing. */
export function changeAgreedEvent(p: {
  dateId: string;
  suggestionId: string;
  kind: 'time' | 'place';
  surface: 'coordinate_card' | 'thread';
  secondsSinceSuggested: number;
  minutesToDeadline: number;
}): AnalyticsEventSpec {
  return {
    eventName: CHANGE_AGREED,
    properties: {
      date_id: p.dateId,
      suggestion_id: p.suggestionId,
      kind: p.kind,
      surface: p.surface,
      seconds_since_suggested: p.secondsSinceSuggested,
      minutes_to_deadline: p.minutesToDeadline,
    },
  };
}

/** `change_expired`: the expiry job found it still pending at meet - 0:30. A replaced one never expires. */
export function changeExpiredEvent(
  dateId: string,
  suggestionId: string,
  kind: 'time' | 'place',
): AnalyticsEventSpec {
  return {
    eventName: CHANGE_EXPIRED,
    properties: { date_id: dateId, suggestion_id: suggestionId, kind },
  };
}

export const DATE_ENDED = 'date_ended';
export const PROBLEM_STAY_FLAGGED = 'problem_stay_flagged';

/**
 * `date_ended` (Date 06, registry 1.4.36, server). Once, on a successful end only: how it ended (§43),
 * where it started (§45), each person's committed check-in at that moment, and whole minutes from the
 * latest agreed meet time (negative before it). Never a coordinate, a place or free text.
 */
export function dateEndedEvent(p: {
  dateId: string;
  endReason: string;
  entry: string;
  enderCheckedIn: boolean;
  partnerCheckedIn: boolean;
  minutesVsStart: number;
}): AnalyticsEventSpec {
  return {
    eventName: DATE_ENDED,
    properties: {
      date_id: p.dateId,
      end_reason: p.endReason,
      entry: p.entry,
      ender_checked_in: p.enderCheckedIn,
      partner_checked_in: p.partnerCheckedIn,
      minutes_vs_start: p.minutesVsStart,
    },
  };
}

/** `problem_stay_flagged` (Date 06, server): one per "Stay for now" tap, each stored as a flag row. */
export function problemStayFlaggedEvent(
  dateId: string,
  minutesVsStart: number,
): AnalyticsEventSpec {
  return {
    eventName: PROBLEM_STAY_FLAGGED,
    properties: { date_id: dateId, minutes_vs_start: minutesVsStart },
  };
}
