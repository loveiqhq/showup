import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { DataSource } from 'typeorm';

import { AppModule } from './../src/app.module';

/**
 * The "Share some details" answers and the saved flow position (SHOWUP-165 to SHOWUP-173), driven
 * through the real app against a real database.
 *
 * The migration's enums and CHECKs are tested too, in the last test, by writing to the table
 * directly. Through the API they can never be reached -- the DTO refuses every bad value first --
 * so a test that only sent requests would pass with the constraints missing.
 *
 * The order matters and is the walk itself: each test leaves the account where the next one starts,
 * the same way the screens do.
 */
const PHONE = '+491701234165';

describe('Profile details (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let token: string;
  let userId: string;
  let dataSource: DataSource;

  const bearer = () => `Bearer ${token}`;
  const patch = (body: object) =>
    request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send(body);
  const me = () =>
    request(server)
      .get('/me/profile')
      .set('Authorization', bearer())
      .expect(200);
  const progress = () =>
    request(server)
      .get('/me/profile/progress')
      .set('Authorization', bearer())
      .expect(200);

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

  // ── a new account has answered nothing ─────────────────────────────────────

  it('a fresh profile has no detail answers and no flow position', async () => {
    const profile = await me();
    for (const field of [
      'gender',
      'heightCm',
      'orientation',
      'datingLanguages',
      'education',
      'religion',
      'politics',
    ]) {
      expect(profile.body).toHaveProperty(field, null);
    }
    const p = await progress();
    expect(p.body).toMatchObject({
      hasGender: false,
      hasOrientation: false,
      flowPosition: null,
    });
  });

  // ── height (Profile 14) ────────────────────────────────────────────────────

  it.each([119, 231, 175.5, '175'])(
    'refuses a height of %p -- whole centimetres, 120 to 230',
    async (heightCm) => {
      await patch({ heightCm }).expect(400);
    },
  );

  it('stores a height with its flow position in one save', async () => {
    const res = await patch({ heightCm: 175, flowPosition: 'height' }).expect(
      200,
    );
    expect(res.body.heightCm).toBe(175);
    expect((await progress()).body.flowPosition).toBe('height');
  });

  it('accepts both ends of the range', async () => {
    expect((await patch({ heightCm: 120 }).expect(200)).body.heightCm).toBe(
      120,
    );
    expect((await patch({ heightCm: 230 }).expect(200)).body.heightCm).toBe(
      230,
    );
  });

  // ── gender (Profile 15) ────────────────────────────────────────────────────

  it.each(['male', 'Woman', 'nonbinary', ''])(
    'refuses %p -- only the four §1 values are stored',
    async (gender) => {
      await patch({ gender }).expect(400);
    },
  );

  it('stores a §1 gender, never a label, and progress records it', async () => {
    const res = await patch({
      gender: 'non_binary',
      flowPosition: 'gender',
    }).expect(200);
    expect(res.body.gender).toBe('non_binary');
    const p = await progress();
    expect(p.body.hasGender).toBe(true);
    expect(p.body.flowPosition).toBe('gender');
  });

  // ── orientation (Profile 16) ───────────────────────────────────────────────

  it('stores orientation, and progress records the second mandatory step', async () => {
    const res = await patch({
      orientation: 'bisexual',
      flowPosition: 'orientation',
    }).expect(200);
    expect(res.body.orientation).toBe('bisexual');
    expect((await progress()).body.hasOrientation).toBe(true);
  });

  it('refuses an orientation outside the six', async () => {
    await patch({ orientation: 'homosexual' }).expect(400);
  });

  // ── dating language (Profile 17) ───────────────────────────────────────────

  it('stores languages in list order, whatever order they were ticked in', async () => {
    const res = await patch({
      datingLanguages: ['arabic', 'german', 'french', 'german'],
      flowPosition: 'dating_language',
    }).expect(200);
    expect(res.body.datingLanguages).toEqual(['german', 'french', 'arabic']);
  });

  it('refuses an empty set -- nothing ticked is a skip, which saves nothing', async () => {
    await patch({ datingLanguages: [] }).expect(400);
    expect((await me()).body.datingLanguages).toEqual([
      'german',
      'french',
      'arabic',
    ]);
  });

  it('refuses a language outside the eight', async () => {
    await patch({ datingLanguages: ['english', 'klingon'] }).expect(400);
  });

  // ── education, religion, politics (Profiles 18-20) ─────────────────────────

  it('stores education, religion and politics as their §1 values', async () => {
    const res = await patch({
      education: 'university_degree',
      religion: 'muslim',
      politics: 'middle',
      flowPosition: 'politics',
    }).expect(200);
    expect(res.body).toMatchObject({
      education: 'university_degree',
      religion: 'muslim',
      politics: 'middle',
    });
  });

  it.each([
    ['education', 'bachelor'],
    ['religion', 'christian'],
    ['politics', 'centre'],
  ])('refuses %s %p', async (field, value) => {
    await patch({ [field]: value }).expect(400);
  });

  // ── rule 0: nothing in the flow can erase an answer ────────────────────────

  it('an explicit null clears nothing -- Skip never deletes a saved value', async () => {
    await patch({
      gender: null,
      heightCm: null,
      orientation: null,
      datingLanguages: null,
      education: null,
      religion: null,
      politics: null,
    }).expect(200);
    expect((await me()).body).toMatchObject({
      gender: 'non_binary',
      heightCm: 230,
      orientation: 'bisexual',
      datingLanguages: ['german', 'french', 'arabic'],
      education: 'university_degree',
      religion: 'muslim',
      politics: 'middle',
    });
  });

  // ── the saved flow position ────────────────────────────────────────────────

  it('never rewinds: going back to an earlier screen keeps the furthest step', async () => {
    await patch({ flowPosition: 'height' }).expect(200);
    expect((await progress()).body.flowPosition).toBe('politics');
  });

  it('refuses a position that is not a step after prompts', async () => {
    await patch({ flowPosition: 'interests' }).expect(400);
    await patch({ flowPosition: 'profile_height' }).expect(400);
  });

  // ── "show on my profile" ───────────────────────────────────────────────────

  it('every detail field can be hidden, and hiding never touches discovery', async () => {
    const hidden = [
      'height',
      'gender',
      'orientation',
      'dating_language',
      'education',
      'religion',
      'politics',
    ];
    const res = await patch({ hiddenFields: hidden }).expect(200);
    expect(res.body.hiddenFields).toEqual([...hidden].sort());
    expect(res.body.isVisible).toBe(true);
  });

  it('a hidden answer is still returned to its owner', async () => {
    expect((await me()).body.religion).toBe('muslim');
  });

  it('habits cannot be hidden yet -- its screen is not built', async () => {
    await patch({ hiddenFields: ['habits'] }).expect(400);
  });

  // ── the database's own refusals ────────────────────────────────────────────

  it('the table refuses what the screens refuse, whatever path a value takes', async () => {
    // Past the API on purpose: an admin script, a future endpoint or a hand-run fix-up must hit
    // the same wall. Each attempt runs in a transaction that is rolled back, so the account is left
    // exactly as the walk above left it. The SET clauses are constants in this file.
    const runner = dataSource.createQueryRunner();
    await runner.connect();
    const attempt = async (set: string): Promise<string> => {
      await runner.startTransaction();
      try {
        await runner.query(`UPDATE profiles SET ${set} WHERE user_id = $1`, [
          userId,
        ]);
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
      expect(await attempt('height_cm = 119')).toBe(CHECK_VIOLATION);
      expect(await attempt('height_cm = 231')).toBe(CHECK_VIOLATION);
      expect(await attempt('height_cm = 120')).toBe('accepted');
      expect(await attempt('height_cm = 230')).toBe('accepted');
      expect(await attempt(`dating_languages = '{}'`)).toBe(CHECK_VIOLATION);
      expect(await attempt(`dating_languages = '{klingon}'`)).toBe(NOT_IN_ENUM);
      expect(await attempt(`gender = 'male'`)).toBe(NOT_IN_ENUM);
      expect(await attempt(`orientation = 'homosexual'`)).toBe(NOT_IN_ENUM);
      expect(await attempt(`education = 'masters'`)).toBe(NOT_IN_ENUM);
      expect(await attempt(`religion = 'klingon'`)).toBe(NOT_IN_ENUM);
      expect(await attempt(`politics = 'centre'`)).toBe(NOT_IN_ENUM);
    } finally {
      await runner.release();
    }
    // And the rollbacks really left the walk's answers in place.
    expect((await me()).body.religion).toBe('muslim');
  });
});
