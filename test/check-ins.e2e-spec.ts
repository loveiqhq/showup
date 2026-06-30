import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import request from 'supertest';
import { Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { CheckIn } from './../src/modules/check-ins/entities/check-in.entity';

// Requires the Docker DB. Uses a distinct phone from the other e2e suites to avoid collisions.
const PHONE = '+491701234777';

const hoursFromNow = (h: number) =>
  new Date(Date.now() + h * 3_600_000).toISOString();

describe('Check-ins (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let token: string;
  let userId: string;
  let checkIns: Repository<CheckIn>;
  let checkInId: string;

  const bearer = () => `Bearer ${token}`;

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

    checkIns = app.get<Repository<CheckIn>>(getRepositoryToken(CheckIn), {
      strict: false,
    });
    await checkIns.delete({ userId });
  });

  afterAll(async () => {
    await app.close();
  });

  it('rejects a check-in whose window ends before it starts', async () => {
    await request(server)
      .post('/me/check-ins')
      .set('Authorization', bearer())
      .send({
        availabilityStart: hoursFromNow(4),
        availabilityEnd: hoursFromNow(2),
      })
      .expect(400);
  });

  it('creates an active check-in for a valid window', async () => {
    const res = await request(server)
      .post('/me/check-ins')
      .set('Authorization', bearer())
      .send({
        availabilityStart: hoursFromNow(0),
        availabilityEnd: hoursFromNow(3),
      })
      .expect(201);
    expect(res.body.status).toBe('available');
    expect(res.body.active).toBe(true);
    checkInId = res.body.id;
  });

  it('returns the active check-in', async () => {
    const res = await request(server)
      .get('/me/check-ins/active')
      .set('Authorization', bearer())
      .expect(200);
    expect(res.body.id).toBe(checkInId);
    expect(res.body.active).toBe(true);
  });

  it('blocks a second active check-in', async () => {
    await request(server)
      .post('/me/check-ins')
      .set('Authorization', bearer())
      .send({
        availabilityStart: hoursFromNow(0),
        availabilityEnd: hoursFromNow(2),
      })
      .expect(400);
  });

  it('cancels the check-in', async () => {
    const res = await request(server)
      .post(`/me/check-ins/${checkInId}/cancel`)
      .set('Authorization', bearer())
      .expect(200);
    expect(res.body.status).toBe('cancelled');
  });

  it('reports no active check-in after cancelling', async () => {
    const res = await request(server)
      .get('/me/check-ins/active')
      .set('Authorization', bearer())
      .expect(200);
    expect(res.body).toEqual({});
  });
});
