import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';

import { AppModule } from './../src/app.module';

// Boots the full application (incl. the database connection), so it requires the Docker stack
// to be running: `npm run db:up`. Run with: npm run test:e2e
describe('Health (e2e)', () => {
  let app: INestApplication;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication();
    await app.init();
  });

  afterAll(async () => {
    await app.close();
  });

  it('GET /health → alive', () => {
    return request(app.getHttpServer())
      .get('/health')
      .expect(200)
      .expect((res) => {
        if (res.body.status !== 'ok') {
          throw new Error(`expected status ok, got ${res.body.status}`);
        }
      });
  });

  it('GET /health/ready → database up', () => {
    return request(app.getHttpServer())
      .get('/health/ready')
      .expect(200)
      .expect((res) => {
        if (res.body.info?.database?.status !== 'up') {
          throw new Error('expected database to be up');
        }
      });
  });
});
