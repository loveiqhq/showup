import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import request from 'supertest';
import { Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { ProfilePhoto } from './../src/modules/profiles/entities/profile-photo.entity';
import { Profile } from './../src/modules/profiles/entities/profile.entity';
import { User, UserRole } from './../src/modules/users/entities/user.entity';

// Requires the Docker DB. Uses a distinct phone from the auth e2e to avoid collisions.
const PHONE = '+491701234999';

describe('Profiles (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let token: string;
  let userId: string;
  let users: Repository<User>;
  let photoId: string;

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

    // Log in via phone to obtain a token + user id.
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

    // Reset to a clean, deterministic state for re-runs.
    users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    const profiles = app.get<Repository<Profile>>(getRepositoryToken(Profile), {
      strict: false,
    });
    const photos = app.get<Repository<ProfilePhoto>>(
      getRepositoryToken(ProfilePhoto),
      { strict: false },
    );
    await photos.delete({ userId });
    await profiles.delete({ userId });
    await users.update({ id: userId }, { role: UserRole.User });
  });

  afterAll(async () => {
    await app.close();
  });

  it('GET /me/profile creates and returns an empty profile', async () => {
    const res = await request(server)
      .get('/me/profile')
      .set('Authorization', bearer())
      .expect(200);
    expect(res.body.id).toBeDefined();
    expect(res.body.isComplete).toBe(false);
  });

  it('PATCH /me/profile updates fields and derives age', async () => {
    const res = await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ displayName: 'Leo', dateOfBirth: '1998-04-23', gender: 'male' })
      .expect(200);
    expect(res.body.displayName).toBe('Leo');
    expect(res.body.age).toBeGreaterThanOrEqual(18);
  });

  it('rejects an under-18 date of birth', async () => {
    const year = new Date().getUTCFullYear();
    await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ dateOfBirth: `${year - 10}-01-01` })
      .expect(400);
  });

  // ── per-field visibility (SHOWUP-154) ──────────────────────────────────────
  //
  // The product decision these guard: hiding the age hides a VALUE. It must never make the user
  // less discoverable or less matchable. isVisible is asserted in every one of these because the
  // failure mode being prevented is someone "fixing" a missing field by reaching for that flag.

  it('a profile starts with nothing hidden', async () => {
    const res = await request(server)
      .get('/me/profile')
      .set('Authorization', bearer())
      .expect(200);
    expect(res.body.hiddenFields).toEqual([]);
  });

  it('checking the box hides age and leaves discovery visibility untouched', async () => {
    const res = await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ hiddenFields: ['age'] })
      .expect(200);
    expect(res.body.hiddenFields).toEqual(['age']);
    expect(res.body.isVisible).toBe(true);
  });

  it('a hidden age is still returned on your own profile', async () => {
    // The flag is presentation metadata for whoever renders someone else's profile, not redaction
    // of your own data — and the age still reaches matching either way.
    const res = await request(server)
      .get('/me/profile')
      .set('Authorization', bearer())
      .expect(200);
    expect(res.body.hiddenFields).toEqual(['age']);
    expect(res.body.age).toBeGreaterThanOrEqual(18);
  });

  it('unchecking the box removes age again', async () => {
    const res = await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ hiddenFields: [] })
      .expect(200);
    expect(res.body.hiddenFields).toEqual([]);
    expect(res.body.isVisible).toBe(true);
  });

  it('hiding a field does not change isVisible in either direction', async () => {
    // Go invisible deliberately, then toggle the age flag both ways. isVisible must survive
    // untouched, which is the assertion that would fail if the two were ever wired together.
    await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ isVisible: false })
      .expect(200);

    const hidden = await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ hiddenFields: ['age'] })
      .expect(200);
    expect(hidden.body.isVisible).toBe(false);

    const shown = await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ hiddenFields: [] })
      .expect(200);
    expect(shown.body.isVisible).toBe(false);

    // restore, so later tests see the default
    await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ isVisible: true })
      .expect(200);
  });

  it('setting isVisible does not disturb the hidden set', async () => {
    await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ hiddenFields: ['age'] })
      .expect(200);
    const res = await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ isVisible: true })
      .expect(200);
    expect(res.body.hiddenFields).toEqual(['age']);
  });

  it('rejects a field_id with no control behind it', async () => {
    await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ hiddenFields: ['height'] })
      .expect(400);
  });

  it('rejects isVisible smuggled in as a hidden field', async () => {
    await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ hiddenFields: ['isVisible'] })
      .expect(400);
  });

  it('rejects an absurdly long array', async () => {
    // The cap is a payload guard, not a vocabulary one, so it has to be asserted separately from
    // the allow-list -- every entry here is a VALID value, and it is the length alone that fails.
    await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ hiddenFields: Array(65).fill('age') })
      .expect(400);
  });

  it('de-duplicates a repeated value', async () => {
    const res = await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ hiddenFields: ['age', 'age'] })
      .expect(200);
    expect(res.body.hiddenFields).toEqual(['age']);

    // leave the profile in its default state for the tests that follow
    await request(server)
      .patch('/me/profile')
      .set('Authorization', bearer())
      .send({ hiddenFields: [] })
      .expect(200);
  });

  it('POST /me/photos uploads a photo (moderation pending)', async () => {
    const res = await request(server)
      .post('/me/photos')
      .set('Authorization', bearer())
      .attach('file', Buffer.from([0xff, 0xd8, 0xff, 0xd9]), {
        filename: 'p.jpg',
        contentType: 'image/jpeg',
      })
      .expect(201);
    expect(res.body.moderationStatus).toBe('pending');
    expect(res.body.url).toContain('/uploads/');
    photoId = res.body.id;
  });

  it('profile is not complete with fewer than 4 photos', async () => {
    const res = await request(server)
      .get('/me/profile')
      .set('Authorization', bearer())
      .expect(200);
    expect(res.body.isComplete).toBe(false);
  });

  it('profile is complete with name, DOB and 4 photos', async () => {
    // One photo was uploaded above; add three more to reach the minimum of 4.
    for (let i = 0; i < 3; i++) {
      await request(server)
        .post('/me/photos')
        .set('Authorization', bearer())
        .attach('file', Buffer.from([0xff, 0xd8, 0xff, 0xd9]), {
          filename: `p${i}.jpg`,
          contentType: 'image/jpeg',
        })
        .expect(201);
    }
    const res = await request(server)
      .get('/me/profile')
      .set('Authorization', bearer())
      .expect(200);
    expect(res.body.isComplete).toBe(true);
  });

  it('rejects a non-image upload', async () => {
    await request(server)
      .post('/me/photos')
      .set('Authorization', bearer())
      .attach('file', Buffer.from('hello'), {
        filename: 'note.txt',
        contentType: 'text/plain',
      })
      .expect(415);
  });

  it('DELETE /me/photos/:id removes a photo and drops below complete', async () => {
    await request(server)
      .delete(`/me/photos/${photoId}`)
      .set('Authorization', bearer())
      .expect(204);
    // Four photos were uploaded; deleting one leaves three — back under the minimum.
    const list = await request(server)
      .get('/me/photos')
      .set('Authorization', bearer())
      .expect(200);
    expect(list.body).toHaveLength(3);
    const profile = await request(server)
      .get('/me/profile')
      .set('Authorization', bearer())
      .expect(200);
    expect(profile.body.isComplete).toBe(false);
  });

  it('POST /me/profile/verification sets status to pending', async () => {
    const res = await request(server)
      .post('/me/profile/verification')
      .set('Authorization', bearer())
      .expect(202);
    expect(res.body.verificationStatus).toBe('pending');
  });

  it('admin endpoint is forbidden for a normal user', async () => {
    await request(server)
      .patch(`/admin/profiles/${userId}/verification`)
      .set('Authorization', bearer())
      .send({ status: 'verified' })
      .expect(403);
  });

  it('an admin can set verification status', async () => {
    await users.update({ id: userId }, { role: UserRole.Admin });
    const res = await request(server)
      .patch(`/admin/profiles/${userId}/verification`)
      .set('Authorization', bearer())
      .send({ status: 'verified' })
      .expect(200);
    expect(res.body.verificationStatus).toBe('verified');
  });
});
