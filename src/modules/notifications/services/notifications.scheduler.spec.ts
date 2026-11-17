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

  describe('rescheduleDateReminder (Date 05: an agreed time moves the meet time)', () => {
    const job = (over: Record<string, unknown> = {}) => ({
      data: {
        dateId: 'd1',
        participantUserIds: ['a', 'b'],
        time: '20:00',
        venue: 'X',
      },
      timestamp: 1_000_000,
      opts: { delay: 600_000 },
      remove: jest.fn().mockResolvedValue(undefined),
      ...over,
    });

    it('moves the send time by the shift and keeps the job id and recipients', async () => {
      const j = job();
      queue.getJob.mockResolvedValue(j);
      const moved = await make().rescheduleDateReminder('d1', 30 * 60_000);
      expect(moved).toBe(true);
      expect(j.remove).toHaveBeenCalled();
      const [name, payload, opts] = queue.add.mock.calls[0];
      expect(name).toBe('send');
      expect(opts.jobId).toBe('date-reminder-d1');
      expect(payload.participantUserIds).toEqual(['a', 'b']);
      // The old clock string would now be wrong, so it is dropped.
      expect(payload.time).toBeUndefined();
      expect(payload.venue).toBe('X');
      // Fires at old send time (timestamp + delay) + shift; delay is relative to now (never negative).
      expect(opts.delay).toBeGreaterThanOrEqual(0);
    });

    it('does nothing when no reminder is scheduled', async () => {
      expect(await make().rescheduleDateReminder('d1', 60_000)).toBe(false);
      expect(queue.add).not.toHaveBeenCalled();
    });

    it('leaves a job that is already running alone', async () => {
      const j = job({
        remove: jest.fn().mockRejectedValue(new Error('locked')),
      });
      queue.getJob.mockResolvedValue(j);
      expect(await make().rescheduleDateReminder('d1', 60_000)).toBe(false);
      expect(queue.add).not.toHaveBeenCalled();
    });
  });
});
