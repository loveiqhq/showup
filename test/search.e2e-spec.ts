import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { DataSource } from 'typeorm';

import { AppModule } from './../src/app.module';
import { MatchingService } from './../src/modules/matching/matching.service';
import { meetDay } from './../src/modules/search/util/search';

/**
 * Date search, story 01 (SHOWUP-184), through the real app against a real database: the queue and
 * its rules, the payload's privacy, Not now / Not interested, the like with its message, the match
 * that closes every other open like, the no-overlap closing, the open-likes count, the coach flag
 * and the dev plan switch.
 *
 * The viewer goes through the API, Check-In commit included, so the district and the location are
 * written the way the app writes them. Everybody else is written straight to the database: their
 * profiles are not what is under test, and the file has 60 requests a minute.
 */
const MAX_PHONE = '+491701234184';
const LEONIE_PHONE = '+491701234185';
const BERLIN = { lat: 52.4862, lng: 13.4247 };
const TZ = 'Europe/Berlin';

describe('Date search 01 (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let db: DataSource;
  let maxToken: string;
  let maxId: string;
  let leonieToken: string;
  let leonieId: string;
  const ids: Record<string, string> = {};

  /** A whole hour, `hoursAhead` after the start of the current UTC hour. */
  const hour = (hoursAhead: number) => {
    const d = new Date();
    d.setUTCMinutes(0, 0, 0);
    return new Date(d.getTime() + hoursAhead * 3_600_000);
  };
  const as = (token: string) => `Bearer ${token}`;
  const queue = async (token = maxToken) =>
    (
      await request(server)
        .get('/search/queue')
        .query({ tz: TZ })
        .set('Authorization', as(token))
        .expect(200)
    ).body as {
      resultsCount: number;
      profiles: Array<Record<string, unknown>>;
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

  /** A person with a complete profile and a committed Check-In, written straight to the database. */
  async function person(opts: {
    userId?: string;
    phone: string;
    name: string;
    north: number;
    hours: [number, number];
    complete?: boolean;
    hidden?: string[];
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
          "is_visible", "hidden_fields", "religion", "height_cm", "interests")
       VALUES ($1, $2, '1998-03-01', 'woman', 'straight', $3, true, $4, 'spiritual_other', 172,
               ARRAY['hiking', 'reading']::profiles_interest_enum[])
       ON CONFLICT ("user_id") DO UPDATE SET
         "display_name" = EXCLUDED."display_name", "date_of_birth" = EXCLUDED."date_of_birth",
         "gender" = EXCLUDED."gender", "orientation" = EXCLUDED."orientation",
         "is_complete" = EXCLUDED."is_complete", "hidden_fields" = EXCLUDED."hidden_fields",
         "religion" = EXCLUDED."religion", "height_cm" = EXCLUDED."height_cm",
         "interests" = EXCLUDED."interests"`,
      [userId, opts.name, opts.complete ?? true, opts.hidden ?? []],
    );
    for (let i = 0; i < 4; i++) {
      await db.query(
        `INSERT INTO "profile_photos" ("user_id", "storage_key", "content_type", "position")
         VALUES ($1, $2, 'image/jpeg', $3)`,
        [userId, `${userId}/photo-${i}.jpg`, i],
      );
    }
    await db.query(
      `INSERT INTO "profile_prompts" ("user_id", "topic_id", "answer", "position")
       VALUES ($1, 'first_date', 'Talk about anything real.', 0)`,
      [userId],
    );
    const [window] = await db.query(
      `INSERT INTO "check_in_windows"
         ("user_id", "opened_at", "committed_at", "closes_at", "motivation", "vibes", "uplift",
          "district")
       VALUES ($1, now() - interval '1 hour', now() - interval '1 hour',
               now() + interval '47 hours', 'long_term',
               ARRAY['coffee_chat', 'sunset']::check_in_vibe_enum[],
               'A picnic on the Tempelhofer Feld', 'Kreuzberg')
       RETURNING "id"`,
      [userId],
    );
    await db.query(
      `INSERT INTO "check_ins"
         ("user_id", "status", "availability_start", "availability_end", "preparation_minutes",
          "check_in_window_id", "tier", "location")
       VALUES ($1, 'available', $2, $3, 30, $4, 'preferred',
               ST_SetSRID(ST_MakePoint($5, $6), 4326)::geography)`,
      [
        userId,
        hour(opts.hours[0]),
        hour(opts.hours[1]),
        window.id,
        BERLIN.lng,
        BERLIN.lat + opts.north,
      ],
    );
    return userId;
  }

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
    db = app.get(DataSource);

    // The viewer: a man looking for women, through the API, Check-In committed with a place.
    ({ token: maxToken, id: maxId } = await signUp(MAX_PHONE));
    await request(server)
      .patch('/me/profile')
      .set('Authorization', as(maxToken))
      .send({
        gender: 'man',
        orientation: 'straight',
        flowPosition: 'life_ahead',
      })
      .expect(200);
    await db.query(
      `UPDATE "profiles" SET "display_name" = 'Max', "date_of_birth" = '1996-05-10',
         "is_complete" = true WHERE "user_id" = $1`,
      [maxId],
    );
    const state = await request(server)
      .get('/me/check-in')
      .set('Authorization', as(maxToken))
      .expect(200);
    await request(server)
      .post('/me/check-in/commit')
      .set('Authorization', as(maxToken))
      .send({
        windowId: state.body.windowId,
        slots: [3, 4, 5, 26, 27].map((h) => ({
          startsAt: hour(h).toISOString(),
          tier: 'preferred',
        })),
        motivation: 'long_term',
        vibes: ['coffee_chat', 'sunset', 'no_rush'],
        latitude: BERLIN.lat,
        longitude: BERLIN.lng,
        district: '  Neukölln  ',
      })
      .expect(200);

    // Leonie signs up too, so she can like Max. 1.2 km away, free in Max's 4th and 5th hour.
    ({ token: leonieToken, id: leonieId } = await signUp(LEONIE_PHONE));
    ids.leonie = await person({
      userId: leonieId,
      phone: LEONIE_PHONE,
      name: 'Leonie',
      north: 0.011,
      hours: [4, 6],
      hidden: ['age', 'religion'],
    });
    // Carla: 5 km, free only tomorrow.
    ids.carla = await person({
      phone: '+491700000001',
      name: 'Carla',
      north: 0.045,
      hours: [27, 28],
    });
    // Never in Max's queue: too far, wants women only, blocked, an incomplete profile.
    ids.dana = await person({
      phone: '+491700000002',
      name: 'Dana',
      north: 0.54,
      hours: [4, 6],
    });
    ids.frida = await person({
      phone: '+491700000003',
      name: 'Frida',
      north: 0.018,
      hours: [4, 6],
    });
    await db.query(
      `INSERT INTO "filter_settings" ("user_id", "meet_gender", "min_age", "distance_km")
       VALUES ($1, ARRAY['woman']::profiles_gender_enum[], 18, 10)`,
      [ids.frida],
    );
    ids.gina = await person({
      phone: '+491700000004',
      name: 'Gina',
      north: 0.018,
      hours: [4, 6],
    });
    await db.query(
      `INSERT INTO "blocks" ("blocker_id", "blocked_id") VALUES ($1, $2)`,
      [maxId, ids.gina],
    );
    ids.hanna = await person({
      phone: '+491700000005',
      name: 'Hanna',
      north: 0.009,
      hours: [4, 6],
      complete: false,
    });
  });

  afterAll(async () => {
    await app.close();
  });

  // ── the Check-In names the place ────────────────────────────────────────────

  it('stores the district the phone named at Find my date, trimmed', async () => {
    const [row] = await db.query(
      `SELECT "district" FROM "check_in_windows" WHERE "user_id" = $1 AND "committed_at" IS NOT NULL`,
      [maxId],
    );
    expect(row.district).toBe('Neukölln');
  });

  // ── the queue ───────────────────────────────────────────────────────────────

  it('holds who Max can meet, earliest meet hour first, and nobody the rules keep out', async () => {
    const q = await queue();
    expect(q.resultsCount).toBe(2);
    expect(q.profiles.map((p) => p.name)).toEqual(['Leonie', 'Carla']);
  });

  it('sends the earliest shared hour and its day, in the zone the viewer sent', async () => {
    const [leonie, carla] = (await queue()).profiles;
    expect(leonie.meetAt).toBe(hour(4).toISOString());
    expect(leonie.meetDay).toBe(meetDay(hour(4), new Date(), TZ));
    expect(carla.meetAt).toBe(hour(27).toISOString());
    expect(carla.meetDay).toBe('tomorrow');
  });

  it('never puts a hidden value, an exact distance or a coordinate on the wire', async () => {
    const [leonie] = (await queue()).profiles;
    const wire = JSON.stringify(leonie);
    expect(Object.keys(leonie)).not.toContain('age');
    expect(leonie.essentials).not.toHaveProperty('religion');
    expect(leonie.essentials).toMatchObject({ heightCm: 172, gender: 'woman' });
    expect(leonie.distanceKm).toBe(1);
    expect(wire).not.toMatch(/latitude|longitude|"lat"|"lng"|meters/i);
    expect(wire).not.toContain(String(BERLIN.lat + 0.011));
    expect(leonie).toMatchObject({
      district: 'Kreuzberg',
      motivation: 'long_term',
      vibes: ['coffee_chat', 'sunset'],
      uplift: 'A picnic on the Tempelhofer Feld',
      attendedCount: 0,
      interests: ['hiking', 'reading'],
      prompts: [{ topicId: 'first_date', answer: 'Talk about anything real.' }],
    });
    expect(leonie.photos).toHaveLength(4);
    expect((leonie.photos as Array<{ url: string }>)[0].url).toMatch(
      /^\/uploads\//,
    );
  });

  it('counts only dates both people confirmed', async () => {
    await db.query(
      `INSERT INTO "dates" ("user_a_id", "user_b_id", "scheduled_at", "status",
         "a_confirmed_happened", "b_confirmed_happened")
       VALUES ($1, $2, now() - interval '5 days', 'completed', true, true),
              ($1, $2, now() - interval '9 days', 'completed', true, false)`,
      [ids.carla, ids.dana],
    );
    const carla = (await queue()).profiles.find((p) => p.name === 'Carla');
    expect(carla?.attendedCount).toBe(1);
  });

  // ── Not now / Not interested ─────────────────────────────────────────────────

  it('hides a Not now profile for this Check-In, idempotently', async () => {
    for (let i = 0; i < 2; i++) {
      await request(server)
        .post('/search/passes')
        .set('Authorization', as(maxToken))
        .send({ targetUserId: ids.carla, passType: 'not_now' })
        .expect(204);
    }
    const q = await queue();
    expect(q.profiles.map((p) => p.name)).toEqual(['Leonie']);
    const rows = await db.query(
      `SELECT "pass_type", "window_id" FROM "search_passes" WHERE "viewer_id" = $1`,
      [maxId],
    );
    expect(rows).toHaveLength(1);
    expect(rows[0].window_id).not.toBeNull();
  });

  it('refuses a pass on yourself or on nobody', async () => {
    await request(server)
      .post('/search/passes')
      .set('Authorization', as(maxToken))
      .send({ targetUserId: maxId, passType: 'not_interested' })
      .expect(400);
    await request(server)
      .post('/search/passes')
      .set('Authorization', as(maxToken))
      .send({
        targetUserId: '00000000-0000-4000-8000-000000000000',
        passType: 'not_interested',
      })
      .expect(404);
  });

  it('hides someone the viewer liked, and shows them again once that like has closed', async () => {
    await db.query(
      `INSERT INTO "likes" ("sender_id", "receiver_id", "status") VALUES ($1, $2, 'active')`,
      [maxId, ids.leonie],
    );
    expect((await queue()).profiles.map((p) => p.name)).not.toContain('Leonie');
    await db.query(
      `UPDATE "likes" SET "status" = 'withdrawn', "closed_reason" = 'no_overlap',
         "closed_at" = now() WHERE "sender_id" = $1 AND "receiver_id" = $2`,
      [maxId, ids.leonie],
    );
    expect((await queue()).profiles.map((p) => p.name)).toContain('Leonie');
    // Cleared, so the like tests below start from no like at all.
    await db.query(
      `DELETE FROM "likes" WHERE "sender_id" = $1 AND "receiver_id" = $2`,
      [maxId, ids.leonie],
    );
  });

  // ── likes, the match and the withdrawals ─────────────────────────────────────

  it('refuses a message over 100 characters, and stores a blank one as no message', async () => {
    await request(server)
      .post('/likes')
      .set('Authorization', as(maxToken))
      .send({ targetUserId: ids.leonie, message: 'x'.repeat(101) })
      .expect(400);
    await request(server)
      .post('/likes')
      .set('Authorization', as(leonieToken))
      .send({ targetUserId: maxId, message: '   ', position: 1 })
      .expect(201);
    const [like] = await db.query(
      `SELECT "message", "status" FROM "likes" WHERE "sender_id" = $1`,
      [leonieId],
    );
    expect(like).toEqual({ message: null, status: 'active' });
  });

  it('counts the open likes Max has received', async () => {
    const res = await request(server)
      .get('/me/likes/open-count')
      .set('Authorization', as(maxToken))
      .expect(200);
    expect(res.body).toEqual({ count: 1 });
  });

  it('matches a mutual like with an hour to meet, and closes every other open like of either person', async () => {
    // Max's like to Carla is open first; Dana has an open like to Leonie.
    await request(server)
      .post('/likes')
      .set('Authorization', as(maxToken))
      .send({ targetUserId: ids.carla, message: 'Hi Carla :)' })
      .expect(201);
    await db.query(
      `INSERT INTO "likes" ("sender_id", "receiver_id", "status") VALUES ($1, $2, 'active')`,
      [ids.dana, ids.leonie],
    );

    const res = await request(server)
      .post('/likes')
      .set('Authorization', as(maxToken))
      .send({ targetUserId: ids.leonie, position: 1 })
      .expect(201);
    expect(res.body.matched).toBe(true);
    const matchId = res.body.matchId as string;

    const closed = await db.query(
      `SELECT "sender_id", "receiver_id", "status", "closed_reason", "match_id"
         FROM "likes" WHERE "status" = 'withdrawn' ORDER BY "sender_id"`,
    );
    expect(closed).toHaveLength(2);
    for (const c of closed) {
      expect(c).toMatchObject({ closed_reason: 'matched', match_id: matchId });
    }
    expect(
      closed.map((c: { receiver_id: string }) => c.receiver_id).sort(),
    ).toEqual([ids.carla, ids.leonie].sort());
  });

  it('answers a retry with the same like, and sends nobody a second one', async () => {
    const res = await request(server)
      .post('/likes')
      .set('Authorization', as(maxToken))
      .send({ targetUserId: ids.leonie })
      .expect(201);
    expect(res.body.status).toBe('matched');
    const [{ n }] = await db.query(
      `SELECT count(*)::int AS n FROM "likes" WHERE "sender_id" = $1 AND "receiver_id" = $2`,
      [maxId, ids.leonie],
    );
    expect(n).toBe(1);
  });

  it('empties the queue and the open count once Max has his match', async () => {
    expect((await queue()).resultsCount).toBe(0);
    const res = await request(server)
      .get('/me/likes/open-count')
      .set('Authorization', as(maxToken))
      .expect(200);
    expect(res.body.count).toBe(0);
  });

  it('closes an open like whose two Check-Ins have no hour left, as no_overlap', async () => {
    // Frida and Gina share hours; Gina and Carla do not.
    await db.query(
      `INSERT INTO "likes" ("sender_id", "receiver_id", "status")
       VALUES ($1, $2, 'active'), ($3, $4, 'active')`,
      [ids.frida, ids.gina, ids.gina, ids.carla],
    );
    const closed = await app.get(MatchingService).withdrawNoOverlap();
    expect(closed).toBe(1);
    const rows = await db.query(
      `SELECT "sender_id", "status", "closed_reason", "match_id" FROM "likes"
        WHERE "sender_id" IN ($1, $2)`,
      [ids.frida, ids.gina],
    );
    const bySender = Object.fromEntries(
      rows.map((r: { sender_id: string }) => [r.sender_id, r]),
    );
    expect(bySender[ids.frida]).toMatchObject({ status: 'active' });
    expect(bySender[ids.gina]).toMatchObject({
      status: 'withdrawn',
      closed_reason: 'no_overlap',
      match_id: null,
    });
    // Running it again closes nothing twice.
    expect(await app.get(MatchingService).withdrawNoOverlap()).toBe(0);
  });

  // ── the session: the coach flag and the plan ────────────────────────────────

  it("sets the coach flag only through Don't show again, and returns it with the session", async () => {
    const before = await request(server)
      .get('/auth/me')
      .set('Authorization', as(leonieToken))
      .expect(200);
    expect(before.body).toMatchObject({
      searchCoachHidden: false,
      planTier: 'freemium',
    });
    await request(server)
      .post('/me/search-coach/hide')
      .set('Authorization', as(leonieToken))
      .expect(204);
    const after = await request(server)
      .get('/auth/me')
      .set('Authorization', as(leonieToken))
      .expect(200);
    expect(after.body.searchCoachHidden).toBe(true);
  });

  it('switches the plan from the dev menu outside production, and refuses an unknown plan', async () => {
    await request(server)
      .put('/dev/plan-tier')
      .set('Authorization', as(leonieToken))
      .send({ planTier: 'gold' })
      .expect(400);
    await request(server)
      .put('/dev/plan-tier')
      .set('Authorization', as(leonieToken))
      .send({ planTier: 'premium' })
      .expect(204);
    const me = await request(server)
      .get('/auth/me')
      .set('Authorization', as(leonieToken))
      .expect(200);
    expect(me.body.planTier).toBe('premium');
  });
});
