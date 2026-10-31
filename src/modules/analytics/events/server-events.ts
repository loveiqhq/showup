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
export const LIKE_EXPIRED = 'like_expired';
export const SEARCH_FEED_RANKED = 'search_feed_ranked';

/** One ranked profile in `search_feed_ranked.items`: ids and 0-1 numbers, never a motivation or vibe. */
export interface SearchFeedItem {
  profile_id: string;
  position: number;
  /** The BASE score, before multipliers. The final one is reproducible from this, `boosts`,
   * `soft_match` and the config of `ranker_version`; there is deliberately no second score field. */
  score: number;
  fit: number;
  motivation_fit: number;
  vibe_fit: number;
  profile_fit: number;
  time: number;
  reliability: number;
  distance: number;
  boosts: Array<'liked_you' | 'new_user' | 'not_now'>;
  is_explore: boolean;
  soft_match: boolean;
}

/**
 * `search_feed_ranked` (Matching 01, family I): once per feed load and re-rank, for the viewer. The
 * training record for the later matching phases. `eligible_count` is after the filters and before the
 * gate, so held-back profiles count. Server-side only; the client never computes or logs a score.
 */
export function searchFeedRankedEvent(opts: {
  feedRequestId: string;
  rankerVersion: string;
  layout: 'one_up' | 'grid';
  isPremium: boolean;
  eligibleCount: number;
  items: SearchFeedItem[];
}): AnalyticsEventSpec {
  return {
    eventName: SEARCH_FEED_RANKED,
    properties: {
      feed_request_id: opts.feedRequestId,
      ranker_version: opts.rankerVersion,
      layout: opts.layout,
      is_premium: opts.isPremium,
      eligible_count: opts.eligibleCount,
      items: opts.items,
    },
  };
}

/**
 * `like_expired` (family I, T2): a sent like closed without an answer -- the negative label for the
 * like-back estimate. For the sender. Not fired for a declined like (`pass_sent` covers that), nor when
 * the SENDER's own match closed it: that is their withdrawal, not the target leaving it unanswered.
 */
export function likeExpiredEvent(opts: {
  hoursOpen: number;
  reason: 'check_in_ended' | 'target_matched' | 'out_of_time';
}): AnalyticsEventSpec {
  return {
    eventName: LIKE_EXPIRED,
    properties: {
      hours_open: Math.round(opts.hoursOpen * 100) / 100,
      reason: opts.reason,
    },
  };
}

/**
 * `like_sent` (registry 1.4.29): once per like saved -- never again for a retry of the same one.
 * `surface` is always `profile`: every like is made on the full profile (§35; `grid_card` retired,
 * `likes_received` is pass-only). `position` is the app's, 1-based in this Search visit, and absent
 * when an older app did not send it. Show-up rides as `target_attended_count`, never a band.
 * `feed_request_id` (1.4.22) joins the like to the `search_feed_ranked` that placed the profile;
 * absent, like `position`, until the app sends it.
 */
export function likeSentEvent(opts: {
  hasMessage: boolean;
  targetAttendedCount: number;
  position?: number | null;
  feedRequestId?: string | null;
}): AnalyticsEventSpec {
  return {
    eventName: LIKE_SENT,
    properties: {
      ...(opts.position != null ? { position: opts.position } : {}),
      target_attended_count: opts.targetAttendedCount,
      has_message: opts.hasMessage,
      surface: 'profile',
      ...(opts.feedRequestId ? { feed_request_id: opts.feedRequestId } : {}),
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
