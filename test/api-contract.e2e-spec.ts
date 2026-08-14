/**
 * SHOWUP-98 — the authorization contract of every HTTP route in the app.
 *
 * Why this file exists: most of the other e2e specs (dates, matching, safety, reports, queue,
 * location-proximity) drive the SERVICES directly via `app.get(SomeService)` rather than sending
 * HTTP requests. That tests the domain logic against a real database, which is valuable, but it
 * bypasses the whole HTTP layer — so a route that is missing `@Public()`-less protection, or an
 * admin route missing `AdminGuard`, is completely invisible to them.
 *
 * Authorization is exactly the kind of rule that must never regress silently, so it gets asserted
 * here route by route:
 *   - every protected route rejects a request with no token (401),
 *   - every /admin route rejects a normal signed-in, non-admin user (403),
 *   - every route marked @Public() is actually reachable (not 404 — i.e. the table below is real).
 *
 * Both guards run before the handler and before the ValidationPipe, so no seed data is needed: a
 * placeholder UUID in the path is enough. That is also what makes the assertions self-checking —
 * a typo in a path returns 404, which is neither 401 nor 403, and the test fails.
 *
 * Adding a route to a controller without adding it here will not fail this suite; the counts at the
 * bottom guard against that by comparing against the router's own registered-route table.
 */

// This spec deliberately fires one request at every route in the application, which is more than
// the 60-per-minute throttle allows and would return 429 instead of 401/403. The limit is raised
// for this process only — throttling itself is not what is under test here. Must be set before the
// application is instantiated, because the config factory reads process.env at module init.
// Restored in afterAll: `maxWorkers: 1` means every e2e file shares one process, so leaving this
// raised would silently change the throttle for whichever spec runs next.
const ORIGINAL_THROTTLE_LIMIT = process.env.THROTTLE_LIMIT;
process.env.THROTTLE_LIMIT = '100000';

import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import request from 'supertest';
import { DataSource, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { User, UserRole } from './../src/modules/users/entities/user.entity';

// A distinct phone from every other e2e spec, so parallel-safe cleanup never fights them.
const PHONE = '+491701234777';

// Stands in for any :id / :userId path parameter. Never resolved — the guard answers first.
const ID = '00000000-0000-4000-8000-000000000000';

type Method = 'get' | 'post' | 'patch' | 'delete';
interface Route {
  method: Method;
  path: string;
}

/** The part of the Express router the completeness check reads, narrowed from `any`. */
interface RegisteredRoute {
  path: string;
  methods: Record<string, boolean>;
}
interface RouterStack {
  stack?: Array<{ route?: RegisteredRoute }>;
}

/** Routes that require a valid token. Sourced from the @Controller prefix + method decorator. */
const PROTECTED: Route[] = [
  // account.controller.ts
  { method: 'get', path: '/auth/me' },
  { method: 'post', path: '/account/delete-request' },
  { method: 'post', path: '/account/delete-request/cancel' },
  // auth.controller.ts — the two routes NOT marked @Public(): email is attached to an account that
  // is already signed in, so these legitimately require a token.
  { method: 'post', path: '/auth/email/start' },
  { method: 'post', path: '/auth/email/verify' },
  // check-ins.controller.ts
  { method: 'post', path: '/me/check-ins' },
  { method: 'get', path: '/me/check-ins/active' },
  { method: 'post', path: `/me/check-ins/${ID}/cancel` },
  // dates.controller.ts — no HTTP coverage anywhere before this file
  { method: 'get', path: '/me/dates' },
  { method: 'post', path: `/dates/${ID}/cancel` },
  { method: 'post', path: `/dates/${ID}/confirm` },
  { method: 'post', path: `/dates/${ID}/no-show` },
  { method: 'get', path: `/dates/${ID}/chat` },
  { method: 'post', path: `/dates/${ID}/chat` },
  // matching.controller.ts — no HTTP coverage anywhere before this file
  { method: 'get', path: '/discovery' },
  { method: 'post', path: '/likes' },
  { method: 'get', path: '/me/matches' },
  // notification-preferences.controller.ts
  { method: 'get', path: '/me/notification-preferences' },
  { method: 'patch', path: '/me/notification-preferences' },
  // push-tokens.controller.ts
  { method: 'post', path: '/me/push-tokens' },
  { method: 'delete', path: '/me/push-tokens' },
  // photos.controller.ts
  { method: 'post', path: '/me/photos' },
  { method: 'get', path: '/me/photos' },
  { method: 'delete', path: `/me/photos/${ID}` },
  // profiles.controller.ts
  { method: 'get', path: '/me/profile' },
  { method: 'post', path: '/me/profile' },
  { method: 'patch', path: '/me/profile' },
  { method: 'post', path: '/me/profile/verification' },
  // safety.controller.ts — no HTTP coverage anywhere before this file
  { method: 'post', path: '/blocks' },
  { method: 'delete', path: `/blocks/${ID}` },
  { method: 'get', path: '/me/blocks' },
  { method: 'post', path: '/reports' },
  { method: 'post', path: '/me/verification' },
  { method: 'get', path: '/me/verification' },
];

/** Routes behind AdminGuard. A normal signed-in user must be refused with 403. */
const ADMIN: Route[] = [
  // profile-admin.controller.ts
  { method: 'patch', path: `/admin/profiles/${ID}/verification` },
  { method: 'patch', path: `/admin/photos/${ID}/moderation` },
  // safety-admin.controller.ts
  { method: 'get', path: '/admin/users' },
  { method: 'get', path: `/admin/users/${ID}/safety` },
  { method: 'patch', path: `/admin/users/${ID}/standing` },
  { method: 'patch', path: `/admin/profiles/${ID}/standing` },
  { method: 'patch', path: `/admin/photos/${ID}/standing` },
  { method: 'get', path: '/admin/reports' },
  { method: 'get', path: `/admin/reports/${ID}` },
  { method: 'patch', path: `/admin/reports/${ID}` },
];

/** Routes marked @Public() — reachable with no token at all. */
const PUBLIC: Route[] = [
  { method: 'get', path: '/health' },
  { method: 'get', path: '/health/ready' },
  { method: 'post', path: '/auth/phone/start' },
  { method: 'post', path: '/auth/phone/verify' },
  { method: 'post', path: '/auth/apple' },
  { method: 'post', path: '/auth/google' },
  { method: 'post', path: '/auth/refresh' },
  { method: 'post', path: '/auth/logout' },
];

describe('API authorization contract (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let userToken: string;

  const send = (r: Route) => {
    const req = request(server);
    if (r.method === 'get') return req.get(r.path);
    if (r.method === 'post') return req.post(r.path);
    if (r.method === 'patch') return req.patch(r.path);
    return req.delete(r.path);
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

    // The resend cooldown for one-time codes is stored in the database (60s per phone), so a
    // previous run inside that window would answer 429 here. Clearing it keeps the suite
    // re-runnable back to back.
    await app
      .get(DataSource)
      .query(`DELETE FROM phone_verifications WHERE phone = $1`, [PHONE]);

    const start = await request(server)
      .post('/auth/phone/start')
      .send({ phone: PHONE })
      .expect(200);
    const verify = await request(server)
      .post('/auth/phone/verify')
      .send({ phone: PHONE, code: start.body.devCode })
      .expect(200);
    userToken = verify.body.accessToken;

    // Guarantee this account is NOT an admin, whatever a previous run left behind — otherwise the
    // 403 assertions below would pass for the wrong reason.
    const users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    await users.update({ id: verify.body.user.id }, { role: UserRole.User });
  });

  afterAll(async () => {
    await app.close();
    if (ORIGINAL_THROTTLE_LIMIT === undefined) {
      delete process.env.THROTTLE_LIMIT;
    } else {
      process.env.THROTTLE_LIMIT = ORIGINAL_THROTTLE_LIMIT;
    }
  });

  describe('without a token', () => {
    it.each(PROTECTED)('$method $path → 401', async (route) => {
      const res = await send(route);
      expect(res.status).toBe(401);
    });

    it.each(ADMIN)('$method $path → 401', async (route) => {
      const res = await send(route);
      expect(res.status).toBe(401);
    });
  });

  describe('as a signed-in non-admin user', () => {
    it.each(ADMIN)('$method $path → 403', async (route) => {
      const res = await send(route).set('Authorization', `Bearer ${userToken}`);
      expect(res.status).toBe(403);
    });

    it('reaches a normal protected route with the same token, proving it is valid', async () => {
      const res = await request(server)
        .get('/auth/me')
        .set('Authorization', `Bearer ${userToken}`);
      expect(res.status).toBe(200);
    });
  });

  describe('public routes', () => {
    // Asserting "not 404" proves the path in the table is real and the route is registered. A
    // stronger "not 401" cannot be used here: /auth/refresh and /auth/logout are public but
    // legitimately answer 401 from inside the handler when no refresh token is supplied.
    it.each(PUBLIC)('$method $path is registered', async (route) => {
      const res = await send(route);
      expect(res.status).not.toBe(404);
    });

    it('GET /health needs no token', async () => {
      await request(server).get('/health').expect(200);
    });
  });

  it('covers every route the router has registered', () => {
    // Guards against this file silently going stale: any route added to a controller must be
    // classified above. Compares against Express's own route table rather than a hand-kept number.
    const registered = new Set<string>();
    const router = app.getHttpAdapter().getInstance().router as
      | RouterStack
      | undefined;
    for (const layer of router?.stack ?? []) {
      if (!layer.route) continue;
      for (const m of Object.keys(layer.route.methods)) {
        registered.add(`${m.toLowerCase()} ${layer.route.path}`);
      }
    }

    // Normalise the tables back to parameterised paths so they can be compared to the router.
    const classified = new Set(
      [...PROTECTED, ...ADMIN, ...PUBLIC].map(
        (r) => `${r.method} ${r.path.replace(new RegExp(ID, 'g'), ':param')}`,
      ),
    );
    const normalisedRegistered = new Set(
      [...registered].map((r) => r.replace(/:[A-Za-z]+/g, ':param')),
    );

    const missing = [...normalisedRegistered].filter((r) => !classified.has(r));
    expect(missing).toEqual([]);
  });
});
