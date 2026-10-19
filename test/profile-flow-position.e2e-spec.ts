import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { DataSource } from 'typeorm';

import { AppModule } from './../src/app.module';

/**
 * One person's profile saves arriving at the same time (SHOWUP-165).
 *
 * ITS OWN FILE BECAUSE OF THE RATE LIMIT, not for tidiness. The global throttler allows 60 requests
 * a minute per client and each e2e file builds its own app, so each file has its own budget. These
 * tests are bursts by design -- they only mean anything if the requests really overlap -- and in
 * the main details file they pushed the total past 60 and every test after them got a 429.
 *
 * What they guard: `save()` writes back every column that differs from what it last read, so two
 * saves working from their own reads let the later commit put back the earlier one's old values --
 * an answer erased, or the flow position rewound. The service now holds the profile row lock from
 * read to write. Without it these tests fail within a round or two (checked by removing the lock).
 */
const PHONE = '+491701234166';

describe('Profile saves under concurrency (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let dataSource: DataSource;
  let token: string;
  let userId: string;

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
    dataSource = app.get(DataSource);

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
  });

  afterAll(async () => {
    await app.close();
  });

  const patch = (body: Record<string, unknown>) =>
    request(server)
      .patch('/me/profile')
      .set('Authorization', `Bearer ${token}`)
      .send(body)
      .expect(200);

  /** Straight from the table: what was persisted, not what a response claimed. */
  const stored = async () => {
    const rows: Record<string, unknown>[] = await dataSource.query(
      `SELECT flow_position, height_cm, education, religion, politics
         FROM profiles WHERE user_id = $1`,
      [userId],
    );
    return rows[0];
  };

  it("a brand-new account's first requests in parallel all succeed, with one profile", async () => {
    // getOrCreate used to check-then-insert, so parallel first requests collided on the
    // one-profile-per-user constraint and one of them was a 500. Runs FIRST in this file, while
    // the account still has no profile -- which is the only time the race exists.
    const responses = await Promise.all(
      Array.from({ length: 5 }, () =>
        request(server)
          .get('/me/profile')
          .set('Authorization', `Bearer ${token}`)
          .expect(200),
      ),
    );
    expect(new Set(responses.map((r) => r.body.id)).size).toBe(1);
  });

  it('every answer and the furthest position survive, however the saves interleave', async () => {
    // EACH ROUND STARTS FROM AN EMPTY ROW. Without the reset only round one could ever fail: once
    // the position is on politics every later report is behind it, and a test that cannot fail
    // after its first round proves one round's worth.
    //
    // ANSWERS MIXED IN, NOT ONLY POSITIONS. The failure is not "the position goes backwards", it is
    // "a stale save writes back columns it did not touch" -- so the requests carry different
    // answers, two of them with no position at all, and every answer must be there at the end.
    //
    // 7 rounds x 6 requests, plus the login and the 5 above, stays inside the file's 60 a minute.
    for (let round = 0; round < 7; round++) {
      await dataSource.query(
        `UPDATE profiles
            SET flow_position = NULL, height_cm = NULL, education = NULL,
                religion = NULL, politics = NULL
          WHERE user_id = $1`,
        [userId],
      );
      await Promise.all([
        patch({ heightCm: 171, flowPosition: 'height' }),
        patch({ politics: 'middle', flowPosition: 'politics' }),
        patch({ flowPosition: 'gender' }),
        patch({ flowPosition: 'location' }),
        patch({ religion: 'catholic' }),
        patch({ education: 'phd' }),
      ]);
      expect(await stored()).toEqual({
        flow_position: 'politics',
        height_cm: 171,
        education: 'phd',
        religion: 'catholic',
        politics: 'middle',
      });
    }
  });

  it('the progress endpoint reads the position the saves left', async () => {
    const progress = await request(server)
      .get('/me/profile/progress')
      .set('Authorization', `Bearer ${token}`)
      .expect(200);
    expect(progress.body.flowPosition).toBe('politics');
  });
});
