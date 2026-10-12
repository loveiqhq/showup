import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { DataSource } from 'typeorm';

import { AppModule } from './../src/app.module';

/**
 * Interests, Life and Future (SHOWUP-174 to SHOWUP-176), driven through the real app against a
 * real database.
 *
 * ITS OWN FILE because of the rate limit: each e2e file builds its own app and so has its own 60
 * requests a minute, and the details file is already near it.
 *
 * As in the details file, the order is the walk itself, and the last test writes to the table
 * directly, because the DTO refuses every bad value before the CHECKs could ever be reached.
 */
const PHONE = '+491701234174';

describe('Interests, Life and Future (e2e)', () => {
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
  const position = async (): Promise<string | null> =>
    (
      await request(server)
        .get('/me/profile/progress')
        .set('Authorization', bearer())
        .expect(200)
    ).body.flowPosition;

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

  it('a fresh profile has answered none of the three screens', async () => {
    const profile = (await me()).body;
    for (const field of [
      'activity',
      'diet',
      'alcohol',
      'smoking',
      'rhythm',
      'social',
      'interests',
      'partnership',
      'family',
      'living',
      'familyAhead',
      'partnershipAhead',
      'livingAhead',
    ]) {
      expect(profile[field]).toBeNull();
    }
  });

  // ── Interests (Profile 21) ─────────────────────────────────────────────────

  it('Continue saves the answered How I live groups and the interests, in list order', async () => {
    const res = await patch({
      activity: 'active',
      alcohol: 'socially',
      interests: ['cruises', 'gym', 'reading', 'gym'],
      flowPosition: 'interests',
    }).expect(200);
    expect(res.body).toMatchObject({
      activity: 'active',
      alcohol: 'socially',
      diet: null,
      interests: ['gym', 'reading', 'cruises'],
    });
    expect(await position()).toBe('interests');
  });

  it('an unanswered group is not sent, and keeps what was saved before', async () => {
    const res = await patch({ diet: 'vegan', social: 'balanced' }).expect(200);
    expect(res.body).toMatchObject({
      activity: 'active',
      alcohol: 'socially',
      diet: 'vegan',
      social: 'balanced',
      interests: ['gym', 'reading', 'cruises'],
    });
  });

  it('refuses an eleventh interest, an empty list and a tag outside §3', async () => {
    const eleven = [
      'gym',
      'running',
      'hiking',
      'wine',
      'brunch',
      'cinema',
      'reading',
      'gaming',
      'bars',
      'camping',
      'cruises',
    ];
    await patch({ interests: eleven }).expect(400);
    await patch({ interests: [] }).expect(400);
    await patch({ interests: ['skydiving'] }).expect(400);
    await patch({ alcohol: 'daily' }).expect(400);
    // Nothing above was stored.
    expect((await me()).body.interests).toEqual(['gym', 'reading', 'cruises']);
  });

  it('ten interests are accepted -- the cap is ten, not nine', async () => {
    const ten = [
      'gym',
      'running',
      'hiking',
      'wine',
      'brunch',
      'cinema',
      'reading',
      'gaming',
      'bars',
      'camping',
    ];
    const res = await patch({ interests: ten }).expect(200);
    expect(res.body.interests).toHaveLength(10);
  });

  // ── Life (Profile 22) ──────────────────────────────────────────────────────

  it('Continue saves all three questions, an unanswered one as empty', async () => {
    const res = await patch({
      partnership: ['divorced', 'single'],
      family: [],
      living: ['living_with_children', 'living_alone'],
      flowPosition: 'life_now',
    }).expect(200);
    expect(res.body).toMatchObject({
      partnership: ['single', 'divorced'],
      family: [],
      living: ['living_alone', 'living_with_children'],
    });
  });

  it('refuses No children beside another family answer, and saves nothing', async () => {
    await patch({
      partnership: ['single'],
      family: ['no_children', 'single_parent'],
      living: [],
    }).expect(400);
    const profile = (await me()).body;
    expect(profile.family).toEqual([]);
    expect(profile.living).toEqual(['living_alone', 'living_with_children']);
  });

  it('No children alone is fine', async () => {
    const res = await patch({ family: ['no_children'] }).expect(200);
    expect(res.body.family).toEqual(['no_children']);
  });

  it('a value from the other screen is refused', async () => {
    await patch({ family: ['want_kids'] }).expect(400);
    await patch({ familyAhead: ['no_children'] }).expect(400);
  });

  // ── Future (Profile 23) ────────────────────────────────────────────────────

  it('one family vision at most; find out together stands alone', async () => {
    await patch({ familyAhead: ['want_kids', 'dont_want_kids'] }).expect(400);
    await patch({
      partnershipAhead: ['find_out_together', 'open_relationship'],
    }).expect(400);
    await patch({ livingAhead: ['find_out_together', 'expat_life'] }).expect(
      400,
    );
  });

  it('Continue saves the vision and marks profile creation complete', async () => {
    const res = await patch({
      familyAhead: ['find_out_together'],
      partnershipAhead: ['ethically_non_monogamous', 'open_relationship'],
      livingAhead: [],
      flowPosition: 'life_ahead',
    }).expect(200);
    expect(res.body).toMatchObject({
      familyAhead: ['find_out_together'],
      partnershipAhead: ['open_relationship', 'ethically_non_monogamous'],
      livingAhead: [],
    });
    expect(await position()).toBe('life_ahead');
  });

  it('going back to Interests afterwards never rewinds the position', async () => {
    await patch({ flowPosition: 'interests' }).expect(200);
    expect(await position()).toBe('life_ahead');
  });

  it('an explicit null clears nothing', async () => {
    await patch({ alcohol: null, interests: null, family: null }).expect(200);
    const profile = (await me()).body;
    expect(profile.alcohol).toBe('socially');
    expect(profile.interests).toHaveLength(10);
    expect(profile.family).toEqual(['no_children']);
  });

  // ── the database's own refusals ────────────────────────────────────────────

  it('the table refuses what the screens refuse, whatever path a value takes', async () => {
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
      expect(await attempt(`interests = '{}'`)).toBe(CHECK_VIOLATION);
      expect(
        await attempt(
          `interests = '{gym,running,hiking,wine,brunch,cinema,reading,gaming,bars,camping,cruises}'`,
        ),
      ).toBe(CHECK_VIOLATION);
      expect(await attempt(`interests = '{skydiving}'`)).toBe(NOT_IN_ENUM);
      expect(await attempt(`smoking = 'daily'`)).toBe(NOT_IN_ENUM);
      expect(await attempt(`family = '{no_children,single_parent}'`)).toBe(
        CHECK_VIOLATION,
      );
      expect(await attempt(`family = '{single_parent,co_parenting}'`)).toBe(
        'accepted',
      );
      expect(await attempt(`family_ahead = '{want_kids,dont_want_kids}'`)).toBe(
        CHECK_VIOLATION,
      );
      expect(
        await attempt(
          `partnership_ahead = '{find_out_together,open_relationship}'`,
        ),
      ).toBe(CHECK_VIOLATION);
      expect(
        await attempt(`living_ahead = '{find_out_together,move_abroad}'`),
      ).toBe(CHECK_VIOLATION);
      expect(await attempt(`living = '{}'`)).toBe('accepted');
    } finally {
      await runner.release();
    }
    expect((await me()).body.family).toEqual(['no_children']);
  });
});
