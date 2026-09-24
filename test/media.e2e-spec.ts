import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import request from 'supertest';
import { Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { ProfileMedia } from './../src/modules/profiles/entities/profile-media.entity';

/**
 * The rules a recording has to obey, checked against a real database (SHOWUP-161).
 *
 * The clients enforce the caps too -- the take stops itself at 10 and 15 seconds. That is not
 * duplication: the client stops it so the user is never cut off mid-word, and the server refuses
 * it because a client is a thing somebody can replace. These are the half that cannot be bypassed.
 *
 * A distinct phone from every other suite so the accounts cannot collide.
 */
const PHONE = '+491701234994';

/** Section 20 ids. Never display strings -- that is the rule these tests exist to hold. */
const PROMPT_VIDEO = 'relaxed_and_happy';
const PROMPT_VOICE = 'relaxing_sound';
const PROMPT_OWN = 'own_idea';

/** The cold-start previews section 20 registers, which an account with no ranking must be given. */
const COLD_START_VIDEO = 'relaxed_and_happy';
const COLD_START_VOICE = 'relaxing_sound';

const videoBytes = () => Buffer.alloc(2048, 7);
const voiceBytes = () => Buffer.alloc(1024, 3);

describe('Media (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let token: string;
  let userId: string;
  let media: Repository<ProfileMedia>;

  const bearer = () => `Bearer ${token}`;

  const postVideo = (durationMs = 9_400, promptId = PROMPT_VIDEO) =>
    request(server)
      .post('/me/media')
      .set('Authorization', bearer())
      .field('kind', 'video')
      .field('mediaPromptId', promptId)
      .field('durationMs', String(durationMs))
      .attach('file', videoBytes(), {
        filename: 'take.mp4',
        contentType: 'video/mp4',
      });

  const postVoice = (durationMs = 14_100, promptId = PROMPT_VOICE) =>
    request(server)
      .post('/me/media')
      .set('Authorization', bearer())
      .field('kind', 'voice')
      .field('mediaPromptId', promptId)
      .field('durationMs', String(durationMs))
      .attach('file', voiceBytes(), {
        filename: 'take.m4a',
        contentType: 'audio/mp4',
      });

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

    media = moduleRef.get<Repository<ProfileMedia>>(
      getRepositoryToken(ProfileMedia),
    );
  });

  beforeEach(async () => {
    await media.delete({ userId });
  });

  afterAll(async () => {
    await media.delete({ userId });
    await app.close();
  });

  it('starts empty and still names both previewed prompts', async () => {
    // The empty state is the one that MUST carry previews: the cards it draws are the empty ones.
    const res = await request(server)
      .get('/me/media')
      .set('Authorization', bearer())
      .expect(200);
    expect(res.body.items).toEqual([]);
    expect(res.body.previewVideo).toBe(COLD_START_VIDEO);
    expect(res.body.previewVoice).toBe(COLD_START_VOICE);
    expect(res.body.previewSource).toBe('fallback');
  });

  it('stores a video and reads it back by its prompt id', async () => {
    const res = await postVideo().expect(201);
    expect(res.body.kind).toBe('video');
    expect(res.body.mediaPromptId).toBe(PROMPT_VIDEO);
    expect(res.body.durationMs).toBe(9_400);
    expect(res.body.url).toEqual(expect.any(String));

    const list = await request(server)
      .get('/me/media')
      .set('Authorization', bearer())
      .expect(200);
    expect(list.body.items).toHaveLength(1);
  });

  it('holds one video and one voice note at once', async () => {
    await postVideo().expect(201);
    await postVoice().expect(201);

    const list = await request(server)
      .get('/me/media')
      .set('Authorization', bearer())
      .expect(200);
    expect(list.body.items.map((m: { kind: string }) => m.kind).sort()).toEqual(
      ['video', 'voice'],
    );
  });

  it('REPLACES rather than adds when the same slot is recorded again', async () => {
    // What `Retake` means on this screen, and what stops a retried upload over a flaky connection
    // leaving an account holding two videos with one unreachable from the UI.
    const first = await postVideo(9_400, PROMPT_VIDEO).expect(201);
    const second = await postVideo(4_200, 'comfort_snack').expect(201);

    const list = await request(server)
      .get('/me/media')
      .set('Authorization', bearer())
      .expect(200);
    expect(list.body.items).toHaveLength(1);
    expect(list.body.items[0].id).toBe(first.body.id);
    expect(list.body.items[0].mediaPromptId).toBe('comfort_snack');
    expect(list.body.items[0].durationMs).toBe(4_200);
    expect(second.body.id).toBe(first.body.id);
  });

  it('accepts a take that overshoots the cap by less than the encoder tolerance', async () => {
    // A recorder asked to stop at exactly 10.000s routinely writes a little past it. Refusing that
    // fails the one take where the user did everything right.
    await postVideo(10_400).expect(201);
  });

  it('refuses a take past the cap', async () => {
    await postVideo(12_000).expect(400);
    await postVoice(20_000).expect(400);
  });

  it('refuses a video in the voice slot', async () => {
    await request(server)
      .post('/me/media')
      .set('Authorization', bearer())
      .field('kind', 'voice')
      .field('mediaPromptId', PROMPT_VOICE)
      .field('durationMs', '5000')
      .attach('file', videoBytes(), {
        filename: 'take.mp4',
        contentType: 'video/mp4',
      })
      .expect(415);
  });

  it('refuses a prompt id that is not in the registry', async () => {
    // Including a DISPLAY STRING, which is the mistake the id exists to prevent.
    await postVideo(5_000, 'not_a_prompt').expect(400);
    await postVideo(5_000, 'What my ideal sunny morning looks like').expect(
      400,
    );
  });

  it('accepts own_idea, the escape hatch', async () => {
    const res = await postVoice(6_000, PROMPT_OWN).expect(201);
    expect(res.body.mediaPromptId).toBe(PROMPT_OWN);
  });

  it('deletes one slot and leaves the other', async () => {
    const video = await postVideo().expect(201);
    await postVoice().expect(201);

    await request(server)
      .delete(`/me/media/${video.body.id}`)
      .set('Authorization', bearer())
      .expect(204);

    const list = await request(server)
      .get('/me/media')
      .set('Authorization', bearer())
      .expect(200);
    expect(list.body.items).toHaveLength(1);
    expect(list.body.items[0].kind).toBe('voice');
  });

  it('refuses an unauthenticated read', async () => {
    await request(server).get('/me/media').expect(401);
  });
});
