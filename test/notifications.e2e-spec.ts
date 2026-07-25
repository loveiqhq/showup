import { getQueueToken } from '@nestjs/bullmq';
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { Queue } from 'bullmq';
import request from 'supertest';
import { Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { PushToken } from './../src/modules/notifications/entities/push-token.entity';
import { NotificationsScheduler } from './../src/modules/notifications/services/notifications.scheduler';
import { User } from './../src/modules/users/entities/user.entity';
import {
  QUEUE_CHECK_IN_EXPIRY_NOTIFY,
  QUEUE_DATE_REMINDERS,
} from './../src/queue/queue.constants';

jest.setTimeout(30_000);

// Requires the Docker DB + Redis. Distinct phone from the other e2e suites to avoid collisions.
const PHONE = '+491701234222';

describe('Notifications (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let token: string;
  let userId: string;
  let users: Repository<User>;
  let pushTokens: Repository<PushToken>;

  const bearer = () => `Bearer ${token}`;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication();
    app.useGlobalPipes(
      new ValidationPipe({
        whitelist: true,
        transform: true,
        forbidNonWhitelisted: true,
      }),
    );
    await app.init();
    server = app.getHttpServer();

    users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    pushTokens = app.get<Repository<PushToken>>(getRepositoryToken(PushToken), {
      strict: false,
    });
    await users.delete({ phone: PHONE });

    const start = await request(server)
      .post('/auth/phone/start')
      .send({ phone: PHONE })
      .expect(200);
    const verify = await request(server)
      .post('/auth/phone/verify')
      .send({ phone: PHONE, code: start.body.devCode })
      .expect(200);
    token = verify.body.accessToken;
    userId = verify.body.user.id;
  });

  afterAll(async () => {
    await users.delete({ phone: PHONE }); // cascades tokens + preferences
    await app.close();
  });

  describe('notification preferences (SHOWUP-66)', () => {
    it('defaults to marketing OFF on first read', async () => {
      const res = await request(server)
        .get('/me/notification-preferences')
        .set('Authorization', bearer())
        .expect(200);
      expect(res.body).toEqual({
        essential: true,
        engagement: true,
        marketing: false,
      });
    });

    it('updates only the provided field and persists it', async () => {
      await request(server)
        .patch('/me/notification-preferences')
        .set('Authorization', bearer())
        .send({ marketing: true })
        .expect(200);
      const res = await request(server)
        .get('/me/notification-preferences')
        .set('Authorization', bearer())
        .expect(200);
      expect(res.body.marketing).toBe(true);
      expect(res.body.engagement).toBe(true);
    });
  });

  describe('push tokens (SHOWUP-67)', () => {
    it('registers a device token', async () => {
      const res = await request(server)
        .post('/me/push-tokens')
        .set('Authorization', bearer())
        .send({ token: 'device-token-1', platform: 'ios' })
        .expect(201);
      expect(res.body.platform).toBe('ios');
      expect(res.body.token).toBeUndefined(); // never echoes the token back
    });

    it('does not duplicate when the same token is registered again', async () => {
      await request(server)
        .post('/me/push-tokens')
        .set('Authorization', bearer())
        .send({ token: 'device-token-1', platform: 'ios' })
        .expect(201);
      expect(await pushTokens.count({ where: { userId } })).toBe(1);
    });

    it('deregisters a token', async () => {
      await request(server)
        .delete('/me/push-tokens')
        .set('Authorization', bearer())
        .send({ token: 'device-token-1' })
        .expect(204);
      expect(await pushTokens.count({ where: { userId } })).toBe(0);
    });
  });

  describe('reminder scheduling (SHOWUP-69/70)', () => {
    it('schedules and cancels a date reminder by its deterministic job id', async () => {
      const scheduler = app.get(NotificationsScheduler);
      const dateQueue = app.get<Queue>(getQueueToken(QUEUE_DATE_REMINDERS));
      const dateId = `e2e-${userId}`;

      await scheduler.scheduleDateReminder(
        { dateId, participantUserIds: [userId], time: '8pm', venue: 'X' },
        new Date(Date.now() + 3_600_000),
      );
      expect(await dateQueue.getJob(`date-reminder-${dateId}`)).toBeTruthy();

      await scheduler.cancelDateReminder(dateId);
      expect(await dateQueue.getJob(`date-reminder-${dateId}`)).toBeFalsy();
    });

    it('registers the recurring check-in expiry reminder scan (SHOWUP-70)', async () => {
      const notifyQueue = app.get<Queue>(
        getQueueToken(QUEUE_CHECK_IN_EXPIRY_NOTIFY),
      );
      const schedulers = await notifyQueue.getJobSchedulers();
      expect(schedulers.map((s) => s.key)).toContain(
        'check-in-expiry-notify-every-minute',
      );
    });
  });
});
