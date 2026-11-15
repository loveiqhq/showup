import {
  COORDINATE_UNLOCKED,
  DATE_CANCELLED,
  DATE_COMPLETED,
  DATE_CONFIRMED,
  DATE_ENDED_IN_CHAT,
  ARRIVAL_CONFIRMED,
  ARRIVAL_UNDONE,
  arrivalConfirmedEvent,
  arrivalUndoneEvent,
  coordinateUnlockedEvent,
  dateCancelledEvent,
  dateCompletedEvent,
  dateConfirmedEvent,
  dateEndedInChatEvent,
} from './date-events';

describe('date analytics events', () => {
  it('date_confirmed carries the id and whether a venue was attached', () => {
    expect(dateConfirmedEvent({ id: 'd1', venueId: 'v1' })).toEqual({
      eventName: DATE_CONFIRMED,
      properties: { date_id: 'd1', venue_attached: true },
    });
    expect(dateConfirmedEvent({ id: 'd2', venueId: null })).toEqual({
      eventName: DATE_CONFIRMED,
      properties: { date_id: 'd2', venue_attached: false },
    });
  });

  it('date_completed carries just the id', () => {
    expect(dateCompletedEvent({ id: 'd3' })).toEqual({
      eventName: DATE_COMPLETED,
      properties: { date_id: 'd3' },
    });
  });

  it('date_cancelled flags whether a reason was given (never the reason text)', () => {
    expect(
      dateCancelledEvent({ id: 'd4', cancelReason: 'Something came up' }),
    ).toEqual({
      eventName: DATE_CANCELLED,
      properties: { date_id: 'd4', had_reason: true },
    });
    expect(dateCancelledEvent({ id: 'd5', cancelReason: null })).toEqual({
      eventName: DATE_CANCELLED,
      properties: { date_id: 'd5', had_reason: false },
    });
  });

  it('coordinate_unlocked carries only minutes_before_start (Date 01, registry 1.4.32)', () => {
    expect(coordinateUnlockedEvent(90)).toEqual({
      eventName: COORDINATE_UNLOCKED,
      properties: { minutes_before_start: 90 },
    });
    expect(COORDINATE_UNLOCKED).toBe('coordinate_unlocked');
  });

  it('date_ended_in_chat carries date_id, cant_make_it_from and minutes_before_start, nothing else (Date 02, registry 1.4.33)', () => {
    expect(dateEndedInChatEvent('d6', 'match', 42)).toEqual({
      eventName: DATE_ENDED_IN_CHAT,
      properties: {
        date_id: 'd6',
        cant_make_it_from: 'match',
        minutes_before_start: 42,
      },
    });
    expect(DATE_ENDED_IN_CHAT).toBe('date_ended_in_chat');
  });

  it('arrival_confirmed and arrival_undone carry only their registry properties (Date 03, 1.4.34)', () => {
    expect(
      arrivalConfirmedEvent('d7', 'date_screen' as never, 3, false),
    ).toEqual({
      eventName: ARRIVAL_CONFIRMED,
      properties: {
        date_id: 'd7',
        entry: 'date_screen',
        minutes_vs_start: 3,
        is_first: false,
      },
    });
    expect(arrivalUndoneEvent('d7', 'report' as never, 9)).toEqual({
      eventName: ARRIVAL_UNDONE,
      properties: { date_id: 'd7', entry: 'report', seconds_since_confirm: 9 },
    });
  });
});
