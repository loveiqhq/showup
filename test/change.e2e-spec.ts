import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import request from 'supertest';
import { DataSource, In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { AnalyticsService } from './../src/modules/analytics/analytics.service';
import { ChangeSuggestionsService } from './../src/modules/dates/change-suggestions.service';
import { DEV_PARTNER_PHONE } from './../src/modules/date-screen/date-dev.service';
import { DateEntity } from './../src/modules/dates/entities/date.entity';
import { DateStatus } from './../src/modules/dates/util/date-lifecycle';
import { NotificationDispatchService } from './../src/modules/notifications/services/notification-dispatch.service';
import { Profile } from './../src/modules/profiles/entities/profile.entity';
import { User } from './../src/modules/users/entities/user.entity';

/**
 * Date 05 (SHOWUP-196): suggest, agree, expire, the payload and the thread, through the real app and
 * database. The pure rules are `change-suggestions.spec.ts`, the slots `change-slots.spec.ts`.
 *
 * The clock is the real one, so the meet time is "an hour from now" and the zone is a fixed-offset
 * one whose local time is about noon: the slot list (which stops at local midnight) then always has
 * hours in it, whenever the suite runs.
 */
const PHONES = {
  you: '+491701960101',
  leonie: '+491701960102',
  stranger: '+491701960103',
};
const hourNow = new Date().getUTCHours();
const shift = 12 - hourNow;
const TZ =
  shift === 0
    ? 'Etc/GMT'
    : shift > 0
      ? `Etc/GMT-${shift}`
      : `Etc/GMT+${-shift}`;
const VENUE_NAME = 'E2E Change Cafe';
/** The meet place is Café Fräulein's fixture, so suggesting it is "the current place". */
const BINDING_PLACE_ID = 'fixture:cafe-fraeulein';
const KRONE = { lat: 52.5028, lng: 13.4223 };
const MINUTE = 60_000;

interface SentEvent {
  userId: string;
  eventName: string;
  properties?: Record<string, unknown>;
}

describe('Date 05 change time and place (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let db: DataSource;
  let dates: Repository<DateEntity>;
  const token: Record<string, string> = {};
  const id: Record<string, string> = {};
  let venueId: string;
  let dateId: string;
  let bindingAt: Date;
  let events: SentEvent[];
  let pushes: Array<[string, string, Record<string, string>, unknown]>;

  const as = (who: string) => `Bearer ${token[who]}`;
  const named = (name: string) => events.filter((e) => e.eventName === name);
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

  const screen = async (who: string) =>
    (
      await request(server)
        .get('/me/date')
        .query({ tz: TZ })
        .set('Authorization', as(who))
        .expect(200)
    ).body.date;
  /** The slots the viewer may suggest, from the payload, as the picker reads them. */
  const slotsFor = async (who: string): Promise<string[]> =>
    (await screen(who)).change.slots;
  const suggest = (who: string, body: Record<string, unknown>, on = dateId) =>
    request(server)
      .post(`/dates/${on}/suggestions`)
      .set('Authorization', as(who))
      .send({
        entry: 'chat_chip',
        clientId: randomUUID(),
        tz: TZ,
        ...body,
      });
  const suggestTime = (who: string, time: string, extra = {}) =>
    suggest(who, { kind: 'time', time, ...extra });
  const suggestPlace = (
    who: string,
    placeId = 'fixture:bar-lenz',
    extra = {},
  ) => suggest(who, { kind: 'place', placeId, fromSearch: false, ...extra });
  const agree = (who: string, sid: string, surface = 'coordinate_card') =>
    request(server)
      .post(`/dates/${dateId}/suggestions/${sid}/agree`)
      .set('Authorization', as(who))
      .send({ surface });
  const thread = async (who: string) =>
    (
      await request(server)
        .get(`/dates/${dateId}/suggestions`)
        .set('Authorization', as(who))
        .expect(200)
    ).body as Array<Record<string, any>>;
  const rowOf = async (sid: string) =>
    (
      await db.query<Array<Record<string, any>>>(
        `SELECT * FROM date_change_suggestions WHERE id = $1`,
        [sid],
      )
    )[0];
  const dateRow = () => dates.findOneByOrFail({ id: dateId });

  const addWindow = async (userId: string, from: Date, to: Date) => {
    const [w] = await db.query<Array<{ id: string }>>(
      `INSERT INTO check_in_windows (user_id, committed_at, closes_at, motivation, vibes)
       VALUES ($1, now(), $2, 'long_term', ARRAY['coffee_chat']::check_in_vibe_enum[]) RETURNING id`,
      [userId, new Date(Date.now() + 48 * 3_600_000)],
    );
    await db.query(
      `INSERT INTO check_ins (user_id, availability_start, availability_end, check_in_window_id)
       VALUES ($1, $2, $3, $4)`,
      [userId, from, to, w.id],
    );
  };

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
    await users.delete({
      phone: In([...Object.values(PHONES), DEV_PARTNER_PHONE]),
    });
    for (const who of Object.keys(PHONES) as Array<keyof typeof PHONES>) {
      await signIn(who);
    }
    const profiles = db.getRepository(Profile);
    for (const [who, name] of [
      ['you', 'Max'],
      ['leonie', 'Leonie'],
    ] as const) {
      await profiles.save(
        profiles.create({
          userId: id[who],
          displayName: name,
          dateOfBirth: '1996-01-01',
          isVisible: true,
        }),
      );
    }
    // Both people are free from an hour ago to ten hours ahead.
    for (const who of ['you', 'leonie'] as const) {
      await addWindow(
        id[who],
        new Date(Date.now() - 60 * MINUTE),
        new Date(Date.now() + 10 * 60 * MINUTE),
      );
    }
    await db.query(
      `DELETE FROM venues WHERE name = $1 OR place_id LIKE 'fixture:%'`,
      [VENUE_NAME],
    );
    const [venue] = await db.query<Array<{ id: string }>>(
      `INSERT INTO venues (name, address, location, place_id, place_type)
       VALUES ($1, 'Manteuffelstr. 30, Kreuzberg',
               ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography, $4, 'cafe') RETURNING id`,
      [VENUE_NAME, KRONE.lng, KRONE.lat, BINDING_PLACE_ID],
    );
    venueId = venue.id;

    jest
      .spyOn(app.get(AnalyticsService), 'trackServerEvent')
      .mockImplementation((p) => {
        events.push(p);
        return Promise.resolve();
      });
    jest
      .spyOn(app.get(NotificationDispatchService), 'sendPush')
      .mockImplementation((...args: unknown[]) => {
        pushes.push(args as never);
        return Promise.resolve({ status: 'sent', delivered: 1 } as never);
      });
  });

  beforeEach(async () => {
    events = [];
    pushes = [];
    await dates
      .createQueryBuilder()
      .delete()
      .where('user_a_id IN (:...ids) OR user_b_id IN (:...ids)', {
        ids: [id.you, id.leonie],
      })
      .execute();
    bindingAt = new Date(Date.now() + 60 * MINUTE);
    dateId = (
      await dates.save(
        dates.create({
          userAId: id.you,
          userBId: id.leonie,
          scheduledAt: bindingAt,
          venueId,
          status: DateStatus.Confirmed,
        }),
      )
    ).id;
  });

  afterAll(async () => {
    await dates
      .createQueryBuilder()
      .delete()
      .where('user_a_id IN (:...ids) OR user_b_id IN (:...ids)', {
        ids: [id.you, id.leonie],
      })
      .execute();
    await app
      .get<Repository<User>>(getRepositoryToken(User), { strict: false })
      .delete({ phone: In([...Object.values(PHONES), DEV_PARTNER_PHONE]) });
    await db.query(
      `DELETE FROM venues WHERE name = $1 OR place_id LIKE 'fixture:%'`,
      [VENUE_NAME],
    );
    await app.close();
  });

  describe('suggest', () => {
    it('stores a time suggestion, leaves the binding alone and tells the match', async () => {
      const [slot] = await slotsFor('you');
      const res = await suggestTime('you', slot).expect(201);

      expect(res.body).toMatchObject({
        kind: 'time',
        byYou: true,
        status: 'pending',
        time: slot,
        wasTime: bindingAt.toISOString(),
        wasPlace: { name: VENUE_NAME },
        deadline: new Date(bindingAt.getTime() - 30 * MINUTE).toISOString(),
      });
      const date = await dateRow();
      expect(date.scheduledAt.getTime()).toBe(bindingAt.getTime());
      expect(date.venueId).toBe(venueId);

      const [event] = named('change_suggested');
      expect(named('change_suggested')).toHaveLength(1);
      expect(event.userId).toBe(id.you);
      expect(event.properties).toEqual({
        date_id: dateId,
        suggestion_id: res.body.id,
        kind: 'time',
        entry: 'chat_chip',
        replaces: 'none',
        offset_min: Math.round(
          (new Date(slot).getTime() - bindingAt.getTime()) / MINUTE,
        ),
        from_search: null,
        minutes_to_deadline: expect.any(Number),
        minutes_before_start: expect.any(Number),
      });
      expect(JSON.stringify(event.properties)).not.toMatch(
        /Lenz|Krone|Fr.ulein|address|lat|lng/i,
      );

      expect(pushes).toHaveLength(1);
      const [to, type, params, data] = pushes[0] as unknown as [
        string,
        string,
        Record<string, string>,
        Record<string, string>,
      ];
      expect([to, type]).toEqual([id.leonie, 'date_change_suggested']);
      expect(params.name).toBe('Max');
      expect(params.deadline).toMatch(/^\d\d:\d\d$/);
      expect(data).toEqual({ type: 'date_change_suggested', dateId });
    });

    it('stores a place suggestion as a snapshot, with from_search and no time', async () => {
      const res = await suggestPlace('you', 'fixture:bar-lenz', {
        fromSearch: true,
        entry: 'coordinate_tile',
      }).expect(201);
      expect(res.body).toMatchObject({
        kind: 'place',
        place: {
          placeId: 'fixture:bar-lenz',
          name: 'Bar Lenz',
          type: 'bar',
          address: 'Lausitzer Platz 9, Kreuzberg',
        },
      });
      expect(res.body.time).toBeUndefined();
      const row = await rowOf(res.body.id);
      expect(row.was_place_name).toBe(VENUE_NAME);
      expect(row.was_venue_id).toBe(venueId);
      const [event] = named('change_suggested');
      expect(event.properties).toMatchObject({
        kind: 'place',
        entry: 'coordinate_tile',
        offset_min: null,
        from_search: true,
      });
      // The push carries no place at all.
      expect(JSON.stringify(pushes)).not.toMatch(/Lenz|Kreuzberg/);
    });

    it('replaces the open same-kind one whoever sent it, and says whose', async () => {
      const [a, b, c] = await slotsFor('you');
      const first = (await suggestTime('you', a).expect(201)).body;
      // Leonie answers with Suggest another: it replaces YOUR open one.
      const second = (await suggestTime('leonie', b).expect(201)).body;
      // And then you replace your own... no: hers is open, so yours replaces THEIRS again.
      const third = (await suggestTime('you', c).expect(201)).body;

      expect((await rowOf(first.id)).status).toBe('replaced');
      expect((await rowOf(first.id)).replaced_at).not.toBeNull();
      expect((await rowOf(second.id)).status).toBe('replaced');
      expect((await rowOf(third.id)).status).toBe('pending');
      expect(
        named('change_suggested').map((e) => e.properties?.replaces),
      ).toEqual(['none', 'theirs', 'theirs']);

      // Own: the same person suggests twice in a row.
      events = [];
      const [d] = await slotsFor('you');
      await suggestTime('you', d).expect(201);
      expect(named('change_suggested')[0].properties?.replaces).toBe('own');
    });

    it('keeps one open suggestion per kind: a place does not replace a time', async () => {
      const [slot] = await slotsFor('you');
      const time = (await suggestTime('you', slot).expect(201)).body;
      await suggestPlace('you').expect(201);
      expect((await rowOf(time.id)).status).toBe('pending');
      expect(named('change_suggested')[1].properties?.replaces).toBe('none');
    });

    it('is idempotent on clientId: the stored row, no second event or push', async () => {
      const [slot] = await slotsFor('you');
      const clientId = randomUUID();
      const first = await suggestTime('you', slot, { clientId }).expect(201);
      const again = await suggestTime('you', slot, { clientId }).expect(201);
      expect(again.body.id).toBe(first.body.id);
      expect(named('change_suggested')).toHaveLength(1);
      expect(pushes).toHaveLength(1);
      const count = await db.query(
        `SELECT 1 FROM date_change_suggestions WHERE date_id = $1`,
        [dateId],
      );
      expect(count).toHaveLength(1);
    });

    it('answers 409 past meet - 0:30', async () => {
      await dates.update(
        { id: dateId },
        { scheduledAt: new Date(Date.now() + 20 * MINUTE) },
      );
      await suggestTime(
        'you',
        new Date(Date.now() + 45 * MINUTE).toISOString(),
      ).expect(409);
      await suggestPlace('you').expect(409);
      expect(named('change_suggested')).toHaveLength(0);
    });

    it('answers 409 once either person has a committed check-in', async () => {
      const [slot] = await slotsFor('you');
      await db.query(
        `INSERT INTO date_arrival_check_ins (date_id, user_id, location, entry, client_id, committed_at)
         VALUES ($1, $2, ST_SetSRID(ST_MakePoint(13.4, 52.5), 4326)::geography, 'date_screen', $3, now())`,
        [dateId, id.leonie, randomUUID()],
      );
      await suggestTime('you', slot).expect(409);
      expect((await screen('you')).change.allowed).toBe(false);
    });

    it('answers 409 for a time that is not one of the slots', async () => {
      const [slot] = await slotsFor('you');
      const off = new Date(new Date(slot).getTime() + MINUTE).toISOString();
      await suggestTime('you', off).expect(409);
      // Not later than the meet time either.
      await suggestTime('you', bindingAt.toISOString()).expect(409);
      expect(named('change_suggested')).toHaveLength(0);
    });

    it('answers 409 for the current place, and the list leaves it out', async () => {
      await suggestPlace('you', BINDING_PLACE_ID).expect(409);
      const list = await request(server)
        .get(`/dates/${dateId}/change/places`)
        .set('Authorization', as('you'))
        .expect(200);
      const ids = list.body.places.map((p: { placeId: string }) => p.placeId);
      expect(ids).toHaveLength(4);
      expect(ids).not.toContain(BINDING_PLACE_ID);
    });

    it('answers 400 for a place that is not public and for a missing value', async () => {
      await suggestPlace('you', 'fixture:no-such-place').expect(400);
      await suggest('you', { kind: 'place' }).expect(400);
      await suggest('you', { kind: 'time' }).expect(400);
      await suggest('you', { kind: 'time', time: 'soon' }).expect(400);
      await suggest('you', { kind: 'other' }).expect(400);
    });

    it('answers 403 to a stranger, 404 for an unknown date, 401 without a token', async () => {
      const [slot] = await slotsFor('you');
      await suggestTime('stranger', slot).expect(403);
      await suggestTime('you', slot, {}).expect(201);
      await suggest('you', { kind: 'time', time: slot }, randomUUID()).expect(
        404,
      );
      await request(server)
        .post(`/dates/${dateId}/suggestions`)
        .send({})
        .expect(401);
    });
  });

  describe('agree', () => {
    it('a time on the card moves the meet time, the open place deadline, and stamps an open chat', async () => {
      const [, slot] = await slotsFor('you');
      const time = (await suggestTime('you', slot).expect(201)).body;
      const place = (await suggestPlace('you').expect(201)).body;
      events = [];
      pushes = [];

      const res = await agree('leonie', time.id).expect(200);

      expect(res.body.status).toBe('agreed');
      expect(res.body.byYou).toBe(false);
      expect(res.body.answeredAt).toEqual(expect.any(String));
      const date = await dateRow();
      expect(date.scheduledAt.toISOString()).toBe(slot);
      expect(date.venueId).toBe(venueId);
      // The open place suggestion now answers to the NEW meet time - 0:30.
      expect((await rowOf(place.id)).deadline.toISOString()).toBe(
        new Date(new Date(slot).getTime() - 30 * MINUTE).toISOString(),
      );
      // The chat was open by the old time (an hour away) and is stamped now, so the later time
      // cannot shut it.
      expect(date.coordinateUnlockedAt).not.toBeNull();
      const unlocked = named('coordinate_unlocked');
      expect(unlocked.map((e) => e.userId).sort()).toEqual(
        [id.you, id.leonie].sort(),
      );
      expect(unlocked[0].properties).toEqual({
        minutes_before_start: expect.any(Number),
        cause: 'time',
      });

      const [agreed] = named('change_agreed');
      expect(named('change_agreed')).toHaveLength(1);
      expect(agreed.userId).toBe(id.leonie);
      expect(agreed.properties).toEqual({
        date_id: dateId,
        suggestion_id: time.id,
        kind: 'time',
        surface: 'coordinate_card',
        seconds_since_suggested: expect.any(Number),
        minutes_to_deadline: expect.any(Number),
      });
      const push = pushes.find((p) => p[1] === 'date_change_agreed');
      expect(push?.[0]).toBe(id.you);
      expect(push?.[2]).toEqual({ name: 'Leonie' });
      expect(push?.[3]).toEqual({ type: 'date_change_agreed', dateId });
    });

    it('a time agreed on a chat that is still locked leaves it to the new time', async () => {
      await dates.update(
        { id: dateId },
        { scheduledAt: new Date(Date.now() + 180 * MINUTE) },
      );
      const [slot] = await slotsFor('you');
      const time = (await suggestTime('you', slot).expect(201)).body;
      events = [];
      await agree('leonie', time.id, 'thread').expect(200);
      expect((await dateRow()).coordinateUnlockedAt).toBeNull();
      expect(named('coordinate_unlocked')).toHaveLength(0);
      expect(named('change_agreed')[0].properties?.surface).toBe('thread');
    });

    it('a place upserts the venue by place id and moves the meet place', async () => {
      const place = (await suggestPlace('you').expect(201)).body;
      await agree('leonie', place.id, 'thread').expect(200);

      const date = await dateRow();
      expect(date.venueId).not.toBe(venueId);
      const [venue] = await db.query<Array<Record<string, any>>>(
        `SELECT name, address, place_id, place_type,
                ST_Y(location::geometry) AS lat FROM venues WHERE id = $1`,
        [date.venueId],
      );
      expect(venue).toMatchObject({
        name: 'Bar Lenz',
        address: 'Lausitzer Platz 9, Kreuzberg',
        place_id: 'fixture:bar-lenz',
        place_type: 'bar',
      });
      expect(Number(venue.lat)).toBeCloseTo(52.4994, 4);
      // The time is untouched.
      expect(date.scheduledAt.getTime()).toBe(bindingAt.getTime());

      // Agreeing the same place again from another suggestion reuses the venue row.
      await suggestPlace('leonie', BINDING_PLACE_ID).expect(201);
      await suggestPlace('you', 'fixture:bar-lenz').expect(409); // now the current place
      const venues = await db.query(
        `SELECT 1 FROM venues WHERE place_id = 'fixture:bar-lenz'`,
      );
      expect(venues).toHaveLength(1);
    });

    it('answers 409 and fires nothing for a settled one, the sender, and a late race', async () => {
      const [a, b] = await slotsFor('you');
      const first = (await suggestTime('you', a).expect(201)).body;
      // The sender may not agree to their own.
      await agree('you', first.id).expect(409);
      // A second suggestion replaces the first; the first can no longer be agreed (the race).
      const second = (await suggestTime('you', b).expect(201)).body;
      events = [];
      pushes = [];
      await agree('leonie', first.id).expect(409);
      expect(named('change_agreed')).toHaveLength(0);
      expect(pushes).toHaveLength(0);
      expect((await dateRow()).scheduledAt.getTime()).toBe(bindingAt.getTime());

      await agree('leonie', second.id).expect(200);
      // And once agreed, a repeat is a 409 as well, with no second event.
      await agree('leonie', second.id).expect(409);
      expect(named('change_agreed')).toHaveLength(1);
    });

    it('answers 403 to a stranger, 404 for an unknown suggestion', async () => {
      const [slot] = await slotsFor('you');
      const time = (await suggestTime('you', slot).expect(201)).body;
      await agree('stranger', time.id).expect(403);
      await agree('leonie', randomUUID()).expect(404);
      await request(server)
        .post(`/dates/${dateId}/suggestions/${time.id}/agree`)
        .set('Authorization', as('leonie'))
        .send({ surface: 'nowhere' })
        .expect(400);
    });

    it('answers 409 when the deadline has passed or someone has arrived', async () => {
      const [a, b] = await slotsFor('you');
      const late = (await suggestTime('you', a).expect(201)).body;
      await db.query(
        `UPDATE date_change_suggestions SET deadline = now() - interval '1 minute' WHERE id = $1`,
        [late.id],
      );
      await agree('leonie', late.id).expect(409);

      const fresh = (await suggestTime('you', b).expect(201)).body;
      await db.query(
        `INSERT INTO date_arrival_check_ins (date_id, user_id, location, entry, client_id, committed_at)
         VALUES ($1, $2, ST_SetSRID(ST_MakePoint(13.4, 52.5), 4326)::geography, 'date_screen', $3, now())`,
        [dateId, id.you, randomUUID()],
      );
      await agree('leonie', fresh.id).expect(409);
    });
  });

  describe('expiry', () => {
    it('expires only pending rows past the deadline, snapshots the binding, one event and both pushes', async () => {
      const [a, b] = await slotsFor('you');
      const replaced = (await suggestTime('you', a).expect(201)).body;
      const time = (await suggestTime('you', b).expect(201)).body; // replaces `replaced`
      const place = (await suggestPlace('you').expect(201)).body;
      // The replaced one is past its deadline too: it must never expire.
      await db.query(
        `UPDATE date_change_suggestions SET deadline = now() - interval '5 minutes'
          WHERE id IN ($1, $2)`,
        [replaced.id, time.id],
      );
      events = [];
      pushes = [];

      const expired = await app.get(ChangeSuggestionsService).expireDue();

      expect(expired).toBe(1);
      const row = await rowOf(time.id);
      expect(row.status).toBe('expired');
      expect(row.expired_at).not.toBeNull();
      expect(row.expired_binding_time.getTime()).toBe(bindingAt.getTime());
      expect(row.expired_binding_place_name).toBe(VENUE_NAME);
      expect((await rowOf(replaced.id)).status).toBe('replaced');
      expect((await rowOf(place.id)).status).toBe('pending');

      expect(named('change_expired')).toHaveLength(1);
      expect(named('change_expired')[0].properties).toEqual({
        date_id: dateId,
        suggestion_id: time.id,
        kind: 'time',
      });
      const expiredPushes = pushes.filter(
        (p) => p[1] === 'date_change_expired',
      );
      expect(expiredPushes.map((p) => p[0]).sort()).toEqual(
        [id.you, id.leonie].sort(),
      );
      expect(expiredPushes[0][3]).toEqual({
        type: 'date_change_expired',
        dateId,
      });

      // A second run finds nothing: nothing fires twice.
      events = [];
      pushes = [];
      expect(await app.get(ChangeSuggestionsService).expireDue()).toBe(0);
      expect(events).toHaveLength(0);
      expect(pushes).toHaveLength(0);

      // An expired one cannot be agreed.
      await agree('leonie', time.id).expect(409);
    });

    it('does not touch a date that is no longer confirmed', async () => {
      const [slot] = await slotsFor('you');
      const time = (await suggestTime('you', slot).expect(201)).body;
      await db.query(
        `UPDATE date_change_suggestions SET deadline = now() - interval '1 minute' WHERE id = $1`,
        [time.id],
      );
      await dates.update({ id: dateId }, { status: DateStatus.Cancelled });
      events = [];
      expect(await app.get(ChangeSuggestionsService).expireDue()).toBe(0);
      expect(events).toHaveLength(0);
    });
  });

  describe('the payload and the thread', () => {
    it('GET /me/date carries change and changeRequest for each person', async () => {
      const slots = await slotsFor('you');
      expect(slots.length).toBeGreaterThan(2);
      expect([...slots].sort()).toEqual(slots);
      expect(new Date(slots[0]).getTime()).toBeGreaterThan(bindingAt.getTime());

      const time = (await suggestTime('you', slots[0]).expect(201)).body;
      const mine = await screen('you');
      const theirs = await screen('leonie');

      expect(mine.change).toMatchObject({
        cutoffAt: new Date(bindingAt.getTime() - 30 * MINUTE).toISOString(),
        allowed: true,
      });
      expect(mine.change.timeWas).toBeUndefined();
      expect(mine.change.placeWas).toBeUndefined();
      expect(mine.change.pending).toHaveLength(1);
      expect(mine.change.pending[0]).toMatchObject({
        id: time.id,
        byYou: true,
      });
      expect(mine.changeRequest).toBeNull();
      expect(theirs.changeRequest).toMatchObject({
        id: time.id,
        kind: 'time',
        byYou: false,
        time: slots[0],
      });
      expect(theirs.change.pending[0].byYou).toBe(false);
    });

    it('the request is the earliest deadline, time first on a tie, and agreed changes fill timeWas / placeWas', async () => {
      const [a] = await slotsFor('you');
      const time = (await suggestTime('you', a).expect(201)).body;
      const place = (await suggestPlace('you').expect(201)).body;
      // Same deadline (both meet - 0:30): time first.
      expect((await screen('leonie')).changeRequest.id).toBe(time.id);
      // Make the place due sooner.
      await db.query(
        `UPDATE date_change_suggestions SET deadline = deadline - interval '5 minutes' WHERE id = $1`,
        [place.id],
      );
      expect((await screen('leonie')).changeRequest.id).toBe(place.id);

      await agree('leonie', place.id).expect(200);
      let me = await screen('you');
      expect(me.change.placeWas).toEqual({
        name: VENUE_NAME,
        address: 'Manteuffelstr. 30, Kreuzberg',
      });
      expect(me.change.timeWas).toBeUndefined();
      expect(me.place.name).toBe('Bar Lenz');

      await agree('leonie', time.id).expect(200);
      me = await screen('you');
      expect(me.change.timeWas).toBe(bindingAt.toISOString());
      expect(me.meetAt).toBe(a);
      expect(me.changeRequest).toBeNull();
      expect(me.change.pending).toEqual([]);
    });

    it('the thread lists every suggestion with its history, oldest first', async () => {
      const [a, b] = await slotsFor('you');
      const first = (await suggestTime('you', a).expect(201)).body;
      const second = (await suggestTime('leonie', b).expect(201)).body;
      await agree('you', second.id, 'thread').expect(200);
      const place = (await suggestPlace('you').expect(201)).body;
      await db.query(
        `UPDATE date_change_suggestions SET deadline = now() - interval '1 minute' WHERE id = $1`,
        [place.id],
      );
      await app.get(ChangeSuggestionsService).expireDue();

      const items = await thread('you');
      expect(items.map((i) => [i.id, i.status])).toEqual([
        [first.id, 'replaced'],
        [second.id, 'agreed'],
        [place.id, 'expired'],
      ]);
      expect(items[1]).toMatchObject({
        byYou: false,
        answeredAt: expect.any(String),
      });
      expect(items[2]).toMatchObject({
        kind: 'place',
        expiredAt: expect.any(String),
        // What stayed binding when it expired: the agreed time, and the original place.
        expiredBindingTime: b,
        expiredBindingPlace: { name: VENUE_NAME },
      });
      expect((await thread('leonie'))[1].byYou).toBe(true);
      await request(server)
        .get(`/dates/${dateId}/suggestions`)
        .set('Authorization', as('stranger'))
        .expect(403);
    });

    it('leaves the existing chat read an array of messages', async () => {
      const res = await request(server)
        .get(`/dates/${dateId}/chat`)
        .set('Authorization', as('you'))
        .expect(200);
      expect(Array.isArray(res.body)).toBe(true);
    });
  });

  describe('dev routes (the test match)', () => {
    it('suggest, agree and expire for the caller through the real path', async () => {
      const dev = (path: string, body: object = {}) =>
        request(server)
          .post(`/dev/date${path}`)
          .set('Authorization', as('you'))
          .send(body);
      // The dev menu refuses while the caller has a date with a real person: end this one.
      await dates.update({ id: dateId }, { status: DateStatus.Cancelled });
      await dev('', { minutesFromNow: 120 }).expect(204);

      await dev('/change', { kind: 'time', minutesLater: 30 }).expect(204);
      let me = await screen('you');
      expect(me.changeRequest).toMatchObject({ kind: 'time', byYou: false });
      const wanted = new Date(me.meetAt).getTime() + 30 * MINUTE;
      expect(new Date(me.changeRequest.time).getTime()).toBeGreaterThanOrEqual(
        wanted,
      );
      // The match agrees to the CALLER's suggestion, and the caller has none yet.
      await dev('/change/agree').expect(404);

      // The caller answers hers on the real route.
      await request(server)
        .post(`/dates/${me.id}/suggestions/${me.changeRequest.id}/agree`)
        .set('Authorization', as('you'))
        .send({ surface: 'coordinate_card' })
        .expect(200);
      me = await screen('you');
      expect(me.change.timeWas).toBeDefined();

      // The caller suggests a place; the match agrees to it through the dev route.
      const listed = await request(server)
        .get(`/dates/${me.id}/change/places`)
        .set('Authorization', as('you'))
        .expect(200);
      const pick = listed.body.places[0];
      await request(server)
        .post(`/dates/${me.id}/suggestions`)
        .set('Authorization', as('you'))
        .send({
          kind: 'place',
          placeId: pick.placeId,
          entry: 'coordinate_tile',
          clientId: randomUUID(),
        })
        .expect(201);
      await dev('/change/agree').expect(204);
      me = await screen('you');
      expect(me.place.name).toBe(pick.name);
      expect(me.change.pending).toEqual([]);

      await dev('/change', { kind: 'place' }).expect(204);
      me = await screen('you');
      expect(me.changeRequest).toMatchObject({ kind: 'place' });
      expect(me.changeRequest.place.name).not.toBe('Cafe Krone');

      await dev('/change/expire').expect(204);
      me = await screen('you');
      expect(me.changeRequest).toBeNull();
      const items = (
        await request(server)
          .get(`/dates/${me.id}/suggestions`)
          .set('Authorization', as('you'))
          .expect(200)
      ).body as Array<Record<string, any>>;
      expect(items.at(-1)?.status).toBe('expired');
    });
  });
});
