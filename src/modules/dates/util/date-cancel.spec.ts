import { cancelRefusal, hoursNotice } from './date-cancel';
import { DateStatus } from './date-lifecycle';

describe('hoursNotice', () => {
  const meetAt = new Date('2026-11-24T19:00:00.000Z');
  it('is hours to the agreed time, three days ahead', () => {
    expect(hoursNotice(new Date('2026-11-21T19:00:00.000Z'), meetAt)).toBe(72);
  });
  it('is two decimals, ten minutes ahead', () => {
    expect(hoursNotice(new Date('2026-11-24T18:50:00.000Z'), meetAt)).toBe(
      0.17,
    );
  });
  it('is negative after the meet time', () => {
    expect(hoursNotice(new Date('2026-11-24T20:30:00.000Z'), meetAt)).toBe(
      -1.5,
    );
  });
  it('never returns negative zero', () => {
    expect(Object.is(hoursNotice(meetAt, meetAt), 0)).toBe(true);
  });
});

describe('cancelRefusal', () => {
  it('allows a confirmed date, canceller not checked in', () => {
    expect(
      cancelRefusal({
        status: DateStatus.Confirmed,
        cancellerCheckedIn: false,
      }),
    ).toBeNull();
  });
  it('refuses a date that is not confirmed', () => {
    for (const status of [
      DateStatus.Cancelled,
      DateStatus.Ended,
      DateStatus.EndedInChat,
    ]) {
      expect(cancelRefusal({ status, cancellerCheckedIn: false })).toBe(
        'date_not_active',
      );
    }
  });
  it('refuses a checked-in canceller', () => {
    expect(
      cancelRefusal({ status: DateStatus.Confirmed, cancellerCheckedIn: true }),
    ).toBe('checked_in');
  });
  it('not-active wins over checked_in', () => {
    expect(
      cancelRefusal({ status: DateStatus.Cancelled, cancellerCheckedIn: true }),
    ).toBe('date_not_active');
  });
});
