import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import request from 'supertest';
import { DataSource, In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { CheckInWindow } from './../src/modules/check-ins/entities/check-in-window.entity';
import { CheckInsService } from './../src/modules/check-ins/check-ins.service';
import { DateChatMessage } from './../src/modules/dates/entities/date-chat-message.entity';
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
    await users.delete({ phone: In(Object.values(PHONES)) });

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
      .delete({ phone: In(Object.values(PHONES)) });
    await app.close();
  });

  it('answers date: null when there is no active date', async () => {
    const res = await getDate();
    expect(res.body).toEqual({ date: null });
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
        reason: ChatReason.CantFindYou,
        body: 'Where are you sitting?',
        createdAt: new Date(Date.now() + 1000),
      }),
    );
    expect((await getDate()).body.date.unread).toMatchObject({
      count: 1,
      reason: 'cant_find_you',
    });
  });

  it('is not someone else’s date', async () => {
    await makeDate(180);
    expect((await getDate('other')).body).toEqual({ date: null });
  });
});
