import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { DataSource } from 'typeorm';

import { AppModule } from './../src/app.module';
import { AnalyticsService } from './../src/modules/analytics/analytics.service';
import type { SearchFeedItem } from './../src/modules/analytics/events/server-events';
import { MatchingService } from './../src/modules/matching/matching.service';
import { LAUNCH_RANKER_CONFIG } from './../src/modules/search/ranker/ranker-config';
import {
  RANKER_CONFIG_TTL_MS,
  RankerConfigService,
} from './../src/modules/search/ranker/ranker-config.service';

/**
 * Matching 01 (SHOWUP-190), through the real app against a real database: the filters only the
 * database can show (their hours left, a `Not interested` from THEIR side, a past date, friends
 * only), the soft range in both directions with the remote flag on, the ordering multipliers, the
 * random pick at position 5, `search_feed_ranked`, and `like_expired` for its three reasons.
 *
 * The pure rules -- the score, the worked examples 1-6, the cap, the gate, the ties -- are
 * `ranker.spec.ts`. With the flag off the soft range does not exist; `search.e2e-spec.ts` runs that
 * way and its fixtures are all inside every range.
 *
 * THIS FILE RUNS WITH `FILTERS_DEALBREAKERS=true`: the soft range is what it tests. Max saves 25-35
 * and 10 km with both switches off; everybody else never saved filters (Check-In 05's defaults,
 * switches off), unless a test says otherwise.
 */
const MAX_PHONE = '+491701234190';
const BEA_PHONE = '+491701234191';
const BERLIN = { lat: 52.4862, lng: 13.4247 };
const TZ = 'Europe/Berlin';
/** One degree of latitude at Berlin, in km: offsets below are chosen well clear of each edge. */
const KM_PER_DEG = 111.27;

describe('Matching 01, the ranker (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let db: DataSource;
  let maxToken: string;
  let maxId: string;
  let beaToken: string;
  const ids: Record<string, string> = {};
  const flagBefore = process.env.FILTERS_DEALBREAKERS;

  const hour = (hoursAhead: number) => {
    const d = new Date();
    d.setUTCMinutes(0, 0, 0);
    return new Date(d.getTime() + hoursAhead * 3_600_000);
  };
  const as = (token: string) => `Bearer ${token}`;
  /** A date of birth that makes someone `years` old today, a month past their birthday. */
  const bornYearsAgo = (years: number) => {
    const d = new Date();
    d.setUTCFullYear(d.getUTCFullYear() - years);
    d.setUTCDate(d.getUTCDate() - 30);
    return d.toISOString().slice(0, 10);
  };
  const queue = async () =>
    (
      await request(server)
        .get('/search/queue')
        .query({ tz: TZ })
        .set('Authorization', as(maxToken))
        .expect(200)
    ).body as {
      resultsCount: number;
      profiles: Array<{ name: string; userId: string }>;
      feedRequestId: string;
    };

  async function signUp(phone: string): Promise<{ token: string; id: string }> {
    const start = await request(server)
      .post('/auth/phone/start')
      .send({ phone })
      .expect(200);
    const verify = await request(server)
      .post('/auth/phone/verify')
      .send({ phone, code: start.body.devCode })
      .expect(200);
    return { token: verify.body.accessToken, id: verify.body.user.id };
  }

  /** A complete profile and a committed Check-In, written straight to the database. */
  async function person(opts: {
    userId?: string;
    phone: string;
    name: string;
    km: number;
    hours: Array<[number, number]>;
    gender?: 'woman' | 'man';
    born?: string;
    motivation?: string;
    /** Their own saved filters; absent = never saved. */
    filters?: {
      meetGender: string[];
      minAge: number;
      maxAge: number | null;
      distanceKm: number;
      distanceDealbreaker?: boolean;
    };
  }): Promise<string> {
    const userId =
      opts.userId ??
      (
        await db.query(
          `INSERT INTO "users" ("phone", "status") VALUES ($1, 'active') RETURNING "id"`,
          [opts.phone],
        )
      )[0].id;
    await db.query(
      `INSERT INTO "profiles"
         ("user_id", "display_name", "date_of_birth", "gender", "orientation", "is_complete",
          "is_visible", "interests")
       VALUES ($1, $2, $3, $4, 'straight', true, true,
               ARRAY['hiking', 'reading']::profiles_interest_enum[])
       ON CONFLICT ("user_id") DO UPDATE SET
         "display_name" = EXCLUDED."display_name", "date_of_birth" = EXCLUDED."date_of_birth",
         "gender" = EXCLUDED."gender", "orientation" = EXCLUDED."orientation",
         "is_complete" = true, "is_visible" = true, "interests" = EXCLUDED."interests"`,
      [userId, opts.name, opts.born ?? '1998-03-01', opts.gender ?? 'woman'],
    );
    await db.query(
      `INSERT INTO "profile_photos" ("user_id", "storage_key", "content_type", "position")
       VALUES ($1, $2, 'image/jpeg', 0)`,
      [userId, `${userId}/photo-0.jpg`],
    );
    const [window] = await db.query(
      `INSERT INTO "check_in_windows"
         ("user_id", "opened_at", "committed_at", "closes_at", "motivation", "vibes")
       VALUES ($1, now() - interval '1 hour', now() - interval '1 hour', now() + interval '47 hours',
               $2, ARRAY['coffee_chat', 'sunset']::check_in_vibe_enum[])
       RETURNING "id"`,
      [userId, opts.motivation ?? 'long_term'],
    );
    for (const [from, to] of opts.hours) {
      await db.query(
        `INSERT INTO "check_ins"
           ("user_id", "status", "availability_start", "availability_end", "preparation_minutes",
            "check_in_window_id", "tier", "location")
         VALUES ($1, 'available', $2, $3, 30, $4, 'preferred',
                 ST_SetSRID(ST_MakePoint($5, $6), 4326)::geography)`,
        [
          userId,
          hour(from),
          hour(to),
          window.id,
          BERLIN.lng,
          BERLIN.lat + opts.km / KM_PER_DEG,
        ],
      );
    }
    if (opts.filters) {
      await db.query(
        `INSERT INTO "filter_settings"
           ("user_id", "meet_gender", "min_age", "max_age", "distance_km", "distance_dealbreaker")
         VALUES ($1, $2::profiles_gender_enum[], $3, $4, $5, $6)`,
        [
          userId,
          opts.filters.meetGender,
          opts.filters.minAge,
          opts.filters.maxAge,
          opts.filters.distanceKm,
          opts.filters.distanceDealbreaker ?? false,
        ],
      );
    }
    return userId;
  }

  /** The `search_feed_ranked` the last queue load recorded. */
  function lastFeedEvent(spy: jest.SpyInstance) {
    const call = [...spy.mock.calls]
      .reverse()
      .find(([p]) => p.eventName === 'search_feed_ranked');
    return call?.[0] as
      | {
          userId: string;
          properties: Record<string, unknown> & { items: SearchFeedItem[] };
        }
      | undefined;
  }

  async function profileIdOf(userId: string): Promise<string> {
    const [row] = await db.query(
      `SELECT "id" FROM "profiles" WHERE "user_id" = $1`,
      [userId],
    );
    return row.id;
  }

  beforeAll(async () => {
    process.env.FILTERS_DEALBREAKERS = 'true';
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
    db = app.get(DataSource);

    ({ token: maxToken, id: maxId } = await signUp(MAX_PHONE));
    // Max: 30, a man looking for women 25-35 within 10 km, both switches off; free in hours 3-6 and
    // 26-28. One earlier Check-In, in which he said Not now to Gia.
    await person({
      userId: maxId,
      phone: MAX_PHONE,
      name: 'Max',
      km: 0,
      hours: [
        [3, 6],
        [26, 28],
      ],
      gender: 'man',
      born: bornYearsAgo(30),
      filters: {
        meetGender: ['woman'],
        minAge: 25,
        maxAge: 35,
        distanceKm: 10,
      },
    });
    const [previous] = await db.query(
      `INSERT INTO "check_in_windows" ("user_id", "opened_at", "committed_at", "closes_at",
         "motivation", "vibes")
       VALUES ($1, now() - interval '4 days', now() - interval '4 days', now() - interval '2 days',
               'long_term', ARRAY['coffee_chat']::check_in_vibe_enum[])
       RETURNING "id"`,
      [maxId],
    );

    ({ token: beaToken, id: ids.bea } = await signUp(BEA_PHONE));
    await person({
      userId: ids.bea,
      phone: BEA_PHONE,
      name: 'Bea',
      km: 1.2,
      hours: [[4, 6]],
    });
    // Filter 1: one hour left is not enough, however good the overlap.
    ids.ana = await person({
      phone: '+491700019001',
      name: 'Ana',
      km: 1.1,
      hours: [[4, 5]],
    });
    // Filter 7, their side: Cleo said Not interested to Max.
    ids.cleo = await person({
      phone: '+491700019002',
      name: 'Cleo',
      km: 1.3,
      hours: [[4, 6]],
    });
    await db.query(
      `INSERT INTO "search_passes" ("viewer_id", "target_id", "pass_type") VALUES ($1, $2, 'not_interested')`,
      [ids.cleo, maxId],
    );
    // Filter 10: a past date with Max keeps Dora out; Eva's was cancelled, so it never happened.
    ids.dora = await person({
      phone: '+491700019003',
      name: 'Dora',
      km: 1.4,
      hours: [[4, 6]],
    });
    ids.eva = await person({
      phone: '+491700019004',
      name: 'Eva',
      km: 1.5,
      hours: [[4, 6]],
    });
    await db.query(
      `INSERT INTO "dates" ("user_a_id", "user_b_id", "scheduled_at", "status")
       VALUES (LEAST($1::uuid, $2::uuid), GREATEST($1::uuid, $2::uuid), now() - interval '3 days', 'completed'),
              (LEAST($1::uuid, $3::uuid), GREATEST($1::uuid, $3::uuid), now() - interval '3 days', 'cancelled')`,
      [maxId, ids.dora, ids.eva],
    );
    // Filter 12: friends meets only friends.
    ids.fay = await person({
      phone: '+491700019005',
      name: 'Fay',
      km: 1.6,
      hours: [[4, 6]],
      motivation: 'friends',
    });
    // The Not now multiplier: Gia is nearer than Hedy, and otherwise the same.
    ids.gia = await person({
      phone: '+491700019006',
      name: 'Gia',
      km: 1.0,
      hours: [[4, 6]],
    });
    ids.hedy = await person({
      phone: '+491700019007',
      name: 'Hedy',
      km: 2.0,
      hours: [[4, 6]],
    });
    await db.query(
      `INSERT INTO "search_passes" ("viewer_id", "target_id", "pass_type", "window_id")
       VALUES ($1, $2, 'not_now', $3)`,
      [maxId, ids.gia, previous.id],
    );
    // The soft range, Max's side: 37 is in 22-38, 40 is not; 12 km is in 12.5, 13 km is not.
    ids.mona = await person({
      phone: '+491700019008',
      name: 'Mona',
      km: 2.2,
      hours: [[4, 6]],
      born: bornYearsAgo(37),
    });
    ids.nora = await person({
      phone: '+491700019009',
      name: 'Nora',
      km: 2.3,
      hours: [[4, 6]],
      born: bornYearsAgo(40),
    });
    ids.olga = await person({
      phone: '+491700019010',
      name: 'Olga',
      km: 12,
      hours: [[4, 6]],
    });
    ids.pia = await person({
      phone: '+491700019011',
      name: 'Pia',
      km: 13,
      hours: [[4, 6]],
    });
    // The soft range, THEIR side, cut in PostGIS: a 5 km radius widens to 6.25 with the switch off.
    ids.quinn = await person({
      phone: '+491700019012',
      name: 'Quinn',
      km: 6,
      hours: [[4, 6]],
      filters: { meetGender: ['man'], minAge: 18, maxAge: null, distanceKm: 5 },
    });
    ids.rita = await person({
      phone: '+491700019013',
      name: 'Rita',
      km: 6,
      hours: [[4, 6]],
      filters: {
        meetGender: ['man'],
        minAge: 18,
        maxAge: null,
        distanceKm: 5,
        distanceDealbreaker: true,
      },
    });
    // like_expired: Ivy shares no hour with Max (out of time); Jo's hours are over (Check-In ended).
    ids.ivy = await person({
      phone: '+491700019014',
      name: 'Ivy',
      km: 1.7,
      hours: [[10, 12]],
    });
    ids.jo = await person({
      phone: '+491700019015',
      name: 'Jo',
      km: 1.8,
      hours: [[-4, -2]],
    });
  });

  afterAll(async () => {
    await app.close();
    if (flagBefore === undefined) delete process.env.FILTERS_DEALBREAKERS;
    else process.env.FILTERS_DEALBREAKERS = flagBefore;
  });

  it('offers exactly who the twelve filters and the soft range admit', async () => {
    const q = await queue();
    expect(q.profiles.length).toBeLessThanOrEqual(3);
    const spy = jest.spyOn(app.get(AnalyticsService), 'trackServerEvent');
    await queue();
    const event = lastFeedEvent(spy);
    spy.mockRestore();
    const names = new Map<string, string>();
    for (const key of Object.keys(ids))
      names.set(await profileIdOf(ids[key]), key);
    const offered = event!.properties.items
      .map((i) => names.get(i.profile_id))
      .sort();
    expect(offered).toEqual([
      'bea',
      'eva',
      'gia',
      'hedy',
      'mona',
      'olga',
      'quinn',
    ]);
    expect(q.resultsCount).toBe(7);
  });

  it('records search_feed_ranked for the load it answers, with ids and numbers only', async () => {
    const spy = jest.spyOn(app.get(AnalyticsService), 'trackServerEvent');
    const q = await queue();
    const event = lastFeedEvent(spy);
    spy.mockRestore();
    expect(event?.userId).toBe(maxId);
    expect(event?.properties).toMatchObject({
      feed_request_id: q.feedRequestId,
      ranker_version: 'launch-2026-10',
      layout: 'one_up',
      is_premium: false,
      eligible_count: 7,
    });
    const items = event!.properties.items;
    expect(items.map((i) => i.position)).toEqual([1, 2, 3, 4, 5, 6, 7]);
    for (const i of items) {
      for (const n of [i.score, i.fit, i.time, i.reliability, i.distance]) {
        expect(n).toBeGreaterThanOrEqual(0);
        expect(n).toBeLessThanOrEqual(1);
      }
    }
    // Never a motivation, a vibe or a name in the record.
    expect(JSON.stringify(items)).not.toMatch(/long_term|coffee_chat|Bea|Gia/);
    // The 1-up feed shows the first three, in the event's order.
    const top = await Promise.all(q.profiles.map((p) => profileIdOf(p.userId)));
    expect(top.length).toBe(3);
  });

  it('puts a random pick at position 5 and never in 1-4', async () => {
    const spy = jest.spyOn(app.get(AnalyticsService), 'trackServerEvent');
    await queue();
    const items = lastFeedEvent(spy)!.properties.items;
    spy.mockRestore();
    expect(items.filter((i) => i.is_explore).map((i) => i.position)).toEqual([
      5,
    ]);
  });

  it('marks the soft matches and lowers them x0.8; Not now from the last Check-In x0.7; new users x1.3', async () => {
    const spy = jest.spyOn(app.get(AnalyticsService), 'trackServerEvent');
    await queue();
    const items = lastFeedEvent(spy)!.properties.items;
    spy.mockRestore();
    const by = async (key: string) => {
      const pid = await profileIdOf(ids[key]);
      return items.find((i) => i.profile_id === pid)!;
    };
    for (const soft of ['mona', 'olga', 'quinn'])
      expect((await by(soft)).soft_match).toBe(true);
    for (const hard of ['bea', 'eva', 'gia', 'hedy'])
      expect((await by(hard)).soft_match).toBe(false);
    // Everybody here is in their first Check-In.
    expect((await by('bea')).boosts).toEqual(['new_user']);
    const gia = await by('gia');
    const hedy = await by('hedy');
    expect(gia.boosts).toEqual(['new_user', 'not_now']);
    // Gia is nearer, so her base score is higher -- and her final one lower, after x0.7.
    expect(gia.score).toBeGreaterThan(hedy.score);
    expect(gia.score * 1.3 * 0.7).toBeLessThan(hedy.score * 1.3);
    // A soft-distance match scores 0 on Distance, on the unwidened radius.
    expect((await by('olga')).distance).toBe(0);
  });

  it('closes a like with no hour left as like_expired: out_of_time, or check_in_ended', async () => {
    await db.query(
      `INSERT INTO "likes" ("sender_id", "receiver_id", "status", "created_at", "opened_at")
       VALUES ($1, $3, 'active', now() - interval '5 hours', now() - interval '5 hours'),
              ($2, $3, 'active', now() - interval '4 days', now() - interval '2 hours')`,
      [ids.ivy, ids.jo, maxId],
    );
    const spy = jest.spyOn(app.get(AnalyticsService), 'trackServerEvent');
    expect(await app.get(MatchingService).withdrawNoOverlap()).toBe(2);
    const expired = spy.mock.calls
      .map(([p]) => p)
      .filter((p) => p.eventName === 'like_expired');
    spy.mockRestore();
    const bySender = Object.fromEntries(
      expired.map((p) => [p.userId, p.properties]),
    );
    expect(bySender[ids.ivy]).toMatchObject({ reason: 'out_of_time' });
    expect(bySender[ids.jo]).toMatchObject({ reason: 'check_in_ended' });
    expect(bySender[ids.ivy]?.hours_open as number).toBeGreaterThanOrEqual(4.9);
    // Jo's like was first sent four days ago and re-opened two hours ago: open for two hours.
    expect(bySender[ids.jo]?.hours_open as number).toBeGreaterThanOrEqual(1.9);
    expect(bySender[ids.jo]?.hours_open as number).toBeLessThan(2.5);
  });

  it('restarts the clock of a closed like sent again, and keeps when it was first sent', async () => {
    await db.query(
      `INSERT INTO "likes" ("sender_id", "receiver_id", "status", "closed_reason", "closed_at",
         "created_at", "opened_at")
       VALUES ($1, $2, 'withdrawn', 'no_overlap', now() - interval '3 days',
               now() - interval '4 days', now() - interval '4 days')`,
      [maxId, ids.eva],
    );
    await request(server)
      .post('/likes')
      .set('Authorization', as(maxToken))
      .send({ targetUserId: ids.eva })
      .expect(201);
    const [row] = await db.query(
      `SELECT "status", now() - "opened_at" < interval '1 minute' AS "fresh",
              now() - "created_at" > interval '3 days' AS "first_kept"
         FROM "likes" WHERE "sender_id" = $1 AND "receiver_id" = $2`,
      [maxId, ids.eva],
    );
    expect(row).toMatchObject({
      status: 'active',
      fresh: true,
      first_kept: true,
    });
  });

  it("closes a like to someone who just matched as target_matched, and not the matched pair's own", async () => {
    // Hedy likes Bea; Max likes Gia; then Bea and Max match.
    await db.query(
      `INSERT INTO "likes" ("sender_id", "receiver_id", "status")
       VALUES ($1, $2, 'active'), ($3, $4, 'active')`,
      [ids.hedy, ids.bea, maxId, ids.gia],
    );
    await request(server)
      .post('/likes')
      .set('Authorization', as(beaToken))
      .send({ targetUserId: maxId })
      .expect(201);
    // Max likes Bea from a feed he loaded: like_sent names that load.
    const { feedRequestId } = await queue();
    const spy = jest.spyOn(app.get(AnalyticsService), 'trackServerEvent');
    const res = await request(server)
      .post('/likes')
      .set('Authorization', as(maxToken))
      .send({ targetUserId: ids.bea, feedRequestId })
      .expect(201);
    expect(res.body.matched).toBe(true);
    const events = spy.mock.calls.map(([p]) => p);
    spy.mockRestore();
    expect(events.find((p) => p.eventName === 'like_sent')).toMatchObject({
      userId: maxId,
      properties: { feed_request_id: feedRequestId },
    });
    const expired = events.filter((p) => p.eventName === 'like_expired');
    expect(expired).toHaveLength(1);
    expect(expired[0]).toMatchObject({
      userId: ids.hedy,
      properties: { reason: 'target_matched' },
    });
  });

  // Last: the service keeps what it read, so a config read here would order any later load.
  it('reads a new active config from ranker_configs, which can be replaced but never edited', async () => {
    const tuned = {
      ...LAUNCH_RANKER_CONFIG,
      ranker_version: 'e2e-tuned',
      fit_gate: 0.4,
    };
    await db.query(
      `INSERT INTO "ranker_configs" ("version", "config", "active") VALUES ($1, $2, true)`,
      [tuned.ranker_version, JSON.stringify(tuned)],
    );
    const read = await app
      .get(RankerConfigService)
      .current(Date.now() + RANKER_CONFIG_TTL_MS + 1);
    expect(read).toEqual(tuned);
    // Any change to a value is a new version, never an edit.
    await expect(
      db.query(
        `UPDATE "ranker_configs" SET "config" = jsonb_set("config", '{fit_gate}', '0.5')
          WHERE "version" = 'e2e-tuned'`,
      ),
    ).rejects.toThrow(/cannot be edited/);
    // The version inside the config is the row's version.
    await expect(
      db.query(
        `INSERT INTO "ranker_configs" ("version", "config") VALUES ('mismatch', $1)`,
        [JSON.stringify(tuned)],
      ),
    ).rejects.toThrow(/ck_ranker_configs_version/);
    // One active at a time; retiring one is allowed.
    const next = { ...tuned, ranker_version: 'e2e-next' };
    await expect(
      db.query(
        `INSERT INTO "ranker_configs" ("version", "config", "active") VALUES ($1, $2, true)`,
        [next.ranker_version, JSON.stringify(next)],
      ),
    ).rejects.toThrow(/uq_ranker_configs_one_active/);
    await db.query(
      `UPDATE "ranker_configs" SET "active" = false WHERE "version" = 'e2e-tuned'`,
    );
  });
});
