import { DateReminderProcessor } from './date-reminder.processor';

describe('DateReminderProcessor (SHOWUP-69)', () => {
  let dispatch: { sendPush: jest.Mock };
  const make = () => new DateReminderProcessor(dispatch as any);

  beforeEach(() => {
    dispatch = {
      sendPush: jest.fn().mockResolvedValue({ status: 'sent', delivered: 1 }),
    };
  });

  it('sends a date reminder to every participant of that date and no one else', async () => {
    await make().process({
      data: {
        dateId: 'd1',
        participantUserIds: ['a', 'b'],
        time: '8:00 PM',
        venue: 'Café Central',
      },
    } as any);

    expect(dispatch.sendPush).toHaveBeenCalledTimes(2);
    expect(dispatch.sendPush).toHaveBeenCalledWith('a', 'date_reminder', {
      time: '8:00 PM',
      venue: 'Café Central',
    });
    expect(dispatch.sendPush).toHaveBeenCalledWith('b', 'date_reminder', {
      time: '8:00 PM',
      venue: 'Café Central',
    });
  });

  it('tolerates a reminder with no venue', async () => {
    await make().process({
      data: { dateId: 'd2', participantUserIds: ['a'], time: '9pm' },
    } as any);
    expect(dispatch.sendPush).toHaveBeenCalledWith('a', 'date_reminder', {
      time: '9pm',
      venue: '',
    });
  });
});
