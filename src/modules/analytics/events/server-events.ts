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
export const LIKE_WITHDRAWN = 'like_withdrawn';

/**
 * `like_sent` (registry 1.4.29): once per like saved -- never again for a retry of the same one.
 * `surface` is always `profile`: every like is made on the full profile (§35; `grid_card` retired,
 * `likes_received` is pass-only). `position` is the app's, 1-based in this Search visit, and absent
 * when an older app did not send it. Show-up rides as `target_attended_count`, never a band.
 */
export function likeSentEvent(opts: {
  hasMessage: boolean;
  targetAttendedCount: number;
  position?: number | null;
}): AnalyticsEventSpec {
  return {
    eventName: LIKE_SENT,
    properties: {
      ...(opts.position != null ? { position: opts.position } : {}),
      target_attended_count: opts.targetAttendedCount,
      has_message: opts.hasMessage,
      surface: 'profile',
    },
  };
}

/**
 * `like_withdrawn`: one per open like that closed without a decision (§36). `matched` carries the
 * match that closed it; `no_overlap` has none.
 */
export function likeWithdrawnEvent(opts: {
  matchId: string | null;
  reason: 'matched' | 'no_overlap';
}): AnalyticsEventSpec {
  return {
    eventName: LIKE_WITHDRAWN,
    properties: { match_id: opts.matchId, reason: opts.reason },
  };
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
