import { CheckInExpiryNotifyProcessor } from './check-in-expiry-notify.processor';

describe('CheckInExpiryNotifyProcessor (SHOWUP-70)', () => {
  let checkIns: {
    findExpiringSoon: jest.Mock;
    markExpiryReminderSent: jest.Mock;
  };
  let dispatch: { sendPush: jest.Mock };
  const config = { get: jest.fn().mockReturnValue(10) };
  const make = () =>
    new CheckInExpiryNotifyProcessor(
      checkIns as any,
      dispatch as any,
      config as any,
    );

  beforeEach(() => {
    checkIns = {
      findExpiringSoon: jest.fn().mockResolvedValue([]),
      markExpiryReminderSent: jest.fn().mockResolvedValue(undefined),
    };
    dispatch = {
      sendPush: jest.fn().mockResolvedValue({ status: 'sent', delivered: 1 }),
    };
  });

  it('reminds each soon-to-expire check-in and marks it so it never repeats', async () => {
    checkIns.findExpiringSoon.mockResolvedValue([
      { id: 'c1', userId: 'u1' },
      { id: 'c2', userId: 'u2' },
    ]);
    const res = await make().process();

    expect(dispatch.sendPush).toHaveBeenCalledWith('u1', 'check_in_expiry', {});
    expect(dispatch.sendPush).toHaveBeenCalledWith('u2', 'check_in_expiry', {});
    expect(checkIns.markExpiryReminderSent).toHaveBeenCalledWith('c1');
    expect(checkIns.markExpiryReminderSent).toHaveBeenCalledWith('c2');
    expect(res.notified).toBe(2);
  });

  it('marks as reminded even when the push is suppressed by preferences (no re-scan next minute)', async () => {
    checkIns.findExpiringSoon.mockResolvedValue([{ id: 'c1', userId: 'u1' }]);
    dispatch.sendPush.mockResolvedValue({ status: 'skipped', delivered: 0 });
    const res = await make().process();

    expect(checkIns.markExpiryReminderSent).toHaveBeenCalledWith('c1');
    expect(res.notified).toBe(0);
  });

  it('does nothing when nothing is expiring soon', async () => {
    const res = await make().process();
    expect(dispatch.sendPush).not.toHaveBeenCalled();
    expect(res.notified).toBe(0);
  });
});
