/**
 * The Coordinate chat (Date 02, SHOWUP-194): the chat contract on top of SHOWUP-115's routes --
 * a reason optional (the registry's §40, never the two that open the Date 05 picker), a body
 * required, read receipts on your own messages.
 */
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import request from 'supertest';
import { In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { DateEntity } from './../src/modules/dates/entities/date.entity';
import { DateStatus } from './../src/modules/dates/util/date-lifecycle';
import { User } from './../src/modules/users/entities/user.entity';

const PHONES = {
  you: '+491701940001',
  leonie: '+491701940002',
};

describe('The Coordinate chat (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let dates: Repository<DateEntity>;
  const token: Record<string, string> = {};
  const id: Record<string, string> = {};

  const as = (who: keyof typeof PHONES) => `Bearer ${token[who]}`;
  const minutesFromNow = (m: number) => new Date(Date.now() + m * 60_000);

  const signIn = async (who: keyof typeof PHONES) => {
    const start = await request(server)
      .post('/auth/phone/start')
      .send({ phone: PHONES[who] })
      .expect(200);
    const verify = await request(server)
      .post('/auth/phone/verify')
      .send({ phone: PHONES[who], code: start.body.devCode })
      .expect(200);
    token[who] = verify.body.accessToken;
    id[who] = verify.body.user.id;
  };

  /** A date between you and Leonie, `meetInMinutes` from now; one at a time, as the app allows. */
  const makeDate = async (meetInMinutes: number, status = DateStatus.Confirmed) => {
    await dates.delete({ userAId: id.you });
    return (
      await dates.save(
        dates.create({
          userAId: id.you,
          userBId: id.leonie,
          scheduledAt: minutesFromNow(meetInMinutes),
          status,
        }),
      )
    ).id;
  };

  const send = (dateId: string, who: keyof typeof PHONES, body: object) =>
    request(server).post(`/dates/${dateId}/chat`).set('Authorization', as(who)).send(body);
  const list = (dateId: string, who: keyof typeof PHONES) =>
    request(server).get(`/dates/${dateId}/chat`).set('Authorization', as(who)).expect(200);

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile();
    app = moduleRef.createNestApplication();
    app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));
    await app.init();
    server = app.getHttpServer();
    dates = app.get<Repository<DateEntity>>(getRepositoryToken(DateEntity), { strict: false });
    const users = app.get<Repository<User>>(getRepositoryToken(User), { strict: false });
    await users.delete({ phone: In(Object.values(PHONES)) });
    await signIn('you');
    await signIn('leonie');
  });

  afterAll(async () => {
    const users = app.get<Repository<User>>(getRepositoryToken(User), { strict: false });
    await dates.delete({ userAId: id.you });
    await users.delete({ phone: In(Object.values(PHONES)) });
    await app.close();
  });

  it('sends a message typed with no chip, listed with no reason', async () => {
    const dateId = await makeDate(60);
    await send(dateId, 'you', { body: 'Hi! Looking forward to it' }).expect(201);
    const { body } = await list(dateId, 'you');
    expect(body).toHaveLength(1);
    expect(body[0]).toMatchObject({ reason: null, body: 'Hi! Looking forward to it', fromYou: true });
  });

  it('sends a chip message with its §40 reason', async () => {
    const dateId = await makeDate(60);
    const res = await send(dateId, 'you', {
      reason: 'running_late',
      body: 'Running about 10 min late — sorry! Almost there.',
    }).expect(201);
    expect(res.body).toMatchObject({ reason: 'running_late', fromYou: true, read: false });
  });

  it('refuses the two reasons that open the Date 05 picker', async () => {
    const dateId = await makeDate(60);
    await send(dateId, 'you', { reason: 'change_time', body: 'x' }).expect(400);
    await send(dateId, 'you', { reason: 'change_place', body: 'x' }).expect(400);
  });

  it('refuses a message with no body, a blank one, or one over 1000 characters', async () => {
    const dateId = await makeDate(60);
    await send(dateId, 'you', { reason: 'cant_find' }).expect(400);
    await send(dateId, 'you', { reason: 'cant_find', body: '   ' }).expect(400);
    await send(dateId, 'you', { body: 'x'.repeat(1001) }).expect(400);
    await send(dateId, 'you', { body: 'x'.repeat(1000) }).expect(201);
  });

  it('refuses a message before the chat opens at meet − 1:30', async () => {
    const dateId = await makeDate(180);
    const res = await send(dateId, 'you', { reason: 'on_my_way', body: 'On my way' }).expect(400);
    expect(res.body.message).toContain('opens about 90 minutes before');
  });

  it('refuses a message on a date that has ended in the chat', async () => {
    const dateId = await makeDate(60, DateStatus.EndedInChat);
    await send(dateId, 'you', { body: 'Still there?' }).expect(409);
  });

  it('reads your message as read once the other person has read the chat, and theirs as unread-free', async () => {
    const dateId = await makeDate(60);
    await send(dateId, 'you', { reason: 'on_my_way', body: 'On my way now — see you in a few!' }).expect(201);
    expect((await list(dateId, 'you')).body[0].read).toBe(false);

    await request(server).post(`/dates/${dateId}/chat/read`).set('Authorization', as('leonie')).expect(204);
    expect((await list(dateId, 'you')).body[0].read).toBe(true);

    const theirs = (await list(dateId, 'leonie')).body[0];
    expect(theirs).toMatchObject({ fromYou: false, read: null });
  });
});
