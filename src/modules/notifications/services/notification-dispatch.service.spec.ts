import { UserStatus } from '../../users/entities/user.entity';
import { NotificationLogStatus } from '../entities/notification-log.entity';
import { NotificationDispatchService } from './notification-dispatch.service';

class FakeLogRepo {
  rows: any[] = [];
  create(p: any) {
    return { ...p };
  }
  save(e: any) {
    this.rows.push(e);
    return Promise.resolve(e);
  }
}

const activeUser = (over: Record<string, unknown> = {}) => ({
  id: 'user-1',
  status: UserStatus.Active,
  email: 'a@example.com',
  locale: 'en',
  ...over,
});

describe('NotificationDispatchService', () => {
  let logRepo: FakeLogRepo;
  let users: { findOne: jest.Mock };
  let prefs: { getState: jest.Mock };
  let pushTokens: { listForUser: jest.Mock; prune: jest.Mock };
  let pushSender: { send: jest.Mock };
  let emailSender: { send: jest.Mock };

  const make = () =>
    new NotificationDispatchService(
      users as any,
      prefs as any,
      pushTokens as any,
      logRepo as any,
      pushSender,
      emailSender,
    );

  beforeEach(() => {
    logRepo = new FakeLogRepo();
    users = { findOne: jest.fn().mockResolvedValue(activeUser()) };
    prefs = {
      getState: jest.fn().mockResolvedValue({
        essential: true,
        engagement: true,
        marketing: false,
      }),
    };
    pushTokens = {
      listForUser: jest
        .fn()
        .mockResolvedValue([{ token: 't1' }, { token: 't2' }]),
      prune: jest.fn().mockResolvedValue(undefined),
    };
    pushSender = {
      send: jest.fn().mockResolvedValue({ success: true, messageId: 'm' }),
    };
    emailSender = {
      send: jest.fn().mockResolvedValue({ success: true, messageId: 'e' }),
    };
  });

  describe('sendPush', () => {
    it('fans a push out to every registered token and logs each as sent', async () => {
      const res = await make().sendPush('user-1', 'date_reminder', {
        time: '8pm',
        venue: 'X',
      });
      expect(pushSender.send).toHaveBeenCalledTimes(2);
      expect(res.delivered).toBe(2);
      expect(res.status).toBe('sent');
      expect(
        logRepo.rows.filter((r) => r.status === NotificationLogStatus.Sent),
      ).toHaveLength(2);
    });

    it('does NOT send to a deleted user, and logs it as skipped (GDPR gate)', async () => {
      users.findOne.mockResolvedValue(
        activeUser({ status: UserStatus.Deleted }),
      );
      const res = await make().sendPush('user-1', 'date_reminder', {});
      expect(pushSender.send).not.toHaveBeenCalled();
      expect(res.status).toBe('skipped');
      expect(logRepo.rows[0].status).toBe(NotificationLogStatus.Skipped);
    });

    it('suppresses an engagement push when the toggle is off', async () => {
      prefs.getState.mockResolvedValue({
        essential: true,
        engagement: false,
        marketing: false,
      });
      const res = await make().sendPush('user-1', 'check_in_expiry', {});
      expect(pushSender.send).not.toHaveBeenCalled();
      expect(res.status).toBe('skipped');
    });

    it('still sends a critical push even when all toggles are off', async () => {
      prefs.getState.mockResolvedValue({
        essential: false,
        engagement: false,
        marketing: false,
      });
      const res = await make().sendPush('user-1', 'safety_alert', {});
      expect(pushSender.send).toHaveBeenCalledTimes(2);
      expect(res.status).toBe('sent');
    });

    it('prunes a token FCM reports as invalid, but keeps valid ones', async () => {
      pushSender.send
        .mockResolvedValueOnce({
          success: false,
          invalidToken: true,
          error: 'gone',
        })
        .mockResolvedValueOnce({ success: true, messageId: 'm' });
      const res = await make().sendPush('user-1', 'date_reminder', {});
      expect(pushTokens.prune).toHaveBeenCalledWith(['t1']);
      expect(res.delivered).toBe(1);
    });

    it('does NOT prune on a transient (non-invalid-token) failure', async () => {
      pushSender.send.mockResolvedValue({ success: false, error: 'timeout' });
      await make().sendPush('user-1', 'date_reminder', {});
      expect(pushTokens.prune).toHaveBeenCalledWith([]);
    });

    it('localises using the user’s stored locale', async () => {
      users.findOne.mockResolvedValue(activeUser({ locale: 'de' }));
      await make().sendPush('user-1', 'date_reminder', { time: '20:00' });
      const sent = pushSender.send.mock.calls[0][0];
      expect(sent.title).toMatch(/Date/); // German catalog title "Dein Date steht bald an"
      expect(logRepo.rows[0].locale).toBe('de');
    });
  });

  describe('sendEmail', () => {
    it('sends a transactional email to the user’s address and logs it', async () => {
      const res = await make().sendEmail('user-1', 'email_verification', {
        code: '123456',
      });
      expect(emailSender.send).toHaveBeenCalledTimes(1);
      const msg = emailSender.send.mock.calls[0][0];
      expect(msg.to).toBe('a@example.com');
      expect(msg.body).toContain('123456');
      expect(res.status).toBe('sent');
    });

    it('fails cleanly when the user has no email address', async () => {
      users.findOne.mockResolvedValue(activeUser({ email: null }));
      const res = await make().sendEmail('user-1', 'password_reset', {
        code: '9',
      });
      expect(emailSender.send).not.toHaveBeenCalled();
      expect(res.status).toBe('failed');
    });

    it('DOES send a critical account-closure email to a deletion-pending user', async () => {
      users.findOne.mockResolvedValue(
        activeUser({ status: UserStatus.DeletionPending }),
      );
      const res = await make().sendEmail('user-1', 'account_closure', {});
      expect(emailSender.send).toHaveBeenCalledTimes(1);
      expect(res.status).toBe('sent');
    });

    it('never emails a fully deleted user, even a critical message', async () => {
      users.findOne.mockResolvedValue(
        activeUser({ status: UserStatus.Deleted }),
      );
      const res = await make().sendEmail('user-1', 'email_verification', {
        code: '1',
      });
      expect(emailSender.send).not.toHaveBeenCalled();
      expect(res.status).toBe('skipped');
    });

    it('skips a non-critical email to a suspended user', async () => {
      users.findOne.mockResolvedValue(
        activeUser({ status: UserStatus.Suspended }),
      );
      const res = await make().sendEmail('user-1', 'marketing_generic', {});
      expect(emailSender.send).not.toHaveBeenCalled();
      expect(res.status).toBe('skipped');
    });
  });
});
