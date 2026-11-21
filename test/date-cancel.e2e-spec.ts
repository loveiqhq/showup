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
import { DateEntity } from './../src/modules/dates/entities/date.entity';
import { DateStatus } from './../src/modules/dates/util/date-lifecycle';
import { NotificationDispatchService } from './../src/modules/notifications/services/notification-dispatch.service';
import { SafetyService } from './../src/modules/safety/safety.service';
import { User } from './../src/modules/users/entities/user.entity';

/**
 * Date 07 (SHOWUP-198): cancel this date, through the real app and database. Pure rules are
 * `date-cancel.spec.ts`. Max is the canceller, Leonie the match; Anna is a control who is still found.
 */
const PHONES = {
  max: '+491701980101',
  leonie: '+491701980102',
  anna: '+491701980103',
};
const MINUTE = 60_000;
const BERLIN = { lat: 52.4862, lng: 13.4247 };
const TZ = 'Europe/Berlin';

interface SentEvent {
  userId: string;
  eventName: string;
  properties?: Record<string, unknown>;
}

describe('Date 07 cancel (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let db: DataSource;
  let dates: Repository<DateEntity>;
  let config: ConfigService;
  let pushes: Array<{ userId: string; type: string; params: unknown }>;
  const token: Record<string, string> = {};
  const id: Record<string, string> = {};
  let dateId: string;
  let events: SentEvent[];

  const as = (who: string) => `Bearer ${token[who]}`;
  const named = (name: string) => events.filter((e) => e.eventName === name);
  const hour = (hoursAhead: number) => {
    const d = new Date();
    d.setUTCMinutes(0, 0, 0);
    return new Date(d.getTime() + hoursAhead * 3_600_000);
  };
  const signUp = async (who: keyof typeof PHONES) => {
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
  const cancel = (who: string, body: object = {}) =>
    request(server)
      .post(`/dates/${dateId}/cancel`)
      .set('Authorization', as(who))
      .send(body);
  const me = (who: string) =>
    request(server).get('/me/date').set('Authorization', as(who)).expect(200);
  const dateRow = () => dates.findOneByOrFail({ id: dateId });
  const checkIn = (who: string) =>
    db.query(
      `INSERT INTO date_arrival_check_ins
         (date_id, user_id, location, entry, client_id, committed_at)
       VALUES ($1, $2, ST_SetSRID(ST_MakePoint(13.42, 52.5), 4326)::geography,
               'date_screen', $3, now())`,
      [dateId, id[who], randomUUID()],
    );
  const pairBlocks = () =>
    db.query<Array<{ user_low: string; user_high: string; reason: string }>>(
      `SELECT * FROM date_pair_blocks WHERE date_id = $1`,
      [dateId],
    );
  const matchStatus = async () =>
    (
      await db.query<Array<{ status: string }>>(
        `SELECT status FROM matches WHERE user_a_id = ANY($1::uuid[])`,
        [[id.max, id.leonie]],
      )
    )[0].status;
  const clearPair = async () => {
    await db.query(
      `DELETE FROM date_pair_blocks WHERE user_low = ANY($1::uuid[])`,
      [[id.max, id.leonie]],
    );
    await dates
      .createQueryBuilder()
      .delete()
      .where('user_a_id IN (:...ids) OR user_b_id IN (:...ids)', {
        ids: [id.max, id.leonie],
      })
      .execute();
    await db.query(`DELETE FROM matches WHERE user_a_id = ANY($1::uuid[])`, [
      [id.max, id.leonie],
    ]);
  };
  /** A confirmed date whose agreed time is `minutes` from now, with an active match. */
  const newDate = async (minutes: number) => {
    await clearPair();
    const [a, b] = [id.max, id.leonie].sort();
    await db.query(
      `INSERT INTO matches (user_a_id, user_b_id, status) VALUES ($1, $2, 'active')`,
      [a, b],
    );
    dateId = (
      await dates.save(
        dates.create({
          userAId: id.max,
          userBId: id.leonie,
          scheduledAt: new Date(Date.now() + minutes * MINUTE),
          status: DateStatus.Confirmed,
        }),
      )
    ).id;
  };

  /** A person with a complete profile and a committed Check-In, written straight to the database. */
  async function person(opts: {
    userId: string;
    name: string;
    north: number;
    hours: [number, number];
    gender: 'woman' | 'man';
  }): Promise<void> {
    await db.query(
      `INSERT INTO "profiles"
         ("user_id", "display_name", "date_of_birth", "gender", "orientation", "is_complete",
          "is_visible", "hidden_fields")
       VALUES ($1, $2, '1998-03-01', $3, 'straight', true, true, '{}')
       ON CONFLICT ("user_id") DO UPDATE SET
         "display_name" = EXCLUDED."display_name", "date_of_birth" = EXCLUDED."date_of_birth",
         "gender" = EXCLUDED."gender", "orientation" = EXCLUDED."orientation",
         "is_complete" = true`,
      [opts.userId, opts.name, opts.gender],
    );
    await db.query(
      `INSERT INTO "profile_photos" ("user_id", "storage_key", "content_type", "position")
       VALUES ($1, $2, 'image/jpeg', 0)`,
      [opts.userId, `${opts.userId}/photo-0.jpg`],
    );
    const [window] = await db.query(
      `INSERT INTO "check_in_windows"
         ("user_id", "opened_at", "committed_at", "closes_at", "motivation", "vibes")
       VALUES ($1, now() - interval '1 hour', now() - interval '1 hour',
               now() + interval '47 hours', 'long_term',
               ARRAY['coffee_chat']::check_in_vibe_enum[])
       RETURNING "id"`,
      [opts.userId],
    );
    await db.query(
      `INSERT INTO "check_ins"
         ("user_id", "status", "availability_start", "availability_end", "preparation_minutes",
          "check_in_window_id", "tier", "location")
       VALUES ($1, 'available', $2, $3, 30, $4, 'preferred',
               ST_SetSRID(ST_MakePoint($5, $6), 4326)::geography)`,
      [
        opts.userId,
        hour(opts.hours[0]),
        hour(opts.hours[1]),
        window.id,
        BERLIN.lng,
        BERLIN.lat + opts.north,
      ],
    );
  }
  const likeRow = (from: string, to: string) =>
    db.query(
      `INSERT INTO "likes" ("sender_id", "receiver_id", "status", "message")
       VALUES ($1, $2, 'active', NULL)`,
      [from, to],
    );

  const queueNames = async (who: string) =>
    (
      (
        await request(server)
          .get('/search/queue')
          .query({ tz: TZ })
          .set('Authorization', as(who))
          .expect(200)
      ).body.profiles as Array<{ name: string }>
    ).map((p) => p.name);
  const gridNames = async (who: string) =>
    (
      (
        await request(server)
          .get('/search/grid')
          .query({ tz: TZ })
          .set('Authorization', as(who))
          .expect(200)
      ).body.cards as Array<{ profile: { name: string } }>
    ).map((c) => c.profile.name);
  const likeNames = async (who: string) =>
    (
      (
        await request(server)
          .get('/me/likes/received')
          .query({ tz: TZ })
          .set('Authorization', as(who))
          .expect(200)
      ).body.cards as Array<{ name: string }>
    ).map((c) => c.name);

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
    dates = app.get<Repository<DateEntity>>(getRepositoryToken(DateEntity), {
      strict: false,
    });
    await app
      .get<Repository<User>>(getRepositoryToken(User), { strict: false })
      .delete({ phone: In([...Object.values(PHONES), DEV_PARTNER_PHONE]) });
    for (const who of Object.keys(PHONES) as Array<keyof typeof PHONES>) {
      await signUp(who);
    }
    await person({
      userId: id.max,
      name: 'Max',
      north: 0,
      hours: [3, 6],
      gender: 'man',
    });
    await person({
      userId: id.leonie,
      name: 'Leonie',
      north: 0.011,
      hours: [4, 6],
      gender: 'woman',
    });
    await person({
      userId: id.anna,
      name: 'Anna',
      north: 0.016,
      hours: [3, 5],
      gender: 'woman',
    });
    for (const who of ['max', 'leonie']) {
      await request(server)
        .put('/dev/plan-tier')
        .set('Authorization', as(who))
        .send({ planTier: 'premium' })
        .expect(204);
    }
    jest
      .spyOn(app.get(AnalyticsService), 'trackServerEvent')
      .mockImplementation((p) => {
        events.push(p);
        return Promise.resolve();
      });
    jest
      .spyOn(app.get(NotificationDispatchService), 'sendPush')
      .mockImplementation((userId, type, params) => {
        pushes.push({ userId, type, params });
        return Promise.resolve({ status: 'sent', delivered: 1 });
      });
  });

  beforeEach(async () => {
    events = [];
    pushes = [];
    jest.spyOn(config, 'get').mockRestore();
    await newDate(180);
  });

  afterAll(async () => {
    await clearPair();
    await app
      .get<Repository<User>>(getRepositoryToken(User), { strict: false })
      .delete({ phone: In([...Object.values(PHONES), DEV_PARTNER_PHONE]) });
    await app.close();
  });

  it('stores everything, blocks the pair, closes the match, pushes the match and fires date_cancelled once', async () => {
    const before = Date.now();
    const res = await cancel('max', {
      reason: 'an old client still sends this',
    }).expect(200);
    expect(res.body).toEqual({
      dateId,
      name: 'Leonie',
      endedByYou: true,
      reason: 'cancelled',
    });
    const row = await dateRow();
    expect(row).toMatchObject({
      status: DateStatus.Cancelled,
      cancelledById: id.max,
      penaltyPoints: 10,
      enderCheckedIn: false,
      partnerCheckedIn: false,
      cancelChangeRequested: false,
      cancelReason: null,
    });
    expect(row.cancelledAt!.getTime()).toBeGreaterThanOrEqual(before - 1000);
    // 180 minutes ahead, read a moment later: 3 hours to two decimals.
    expect(row.hoursNotice).toBeGreaterThanOrEqual(2.99);
    expect(row.hoursNotice).toBeLessThanOrEqual(3);

    const [pair] = await pairBlocks();
    const [low, high] = [id.max, id.leonie].sort();
    expect(pair).toMatchObject({
      user_low: low,
      user_high: high,
      reason: 'red_link_cancel',
    });
    expect(await matchStatus()).toBe('cancelled');
    const history = await db.query<Array<{ to_status: string }>>(
      `SELECT to_status FROM date_status_changes WHERE date_id = $1`,
      [dateId],
    );
    expect(history.map((h) => h.to_status)).toContain('cancelled');

    const sent = named('date_cancelled');
    expect(sent).toHaveLength(1);
    expect(sent[0].userId).toBe(id.max);
    expect(sent[0].properties).toEqual({
      date_id: dateId,
      hours_notice: row.hoursNotice,
      penalty_points: 10,
      change_requested: false,
    });
    expect(pushes).toEqual([
      { userId: id.leonie, type: 'date_cancelled', params: { name: 'Max' } },
    ]);
  });

  it('stores both check-in states: the match had checked in, the canceller had not', async () => {
    await checkIn('leonie');
    await cancel('max').expect(200);
    expect(await dateRow()).toMatchObject({
      enderCheckedIn: false,
      partnerCheckedIn: true,
    });
  });

  it('refuses a checked-in canceller with 409 checked_in, and stores and fires nothing', async () => {
    await checkIn('max');
    const res = await cancel('max').expect(409);
    expect(res.body.code).toBe('checked_in');
    expect(await dateRow()).toMatchObject({
      status: DateStatus.Confirmed,
      penaltyPoints: null,
    });
    expect(await pairBlocks()).toHaveLength(0);
    expect(named('date_cancelled')).toHaveLength(0);
    expect(pushes).toHaveLength(0);
  });

  it('a second cancel is 409 date_not_active with no second event, push or change', async () => {
    await cancel('max').expect(200);
    const again = await cancel('max').expect(409);
    expect(again.body.code).toBe('date_not_active');
    const other = await cancel('leonie').expect(409);
    expect(other.body.code).toBe('date_not_active');
    expect(named('date_cancelled')).toHaveLength(1);
    expect(pushes).toHaveLength(1);
    expect((await dateRow()).cancelledById).toBe(id.max);
  });

  it('two simultaneous cancels: one 200 and one 409, one event', async () => {
    const [a, b] = await Promise.all([cancel('max'), cancel('leonie')]);
    expect([a.status, b.status].sort()).toEqual([200, 409]);
    expect(named('date_cancelled')).toHaveLength(1);
    expect(await pairBlocks()).toHaveLength(1);
  });

  it('reports the cancel to the match as H and to the canceller as endedByYou, until each has seen it', async () => {
    await cancel('max').expect(200);
    const match = (await me('leonie')).body;
    expect(match.date).toBeNull();
    expect(match.ended).toMatchObject({
      dateId,
      name: 'Max',
      endedByYou: false,
      reason: 'cancelled',
    });
    expect(match.ended.partnerScreen).toBeUndefined();
    expect((await me('max')).body.ended).toMatchObject({
      name: 'Leonie',
      endedByYou: true,
      reason: 'cancelled',
    });

    await request(server)
      .post(`/dates/${dateId}/ended/seen`)
      .set('Authorization', as('leonie'))
      .expect(204);
    expect((await me('leonie')).body.ended).toBeNull();
    expect((await me('max')).body.ended).not.toBeNull();
  });

  it('stores change_requested = true after a cancel_sheet suggestion, and false after another entry', async () => {
    await db.query(
      `INSERT INTO date_change_suggestions
         (date_id, sender_id, kind, value_time, was_time, entry, deadline, client_id)
       VALUES ($1, $2, 'time', now() + interval '4 hours', now() + interval '3 hours',
               'chat_chip', now() + interval '1 hour', $3)`,
      [dateId, id.max, randomUUID()],
    );
    await cancel('max').expect(200);
    expect((await dateRow()).cancelChangeRequested).toBe(false);

    await newDate(180);
    events = [];
    await db.query(
      `INSERT INTO date_change_suggestions
         (date_id, sender_id, kind, value_time, was_time, entry, deadline, client_id)
       VALUES ($1, $2, 'time', now() + interval '4 hours', now() + interval '3 hours',
               'cancel_sheet', now() + interval '1 hour', $3)`,
      [dateId, id.max, randomUUID()],
    );
    await cancel('max').expect(200);
    expect(await dateRow()).toMatchObject({
      cancelChangeRequested: true,
      penaltyPoints: 10,
    });
    expect(named('date_cancelled')[0].properties).toMatchObject({
      change_requested: true,
      penalty_points: 10,
    });
  });

  it('hours_notice is negative after the agreed time', async () => {
    await newDate(-90);
    await cancel('max').expect(200);
    const row = await dateRow();
    expect(row.hoursNotice).toBeLessThanOrEqual(-1.5);
    expect(row.hoursNotice).toBeGreaterThanOrEqual(-1.52);
  });

  it('refuses a stranger with 403 and an unknown date with 404', async () => {
    await cancel('anna').expect(403);
    await request(server)
      .post(`/dates/${randomUUID()}/cancel`)
      .set('Authorization', as('max'))
      .send({})
      .expect(404);
  });

  it('answers 404 and stores nothing when the DATE_ENDS_ENABLED switch is off', async () => {
    const real = config.get.bind(config);
    jest
      .spyOn(config, 'get')
      .mockImplementation((key: string, ...rest: unknown[]) =>
        key === 'DATE_ENDS_ENABLED'
          ? 'false'
          : (real as (k: string, ...r: unknown[]) => unknown)(key, ...rest),
      );
    await cancel('max').expect(404);
    expect((await dateRow()).status).toBe(DateStatus.Confirmed);
    expect(await pairBlocks()).toHaveLength(0);
  });

  describe("the pair never appears in each other's search", () => {
    it('is hidden from the queue, the Premium grid and Likes received both ways; the control stays', async () => {
      await likeRow(id.leonie, id.max);
      await likeRow(id.anna, id.max);
      await likeRow(id.max, id.leonie);
      // Control, before the cancel the date itself locks both people out of each other's results.
      await cancel('max').expect(200);

      for (const who of ['max', 'leonie'] as const) {
        const them = who === 'max' ? 'Leonie' : 'Max';
        expect(await queueNames(who)).not.toContain(them);
        expect(await gridNames(who)).not.toContain(them);
        expect(await likeNames(who)).not.toContain(them);
      }
      // Anna is still found by Max: it is the pair block, not a broken search.
      expect(await queueNames('max')).toContain('Anna');
      expect(await gridNames('max')).toContain('Anna');
      expect(await likeNames('max')).toContain('Anna');

      // And only that pair's record hides them: wipe the date, the match and the block (a test-only
      // reset) and they come back, so the search itself is sound.
      await clearPair();
      expect(await likeNames('max')).toContain('Leonie');
      expect(await likeNames('leonie')).toContain('Max');
      // Max liking Leonie keeps her out of his own queue and grid, so drop that like to read them.
      await db.query(`DELETE FROM likes WHERE sender_id = $1`, [id.max]);
      expect(await queueNames('max')).toContain('Leonie');
      expect(await gridNames('max')).toContain('Leonie');
      await db.query(`DELETE FROM likes WHERE sender_id = $1`, [id.leonie]);
      expect(await queueNames('leonie')).toContain('Max');
      expect(await gridNames('leonie')).toContain('Max');
    });

    it('matching reads the same union, in both directions, and a like between them is refused', async () => {
      await cancel('max').expect(200);
      const safety = app.get(SafetyService);
      expect(await safety.blockedUserIdsFor(id.max)).toContain(id.leonie);
      expect(await safety.blockedUserIdsFor(id.leonie)).toContain(id.max);
      expect(await safety.isBlockedEitherWay(id.leonie, id.max)).toBe(true);
      await request(server)
        .post('/likes')
        .set('Authorization', as('max'))
        .send({ targetUserId: id.leonie, position: 1 })
        .expect((r) => {
          if (r.status < 400) throw new Error(`like was accepted: ${r.status}`);
        });
    });
  });
});
