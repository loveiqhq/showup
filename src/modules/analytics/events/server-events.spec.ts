import {
  ACCOUNT_CREATED,
  CHECK_IN_CREATED,
  LIKE_SENT,
  LIKE_WITHDRAWN,
  MATCH_CREATED,
  NOTIFICATION_SENT,
  accountCreatedEvent,
  checkInCreatedEvent,
  likeSentEvent,
  likeWithdrawnEvent,
  matchCreatedEvent,
  notificationSentEvent,
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
