import { NotificationsScheduler } from './notifications.scheduler';

describe('NotificationsScheduler (date reminders, SHOWUP-69)', () => {
  let queue: { add: jest.Mock; getJob: jest.Mock };
  const make = () => new NotificationsScheduler(queue as any);

  beforeEach(() => {
    queue = {
      add: jest.fn().mockResolvedValue(undefined),
      getJob: jest.fn().mockResolvedValue(undefined),
    };
  });

  it('schedules a delayed reminder with a deterministic per-date job id (idempotent)', async () => {
    const sendAt = new Date(Date.now() + 60 * 60 * 1000); // 1h out
    await make().scheduleDateReminder(
      { dateId: 'd1', participantUserIds: ['a', 'b'], time: '8pm', venue: 'X' },
      sendAt,
    );
    expect(queue.add).toHaveBeenCalledTimes(1);
    const [name, payload, opts] = queue.add.mock.calls[0];
    expect(name).toBe('send');
    expect(payload).toMatchObject({
      dateId: 'd1',
      participantUserIds: ['a', 'b'],
    });
    expect(opts.jobId).toBe('date-reminder-d1');
    expect(opts.delay).toBeGreaterThan(0);
  });

  it('never schedules a negative delay (past sendAt fires immediately)', async () => {
    await make().scheduleDateReminder(
      { dateId: 'd2', participantUserIds: ['a'] },
      new Date(Date.now() - 5000),
    );
    expect(queue.add.mock.calls[0][2].delay).toBe(0);
  });

  it('cancels a scheduled reminder by removing its job', async () => {
    const remove = jest.fn().mockResolvedValue(undefined);
    queue.getJob.mockResolvedValue({ remove });
    await make().cancelDateReminder('d1');
    expect(queue.getJob).toHaveBeenCalledWith('date-reminder-d1');
    expect(remove).toHaveBeenCalled();
  });

  it('cancelling a non-existent reminder is a no-op', async () => {
    await expect(make().cancelDateReminder('nope')).resolves.toBeUndefined();
  });
});
