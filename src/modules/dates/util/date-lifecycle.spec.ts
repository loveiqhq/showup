import { DateStatus, canTransition, transitionError } from './date-lifecycle';

describe('date lifecycle state machine', () => {
  it('exposes the five stages', () => {
    expect(DateStatus.Confirmed).toBe('confirmed');
    expect(DateStatus.Cancelled).toBe('cancelled');
    expect(DateStatus.Completed).toBe('completed');
    expect(DateStatus.NoShowReported).toBe('no_show_reported');
    expect(DateStatus.Disputed).toBe('disputed');
  });

  it('allows the moves out of "confirmed"', () => {
    expect(canTransition(DateStatus.Confirmed, DateStatus.Cancelled)).toBe(
      true,
    );
    expect(canTransition(DateStatus.Confirmed, DateStatus.Completed)).toBe(
      true,
    );
    expect(canTransition(DateStatus.Confirmed, DateStatus.NoShowReported)).toBe(
      true,
    );
  });

  it('blocks nonsensical jumps', () => {
    // Confirmed cannot jump straight to disputed
    expect(canTransition(DateStatus.Confirmed, DateStatus.Disputed)).toBe(
      false,
    );
    // Cancelled and completed are terminal
    expect(canTransition(DateStatus.Cancelled, DateStatus.Completed)).toBe(
      false,
    );
    expect(canTransition(DateStatus.Completed, DateStatus.Cancelled)).toBe(
      false,
    );
  });

  it('allows a reported no-show to be disputed or resolved as completed', () => {
    expect(canTransition(DateStatus.NoShowReported, DateStatus.Disputed)).toBe(
      true,
    );
    expect(canTransition(DateStatus.NoShowReported, DateStatus.Completed)).toBe(
      true,
    );
  });

  it('transitionError is null for an allowed move and a message for a blocked one', () => {
    expect(
      transitionError(DateStatus.Confirmed, DateStatus.Completed),
    ).toBeNull();
    expect(
      transitionError(DateStatus.Cancelled, DateStatus.Completed),
    ).not.toBeNull();
  });
});
