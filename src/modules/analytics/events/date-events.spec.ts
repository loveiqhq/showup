import {
  DATE_CANCELLED,
  DATE_COMPLETED,
  DATE_CONFIRMED,
  dateCancelledEvent,
  dateCompletedEvent,
  dateConfirmedEvent,
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
});
