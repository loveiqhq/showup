import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import request from 'supertest';
import { DataSource, In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { ArrivalCheckInsService } from './../src/modules/dates/arrival-check-ins.service';
import { AnalyticsService } from './../src/modules/analytics/analytics.service';
import { DateEntity } from './../src/modules/dates/entities/date.entity';
import { DateStatus } from './../src/modules/dates/util/date-lifecycle';
import { NotificationDispatchService } from './../src/modules/notifications/services/notification-dispatch.service';
import { Profile } from './../src/modules/profiles/entities/profile.entity';
import { User } from './../src/modules/users/entities/user.entity';

/**
 * The arrival check-in's write side (Date 03, SHOWUP-195): store, replace, undo, commit and the push,
 * through the real app against a real database. The pure rules are `arrival.spec.ts`.
 */
const PHONES = {
  you: '+491701950001',
  leonie: '+491701950002',
  other: '+491701950003',
};
const SPOT = { latitude: 52.5186, longitude: 13.3761, accuracyM: 12.5 };

interface SentEvent {
  userId: string;
  eventName: string;
  properties?: Record<string, unknown>;
}

describe('Arrival check-in (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let db: DataSource;
  let dates: Repository<DateEntity>;
  let sendPush: jest.SpyInstance;
  const token: Record<string, string> = {};
  const id: Record<string, string> = {};
  let dateId: string;
  let events: SentEvent[];
  let pushes: unknown[][];

  const as = (who: string) => `Bearer ${token[who]}`;
  const body = (over: Record<string, unknown> = {}) => ({
    ...SPOT,
    entry: 'date_screen',
    clientId: randomUUID(),
    ...over,
  });
  const create = (who: string, payload: object, on = () => dateId) =>
    request(server)
      .post(`/dates/${on()}/arrival`)
      .set('Authorization', as(who))
      .send(payload);
  const undo = (who: string, on = () => dateId) =>
    request(server)
      .delete(`/dates/${on()}/arrival`)
      .set('Authorization', as(who));
  const commit = (who: string, on = () => dateId) =>
    request(server)
      .post(`/dates/${on()}/arrival/commit`)
      .set('Authorization', as(who));
  const named = (name: string) => events.filter((e) => e.eventName === name);
  const rows = () =>
    db.query<
      Array<{
        user_id: string;
        entry: string;
        accuracy_m: number;
        committed_at: Date | null;
        lng: number;
        lat: number;
      }>
    >(
      `SELECT user_id, entry, accuracy_m, committed_at, ST_X(location::geometry) AS lng,
              ST_Y(location::geometry) AS lat
         FROM date_arrival_check_ins WHERE date_id = $1`,
      [dateId],
    );

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

  const makeDate = async (status = DateStatus.Confirmed) =>
    (
      await dates.save(
        dates.create({
          userAId: id.you,
          userBId: id.leonie,
          scheduledAt: new Date(Date.now() + 60 * 60_000),
          status,
        }),
      )
    ).id;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication();
    app.useGlobalPipes(
      new ValidationPipe({ whitelist: true, transform: true }),
    );
    await app.init();
    server = app.getHttpServer();
    db = app.get(DataSource);
    dates = app.get<Repository<DateEntity>>(getRepositoryToken(DateEntity), {
      strict: false,
    });
    const users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    await users.delete({ phone: In(Object.values(PHONES)) });
    await signIn('you');
    await signIn('leonie');
    await signIn('other');
    const profiles = db.getRepository(Profile);
    await profiles.save(
      profiles.create({ userId: id.you, displayName: 'Max' }),
    );
    await profiles.save(
      profiles.create({ userId: id.leonie, displayName: 'Leonie' }),
    );

    jest
      .spyOn(app.get(AnalyticsService), 'trackServerEvent')
      .mockImplementation((p) => {
        events.push(p);
        return Promise.resolve();
      });
    sendPush = jest
      .spyOn(app.get(NotificationDispatchService), 'sendPush')
      .mockImplementation((...args: unknown[]) => {
        pushes.push(args);
        return Promise.resolve({ status: 'sent', delivered: 1 } as never);
      });
  });

  beforeEach(async () => {
    events = [];
    pushes = [];
    await dates
      .createQueryBuilder()
      .delete()
      .where('user_a_id IN (:...ids)', { ids: [id.you, id.leonie] })
      .execute();
    dateId = await makeDate();
  });

  afterAll(async () => {
    await db.getRepository(User).delete({ phone: In(Object.values(PHONES)) });
    await app.close();
  });

  it('stores the point, accuracy and entry, and answers with no coordinate', async () => {
    const res = await create('you', body()).expect(201);
    expect(res.body.committed).toBe(false);
    expect(typeof res.body.checkedInAt).toBe('string');
    expect(JSON.stringify(res.body)).not.toMatch(/52\.5|13\.37|latitude/);
    const [row] = await rows();
    expect(row.lng).toBeCloseTo(SPOT.longitude, 5);
    expect(row.lat).toBeCloseTo(SPOT.latitude, 5);
    expect(row.accuracy_m).toBe(12.5);
    expect(row.entry).toBe('date_screen');
    expect(row.committed_at).toBeNull();
  });

  it('a resend of the same tap stores once and reports once; another tap replaces the point', async () => {
    const first = body();
    await create('you', first).expect(201);
    await create('you', first).expect(201);
    expect(await rows()).toHaveLength(1);
    expect(named('arrival_confirmed')).toHaveLength(1);

    await create(
      'you',
      body({ latitude: 48.1, longitude: 11.5, entry: 'chat_chip' }),
    ).expect(201);
    const stored = await rows();
    expect(stored).toHaveLength(1);
    expect(stored[0].lat).toBeCloseTo(48.1, 5);
    expect(stored[0].entry).toBe('chat_chip');
    expect(named('arrival_confirmed')).toHaveLength(2);
  });

  it('is_first is true for the first person and false for the second', async () => {
    await create('you', body()).expect(201);
    await create('leonie', body()).expect(201);
    const sent = named('arrival_confirmed');
    expect(sent[0].userId).toBe(id.you);
    expect(sent[0].properties?.is_first).toBe(true);
    expect(sent[0].properties?.entry).toBe('date_screen');
    expect(sent[0].properties?.minutes_vs_start).toBeLessThan(0);
    expect(sent[1].properties?.is_first).toBe(false);
    expect(JSON.stringify(sent)).not.toMatch(/52\.5|accuracy/);
  });

  it('undo deletes an uncommitted row once and reports it once', async () => {
    await create('you', body({ entry: 'report' })).expect(201);
    await undo('you').expect(204);
    expect(await rows()).toHaveLength(0);
    const undone = named('arrival_undone');
    expect(undone).toHaveLength(1);
    expect(undone[0].properties?.entry).toBe('report');
    expect(
      undone[0].properties?.seconds_since_confirm as number,
    ).toBeGreaterThanOrEqual(0);
    await undo('you').expect(204);
    expect(named('arrival_undone')).toHaveLength(1);
  });

  it('commit sets committed_at and pushes the partner once', async () => {
    await create('you', body()).expect(201);
    await commit('you').expect(204);
    const [row] = await rows();
    expect(row.committed_at).not.toBeNull();
    expect(pushes).toEqual([
      [
        id.leonie,
        'date_partner_arrived',
        { name: 'Max' },
        { type: 'date_partner_arrived', dateId },
      ],
    ]);
    await commit('you').expect(204);
    expect(pushes).toHaveLength(1);
  });

  it('a push failure does not fail the commit', async () => {
    await create('you', body()).expect(201);
    sendPush.mockRejectedValueOnce(new Error('fcm down'));
    await commit('you').expect(204);
  });

  it('after commit: undo and a new check-in are 409; a resend of the same tap is the stored row', async () => {
    const first = body();
    await create('you', first).expect(201);
    await commit('you').expect(204);
    await undo('you').expect(409);
    await create('you', body()).expect(409);
    const again = await create('you', first).expect(201);
    expect(again.body.committed).toBe(true);
  });

  it('commit after undo is 404', async () => {
    await create('you', body()).expect(201);
    await undo('you').expect(204);
    await commit('you').expect(404);
    expect(pushes).toHaveLength(0);
  });

  it('refuses a stranger, an unknown date and an ended date', async () => {
    await create('other', body()).expect(403);
    await undo('other').expect(403);
    await commit('other').expect(403);
    const missing = randomUUID();
    await create('you', body(), () => missing).expect(404);
    const ended = await makeDate(DateStatus.EndedInChat);
    await create('you', body(), () => ended).expect(409);
  });

  it('the retention sweep deletes a row a year and a second old and keeps one a second younger', async () => {
    const now = new Date('2027-06-15T12:00:00.000Z');
    const old = new Date('2026-06-15T11:59:59.000Z');
    const young = new Date('2026-06-15T12:00:01.000Z');
    await create('you', body()).expect(201);
    await create('leonie', body()).expect(201);
    await db.query(
      `UPDATE date_arrival_check_ins SET checked_in_at = CASE WHEN user_id = $2 THEN $3::timestamptz ELSE $4::timestamptz END WHERE date_id = $1`,
      [dateId, id.you, old, young],
    );
    expect(await app.get(ArrivalCheckInsService).deleteExpired(now)).toBe(1);
    const left = await rows();
    expect(left.map((r) => r.user_id)).toEqual([id.leonie]);
  });

  it.each([
    ['latitude 91', { latitude: 91 }],
    ['longitude -181', { longitude: -181 }],
    ['accuracy -1', { accuracyM: -1 }],
    ['entry nowhere', { entry: 'nowhere' }],
    ['no clientId', { clientId: undefined }],
  ])('rejects %s with 400', async (_name, over) => {
    await create('you', body(over)).expect(400);
    expect(await rows()).toHaveLength(0);
  });
});
