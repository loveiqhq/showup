/**
 * Server-emitted analytics events for backend actions that already exist (Epic 11). Pure builders,
 * so every event name and property shape lives (and is tested) in one place. Properties are ids and
 * booleans only — never free text, message bodies, or anything special-category.
 *
 * Families: matching/liking (I/J), check-ins (H), notifications (G/J), auth/account (B).
 */

export interface AnalyticsEventSpec {
  eventName: string;
  properties: Record<string, unknown>;
}

// ── Matching / liking ────────────────────────────────────────────────────────
export const LIKE_SENT = 'like_sent';
export const MATCH_CREATED = 'match_created';

export function likeSentEvent(opts: {
  hasMessage: boolean;
}): AnalyticsEventSpec {
  return { eventName: LIKE_SENT, properties: { has_message: opts.hasMessage } };
}

export function matchCreatedEvent(opts: {
  matchId: string;
}): AnalyticsEventSpec {
  return { eventName: MATCH_CREATED, properties: { match_id: opts.matchId } };
}

// ── Check-ins ─────────────────────────────────────────────────────────────────
export const CHECK_IN_CREATED = 'check_in_created';

export function checkInCreatedEvent(opts: {
  hasLocation: boolean;
}): AnalyticsEventSpec {
  return {
    eventName: CHECK_IN_CREATED,
    properties: { has_location: opts.hasLocation },
  };
}

// ── Notifications ─────────────────────────────────────────────────────────────
export const NOTIFICATION_SENT = 'notification_sent';

export function notificationSentEvent(
  channel: 'push' | 'email',
  notificationType: string,
): AnalyticsEventSpec {
  return {
    eventName: NOTIFICATION_SENT,
    properties: { channel, notification_type: notificationType },
  };
}

// ── Auth / account ────────────────────────────────────────────────────────────
export const ACCOUNT_CREATED = 'account_created';

export function accountCreatedEvent(authMethod: string): AnalyticsEventSpec {
  return {
    eventName: ACCOUNT_CREATED,
    properties: { auth_method: authMethod },
  };
}
