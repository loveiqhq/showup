import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import request from 'supertest';
import { Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { ProfilePrompt } from './../src/modules/profiles/entities/profile-prompt.entity';

/**
 * The three rules a prompt has to obey, checked against a real database (SHOWUP-158).
 *
 * The clients enforce the same three. That is not duplication: the client enforces them so the
 * user is never told off after the fact, and the server enforces them because a client is a thing
 * somebody can replace. These tests are the half that cannot be bypassed.
 *
 * Requires the Docker DB, like every other e2e here. A distinct phone from the other suites so the
 * accounts cannot collide.
 */
const PHONE = '+491701234997';

/**
 * Topic ids as the tracking registry's 17 dictates them, not as this repo once invented them.
 *
 * Named here rather than inlined so the set is checkable at a glance: `first_date_usually` and
 * `cross_town_for` are two of the six corrected on 16 September 2026, and `hot_take` is one of the
 * nine that always agreed. A test that used the old spellings would still pass -- the route stores
 * whatever id it is given -- which is exactly why it has to be asserted rather than assumed.
 */
const TOPIC_A = 'first_date_usually';
const TOPIC_B = 'hot_take';
const TOPIC_C = 'cross_town_for';

describe('Prompts (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let token: string;
  let userId: string;
  let prompts: Repository<ProfilePrompt>;

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

    prompts = moduleRef.get<Repository<ProfilePrompt>>(
      getRepositoryToken(ProfilePrompt),
    );
  });

  beforeEach(async () => {
    await prompts.delete({ userId });
  });

  afterAll(async () => {
    await prompts.delete({ userId });
    await app.close();
  });

  it('starts empty', async () => {
    const res = await request(server)
      .get('/me/prompts')
      .set('Authorization', bearer())
      .expect(200);
    expect(res.body).toEqual([]);
  });

  it('saves a prompt and reads it back', async () => {
    const res = await request(server)
      .put(`/me/prompts/${TOPIC_A}`)
      .set('Authorization', bearer())
      .send({ answer: 'Talk about anything real.' })
      .expect(200);
    expect(res.body.topicId).toBe(TOPIC_A);
    expect(res.body.answer).toBe('Talk about anything real.');
    expect(res.body.position).toBe(0);

    const list = await request(server)
      .get('/me/prompts')
      .set('Authorization', bearer())
      .expect(200);
    expect(list.body).toHaveLength(1);
  });

  it('is idempotent on the topic: saving twice overwrites rather than adding', async () => {
    // What "Save overwrites" means on the write sheet, and what stops a double-tap writing two
    // rows for one question.
    await request(server)
      .put(`/me/prompts/${TOPIC_A}`)
      .set('Authorization', bearer())
      .send({ answer: 'First answer.' })
      .expect(200);
    const second = await request(server)
      .put(`/me/prompts/${TOPIC_A}`)
      .set('Authorization', bearer())
      .send({ answer: 'Second answer.' })
      .expect(200);

    expect(second.body.answer).toBe('Second answer.');
    const list = await request(server)
      .get('/me/prompts')
      .set('Authorization', bearer())
      .expect(200);
    expect(list.body).toHaveLength(1);
  });

  it('puts a new prompt at the bottom of the list', async () => {
    // "A new card appears at the bottom, not the top — the reading order stays chronological."
    for (const topic of [TOPIC_A, TOPIC_B, TOPIC_C]) {
      await request(server)
        .put(`/me/prompts/${topic}`)
        .set('Authorization', bearer())
        .send({ answer: `Answer for ${topic}.` })
        .expect(200);
    }
    const list = await request(server)
      .get('/me/prompts')
      .set('Authorization', bearer())
      .expect(200);
    expect(list.body.map((p: { topicId: string }) => p.topicId)).toEqual([
      TOPIC_A,
      TOPIC_B,
      TOPIC_C,
    ]);
    expect(list.body.map((p: { position: number }) => p.position)).toEqual([
      0, 1, 2,
    ]);
  });

  it('refuses a fourth topic', async () => {
    for (const topic of [TOPIC_A, TOPIC_B, TOPIC_C]) {
      await request(server)
        .put(`/me/prompts/${topic}`)
        .set('Authorization', bearer())
        .send({ answer: 'Something.' })
        .expect(200);
    }
    await request(server)
      .put('/me/prompts/weird_habit')
      .set('Authorization', bearer())
      .send({ answer: 'A fourth.' })
      .expect(400);
  });

  it('still lets an existing prompt be edited at the cap', async () => {
    // Refusing this would mean a user with three prompts could never fix a typo.
    for (const topic of [TOPIC_A, TOPIC_B, TOPIC_C]) {
      await request(server)
        .put(`/me/prompts/${topic}`)
        .set('Authorization', bearer())
        .send({ answer: 'Something.' })
        .expect(200);
    }
    const res = await request(server)
      .put('/me/prompts/hot_take')
      .set('Authorization', bearer())
      .send({ answer: 'A better something.' })
      .expect(200);
    expect(res.body.answer).toBe('A better something.');
  });

  it('refuses an answer over 160 characters', async () => {
    await request(server)
      .put(`/me/prompts/${TOPIC_A}`)
      .set('Authorization', bearer())
      .send({ answer: 'x'.repeat(161) })
      .expect(400);
  });

  it('accepts exactly 160', async () => {
    const res = await request(server)
      .put(`/me/prompts/${TOPIC_A}`)
      .set('Authorization', bearer())
      .send({ answer: 'x'.repeat(160) })
      .expect(200);
    expect(res.body.answer).toHaveLength(160);
  });

  it('refuses a whitespace-only answer', async () => {
    // `MinLength(1)` cannot see this — " " has length 1. Whitespace-only counts as empty on both
    // clients and it has to mean the same thing here.
    await request(server)
      .put(`/me/prompts/${TOPIC_A}`)
      .set('Authorization', bearer())
      .send({ answer: '   ' })
      .expect(400);
  });

  it('trims the answer it stores', async () => {
    const res = await request(server)
      .put(`/me/prompts/${TOPIC_A}`)
      .set('Authorization', bearer())
      .send({ answer: '  Talk about anything real.  ' })
      .expect(200);
    expect(res.body.answer).toBe('Talk about anything real.');
  });

  it('deletes a prompt and closes the gap it left', async () => {
    for (const topic of [TOPIC_A, TOPIC_B, TOPIC_C]) {
      await request(server)
        .put(`/me/prompts/${topic}`)
        .set('Authorization', bearer())
        .send({ answer: 'Something.' })
        .expect(200);
    }
    await request(server)
      .delete('/me/prompts/hot_take')
      .set('Authorization', bearer())
      .expect(204);

    const list = await request(server)
      .get('/me/prompts')
      .set('Authorization', bearer())
      .expect(200);
    // 0, 2 would be a list whose next insert collides.
    expect(list.body.map((p: { position: number }) => p.position)).toEqual([
      0, 1,
    ]);
    expect(list.body.map((p: { topicId: string }) => p.topicId)).toEqual([
      'first_date_usually',
      'cross_town_for',
    ]);
  });

  it('deleting something that is not there is not an error', async () => {
    await request(server)
      .delete('/me/prompts/never_written')
      .set('Authorization', bearer())
      .expect(204);
  });

  it('refuses everything without a token', async () => {
    await request(server).get('/me/prompts').expect(401);
    await request(server)
      .put(`/me/prompts/${TOPIC_A}`)
      .send({ answer: 'x' })
      .expect(401);
    await request(server).delete(`/me/prompts/${TOPIC_A}`).expect(401);
  });

  it('stores the registry id verbatim, including the six that were corrected', async () => {
    // The ids are the dictionary's, and the route has no opinion about them -- it stores what it
    // is given. So this asserts the one thing that can actually drift: that the client and the
    // database agree on the spelling the analytics warehouse will later join on.
    for (const topic of [TOPIC_A, TOPIC_C]) {
      const res = await request(server)
        .put(`/me/prompts/${topic}`)
        .set('Authorization', bearer())
        .send({ answer: 'Something.' })
        .expect(200);
      expect(res.body.topicId).toBe(topic);
    }
    const list = await request(server)
      .get('/me/prompts')
      .set('Authorization', bearer())
      .expect(200);
    expect(list.body.map((p: { topicId: string }) => p.topicId)).toEqual([
      'first_date_usually',
      'cross_town_for',
    ]);
  });

  it('reports the prompt count on the profile progress read', async () => {
    const before = await request(server)
      .get('/me/profile/progress')
      .set('Authorization', bearer())
      .expect(200);
    expect(before.body.promptCount).toBe(0);
    // The flow's resume rule reads this; it has to move when a prompt is written.
    expect(before.body).toHaveProperty('displayName');
    expect(before.body).toHaveProperty('emailVerified');
    expect(before.body).toHaveProperty('hasDateOfBirth');
    expect(before.body).toHaveProperty('photoCount');

    await request(server)
      .put(`/me/prompts/${TOPIC_A}`)
      .set('Authorization', bearer())
      .send({ answer: 'Something.' })
      .expect(200);

    const after = await request(server)
      .get('/me/profile/progress')
      .set('Authorization', bearer())
      .expect(200);
    expect(after.body.promptCount).toBe(1);
  });
});
