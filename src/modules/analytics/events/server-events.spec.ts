import {
  ACCOUNT_CREATED,
  CHECK_IN_CREATED,
  LIKE_SENT,
  LIKE_EXPIRED,
  LIKE_WITHDRAWN,
  MATCH_CREATED,
  NOTIFICATION_SENT,
  accountCreatedEvent,
  checkInCreatedEvent,
  likeSentEvent,
  likeExpiredEvent,
  likeWithdrawnEvent,
  matchCreatedEvent,
  notificationSentEvent,
  SEARCH_FEED_RANKED,
  searchFeedRankedEvent,
} from './server-events';

describe('server analytics events', () => {
  it('like_sent carries the registry payload: position, attended count, message flag, surface', () => {
    expect(
      likeSentEvent({ hasMessage: true, targetAttendedCount: 4, position: 2 }),
    ).toEqual({
      eventName: LIKE_SENT,
      properties: {
        position: 2,
        target_attended_count: 4,
        has_message: true,
        surface: 'profile',
      },
    });
  });

  it('like_sent carries the feed it came from when the app sends one (Matching 01)', () => {
    expect(
      likeSentEvent({
        hasMessage: false,
        targetAttendedCount: 0,
        feedRequestId: 'f1',
      }).properties,
    ).toEqual({
      target_attended_count: 0,
      has_message: false,
      surface: 'profile',
      feed_request_id: 'f1',
    });
  });

  it('like_sent leaves position out when the app did not send one, and never the text', () => {
    const event = likeSentEvent({ hasMessage: false, targetAttendedCount: 0 });
    expect(event.properties).toEqual({
      target_attended_count: 0,
      has_message: false,
      surface: 'profile',
    });
    expect(Object.keys(event.properties)).not.toContain('message');
  });

  it('like_withdrawn names the match for matched, and none for no_overlap', () => {
    expect(likeWithdrawnEvent({ matchId: 'm1', reason: 'matched' })).toEqual({
      eventName: LIKE_WITHDRAWN,
      properties: { match_id: 'm1', reason: 'matched' },
    });
    expect(
      likeWithdrawnEvent({ matchId: null, reason: 'no_overlap' }).properties,
    ).toEqual({ match_id: null, reason: 'no_overlap' });
  });

  it('search_feed_ranked carries the registry payload, ids and numbers only', () => {
    const item = {
      profile_id: 'p1',
      position: 1,
      score: 0.698,
      fit: 0.628,
      motivation_fit: 0.6,
      vibe_fit: 0.667,
      profile_fit: 0.75,
      time: 0.838,
      reliability: 0.9,
      distance: 0.5,
      boosts: ['liked_you' as const],
      is_explore: false,
      soft_match: false,
    };
    expect(
      searchFeedRankedEvent({
        feedRequestId: 'f1',
        rankerVersion: 'launch-2026-10',
        layout: 'grid',
        isPremium: true,
        eligibleCount: 7,
        items: [item],
      }),
    ).toEqual({
      eventName: SEARCH_FEED_RANKED,
      properties: {
        feed_request_id: 'f1',
        ranker_version: 'launch-2026-10',
        layout: 'grid',
        is_premium: true,
        eligible_count: 7,
        items: [item],
      },
    });
  });

  it('like_expired carries the hours open, to the hundredth, and the reason', () => {
    expect(
      likeExpiredEvent({ hoursOpen: 5.25678, reason: 'out_of_time' }),
    ).toEqual({
      eventName: LIKE_EXPIRED,
      properties: { hours_open: 5.26, reason: 'out_of_time' },
    });
  });

  it('match_created carries the match id', () => {
    expect(matchCreatedEvent({ matchId: 'm1' })).toEqual({
      eventName: MATCH_CREATED,
      properties: { match_id: 'm1' },
    });
  });

  it('check_in_created flags whether a location was attached', () => {
    expect(checkInCreatedEvent({ hasLocation: true })).toEqual({
      eventName: CHECK_IN_CREATED,
      properties: { has_location: true },
    });
  });

  it('notification_sent carries channel + type, never message content', () => {
    expect(notificationSentEvent('push', 'date_reminder')).toEqual({
      eventName: NOTIFICATION_SENT,
      properties: { channel: 'push', notification_type: 'date_reminder' },
    });
  });

  it('account_created carries the auth method', () => {
    expect(accountCreatedEvent('phone')).toEqual({
      eventName: ACCOUNT_CREATED,
      properties: { auth_method: 'phone' },
    });
    expect(accountCreatedEvent('apple')).toEqual({
      eventName: ACCOUNT_CREATED,
      properties: { auth_method: 'apple' },
    });
  });
});
