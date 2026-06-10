import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';

import { AppModule } from './../src/app.module';
import { AppleVerifier } from './../src/modules/auth/social/apple-verifier';
import { GoogleVerifier } from './../src/modules/auth/social/google-verifier';

// Requires the Docker DB to be up (`npm run db:up`). Social verifiers are replaced with fakes so
// the social endpoints can be exercised end-to-end without real Apple/Google tokens.
const PHONE = '+491701234567';
const googleIdentity = {
  provider: 'google' as const,
  providerId: 'g-e2e-1',
  email: 'g.e2e@example.com',
  emailVerified: true,
  name: 'G E2E',
};

describe('Auth (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(GoogleVerifier)
      .useValue({ verify: jest.fn().mockResolvedValue(googleIdentity) })
      .overrideProvider(AppleVerifier)
      .useValue({ verify: jest.fn() })
      .compile();

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
  });

  afterAll(async () => {
    await app.close();
  });

  // ── Phone OTP ──
  let devCode: string;
  let phoneAccess: string;
  let phoneUserId: string;

  it('POST /auth/phone/start returns a dev code', async () => {
    const res = await request(server)
      .post('/auth/phone/start')
      .send({ phone: PHONE })
      .expect(200);
    expect(res.body.devCode).toMatch(/^\d{4}$/);
    devCode = res.body.devCode;
  });

  it('POST /auth/phone/verify issues tokens for a verified phone', async () => {
    const res = await request(server)
      .post('/auth/phone/verify')
      .send({ phone: PHONE, code: devCode })
      .expect(200);
    expect(res.body.accessToken).toBeDefined();
    expect(res.body.refreshToken).toBeDefined();
    expect(res.body.tokenType).toBe('Bearer');
    expect(res.body.user.phoneVerified).toBe(true);
    phoneAccess = res.body.accessToken;
    phoneUserId = res.body.user.id;
  });

  it('GET /auth/me is protected', () =>
    request(server).get('/auth/me').expect(401));

  it('GET /auth/me returns the current user with a valid token', async () => {
    const res = await request(server)
      .get('/auth/me')
      .set('Authorization', `Bearer ${phoneAccess}`)
      .expect(200);
    expect(res.body.id).toBe(phoneUserId);
  });

  // ── Social ──
  it('POST /auth/google creates/links a user and issues tokens', async () => {
    const res = await request(server)
      .post('/auth/google')
      .send({ idToken: 'fake' })
      .expect(200);
    expect(res.body.user.email).toBe(googleIdentity.email);
    expect(res.body.accessToken).toBeDefined();
  });

  // ── Token lifecycle (fresh google session each time; no OTP cooldown) ──
  const googleLogin = async () => {
    const res = await request(server)
      .post('/auth/google')
      .send({ idToken: 'fake' })
      .expect(200);
    return res.body as {
      accessToken: string;
      refreshToken: string;
      user: { id: string };
    };
  };

  it('refresh rotates tokens and rejects reuse of the old token', async () => {
    const { refreshToken } = await googleLogin();
    const rotated = await request(server)
      .post('/auth/refresh')
      .send({ refreshToken })
      .expect(200);
    expect(rotated.body.refreshToken).not.toBe(refreshToken);
    // Reusing the now-rotated token is rejected.
    await request(server)
      .post('/auth/refresh')
      .send({ refreshToken })
      .expect(401);
  });

  it('logout revokes the refresh token', async () => {
    const { refreshToken } = await googleLogin();
    await request(server)
      .post('/auth/logout')
      .send({ refreshToken })
      .expect(204);
    await request(server)
      .post('/auth/refresh')
      .send({ refreshToken })
      .expect(401);
  });

  it('account deletion request flips status to deletion_pending', async () => {
    const { accessToken } = await googleLogin();
    await request(server)
      .post('/account/delete-request')
      .set('Authorization', `Bearer ${accessToken}`)
      .expect(202);
    const me = await request(server)
      .get('/auth/me')
      .set('Authorization', `Bearer ${accessToken}`)
      .expect(200);
    expect(me.body.status).toBe('deletion_pending');
  });
});
