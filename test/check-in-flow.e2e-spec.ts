import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { DataSource } from 'typeorm';

import { AppModule } from './../src/app.module';

/**
 * The 48hrs Check-In flow (SHOWUP-177, stories 178 to 183), driven through the real app against a
 * real database, in the order the walk takes: profile completion opens a window, the intro sets its
 * flag, Motivation and Vibe save their last answers, Filters saves the settings, and Find my date
 * commits -- once, whatever happens to the request.
 *
 * ITS OWN FILE for the rate limit (each e2e file has its own 60 requests a minute).
 */
const PHONE = '+491701234178';

describe('the 48hrs Check-In flow (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let token: string;
  let userId: string;
  let dataSource: DataSource;

  const bearer = () => `Bearer ${token}`;
  const state = async () =>
    (
      await request(server)
        .get('/me/check-in')
        .set('Authorization', bearer())
        .expect(200)
    ).body;
  const commit = (body: object) =>
    request(server)
      .post('/me/check-in/commit')
      .set('Authorization', bearer())
      .send(body);

  /** An hour slot `hoursAhead` whole hours after the start of the current hour. */
  const hour = (hoursAhead: number) => {
    const d = new Date();
    d.setUTCMinutes(0, 0, 0);
    return new Date(d.getTime() + hoursAhead * 3_600_000).toISOString();
  };

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

    const start = await request(server)
      .post('/auth/phone/start')
      .send({ phone: PHONE })
      .expect(200);
    const verify = await request(server)
      .post('/auth/phone/verify')
      .send({ phone: PHONE, code: start.body.devCode })
      .expect(200);
    token = verify.body.accessToken;
    userId = verify.body.user.id;
    dataSource = app.get(DataSource);
  });

  afterAll(async () => {
    await app.close();
  });

  // ── the window ────────────────────────────────────────────────────────────

  it('has no window before profile creation is complete', async () => {
    const s = await state();
    expect(s.windowId).toBeNull();
    expect(s).toMatchObject({
      introPassed: false,
      active: false,
      firstCheckIn: true,
      lastMotivation: null,
      lastVibes: [],
      filtersSaved: false,
    });
  });

  it('opens a window when profile creation completes, and pre-fills the gender filter from the profile', async () => {
    await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({
        gender: 'woman',
        orientation: 'straight',
        flowPosition: 'life_ahead',
      })
      .expect(200);
    const rows = await dataSource.query(
      `SELECT id, closes_at FROM check_in_windows WHERE user_id = $1`,
      [userId],
    );
    expect(rows).toHaveLength(1);
    // Unfinished: the 48 hours have not started, and will not until Find my date.
    expect(rows[0].closes_at).toBeNull();
    const s = await state();
    expect(s.windowId).toBe(rows[0].id);
    // The same window on every read, until it closes.
    expect((await state()).windowId).toBe(rows[0].id);
    expect(s.filters).toEqual({
      meetGender: ['man'],
      minAge: 18,
      maxAge: null,
      distanceKm: 10,
    });
  });

  // ── the intro and the last answers ─────────────────────────────────────────

  it('sets the intro-passed flag once, idempotently', async () => {
    for (let i = 0; i < 2; i++) {
      await request(server)
        .post('/me/check-in/intro-passed')
        .set('Authorization', bearer())
        .expect(204);
    }
    expect((await state()).introPassed).toBe(true);
  });

  it('saves the last motivation and vibes, the vibes de-duplicated and in list order', async () => {
    await request(server)
      .put('/me/check-in/answers')
      .set('Authorization', bearer())
      .send({ motivation: 'casual' })
      .expect(204);
    await request(server)
      .put('/me/check-in/answers')
      .set('Authorization', bearer())
      .send({ vibes: ['no_rush', 'coffee_chat', 'playful'] })
      .expect(204);
    const s = await state();
    expect(s.lastMotivation).toBe('casual');
    expect(s.lastVibes).toEqual(['coffee_chat', 'playful', 'no_rush']);
  });

  it('refuses an unknown value, six vibes, and a repeated vibe', async () => {
    for (const body of [
      { motivation: 'long-term' },
      { vibes: ['coffeeachat'] },
      { vibes: ['calm', 'cozy', 'curious', 'playful', 'sunset', 'workout'] },
      { vibes: ['calm', 'calm'] },
      { vibes: [] },
    ]) {
      await request(server)
        .put('/me/check-in/answers')
        .set('Authorization', bearer())
        .send(body)
        .expect(400);
    }
  });

  // ── the filter settings ────────────────────────────────────────────────────

  it('saves the filters, storing 65 as no upper limit', async () => {
    const res = await request(server)
      .put('/me/filter-settings')
      .set('Authorization', bearer())
      .send({
        meetGender: ['woman', 'non_binary'],
        minAge: 25,
        maxAge: 65,
        distanceKm: 15,
      })
      .expect(200);
    expect(res.body).toEqual({
      meetGender: ['woman', 'non_binary'],
      minAge: 25,
      maxAge: null,
      distanceKm: 15,
    });
    const s = await state();
    expect(s.filtersSaved).toBe(true);
    expect(s.filters.meetGender).toEqual(['woman', 'non_binary']);
  });

  it('refuses no gender, a maximum below the minimum, and out-of-range values', async () => {
    for (const body of [
      { meetGender: [], minAge: 18, maxAge: null, distanceKm: 10 },
      { meetGender: ['woman'], minAge: 40, maxAge: 30, distanceKm: 10 },
      { meetGender: ['woman'], minAge: 17, maxAge: null, distanceKm: 10 },
      { meetGender: ['woman'], minAge: 18, maxAge: null, distanceKm: 51 },
      { meetGender: ['women'], minAge: 18, maxAge: null, distanceKm: 10 },
    ]) {
      await request(server)
        .put('/me/filter-settings')
        .set('Authorization', bearer())
        .send(body)
        .expect(400);
    }
  });

  // ── the commit ─────────────────────────────────────────────────────────────

  it('refuses contact info with its own error, and saves nothing', async () => {
    const s = await state();
    for (const uplift of [
      'Text me on 0176 2345 6789 :)',
      'mail me at me@example.com',
      'find me on myname.de',
    ]) {
      const res = await commit({
        windowId: s.windowId,
        slots: [{ startsAt: hour(30), tier: 'preferred' }],
        motivation: 'casual',
        vibes: ['calm'],
        uplift,
      }).expect(422);
      expect(res.body.error).toBe('uplift_contact_info');
      expect(res.body.message).toBe(
        'No emails, phone numbers or links allowed.',
      );
    }
    expect((await state()).active).toBe(false);
  });

  it('refuses a reason longer than 100 characters, counting an emoji as one', async () => {
    const s = await state();
    const body = (uplift: string) => ({
      windowId: s.windowId,
      slots: [{ startsAt: hour(30), tier: 'preferred' }],
      motivation: 'casual',
      vibes: ['calm'],
      uplift,
    });
    await commit(body('😀'.repeat(101))).expect(400);
    expect((await state()).active).toBe(false);
  });

  it('commits once: started hours dropped, runs merged per tier, the answers stored', async () => {
    const s = await state();
    const body = {
      windowId: s.windowId,
      slots: [
        { startsAt: hour(0), tier: 'preferred' }, // started -- dropped silently
        { startsAt: hour(2), tier: 'preferred' },
        { startsAt: hour(3), tier: 'preferred' },
        { startsAt: hour(4), tier: 'available' },
        { startsAt: hour(26), tier: 'available' },
      ],
      motivation: 'long_term',
      vibes: ['sunset', 'calm'],
      uplift: '  will take you out on my boat on Wannsee  ',
      latitude: 52.52,
      longitude: 13.405,
    };
    const first = await commit(body).expect(200);
    expect(first.body).toMatchObject({
      windowId: s.windowId,
      active: true,
      firstCheckIn: false,
      lastMotivation: 'long_term',
      // §31 list order, not the order sent: sunset is the 7th value, calm the 9th.
      lastVibes: ['sunset', 'calm'],
    });

    const windows = await dataSource.query(
      `SELECT committed_at, motivation, vibes, uplift FROM check_in_windows WHERE id = $1`,
      [s.windowId],
    );
    expect(windows[0].motivation).toBe('long_term');
    expect(windows[0].uplift).toBe('will take you out on my boat on Wannsee');
    const hours = await dataSource.query(
      `SELECT availability_start, availability_end, tier, location IS NOT NULL AS located
         FROM check_ins WHERE check_in_window_id = $1 ORDER BY availability_start`,
      [s.windowId],
    );
    expect(
      hours.map(
        (h: {
          availability_start: Date;
          availability_end: Date;
          tier: string;
        }) => [
          h.availability_start.toISOString(),
          h.availability_end.toISOString(),
          h.tier,
        ],
      ),
    ).toEqual([
      [hour(2), hour(4), 'preferred'],
      [hour(4), hour(5), 'available'],
      [hour(26), hour(27), 'available'],
    ]);
    expect(hours.every((h: { located: boolean }) => h.located)).toBe(true);

    // A retry after a timeout: the same answer, and nothing written twice.
    const again = await commit(body).expect(200);
    expect(again.body.windowId).toBe(s.windowId);
    const count = await dataSource.query(
      `SELECT count(*)::int AS n FROM check_ins WHERE check_in_window_id = $1`,
      [s.windowId],
    );
    expect(count[0].n).toBe(3);
  });

  it('commits into the current window when the flow outlived its own, and retires the last Check-In', async () => {
    const committed = await state();
    // The 48 hours of the committed Check-In run out: the next read opens a fresh window.
    await dataSource.query(
      `UPDATE check_in_windows SET opened_at = now() - interval '49 hours',
         closes_at = now() - interval '1 hour' WHERE user_id = $1`,
      [userId],
    );
    const flow = await state();
    expect(flow.windowId).not.toBe(committed.windowId);
    expect(flow.active).toBe(false);
    expect(flow.firstCheckIn).toBe(false);
    // Returning users start pre-filled from the last answers.
    expect(flow.lastMotivation).toBe('long_term');

    // The user starts a Check-In in it, leaves halfway, and comes back four days later. Nothing was
    // counted while it was unfinished, so it is still the same window.
    await dataSource.query(
      `UPDATE check_in_windows SET opened_at = now() - interval '100 hours' WHERE id = $1`,
      [flow.windowId],
    );
    expect((await state()).windowId).toBe(flow.windowId);
    const res = await commit({
      windowId: flow.windowId,
      slots: [{ startsAt: hour(5), tier: 'preferred' }],
      motivation: 'friends',
      vibes: ['cozy'],
      uplift: '   ',
    }).expect(200);
    expect(res.body.windowId).toBe(flow.windowId);
    expect(res.body.active).toBe(true);
    // The 48 hours start at Find my date.
    const clock = await dataSource.query(
      `SELECT extract(epoch FROM closes_at - committed_at)::int AS seconds
         FROM check_in_windows WHERE id = $1`,
      [flow.windowId],
    );
    expect(clock[0].seconds).toBe(48 * 3600);
    const current = await dataSource.query(
      `SELECT uplift, motivation FROM check_in_windows WHERE id = $1`,
      [res.body.windowId],
    );
    expect(current[0]).toEqual({ uplift: null, motivation: 'friends' });
    // One active Check-In at a time: the earlier Check-In's hours that were still live are gone.
    const live = await dataSource.query(
      `SELECT count(*)::int AS n FROM check_ins
        WHERE user_id = $1 AND status = 'available' AND check_in_window_id <> $2`,
      [userId, res.body.windowId],
    );
    expect(live[0].n).toBe(0);
  });

  it('a replay of a Check-In whose window has closed does not claim a live Check-In', async () => {
    const live = await state();
    await dataSource.query(
      `UPDATE check_in_windows SET opened_at = now() - interval '49 hours',
         closes_at = now() - interval '1 minute' WHERE id = $1`,
      [live.windowId],
    );
    const res = await commit({
      windowId: live.windowId,
      slots: [],
      motivation: 'friends',
      vibes: ['cozy'],
    }).expect(200);
    expect(res.body.active).toBe(false);
    expect(res.body.windowId).not.toBe(live.windowId);
  });

  it('refuses to give an unfinished Check-In a clock', async () => {
    // The rule the migration added, held by Postgres itself: a clock if and only if finished.
    const unfinished = await state();
    expect(unfinished.active).toBe(false);
    await expect(
      dataSource.query(
        `UPDATE check_in_windows SET closes_at = now() + interval '1 hour' WHERE id = $1`,
        [unfinished.windowId],
      ),
    ).rejects.toThrow(/check_in_windows_clock_starts_at_commit/);
  });

  it('refuses a window that is not the users own', async () => {
    await commit({
      windowId: '00000000-0000-4000-8000-000000000000',
      slots: [],
      motivation: 'friends',
      vibes: ['cozy'],
    }).expect(404);
  });

  // ── the database's own refusals ────────────────────────────────────────────

  it('the tables refuse what the API refuses, whatever path a value takes', async () => {
    const runner = dataSource.createQueryRunner();
    await runner.connect();
    const attempt = async (sql: string): Promise<string> => {
      await runner.startTransaction();
      try {
        await runner.query(sql, [userId]);
        return 'accepted';
      } catch (error) {
        return (error as { code?: string }).code ?? 'unknown';
      } finally {
        await runner.rollbackTransaction();
      }
    };
    const CHECK_VIOLATION = '23514';
    const NOT_IN_ENUM = '22P02';
    try {
      expect(
        await attempt(
          `UPDATE check_in_profiles SET last_vibes = '{calm,cozy,curious,playful,sunset,workout}' WHERE user_id = $1`,
        ),
      ).toBe(CHECK_VIOLATION);
      expect(
        await attempt(
          `UPDATE check_in_profiles SET last_vibes = '{NULL}' WHERE user_id = $1`,
        ),
      ).toBe(CHECK_VIOLATION);
      expect(
        await attempt(
          `UPDATE check_in_profiles SET last_motivation = 'enm' WHERE user_id = $1`,
        ),
      ).toBe(NOT_IN_ENUM);
      expect(
        await attempt(
          `UPDATE filter_settings SET meet_gender = '{}' WHERE user_id = $1`,
        ),
      ).toBe(CHECK_VIOLATION);
      expect(
        await attempt(
          `UPDATE filter_settings SET max_age = 65 WHERE user_id = $1`,
        ),
      ).toBe(CHECK_VIOLATION);
      expect(
        await attempt(
          `UPDATE filter_settings SET min_age = 30, max_age = 20 WHERE user_id = $1`,
        ),
      ).toBe(CHECK_VIOLATION);
      expect(
        await attempt(
          `UPDATE filter_settings SET distance_km = 0 WHERE user_id = $1`,
        ),
      ).toBe(CHECK_VIOLATION);
      expect(
        await attempt(
          `UPDATE check_in_windows SET uplift = '' WHERE user_id = $1`,
        ),
      ).toBe(CHECK_VIOLATION);
      expect(
        await attempt(
          `UPDATE check_in_windows SET motivation = NULL WHERE user_id = $1 AND committed_at IS NOT NULL`,
        ),
      ).toBe(CHECK_VIOLATION);
    } finally {
      await runner.release();
    }
  });
});
