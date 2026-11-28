import { INestApplication, ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import request from 'supertest';
import { DataSource, In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { AnalyticsService } from './../src/modules/analytics/analytics.service';
import { DEV_PARTNER_PHONE } from './../src/modules/date-screen/date-dev.service';
import { DateTimeoutService } from './../src/modules/dates/date-timeout.service';
import { DateEntity } from './../src/modules/dates/entities/date.entity';
import { DateStatus } from './../src/modules/dates/util/date-lifecycle';
import { NotificationDispatchService } from './../src/modules/notifications/services/notification-dispatch.service';
import { Profile } from './../src/modules/profiles/entities/profile.entity';
import { User } from './../src/modules/users/entities/user.entity';

/**
 * Date 09 (SHOWUP-200): the +2 h switch, through the real app and database. The pure rules are
 * `date-timeout.spec.ts`, the service's mocked paths `date-timeout.service.spec.ts`.
 *
 * The clock is the real one for the dates (the meet time is an hour AHEAD, so the app's own worker
 * never sees one as due) and a made-up `now` for the sweep: `closeDue(meet + 2 h)`. The switch is on
 * only while a sweep runs, so a worker running beside the test cannot close a date early.
 */
const PHONES = { you: '+491701990101', leonie: '+491701990102' };
const hourNow = new Date().getUTCHours();
const shift = 12 - hourNow;
const TZ =
  shift === 0
    ? 'Etc/GMT'
    : shift > 0
      ? `Etc/GMT-${shift}`
      : `Etc/GMT+${-shift}`;
const VENUE_NAME = 'E2E Timeout Cafe';
const BINDING_PLACE_ID = 'fixture:cafe-fraeulein';
const KRONE = { lat: 52.5028, lng: 13.4223 };
const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const SPOT = { latitude: 52.5186, longitude: 13.3761, accuracyM: 12.5 };

interface SentEvent {
  userId: string;
  eventName: string;
  properties?: Record<string, unknown>;
}

describe('Date 09 the +2 h switch (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let db: DataSource;
  let dates: Repository<DateEntity>;
  let config: ConfigService;
  let timeouts: DateTimeoutService;
  const token: Record<string, string> = {};
  const id: Record<string, string> = {};
  let venueId: string;
  let dateId: string;
  let meetAt: Date;
  let events: SentEvent[];
  let pushes: Array<{ userId: string; type: string }>;

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

  /** Run one sweep at `now` with the switch `flag` ('true' when omitted; undefined = unset). */
  const sweep = async (now: Date, flag: string | undefined = 'true') => {
    const real = config.get.bind(config);
    const spy = jest
      .spyOn(config, 'get')
      .mockImplementation((key: string, ...rest: unknown[]) =>
        key === 'DATE_TIMEOUT_ENABLED'
          ? flag
          : (real as (k: string, ...r: unknown[]) => unknown)(key, ...rest),
      );
    try {
      return await timeouts.closeDue(now);
    } finally {
      spy.mockRestore();
    }
  };
  const due = () => new Date(meetAt.getTime() + 2 * HOUR);

  const me = (who: string) =>
    request(server)
      .get('/me/date')
      .query({ tz: TZ })
      .set('Authorization', as(who))
      .expect(200);
  const dateRow = () => dates.findOneByOrFail({ id: dateId });
  const matchStatus = async () =>
    (
      await db.query<Array<{ status: string }>>(
        `SELECT status FROM matches WHERE user_a_id = ANY($1::uuid[])`,
        [[id.you, id.leonie]],
      )
    )[0].status;
  const arrive = (who: string) =>
    request(server)
      .post(`/dates/${dateId}/arrival`)
      .set('Authorization', as(who))
      .send({ ...SPOT, entry: 'date_screen', clientId: randomUUID() });
  const commit = (who: string) =>
    request(server)
      .post(`/dates/${dateId}/arrival/commit`)
      .set('Authorization', as(who));
  const checkIn = (who: string) =>
    db.query(
      `INSERT INTO date_arrival_check_ins
         (date_id, user_id, location, entry, client_id, committed_at)
       VALUES ($1, $2, ST_SetSRID(ST_MakePoint(13.42, 52.5), 4326)::geography,
               'date_screen', $3, now())`,
      [dateId, id[who], randomUUID()],
    );
  const end = (who: string) =>
    request(server)
      .post(`/dates/${dateId}/end`)
      .set('Authorization', as(who))
      .send({ reason: 'met', entry: 'end_date_bar' });
  const cancel = (who: string) =>
    request(server)
      .post(`/dates/${dateId}/cancel`)
      .set('Authorization', as(who));
  const discomfort = (who: string) =>
    request(server)
      .post(`/dates/${dateId}/discomfort`)
      .set('Authorization', as(who));
  const suggest = (who: string, body: Record<string, unknown>) =>
    request(server)
      .post(`/dates/${dateId}/suggestions`)
      .set('Authorization', as(who))
      .send({
        entry: 'chat_chip',
        clientId: randomUUID(),
        tz: TZ,
        ...body,
      });
  const agree = (who: string, sid: string) =>
    request(server)
      .post(`/dates/${dateId}/suggestions/${sid}/agree`)
      .set('Authorization', as(who))
      .send({ surface: 'coordinate_card' });
  const slotsFor = async (who: string): Promise<string[]> =>
    (await me(who)).body.date.change.slots;

  const addWindow = async (userId: string, from: Date, to: Date) => {
    const [w] = await db.query<Array<{ id: string }>>(
      `INSERT INTO check_in_windows (user_id, committed_at, closes_at, motivation, vibes)
       VALUES ($1, now(), $2, 'long_term', ARRAY['coffee_chat']::check_in_vibe_enum[]) RETURNING id`,
      [userId, new Date(Date.now() + 48 * HOUR)],
    );
    await db.query(
      `INSERT INTO check_ins (user_id, availability_start, availability_end, check_in_window_id)
       VALUES ($1, $2, $3, $4)`,
      [userId, from, to, w.id],
    );
  };
  const wipe = async () => {
    await dates
      .createQueryBuilder()
      .delete()
      .where('user_a_id IN (:...ids) OR user_b_id IN (:...ids)', {
        ids: [id.you, id.leonie],
      })
      .execute();
    await db.query(`DELETE FROM matches WHERE user_a_id = ANY($1::uuid[])`, [
      [id.you, id.leonie],
    ]);
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
    config = app.get(ConfigService);
    timeouts = app.get(DateTimeoutService);
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
    // Both people are free from an hour ago to ten hours ahead (so a later time can be agreed).
    for (const who of ['you', 'leonie'] as const) {
      await addWindow(
        id[who],
        new Date(Date.now() - HOUR),
        new Date(Date.now() + 10 * HOUR),
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
      .mockImplementation((userId, type) => {
        pushes.push({ userId, type });
        return Promise.resolve({ status: 'sent', delivered: 1 });
      });
  });

  beforeEach(async () => {
    events = [];
    pushes = [];
    await wipe();
    const [a, b] = [id.you, id.leonie].sort();
    await db.query(
      `INSERT INTO matches (user_a_id, user_b_id, status) VALUES ($1, $2, 'active')`,
      [a, b],
    );
    meetAt = new Date(Date.now() + 60 * MINUTE);
    dateId = (
      await dates.save(
        dates.create({
          userAId: id.you,
          userBId: id.leonie,
          scheduledAt: meetAt,
          venueId,
          status: DateStatus.Confirmed,
        }),
      )
    ).id;
  });

  afterAll(async () => {
    await wipe();
    await app
      .get<Repository<User>>(getRepositoryToken(User), { strict: false })
      .delete({ phone: In([...Object.values(PHONES), DEV_PARTNER_PHONE]) });
    await db.query(
      `DELETE FROM venues WHERE name = $1 OR place_id LIKE 'fixture:%'`,
      [VENUE_NAME],
    );
    await app.close();
  });

  describe('closing a date at +2 h', () => {
    it.each([0, 1, 2])(
      'closes it with %i committed check-in(s), for both people, and fires date_ended once',
      async (count) => {
        if (count >= 1) await checkIn('you');
        if (count >= 2) await checkIn('leonie');

        await expect(sweep(due())).resolves.toBe(1);

        const row = await dateRow();
        expect(row.status).toBe(DateStatus.TimedOut);
        expect(row.endedById).toBeNull();
        expect(row.endReason).toBe('timed_out');
        expect(row.endEntry).toBe('auto_switch');
        expect(row.endedAt).toBeInstanceOf(Date);
        expect(await matchStatus()).toBe('cancelled');
        const [{ n }] = await db.query<Array<{ n: number }>>(
          `SELECT count(*)::int AS n FROM date_status_changes
            WHERE date_id = $1 AND to_status = 'timed_out'`,
          [dateId],
        );
        expect(n).toBe(1);

        expect(named('date_ended')).toHaveLength(1);
        expect(named('date_ended')[0]).toMatchObject({
          userId: id.you,
          properties: {
            date_id: dateId,
            end_reason: 'timed_out',
            entry: 'auto_switch',
            checked_in_count: count,
            minutes_vs_start: 120,
          },
        });
        expect(named('date_ended')[0].properties).not.toHaveProperty(
          'ender_checked_in',
        );
        expect(pushes).toEqual([]);

        for (const who of ['you', 'leonie']) {
          const { ended } = (await me(who)).body;
          expect(ended).toMatchObject({
            dateId,
            reason: 'timed_out',
            endedByYou: false,
            partnerScreen: 'date_timed_out',
          });
        }
      },
    );

    it('closes it with an open discomfort flag too', async () => {
      await discomfort('you').expect(204);
      await expect(sweep(due())).resolves.toBe(1);
      expect((await dateRow()).status).toBe(DateStatus.TimedOut);
      expect(named('date_ended')).toHaveLength(1);
    });

    it('does not close it at +2 h minus a minute', async () => {
      await expect(sweep(new Date(due().getTime() - MINUTE))).resolves.toBe(0);
      expect((await dateRow()).status).toBe(DateStatus.Confirmed);
      expect(named('date_ended')).toHaveLength(0);
    });

    it('closes once when the sweep runs twice, with one date_ended', async () => {
      await expect(sweep(due())).resolves.toBe(1);
      await expect(sweep(due())).resolves.toBe(0);
      expect(named('date_ended')).toHaveLength(1);
    });

    it('closes nothing while the switch is unset, false or 1', async () => {
      for (const flag of [undefined, 'false', '1']) {
        await expect(sweep(due(), flag)).resolves.toBe(0);
      }
      expect((await dateRow()).status).toBe(DateStatus.Confirmed);
      expect(named('date_ended')).toHaveLength(0);
    });
  });

  describe('the due time follows the latest agreed meet time', () => {
    it('after an agreed later time the old due time closes nothing and the new one does', async () => {
      const [, slot] = await slotsFor('you');
      const time = (
        await suggest('you', { kind: 'time', time: slot }).expect(201)
      ).body;
      await agree('leonie', time.id).expect(200);
      const later = new Date(slot);
      expect((await dateRow()).scheduledAt.toISOString()).toBe(
        later.toISOString(),
      );

      await expect(sweep(due())).resolves.toBe(0);
      expect((await dateRow()).status).toBe(DateStatus.Confirmed);
      await expect(sweep(new Date(later.getTime() + 2 * HOUR))).resolves.toBe(
        1,
      );
      expect((await dateRow()).status).toBe(DateStatus.TimedOut);
      expect(named('date_ended')[0].properties?.minutes_vs_start).toBe(120);
    });

    it('a pending time suggestion and an agreed place change leave the due time alone', async () => {
      const [, slot] = await slotsFor('you');
      await suggest('you', { kind: 'time', time: slot }).expect(201);
      const place = (
        await suggest('you', {
          kind: 'place',
          placeId: 'fixture:bar-lenz',
          fromSearch: false,
        }).expect(201)
      ).body;
      await agree('leonie', place.id).expect(200);

      expect((await dateRow()).scheduledAt.toISOString()).toBe(
        meetAt.toISOString(),
      );
      await expect(sweep(new Date(due().getTime() - MINUTE))).resolves.toBe(0);
      await expect(sweep(due())).resolves.toBe(1);
    });
  });

  describe('a date that is already over is untouched', () => {
    it.each([
      [DateStatus.Ended],
      [DateStatus.Cancelled],
      [DateStatus.EndedInChat],
    ])('%s', async (status) => {
      await dates.update({ id: dateId }, { status });
      await expect(sweep(due())).resolves.toBe(0);
      expect((await dateRow()).status).toBe(status);
      expect(named('date_ended')).toHaveLength(0);
      expect(await matchStatus()).toBe('active');
    });
  });

  describe('after the switch', () => {
    beforeEach(async () => {
      await sweep(due());
      events = [];
    });

    it('a Date 06 end answers 409 date_not_active and stores nothing', async () => {
      const res = await end('you').expect(409);
      expect(res.body).toMatchObject({ code: 'date_not_active' });
      const row = await dateRow();
      expect(row.status).toBe(DateStatus.TimedOut);
      expect(row.endedById).toBeNull();
      expect(row.endReason).toBe('timed_out');
      expect(named('date_ended')).toHaveLength(0);
      expect(pushes).toEqual([]);
    });

    it('a Date 07 cancel answers 409 date_not_active and stores nothing', async () => {
      const res = await cancel('you').expect(409);
      expect(res.body).toMatchObject({ code: 'date_not_active' });
      const row = await dateRow();
      expect(row.status).toBe(DateStatus.TimedOut);
      expect(row.cancelledById).toBeNull();
      expect(named('date_cancelled')).toHaveLength(0);
    });

    it('an arrival check-in and an undo answer 409 date_not_active', async () => {
      const made = await arrive('you').expect(409);
      expect(made.body).toMatchObject({ code: 'date_not_active' });
      const undone = await request(server)
        .delete(`/dates/${dateId}/arrival`)
        .set('Authorization', as('you'))
        .expect(409);
      expect(undone.body).toMatchObject({ code: 'date_not_active' });
    });
  });

  it('a check-in made before the switch but committed after gets 409, keeps its row, and tells no one', async () => {
    await arrive('you').expect(201);
    await expect(sweep(due())).resolves.toBe(1);

    const res = await commit('you').expect(409);
    expect(res.body).toMatchObject({ code: 'date_not_active' });
    const rows = await db.query<Array<{ committed_at: Date | null }>>(
      `SELECT committed_at FROM date_arrival_check_ins WHERE date_id = $1`,
      [dateId],
    );
    expect(rows).toHaveLength(1);
    expect(rows[0].committed_at).toBeNull();
    expect(pushes.filter((p) => p.type === 'date_partner_arrived')).toEqual([]);
  });

  it('a check-in committed while the date is confirmed still tells her, and is counted by the switch', async () => {
    await arrive('you').expect(201);
    await commit('you').expect(204);
    expect(pushes.map((p) => p.type)).toContain('date_partner_arrived');

    await expect(sweep(due())).resolves.toBe(1);
    expect(named('date_ended')[0].properties?.checked_in_count).toBe(1);
  });
});
