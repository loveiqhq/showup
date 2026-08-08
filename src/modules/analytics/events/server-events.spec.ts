import {
  ACCOUNT_CREATED,
  CHECK_IN_CREATED,
  LIKE_SENT,
  MATCH_CREATED,
  NOTIFICATION_SENT,
  accountCreatedEvent,
  checkInCreatedEvent,
  likeSentEvent,
  matchCreatedEvent,
  notificationSentEvent,
} from './server-events';

describe('server analytics events', () => {
  it('like_sent flags whether a message rode along (never the text)', () => {
    expect(likeSentEvent({ hasMessage: true })).toEqual({
      eventName: LIKE_SENT,
      properties: { has_message: true },
    });
    expect(likeSentEvent({ hasMessage: false })).toEqual({
      eventName: LIKE_SENT,
      properties: { has_message: false },
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
