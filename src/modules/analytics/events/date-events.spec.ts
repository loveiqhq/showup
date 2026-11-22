import {
  CHANGE_AGREED,
  CHANGE_EXPIRED,
  CHANGE_SUGGESTED,
  changeAgreedEvent,
  changeExpiredEvent,
  changeSuggestedEvent,
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
  DATE_ENDED,
  PROBLEM_STAY_FLAGGED,
  dateEndedEvent,
  problemStayFlaggedEvent,
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

  it('date_cancelled carries exactly date_id, hours_notice, penalty_points, change_requested (Date 07)', () => {
    const spec = dateCancelledEvent({
      dateId: 'd4',
      hoursNotice: 71.5,
      penaltyPoints: 10,
      changeRequested: true,
    });
    expect(spec).toEqual({
      eventName: DATE_CANCELLED,
      properties: {
        date_id: 'd4',
        hours_notice: 71.5,
        penalty_points: 10,
        change_requested: true,
      },
    });
    expect(Object.keys(spec.properties).sort()).toEqual([
      'change_requested',
      'date_id',
      'hours_notice',
      'penalty_points',
    ]);
  });

  it('coordinate_unlocked carries only minutes_before_start and cause (Date 03, registry 1.4.38)', () => {
    expect(coordinateUnlockedEvent(90, 'time')).toEqual({
      eventName: COORDINATE_UNLOCKED,
      properties: { minutes_before_start: 90, cause: 'time' },
    });
    expect(coordinateUnlockedEvent(180, 'arrival_check_in')).toEqual({
      eventName: COORDINATE_UNLOCKED,
      properties: { minutes_before_start: 180, cause: 'arrival_check_in' },
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

  it('the Date 05 events carry exactly their registry properties, never a place (1.4.28)', () => {
    const suggested = changeSuggestedEvent({
      dateId: 'd1',
      suggestionId: 's1',
      kind: 'time',
      entry: 'chat_chip',
      replaces: 'theirs',
      offsetMin: 30,
      fromSearch: null,
      minutesToDeadline: 90,
      minutesBeforeStart: 120,
    });
    expect(suggested).toEqual({
      eventName: CHANGE_SUGGESTED,
      properties: {
        date_id: 'd1',
        suggestion_id: 's1',
        kind: 'time',
        entry: 'chat_chip',
        replaces: 'theirs',
        offset_min: 30,
        from_search: null,
        minutes_to_deadline: 90,
        minutes_before_start: 120,
      },
    });
    expect(
      changeSuggestedEvent({
        dateId: 'd1',
        suggestionId: 's2',
        kind: 'place',
        entry: 'coordinate_tile',
        replaces: 'none',
        offsetMin: null,
        fromSearch: true,
        minutesToDeadline: 5,
        minutesBeforeStart: 35,
      }).properties,
    ).toMatchObject({ offset_min: null, from_search: true });
    expect(
      changeAgreedEvent({
        dateId: 'd1',
        suggestionId: 's1',
        kind: 'place',
        surface: 'coordinate_card',
        secondsSinceSuggested: 61,
        minutesToDeadline: 10,
      }),
    ).toEqual({
      eventName: CHANGE_AGREED,
      properties: {
        date_id: 'd1',
        suggestion_id: 's1',
        kind: 'place',
        surface: 'coordinate_card',
        seconds_since_suggested: 61,
        minutes_to_deadline: 10,
      },
    });
    expect(changeExpiredEvent('d1', 's1', 'time')).toEqual({
      eventName: CHANGE_EXPIRED,
      properties: { date_id: 'd1', suggestion_id: 's1', kind: 'time' },
    });
  });

  it('date_ended carries the six fields and nothing else', () => {
    expect(
      dateEndedEvent({
        dateId: 'd1',
        endReason: 'partner_no_show',
        entry: 'report',
        enderCheckedIn: true,
        partnerCheckedIn: false,
        minutesVsStart: -5,
      }),
    ).toEqual({
      eventName: DATE_ENDED,
      properties: {
        date_id: 'd1',
        end_reason: 'partner_no_show',
        entry: 'report',
        ender_checked_in: true,
        partner_checked_in: false,
        minutes_vs_start: -5,
      },
    });
  });

  it('problem_stay_flagged carries the date and the minutes', () => {
    expect(problemStayFlaggedEvent('d1', 12)).toEqual({
      eventName: PROBLEM_STAY_FLAGGED,
      properties: { date_id: 'd1', minutes_vs_start: 12 },
    });
  });
});
