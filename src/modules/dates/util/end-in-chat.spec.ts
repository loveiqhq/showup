import { cantMakeItFrom } from './end-in-chat';
import { ChatReason } from './pre-date-chat';

describe('cantMakeItFrom (Date 02)', () => {
  const m = (senderId: string, reason: ChatReason | null) => ({
    senderId,
    reason,
  });

  it('is self when the one ending the date sent the "I can\'t make it today" message', () => {
    expect(
      cantMakeItFrom(
        [m('A', ChatReason.RunningLate), m('A', ChatReason.CantMakeIt)],
        'A',
      ),
    ).toBe('self');
  });

  it('is match when the other person sent it', () => {
    expect(
      cantMakeItFrom([m('B', ChatReason.CantMakeIt), m('A', null)], 'A'),
    ).toBe('match');
  });

  it('lets the most recent one decide when both sent one', () => {
    expect(
      cantMakeItFrom(
        [m('A', ChatReason.CantMakeIt), m('B', ChatReason.CantMakeIt)],
        'A',
      ),
    ).toBe('match');
    expect(
      cantMakeItFrom(
        [m('B', ChatReason.CantMakeIt), m('A', ChatReason.CantMakeIt)],
        'A',
      ),
    ).toBe('self');
  });

  it('is null with no "I can\'t make it today" message in the thread', () => {
    expect(
      cantMakeItFrom([m('A', ChatReason.RunningLate), m('B', null)], 'A'),
    ).toBeNull();
    expect(cantMakeItFrom([], 'A')).toBeNull();
  });
});
