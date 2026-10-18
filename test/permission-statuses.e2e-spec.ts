import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import request from 'supertest';
import { Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { NotificationDispatchService } from './../src/modules/notifications/services/notification-dispatch.service';
import { User } from './../src/modules/users/entities/user.entity';

jest.setTimeout(30_000);

const PHONE = '+491701234127';

/**
 * The phone's permission statuses on the server (epic SHOWUP-151, built for SHOWUP-163), against
 * real Postgres: the save and the read, the refusals, and the push path skipping a phone whose
 * notifications are switched off.
 */
describe('Permission statuses (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let token: string;
  let userId: string;
  let users: Repository<User>;

  const bearer = () => `Bearer ${token}`;
  const save = (body: object) =>
    request(server)
      .put('/me/permissions')
      .set('Authorization', bearer())
      .send(body);

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
    await users.delete({ phone: PHONE }); // cascades the statuses and push tokens
    await app.close();
  });

  it('needs a signed-in user', async () => {
    await request(server)
      .put('/me/permissions')
      .send({ platform: 'android', statuses: [] })
      .expect(401);
  });

  it('stores what the phone reports, and the newest report wins', async () => {
    await save({
      platform: 'android',
      statuses: [
        { permission: 'notifications', status: 'granted' },
        { permission: 'location', status: 'not_determined' },
      ],
    }).expect(204);
    await save({
      platform: 'ios',
      statuses: [{ permission: 'notifications', status: 'denied' }],
    }).expect(204);

    const res = await request(server)
      .get('/me/permissions')
      .set('Authorization', bearer())
      .expect(200);
    expect(
      (
        res.body as { permission: string; status: string; platform: string }[]
      ).map((r) => [r.permission, r.status, r.platform]),
    ).toEqual([
      ['location', 'not_determined', 'android'],
      ['notifications', 'denied', 'ios'],
    ]);
  });

  it('refuses an empty report, an unknown value and a permission named twice', async () => {
    await save({ platform: 'android', statuses: [] }).expect(400);
    await save({
      platform: 'android',
      statuses: [{ permission: 'contacts', status: 'granted' }],
    }).expect(400);
    await save({
      platform: 'android',
      statuses: [{ permission: 'camera', status: 'maybe' }],
    }).expect(400);
    await save({
      platform: 'android',
      statuses: [
        { permission: 'camera', status: 'granted' },
        { permission: 'camera', status: 'denied' },
      ],
    }).expect(400);
  });

  it('skips pushes to a phone whose notifications are off, and sends again once they are on', async () => {
    await request(server)
      .post('/me/push-tokens')
      .set('Authorization', bearer())
      .send({ token: 'e2e-permission-token', platform: 'android' })
      .expect(200);
    const dispatch = app.get(NotificationDispatchService);

    await save({
      platform: 'android',
      statuses: [{ permission: 'notifications', status: 'denied' }],
    }).expect(204);
    expect(await dispatch.sendPush(userId, 'date_reminder')).toEqual({
      status: 'skipped',
      delivered: 0,
    });

    await save({
      platform: 'android',
      statuses: [{ permission: 'notifications', status: 'granted' }],
    }).expect(204);
    const sent = await dispatch.sendPush(userId, 'date_reminder');
    expect(sent.status).toBe('sent');
    expect(sent.delivered).toBe(1);
  });
});
