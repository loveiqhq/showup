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

  it('profile is complete once it has name, DOB and a photo', async () => {
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

  it('DELETE /me/photos/:id removes the photo', async () => {
    await request(server)
      .delete(`/me/photos/${photoId}`)
      .set('Authorization', bearer())
      .expect(204);
    const res = await request(server)
      .get('/me/photos')
      .set('Authorization', bearer())
      .expect(200);
    expect(res.body).toHaveLength(0);
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
