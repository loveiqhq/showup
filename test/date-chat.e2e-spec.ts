/**
 * The Coordinate chat (Date 02, SHOWUP-194): the chat contract on top of SHOWUP-115's routes --
 * a reason optional (the registry's §40, never the two that open the Date 05 picker), a body
 * required, read receipts on your own messages.
 */
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import request from 'supertest';
import { DataSource, In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { AnalyticsService } from './../src/modules/analytics/analytics.service';
import { DatesService } from './../src/modules/dates/dates.service';
import { DateEntity } from './../src/modules/dates/entities/date.entity';
import { DateStatus } from './../src/modules/dates/util/date-lifecycle';
import { orderedUserPair } from './../src/modules/matching/util/match';
import { Profile } from './../src/modules/profiles/entities/profile.entity';
import { SearchService } from './../src/modules/search/search.service';
import { Availability } from './../src/modules/search/util/likes';
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
  const makeDate = async (
    meetInMinutes: number,
    status = DateStatus.Confirmed,
  ) => {
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
    request(server)
      .post(`/dates/${dateId}/chat`)
      .set('Authorization', as(who))
      .send(body);
  const list = (dateId: string, who: keyof typeof PHONES) =>
    request(server)
      .get(`/dates/${dateId}/chat`)
      .set('Authorization', as(who))
      .expect(200);

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
    dates = app.get<Repository<DateEntity>>(getRepositoryToken(DateEntity), {
      strict: false,
    });
    const users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    await users.delete({ phone: In(Object.values(PHONES)) });
    await signIn('you');
    await signIn('leonie');
    // The Date read builds the match's card from their profile: without one it finds no date.
    const profiles = app.get<Repository<Profile>>(getRepositoryToken(Profile), {
      strict: false,
    });
    for (const [who, name] of [
      ['you', 'Max'],
      ['leonie', 'Leonie'],
    ] as const) {
      await profiles.save(
        profiles.create({
          userId: id[who],
          displayName: name,
          dateOfBirth: '1996-05-01',
          isVisible: true,
        }),
      );
    }
  });

  afterAll(async () => {
    const users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    await dates.delete({ userAId: id.you });
    await users.delete({ phone: In(Object.values(PHONES)) });
    await app.close();
  });

  it('sends a message typed with no chip, listed with no reason', async () => {
    const dateId = await makeDate(60);
    await send(dateId, 'you', { body: 'Hi! Looking forward to it' }).expect(
      201,
    );
    const { body } = await list(dateId, 'you');
    expect(body).toHaveLength(1);
    expect(body[0]).toMatchObject({
      reason: null,
      body: 'Hi! Looking forward to it',
      fromYou: true,
    });
  });

  it('sends a chip message with its §40 reason', async () => {
    const dateId = await makeDate(60);
    const res = await send(dateId, 'you', {
      reason: 'running_late',
      body: 'Running about 10 min late — sorry! Almost there.',
    }).expect(201);
    expect(res.body).toMatchObject({
      reason: 'running_late',
      fromYou: true,
      read: false,
    });
  });

  it('refuses the two reasons that open the Date 05 picker', async () => {
    const dateId = await makeDate(60);
    await send(dateId, 'you', { reason: 'change_time', body: 'x' }).expect(400);
    await send(dateId, 'you', { reason: 'change_place', body: 'x' }).expect(
      400,
    );
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
    const res = await send(dateId, 'you', {
      reason: 'on_my_way',
      body: 'On my way',
    }).expect(400);
    expect(res.body.message).toContain('opens about 90 minutes before');
  });

  it('refuses a message on a date that has ended in the chat', async () => {
    const dateId = await makeDate(60, DateStatus.EndedInChat);
    await send(dateId, 'you', { body: 'Still there?' }).expect(409);
  });

  const getDate = (who: keyof typeof PHONES) =>
    request(server)
      .get('/me/date')
      .query({ tz: 'Europe/Berlin' })
      .set('Authorization', as(who))
      .expect(200);

  it('stamps the tutorial as seen for the caller only (Date 02 A)', async () => {
    const dateId = await makeDate(60);
    expect((await getDate('you')).body.date.chatTutorialSeen).toBe(false);
    await request(server)
      .post(`/dates/${dateId}/chat/tutorial`)
      .set('Authorization', as('you'))
      .expect(204);
    await request(server)
      .post(`/dates/${dateId}/chat/tutorial`)
      .set('Authorization', as('you'))
      .expect(204);
    expect((await getDate('you')).body.date.chatTutorialSeen).toBe(true);
    expect((await getDate('leonie')).body.date.chatTutorialSeen).toBe(false);
  });

  it('shows an unread message with no chip as unread with no reason', async () => {
    await makeDate(60);
    const dateId = (await getDate('you')).body.date.id;
    await send(dateId, 'leonie', { body: 'Hey, almost there' }).expect(201);
    expect((await getDate('you')).body.date.unread).toMatchObject({
      count: 1,
      reason: null,
    });
    expect((await getDate('you')).body.ended).toBeNull();
  });

  it('reads your message as read once the other person has read the chat, and theirs as unread-free', async () => {
    const dateId = await makeDate(60);
    await send(dateId, 'you', {
      reason: 'on_my_way',
      body: 'On my way now — see you in a few!',
    }).expect(201);
    expect((await list(dateId, 'you')).body[0].read).toBe(false);

    await request(server)
      .post(`/dates/${dateId}/chat/read`)
      .set('Authorization', as('leonie'))
      .expect(204);
    expect((await list(dateId, 'you')).body[0].read).toBe(true);

    const theirs = (await list(dateId, 'leonie')).body[0];
    expect(theirs).toMatchObject({ fromYou: false, read: null });
  });

  describe('ending the date in the chat', () => {
    const end = (dateId: string, who: keyof typeof PHONES) =>
      request(server)
        .post(`/dates/${dateId}/end-in-chat`)
        .set('Authorization', as(who));
    const seen = (dateId: string, who: keyof typeof PHONES) =>
      request(server)
        .post(`/dates/${dateId}/ended/seen`)
        .set('Authorization', as(who));

    it('refuses to end it before an "I can\'t make it today" message is in the thread', async () => {
      const dateId = await makeDate(60);
      await send(dateId, 'you', {
        reason: 'running_late',
        body: 'Running late',
      }).expect(201);
      await end(dateId, 'you').expect(409);
    });

    it('ends it for both after the match says they cannot make it -- once, released, no cancel event', async () => {
      const dateId = await makeDate(60);
      const datesService = app.get(DatesService);
      const sent: Array<{
        eventName: string;
        properties?: Record<string, unknown>;
      }> = [];
      const spy = jest
        .spyOn(app.get(AnalyticsService), 'trackServerEvent')
        .mockImplementation((e) => {
          sent.push(e);
          return Promise.resolve();
        });
      try {
        expect(await datesService.isLockedFromMatching(id.you)).toBe(true);
        await send(dateId, 'leonie', {
          reason: 'cant_make_it',
          body: 'So sorry, something came up at work and I cannot make it.',
        }).expect(201);

        const res = await end(dateId, 'you').expect(200);
        expect(res.body).toEqual({ dateId, name: 'Leonie', endedByYou: true });
        await end(dateId, 'you').expect(409);

        expect((await getDate('you')).body).toEqual({
          date: null,
          ended: { dateId, name: 'Leonie', endedByYou: true },
        });
        expect((await getDate('leonie')).body).toEqual({
          date: null,
          ended: { dateId, name: 'Max', endedByYou: false },
        });
        expect(await datesService.isLockedFromMatching(id.you)).toBe(false);
        expect(await datesService.isLockedFromMatching(id.leonie)).toBe(false);

        await seen(dateId, 'leonie').expect(204);
        expect((await getDate('leonie')).body.ended).toBeNull();
        expect((await getDate('you')).body.ended).not.toBeNull();
        await seen(dateId, 'you').expect(204);
        expect((await getDate('you')).body.ended).toBeNull();

        const ended = sent.filter((e) => e.eventName === 'date_ended_in_chat');
        expect(ended).toHaveLength(1);
        expect(ended[0].properties).toMatchObject({
          date_id: dateId,
          cant_make_it_from: 'match',
        });
        expect(typeof ended[0].properties?.minutes_before_start).toBe('number');
        expect(sent.some((e) => e.eventName === 'date_cancelled')).toBe(false);
      } finally {
        spy.mockRestore();
      }
    });

    it('opens Search again for both: the match no longer holds either of them', async () => {
      // The real gate, not the date lock alone: Search, Likes and Instant read an active match from
      // the open Check-In as "has a date" (the SHOWUP-194 review found ending left both shut).
      const db = app.get(DataSource);
      const pair = orderedUserPair(id.you, id.leonie);
      const people = [id.you, id.leonie];
      await db.query(
        `UPDATE "users" SET "status" = 'active' WHERE "id" = ANY($1::uuid[])`,
        [people],
      );
      for (const who of people) {
        await db.query(
          `INSERT INTO "check_in_windows"
             ("user_id", "opened_at", "committed_at", "closes_at", "motivation", "vibes", "uplift",
              "district")
           VALUES ($1, now() - interval '1 hour', now() - interval '1 hour',
                   now() + interval '47 hours', 'long_term',
                   ARRAY['coffee_chat']::check_in_vibe_enum[], 'A walk', 'Kreuzberg')`,
          [who],
        );
      }
      await db.query(
        `INSERT INTO "matches" ("user_a_id", "user_b_id", "status") VALUES ($1, $2, 'active')`,
        pair,
      );
      const search = app.get(SearchService);
      const held = async () => [
        (await search.availabilityOf(id.leonie, id.you)).status,
        (await search.availabilityOf(id.you, id.leonie)).status,
      ];
      try {
        const dateId = await makeDate(60);
        expect(await held()).toEqual([
          Availability.Matched,
          Availability.Matched,
        ]);
        await send(dateId, 'leonie', {
          reason: 'cant_make_it',
          body: 'So sorry, something came up and I cannot make it.',
        }).expect(201);
        await end(dateId, 'you').expect(200);
        expect(await held()).not.toContain(Availability.Matched);
      } finally {
        await db.query(
          `DELETE FROM "matches" WHERE "user_a_id" = $1 AND "user_b_id" = $2`,
          pair,
        );
        await db.query(
          `DELETE FROM "check_in_windows" WHERE "user_id" = ANY($1::uuid[])`,
          [people],
        );
      }
    });

    it("carries the match's photo in the outcome, for H shown at the next launch", async () => {
      // At the next launch there is no held date to take the photo from: H drew an initial (the
      // SHOWUP-194 review). The outcome brings it.
      const db = app.get(DataSource);
      const key = `${id.leonie}/outcome-photo.jpg`;
      await db.query(
        `INSERT INTO "profile_photos" ("user_id", "storage_key", "content_type", "position")
         VALUES ($1, $2, 'image/jpeg', 0)`,
        [id.leonie, key],
      );
      try {
        const dateId = await makeDate(60);
        await send(dateId, 'you', {
          reason: 'cant_make_it',
          body: 'Sorry, I really cannot.',
        }).expect(201);
        await end(dateId, 'you').expect(200);
        const ended = (await getDate('you')).body.ended as {
          photoUrl?: string;
        };
        expect(ended.photoUrl).toContain(key);
      } finally {
        await db.query(
          `DELETE FROM "profile_photos" WHERE "storage_key" = $1`,
          [key],
        );
      }
    });

    it('can still be ended after the meet time has passed', async () => {
      const dateId = await makeDate(60);
      await send(dateId, 'you', {
        reason: 'cant_make_it',
        body: 'Sorry, I really cannot.',
      }).expect(201);
      await dates.update({ id: dateId }, { scheduledAt: minutesFromNow(-30) });
      const res = await end(dateId, 'leonie').expect(200);
      expect(res.body).toEqual({ dateId, name: 'Max', endedByYou: true });
    });

    it('is not a past date to Search: it hides neither from the other and books nobody a date', async () => {
      // Ended in the chat, the date never happened -- as a cancelled one never did. Filter 10 would
      // hide the two from each other for good (the guide tells them to agree another day), and
      // Reliability would count it booked but not attended for both (the SHOWUP-194 review).
      const dateId = await makeDate(60);
      await send(dateId, 'you', {
        reason: 'cant_make_it',
        body: 'Sorry, I really cannot.',
      }).expect(201);
      await dates.update({ id: dateId }, { scheduledAt: minutesFromNow(-30) });
      await end(dateId, 'leonie').expect(200);
      // Search's own two reads, run against the database: they are private to the ranker.
      const probe: {
        pastDatePartners(
          v: string,
          ids: string[],
          now: Date,
        ): Promise<Set<string>>;
        pastDatesOf(
          ids: string[],
          now: Date,
        ): Promise<Map<string, { booked: number; attended: number }>>;
      } = app.get(SearchService);
      const now = new Date();
      expect([
        ...(await probe.pastDatePartners(id.you, [id.leonie], now)),
      ]).toEqual([]);
      const past = await probe.pastDatesOf([id.you, id.leonie], now);
      expect(past.get(id.you)?.booked ?? 0).toBe(0);
      expect(past.get(id.leonie)?.booked ?? 0).toBe(0);
    });
  });
});
