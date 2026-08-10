import { DateStatus } from './date-lifecycle';
import { dateLocksUser, isLockedFromMatching } from './date-lock';

const row = (over: Partial<Parameters<typeof dateLocksUser>[0]> = {}) => ({
  status: DateStatus.Confirmed,
  userAId: 'A',
  userBId: 'B',
  aConfirmedHappened: false,
  bConfirmedHappened: false,
  ...over,
});

describe('dateLocksUser', () => {
  it('locks a participant on a confirmed date they have not reviewed', () => {
    expect(dateLocksUser(row(), 'A')).toBe(true);
    expect(dateLocksUser(row(), 'B')).toBe(true);
  });

  it('releases a participant once they have reviewed their own side', () => {
    expect(dateLocksUser(row({ aConfirmedHappened: true }), 'A')).toBe(false);
  });

  it('still locks the other participant who has not reviewed', () => {
    expect(dateLocksUser(row({ aConfirmedHappened: true }), 'B')).toBe(true);
  });

  it('does not lock on a cancelled date', () => {
    expect(dateLocksUser(row({ status: DateStatus.Cancelled }), 'A')).toBe(false);
  });

  it('does not lock on a completed date', () => {
    expect(
      dateLocksUser(
        row({
          status: DateStatus.Completed,
          aConfirmedHappened: true,
          bConfirmedHappened: true,
        }),
        'A',
      ),
    ).toBe(false);
  });

  it('does not lock a user who is not on the date', () => {
    expect(dateLocksUser(row(), 'C')).toBe(false);
  });
});

describe('isLockedFromMatching', () => {
  it('is locked when any date locks the user', () => {
    expect(
      isLockedFromMatching([row({ status: DateStatus.Cancelled }), row()], 'A'),
    ).toBe(true);
  });

  it('is not locked when no date locks the user', () => {
    expect(
      isLockedFromMatching(
        [
          row({ aConfirmedHappened: true }),
          row({ status: DateStatus.Cancelled }),
        ],
        'A',
      ),
    ).toBe(false);
  });

  it('is not locked with no dates', () => {
    expect(isLockedFromMatching([], 'A')).toBe(false);
  });
});
