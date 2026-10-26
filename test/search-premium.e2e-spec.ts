import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { DataSource } from 'typeorm';

import { AppModule } from './../src/app.module';

/**
 * Date search, stories 02 and 03 (SHOWUP-185, SHOWUP-186), through the real app against a real
 * database: the Premium gate, the grid and its `likedYou`, the availability re-check, the Likes list
 * (order, message, closed cards, seen marking), `Not interested` declining a like, `Not now` leaving
 * it open, a confirm refused for each reason, a plain like to someone gone, two confirms racing for
 * one like, and a like closed by the viewer's own match never shown as Too late.
 *
 * Max is the viewer, through the API with a committed Check-In. Leonie and Ben sign up too, because
 * they send likes of their own; everybody else is written straight to the database. The file has 60
 * requests a minute.
 */
const MAX_PHONE = '+491701235185';
const LEONIE_PHONE = '+491701235186';
const BEN_PHONE = '+491701235187';
const BERLIN = { lat: 52.4862, lng: 13.4247 };
const TZ = 'Europe/Berlin';

describe('Date search 02 and 03 (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let db: DataSource;
  let maxToken: string;
  let maxId: string;
  let leonieToken: string;
  let benToken: string;
  const ids: Record<string, string> = {};

  /** A whole hour, `hoursAhead` after the start of the current UTC hour. */
  const hour = (hoursAhead: number) => {
    const d = new Date();
    d.setUTCMinutes(0, 0, 0);
    return new Date(d.getTime() + hoursAhead * 3_600_000);
  };
  const as = (token: string) => `Bearer ${token}`;

  type Card = {
    userId: string;
    name: string;
    age?: number;
    photo?: { url: string };
    closed?: string;
    message?: string;
    profile?: Record<string, unknown>;
  };
  const likesReceived = async () =>
    (
      await request(server)
        .get('/me/likes/received')
        .query({ tz: TZ })
        .set('Authorization', as(maxToken))
        .expect(200)
    ).body as {
      openCount: number;
      closedCount: number;
      cards: Card[];
      viewer: { vibes: string[]; interests: string[] };
    };
  const openCount = async () =>
    (
      await request(server)
        .get('/me/likes/open-count')
        .set('Authorization', as(maxToken))
        .expect(200)
    ).body.count as number;
  const like = (token: string, targetUserId: string) =>
    request(server)
      .post('/likes')
      .set('Authorization', as(token))
      .send({ targetUserId, position: 1 });

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
    gender?: 'woman' | 'man';
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
          "is_visible", "hidden_fields")
       VALUES ($1, $2, '1998-03-01', $3, 'straight', true, true, '{}')
       ON CONFLICT ("user_id") DO UPDATE SET
         "display_name" = EXCLUDED."display_name", "date_of_birth" = EXCLUDED."date_of_birth",
         "gender" = EXCLUDED."gender", "orientation" = EXCLUDED."orientation",
         "is_complete" = true`,
      [userId, opts.name, opts.gender ?? 'woman'],
    );
    await db.query(
      `INSERT INTO "profile_photos" ("user_id", "storage_key", "content_type", "position")
       VALUES ($1, $2, 'image/jpeg', 0)`,
      [userId, `${userId}/photo-0.jpg`],
    );
    const [window] = await db.query(
      `INSERT INTO "check_in_windows"
         ("user_id", "opened_at", "committed_at", "closes_at", "motivation", "vibes")
       VALUES ($1, now() - interval '1 hour', now() - interval '1 hour',
               now() + interval '47 hours', 'long_term',
               ARRAY['coffee_chat']::check_in_vibe_enum[])
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

  /** `from` liked `to`, open, with an optional message. */
  const likeRow = (from: string, to: string, message: string | null = null) =>
    db.query(
      `INSERT INTO "likes" ("sender_id", "receiver_id", "status", "message")
       VALUES ($1, $2, 'active', $3)`,
      [from, to, message],
    );

  /** An active match between two people, made now -- inside both of their Check-Ins. */
  const matchRow = async (a: string, b: string): Promise<string> => {
    const [x, y] = a < b ? [a, b] : [b, a];
    const [m] = await db.query(
      `INSERT INTO "matches" ("user_a_id", "user_b_id", "status") VALUES ($1, $2, 'active')
       RETURNING "id"`,
      [x, y],
    );
    return m.id;
  };

  const statusOf = (userId: string) =>
    db
      .query(
        `SELECT * FROM "likes" WHERE "sender_id" = $1 AND "receiver_id" = $2`,
        [userId, maxId],
      )
      .then((rows: Array<Record<string, unknown>>) => rows[0]);

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

    // The viewer: a man looking for women, Check-In committed with a place.
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
        vibes: ['coffee_chat'],
        latitude: BERLIN.lat,
        longitude: BERLIN.lng,
        district: 'Neukölln',
      })
      .expect(200);

    ({ token: leonieToken, id: ids.leonie } = await signUp(LEONIE_PHONE));
    await person({
      userId: ids.leonie,
      phone: LEONIE_PHONE,
      name: 'Leonie',
      north: 0.011,
      hours: [4, 6],
    });
    ({ token: benToken, id: ids.ben } = await signUp(BEN_PHONE));
    await person({
      userId: ids.ben,
      phone: BEN_PHONE,
      name: 'Ben',
      north: 0.02,
      hours: [3, 5],
      gender: 'man',
    });
    ids.anna = await person({
      phone: '+491700001001',
      name: 'Anna',
      north: 0.016,
      hours: [3, 5],
    });
    ids.sofia = await person({
      phone: '+491700001002',
      name: 'Sofia',
      north: 0.022,
      hours: [4, 6],
    });
    ids.clara = await person({
      phone: '+491700001003',
      name: 'Clara',
      north: 0.028,
      hours: [4, 6],
    });
    ids.dana = await person({
      phone: '+491700001004',
      name: 'Dana',
      north: 0.03,
      hours: [4, 6],
      gender: 'man',
    });
    ids.emma = await person({
      phone: '+491700001005',
      name: 'Emma',
      north: 0.012,
      hours: [10, 11],
    });
    ids.mia = await person({
      phone: '+491700001006',
      name: 'Mia',
      north: 0.014,
      hours: [26, 28],
    });
    ids.hanna = await person({
      phone: '+491700001007',
      name: 'Hanna',
      north: 0.013,
      hours: [26, 28],
    });
    ids.liv = await person({
      phone: '+491700001008',
      name: 'Liv',
      north: 0.015,
      hours: [26, 28],
    });
    // Zoe never liked Max and shares no hour with him.
    ids.zoe = await person({
      phone: '+491700001009',
      name: 'Zoe',
      north: 0.017,
      hours: [10, 11],
    });

    await likeRow(
      ids.leonie,
      maxId,
      "I love that you're into cooking too. Coffee at Bonanza?",
    );
    await likeRow(ids.anna, maxId);
    await likeRow(ids.emma, maxId); // never overlapped: Out of time on the first visit
    await likeRow(ids.mia, maxId);
    await likeRow(ids.hanna, maxId);
    await likeRow(ids.liv, maxId);
    // Clara matched Dana first: her like to Max closed then, Too late, not seen yet.
    const claraDana = await matchRow(ids.clara, ids.dana);
    await db.query(
      `INSERT INTO "likes" ("sender_id", "receiver_id", "status", "closed_reason", "closed_at",
         "match_id")
       VALUES ($1, $2, 'withdrawn', 'matched', now() - interval '10 minutes', $3)`,
      [ids.clara, maxId, claraDana],
    );
  });

  afterAll(async () => {
    await app.close();
  });

  // ── the Premium gate ────────────────────────────────────────────────────────

  it('refuses the grid, the re-check and the likes list to a Freemium account', async () => {
    await request(server)
      .get('/search/grid')
      .set('Authorization', as(maxToken))
      .expect(403);
    await request(server)
      .post('/search/availability')
      .set('Authorization', as(maxToken))
      .send({ userIds: [ids.anna] })
      .expect(403);
    await request(server)
      .get('/me/likes/received')
      .set('Authorization', as(maxToken))
      .expect(403);
    await request(server)
      .put('/dev/plan-tier')
      .set('Authorization', as(maxToken))
      .send({ planTier: 'premium' })
      .expect(204);
  });

  // ── the grid (Search 02) ────────────────────────────────────────────────────

  it('holds the whole queue in queue order, each card saying whether that person liked Max', async () => {
    const res = await request(server)
      .get('/search/grid')
      .query({ tz: TZ })
      .set('Authorization', as(maxToken))
      .expect(200);
    const cards = res.body.cards as Array<{
      likedYou: boolean;
      profile: { name: string; meetAt: string; photos: unknown[] };
    }>;
    // Clara has a date, Emma no shared hour, Ben and Dana are men: none of them is offered.
    expect(cards.map((c) => c.profile.name)).toEqual([
      'Anna',
      'Leonie',
      'Sofia',
      'Hanna',
      'Mia',
      'Liv',
    ]);
    expect(res.body.resultsCount).toBe(6);
    expect(
      Object.fromEntries(cards.map((c) => [c.profile.name, c.likedYou])),
    ).toEqual({
      Anna: true,
      Leonie: true,
      Sofia: false,
      Hanna: true,
      Mia: true,
      Liv: true,
    });
    expect(cards[0].profile.meetAt).toBe(hour(3).toISOString());
    expect(cards[0].profile.photos).toHaveLength(1);
  });

  // ── the availability re-check ───────────────────────────────────────────────

  it('re-reads every card in one call, in the order asked, and says nothing about an unknown id', async () => {
    const unknown = '00000000-0000-4000-8000-000000000000';
    const res = await request(server)
      .post('/search/availability')
      .query({ tz: TZ })
      .set('Authorization', as(maxToken))
      // Nobody here liked Max: a re-check of a liker closes their card, as the next test shows.
      .send({ userIds: [ids.sofia, ids.dana, ids.zoe, unknown] })
      .expect(200);
    expect(res.body.statuses).toEqual([
      {
        userId: ids.sofia,
        status: 'available',
        meetAt: hour(4).toISOString(),
        meetDay: expect.stringMatching(/^(today|tomorrow)$/),
      },
      { userId: ids.dana, status: 'matched' },
      { userId: ids.zoe, status: 'no_overlap' },
      { userId: unknown, status: 'no_overlap' },
    ]);
    await request(server)
      .post('/search/availability')
      .set('Authorization', as(maxToken))
      .send({ userIds: ['not-an-id'] })
      .expect(400);
  });

  // ── the Likes list (Search 03) ──────────────────────────────────────────────

  it('lists open likes earliest meet time first, then the closed ones, and counts what the badge counts', async () => {
    const list = await likesReceived();
    expect(list.cards.map((c) => [c.name, c.closed ?? 'open'])).toEqual([
      ['Anna', 'open'],
      ['Leonie', 'open'],
      ['Hanna', 'open'],
      ['Mia', 'open'],
      ['Liv', 'open'],
      // Emma's like had no time left: closed on this load, so it shows as Out of time now.
      ['Clara', 'matched'],
      ['Emma', 'no_overlap'],
    ]);
    expect(list.openCount).toBe(5);
    expect(list.closedCount).toBe(2);
    expect(await openCount()).toBe(5);

    const leonie = list.cards[1];
    expect(leonie.message).toBe(
      "I love that you're into cooking too. Coffee at Bonanza?",
    );
    expect(leonie.profile).toMatchObject({
      userId: ids.leonie,
      meetAt: hour(4).toISOString(),
      distanceKm: 1,
    });
    expect(leonie.photo?.url).toMatch(/^\/uploads\//);
    const clara = list.cards[5];
    expect(clara).toEqual({
      userId: ids.clara,
      name: 'Clara',
      age: expect.any(Number),
      photo: { url: expect.stringMatching(/^\/uploads\//) },
      closed: 'matched',
    });
    expect(await statusOf(ids.emma)).toMatchObject({
      status: 'withdrawn',
      closed_reason: 'no_overlap',
    });
  });

  it('leaves out a closed card on the visit after the one it was first shown in', async () => {
    const list = await likesReceived();
    expect(list.cards.map((c) => c.name)).not.toEqual(
      expect.arrayContaining(['Clara']),
    );
    expect(list.closedCount).toBe(0);
  });

  it('closes a like on the re-check, counts it seen, and drops it from the badge', async () => {
    await matchRow(ids.mia, ids.dana);
    const res = await request(server)
      .post('/search/availability')
      .query({ tz: TZ })
      .set('Authorization', as(maxToken))
      .send({ userIds: [ids.mia] })
      .expect(200);
    expect(res.body.statuses[0].status).toBe('matched');
    const mia = await statusOf(ids.mia);
    expect(mia).toMatchObject({
      status: 'withdrawn',
      closed_reason: 'matched',
    });
    expect(mia.closed_seen_at).not.toBeNull();
    expect(await openCount()).toBe(4);
  });

  // ── Not now and Not interested on a liked-you profile ───────────────────────

  it('keeps a like open after Not now', async () => {
    await request(server)
      .post('/search/passes')
      .set('Authorization', as(maxToken))
      .send({ targetUserId: ids.anna, passType: 'not_now' })
      .expect(204);
    expect(await openCount()).toBe(4);
    expect((await statusOf(ids.anna)).status).toBe('active');
  });

  it('declines a like for good on Not interested, and never tells the liker', async () => {
    await request(server)
      .post('/search/passes')
      .set('Authorization', as(maxToken))
      .send({ targetUserId: ids.leonie, passType: 'not_interested' })
      .expect(204);
    expect((await statusOf(ids.leonie)).status).toBe('declined');
    expect(await openCount()).toBe(3);

    // Leonie likes Max again: success, still open as far as she can tell, and never mutual.
    const again = await like(leonieToken, maxId).expect(201);
    expect(again.body).toMatchObject({ status: 'active', matched: false });
    expect(again.body).not.toHaveProperty('closed');
    // Her Search does not change either: Max stays out of it, as while the like was open.
    const q = await request(server)
      .get('/search/queue')
      .query({ tz: TZ })
      .set('Authorization', as(leonieToken))
      .expect(200);
    expect(
      (q.body.profiles as Array<{ userId: string }>).map((p) => p.userId),
    ).not.toContain(maxId);
  });

  // ── confirms that cannot become a date ──────────────────────────────────────

  it('refuses a confirm when the liker has just matched someone else, and counts the card seen', async () => {
    const res = await like(maxToken, ids.clara).expect(201);
    expect(res.body).toMatchObject({
      matched: false,
      status: 'withdrawn',
      closed: 'matched',
    });
  });

  it('refuses a confirm when the two have no time left to meet', async () => {
    await db.query(
      `UPDATE "check_ins" SET "availability_start" = $2, "availability_end" = $3
        WHERE "user_id" = $1`,
      [ids.hanna, hour(10), hour(11)],
    );
    const res = await like(maxToken, ids.hanna).expect(201);
    expect(res.body).toMatchObject({ matched: false, closed: 'no_overlap' });
    const hanna = await statusOf(ids.hanna);
    expect(hanna).toMatchObject({
      status: 'withdrawn',
      closed_reason: 'no_overlap',
    });
    expect(hanna.closed_seen_at).not.toBeNull();
  });

  it('accepts a plain like to someone who has a date: no error, closed at once', async () => {
    const res = await like(maxToken, ids.sofia).expect(201);
    expect(res.body).toMatchObject({ matched: false, status: 'active' });
    await matchRow(ids.sofia, ids.ben);
    // Sofia now has a date; a like to her lands closed.
    await db.query(
      `DELETE FROM "likes" WHERE "sender_id" = $1 AND "receiver_id" = $2`,
      [maxId, ids.sofia],
    );
    const late = await like(maxToken, ids.sofia).expect(201);
    expect(late.body).toMatchObject({ matched: false, closed: 'matched' });
  });

  // ── two confirms for one like ───────────────────────────────────────────────

  it('lets the first of two confirms win, and refuses the other with matched', async () => {
    await db.query(
      `DELETE FROM "matches" WHERE "user_a_id" = $1 OR "user_b_id" = $1`,
      [ids.ben],
    );
    await likeRow(ids.anna, ids.ben);
    await likeRow(ids.anna, ids.leonie);
    const [a, b] = await Promise.all([
      like(benToken, ids.anna).expect(201),
      like(leonieToken, ids.anna).expect(201),
    ]);
    const answers = [a.body, b.body];
    expect(answers.filter((x) => x.matched === true)).toHaveLength(1);
    expect(answers.filter((x) => x.closed === 'matched')).toHaveLength(1);
    const [{ n }] = await db.query(
      `SELECT count(*)::int AS n FROM "matches"
        WHERE ("user_a_id" = $1 OR "user_b_id" = $1) AND "status" = 'active'`,
      [ids.anna],
    );
    expect(n).toBe(1);
  });

  // ── the viewer's own match ──────────────────────────────────────────────────

  it("never shows a like closed by Max's own match as Too late", async () => {
    // Sofia's match is gone, so her like to Max is one his match will close.
    await db.query(
      `DELETE FROM "matches" WHERE "user_a_id" = $1 OR "user_b_id" = $1`,
      [ids.sofia],
    );
    await likeRow(ids.sofia, maxId);
    const won = await like(maxToken, ids.liv).expect(201);
    expect(won.body.matched).toBe(true);
    expect(await statusOf(ids.sofia)).toMatchObject({
      status: 'withdrawn',
      closed_reason: 'matched',
    });
    const list = await likesReceived();
    expect(list.cards.map((c) => c.name)).not.toContain('Sofia');
    expect((await statusOf(ids.sofia)).closed_seen_at).not.toBeNull();
  });
});
