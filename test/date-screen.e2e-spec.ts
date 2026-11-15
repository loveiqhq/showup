import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import request from 'supertest';
import { DataSource, In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { DEV_PARTNER_PHONE } from './../src/modules/date-screen/date-dev.service';
import { NotificationDispatchService } from './../src/modules/notifications/services/notification-dispatch.service';
import { AnalyticsService } from './../src/modules/analytics/analytics.service';
import { CheckInWindow } from './../src/modules/check-ins/entities/check-in-window.entity';
import { CheckInsService } from './../src/modules/check-ins/check-ins.service';
import { DateChatMessage } from './../src/modules/dates/entities/date-chat-message.entity';
import { DatesService } from './../src/modules/dates/dates.service';
import { DateEntity } from './../src/modules/dates/entities/date.entity';
import { DateStatus } from './../src/modules/dates/util/date-lifecycle';
import { ChatReason } from './../src/modules/dates/util/pre-date-chat';
import { Profile } from './../src/modules/profiles/entities/profile.entity';
import { User } from './../src/modules/users/entities/user.entity';

/**
 * The Date screen's reads (Date 01, SHOWUP-193) through the real app against a real database:
 * `GET /me/date` (no date, the locked and open states, only what matches, hidden fields absent, the
 * unread message) and `GET /me/date/profile`. The pure rules -- the overlap, the unread count, the
 * unlock instant -- are `active-date.spec.ts`.
 *
 * The date is inserted directly: this file tests what the screen READS. Creating one from a match
 * is `dates.e2e-spec.ts` and `matching.e2e-spec.ts`.
 */
const ALL_PHONES = () => [...Object.values(PHONES), DEV_PARTNER_PHONE];
const PHONES = {
  you: '+491701930001',
  leonie: '+491701930002',
  other: '+491701930003',
};
const VENUE = {
  name: 'E2E Date Screen Cafe',
  address: 'Oranienstrasse 42, Kreuzberg',
};
const BRANDENBURG_GATE = { lat: 52.5163, lng: 13.3777 };
const REICHSTAG = { lat: 52.5186, lng: 13.3761 };
/** About 5.4 km from the Reichstag. */
const TEMPELHOF = { lat: 52.473, lng: 13.403 };
const TZ = 'Europe/Berlin';

describe('The Date screen (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let db: DataSource;
  let dates: Repository<DateEntity>;
  const token: Record<string, string> = {};
  const id: Record<string, string> = {};
  let venueId: string;

  const as = (who: string) => `Bearer ${token[who]}`;
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

  const getDate = (who = 'you') =>
    request(server)
      .get('/me/date')
      .query({ tz: TZ })
      .set('Authorization', as(who))
      .expect(200);

  const makeDate = async (meetInMinutes: number) =>
    (
      await dates.save(
        dates.create({
          userAId: id.you,
          userBId: id.leonie,
          scheduledAt: minutesFromNow(meetInMinutes),
          venueId,
          status: DateStatus.Confirmed,
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
    await users.delete({ phone: In(ALL_PHONES()) });

    await signIn('you');
    await signIn('leonie');
    await signIn('other');

    const profiles = db.getRepository(Profile);
    await profiles.save(
      profiles.create({
        userId: id.you,
        displayName: 'Max',
        dateOfBirth: '1995-03-01',
        isVisible: true,
        interests: ['hiking', 'reading', 'cooking'],
      }),
    );
    await profiles.save(
      profiles.create({
        userId: id.leonie,
        displayName: 'Leonie',
        dateOfBirth: '1998-02-01',
        isVisible: true,
        interests: ['reading', 'hiking', 'brunch'],
      }),
    );

    const windows = db.getRepository(CheckInWindow);
    await windows.save(
      windows.create({
        userId: id.you,
        closesAt: minutesFromNow(48 * 60),
        committedAt: new Date(),
        motivation: 'long_term',
        vibes: ['playful', 'coffee_chat'],
        district: 'Mitte',
      }),
    );
    await windows.save(
      windows.create({
        userId: id.leonie,
        closesAt: minutesFromNow(48 * 60),
        committedAt: new Date(),
        motivation: 'long_term',
        vibes: ['coffee_chat', 'nature_walk', 'playful'],
        district: 'Neukölln',
      }),
    );

    const checkIns = app.get(CheckInsService);
    const span = {
      availabilityStart: minutesFromNow(1).toISOString(),
      availabilityEnd: minutesFromNow(240).toISOString(),
      preparationMinutes: 20,
    };
    await checkIns.create(id.you, {
      ...span,
      latitude: BRANDENBURG_GATE.lat,
      longitude: BRANDENBURG_GATE.lng,
    });
    await checkIns.create(id.leonie, {
      ...span,
      latitude: REICHSTAG.lat,
      longitude: REICHSTAG.lng,
    });

    await db.query(`DELETE FROM venues WHERE name = $1`, [VENUE.name]);
    const [venue] = await db.query<Array<{ id: string }>>(
      `INSERT INTO venues (name, address, location)
       VALUES ($1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography) RETURNING id`,
      [VENUE.name, VENUE.address, REICHSTAG.lng, REICHSTAG.lat],
    );
    venueId = venue.id;
  });

  beforeEach(async () => {
    await dates
      .createQueryBuilder()
      .delete()
      .where('user_a_id IN (:...ids) OR user_b_id IN (:...ids)', {
        ids: Object.values(id),
      })
      .execute();
    await db
      .getRepository(Profile)
      .update({ userId: id.leonie }, { hiddenFields: [] });
  });

  afterAll(async () => {
    await db.query(`DELETE FROM venues WHERE name = $1`, [VENUE.name]);
    await app
      .get<Repository<User>>(getRepositoryToken(User), { strict: false })
      .delete({ phone: In(ALL_PHONES()) });
    await app.close();
  });

  it('answers date: null when there is no active date', async () => {
    const res = await getDate();
    expect(res.body).toEqual({ date: null, ended: null });
  });

  it('shows a date three hours away as locked, with the agreed time and place', async () => {
    const dateId = await makeDate(180);
    const { date } = (await getDate()).body;
    expect(date.id).toBe(dateId);
    expect(date.coordinateOpen).toBe(false);
    expect(new Date(date.unlockAt).getTime()).toBe(
      new Date(date.meetAt).getTime() - 90 * 60_000,
    );
    expect(date.place).toEqual(VENUE);
    expect(date.match.name).toBe('Leonie');
    expect(date.match.district).toBe('Neukölln');
    expect(date.match.attendedCount).toBe(0);
    expect(typeof date.match.age).toBe('number');
    // Brandenburg Gate to the Reichstag is about 260 m: "under 1 km" is 0.
    expect(date.match.distanceKm).toBe(0);
    expect(date.checkedIn).toBe(false);
    expect(date.unread).toBeNull();
  });

  it('opens coordination from meet − 1:30', async () => {
    await makeDate(80);
    expect((await getDate()).body.date.coordinateOpen).toBe(true);
  });

  it('carries only what matches: shared vibes and interests, both motivations', async () => {
    await makeDate(180);
    const { date } = (await getDate()).body;
    expect(date.sharedVibes).toEqual(['coffee_chat', 'playful']);
    expect(date.sharedInterests).toEqual(['reading', 'hiking']);
    expect(date.motivations).toEqual({ you: 'long_term', them: 'long_term' });
  });

  it('never sends an age the match hid', async () => {
    await db
      .getRepository(Profile)
      .update({ userId: id.leonie }, { hiddenFields: ['age'] });
    await makeDate(180);
    const res = await getDate();
    expect(res.body.date.match.age).toBeUndefined();
    expect(JSON.stringify(res.body)).not.toContain('"age"');
  });

  it('is the same date seen from the other side', async () => {
    const dateId = await makeDate(180);
    const { date } = (await getDate('leonie')).body;
    expect(date.id).toBe(dateId);
    expect(date.match.name).toBe('Max');
  });

  it('counts the match’s unread messages and previews the newest', async () => {
    const dateId = await makeDate(60);
    const messages = db.getRepository(DateChatMessage);
    await messages.save(
      messages.create({
        dateId,
        senderId: id.leonie,
        reason: ChatReason.RunningLate,
        body: 'Running about 10 min late — sorry!',
      }),
    );
    const { date } = (await getDate()).body;
    expect(date.unread).toMatchObject({
      count: 1,
      reason: 'running_late',
      body: 'Running about 10 min late — sorry!',
    });
    // Your own message is never unread for you.
    expect((await getDate('leonie')).body.date.unread).toBeNull();
  });

  it('returns the match’s full profile, and 404 without a date', async () => {
    await request(server)
      .get('/me/date/profile')
      .query({ tz: TZ })
      .set('Authorization', as('you'))
      .expect(404);
    await makeDate(180);
    const res = await request(server)
      .get('/me/date/profile')
      .query({ tz: TZ })
      .set('Authorization', as('you'))
      .expect(200);
    expect(res.body.name).toBe('Leonie');
    expect(res.body.interests).toEqual(['reading', 'hiking', 'brunch']);
    expect(JSON.stringify(res.body)).not.toContain('distanceMeters');
  });

  it('clears unread once the thread is marked read, and only for a participant (403, as the chat)', async () => {
    const dateId = await makeDate(60);
    const messages = db.getRepository(DateChatMessage);
    await messages.save(
      messages.create({
        dateId,
        senderId: id.leonie,
        reason: ChatReason.OnMyWay,
        body: null,
      }),
    );
    expect((await getDate()).body.date.unread.count).toBe(1);

    await request(server)
      .post(`/dates/${dateId}/chat/read`)
      .set('Authorization', as('other'))
      .expect(403);
    await request(server)
      .post(`/dates/${dateId}/chat/read`)
      .set('Authorization', as('you'))
      .expect(204);
    expect((await getDate()).body.date.unread).toBeNull();

    // A message after the read is unread again.
    await messages.save(
      messages.create({
        dateId,
        senderId: id.leonie,
        reason: ChatReason.CantFind,
        body: 'Where are you sitting?',
        createdAt: new Date(Date.now() + 1000),
      }),
    );
    expect((await getDate()).body.date.unread).toMatchObject({
      count: 1,
      reason: 'cant_find',
    });
  });

  it('tells each side only what the arrival rules allow: committed, and until the chat is read', async () => {
    const dateId = await makeDate(60);
    const body = (clientId: string) => ({
      latitude: REICHSTAG.lat,
      longitude: REICHSTAG.lng,
      entry: 'date_screen',
      clientId,
    });
    await request(server)
      .post(`/dates/${dateId}/arrival`)
      .set('Authorization', as('you'))
      .send(body('3f0b8e0e-8a5e-4c43-9a55-0a1a3d9d1c01'))
      .expect(201);
    expect((await getDate('you')).body.date.checkedIn).toBe(false);
    expect((await getDate('leonie')).body.date.arrival).toBeNull();

    await request(server)
      .post(`/dates/${dateId}/arrival/commit`)
      .set('Authorization', as('you'))
      .expect(204);
    expect((await getDate('you')).body.date.checkedIn).toBe(true);
    expect((await getDate('you')).body.date.arrival).toBeNull();
    const seen = (await getDate('leonie')).body.date;
    expect(typeof seen.arrival.checkedInAt).toBe('string');
    expect(JSON.stringify(seen.arrival)).not.toMatch(/52\.5|13\.37/);

    await request(server)
      .post(`/dates/${dateId}/chat/read`)
      .set('Authorization', as('leonie'))
      .expect(204);
    expect((await getDate('leonie')).body.date.arrival).toBeNull();
  });

  it('stamps coordinate_unlocked once for a due date, and leaves a later one alone', async () => {
    const due = await makeDate(60);
    const sent: Array<{
      userId: string;
      eventName: string;
      properties?: object;
    }> = [];
    const analytics = app.get(AnalyticsService);
    const spy = jest
      .spyOn(analytics, 'trackServerEvent')
      .mockImplementation((p) => {
        sent.push(p);
        return Promise.resolve();
      });
    try {
      const datesService = app.get(DatesService);
      expect(await datesService.unlockDueCoordinates()).toBe(1);
      expect(await datesService.unlockDueCoordinates()).toBe(0);
      const unlocks = sent.filter((e) => e.eventName === 'coordinate_unlocked');
      expect(unlocks.map((e) => e.userId).sort()).toEqual(
        [id.you, id.leonie].sort(),
      );
      expect(unlocks[0].properties).toEqual({ minutes_before_start: 59 });
      const [row] = await db.query<Array<{ at: Date | null }>>(
        `SELECT coordinate_unlocked_at AS "at" FROM dates WHERE id = $1`,
        [due],
      );
      expect(row.at).not.toBeNull();
    } finally {
      spy.mockRestore();
    }
  });

  it('shows the real distance between two located people, on the screen and on the full profile', async () => {
    // Leonie at the Reichstag, the other person at Tempelhof: two real Check-Ins about 5.4 km apart.
    await app.get(CheckInsService).create(id.other, {
      availabilityStart: minutesFromNow(1).toISOString(),
      availabilityEnd: minutesFromNow(240).toISOString(),
      preparationMinutes: 20,
      latitude: TEMPELHOF.lat,
      longitude: TEMPELHOF.lng,
    });
    await dates.save(
      dates.create({
        userAId: id.other,
        userBId: id.leonie,
        scheduledAt: minutesFromNow(180),
        venueId,
        status: DateStatus.Confirmed,
      }),
    );
    expect((await getDate('other')).body.date.match.distanceKm).toBe(5);
    const profile = await request(server)
      .get('/me/date/profile')
      .query({ tz: TZ })
      .set('Authorization', as('other'))
      .expect(200);
    expect(profile.body.distanceKm).toBe(5);
  });

  it('never touches a date with a real person through the dev helpers', async () => {
    const real = await makeDate(60);
    // A test date cannot be made over a real one: the app holds one date at a time.
    await request(server)
      .post('/dev/date')
      .set('Authorization', as('you'))
      .send({ minutesFromNow: 60 })
      .expect(409);
    // The match's message and the match's end act on the test match's date only.
    await request(server)
      .post('/dev/date/message')
      .set('Authorization', as('you'))
      .send({ body: 'Not from Leonie' })
      .expect(404);
    await request(server)
      .post('/dev/date/end-in-chat')
      .set('Authorization', as('you'))
      .expect(404);
    await request(server)
      .post('/dev/date/end')
      .set('Authorization', as('you'))
      .expect(204);
    const after = await dates.findOneByOrFail({ id: real });
    expect(after.status).toBe(DateStatus.Confirmed);
    const chat = await request(server)
      .get(`/dates/${real}/chat`)
      .set('Authorization', as('you'))
      .expect(200);
    expect(chat.body).toEqual([]);
  });

  it('is not someone else’s date', async () => {
    await makeDate(180);
    expect((await getDate('other')).body).toEqual({ date: null, ended: null });
  });

  it('walks every state with the dev helpers: create, message as the match, end', async () => {
    await request(server)
      .post('/dev/date')
      .set('Authorization', as('other'))
      .send({ minutesFromNow: 60 })
      .expect(204);
    const created = (await getDate('other')).body.date;
    expect(created.coordinateOpen).toBe(true);
    expect(created.match.name).toBe('Leonie');
    expect(created.place).toEqual({
      name: 'Cafe Krone',
      address: 'Oranienstrasse 42, Kreuzberg',
    });

    await request(server)
      .post('/dev/date/message')
      .set('Authorization', as('other'))
      .send({
        reason: 'running_late',
        body: 'Running about 10 min late — sorry!',
      })
      .expect(204);
    expect((await getDate('other')).body.date.unread).toMatchObject({
      count: 1,
      reason: 'running_late',
    });

    // A second create replaces the test date rather than stacking a second one.
    await request(server)
      .post('/dev/date')
      .set('Authorization', as('other'))
      .send({ minutesFromNow: 180 })
      .expect(204);
    expect((await getDate('other')).body.date.coordinateOpen).toBe(false);

    await request(server)
      .post('/dev/date/end')
      .set('Authorization', as('other'))
      .expect(204);
    expect((await getDate('other')).body).toEqual({ date: null, ended: null });
  });

  it('the test match arrives through the real path: one push, the arrival set, a second call silent', async () => {
    const sendPush = jest
      .spyOn(app.get(NotificationDispatchService), 'sendPush')
      .mockResolvedValue({ status: 'sent', delivered: 1 } as never);
    try {
      await request(server)
        .post('/dev/date/arrival')
        .set('Authorization', as('other'))
        .expect(404);
      await request(server)
        .post('/dev/date')
        .set('Authorization', as('other'))
        .send({ minutesFromNow: 60 })
        .expect(204);
      await request(server)
        .post('/dev/date/arrival')
        .set('Authorization', as('other'))
        .expect(204);
      await request(server)
        .post('/dev/date/arrival')
        .set('Authorization', as('other'))
        .expect(204);
      expect(
        typeof (await getDate('other')).body.date.arrival.checkedInAt,
      ).toBe('string');
      expect(sendPush).toHaveBeenCalledTimes(1);
      expect(sendPush.mock.calls[0][1]).toBe('date_partner_arrived');
    } finally {
      sendPush.mockRestore();
      await request(server)
        .post('/dev/date/end')
        .set('Authorization', as('other'));
    }
  });
});
