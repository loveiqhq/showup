import { getQueueToken } from '@nestjs/bullmq';
import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { Queue } from 'bullmq';
import { In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import {
  CheckIn,
  CheckInStatus,
} from './../src/modules/check-ins/entities/check-in.entity';
import { CheckInExpiryProcessor } from './../src/modules/check-ins/jobs/check-in-expiry.processor';
import { QUEUE_CHECK_IN_EXPIRY } from './../src/queue/queue.constants';
import { RedisHealthIndicator } from './../src/queue/redis.health';
import { User } from './../src/modules/users/entities/user.entity';

jest.setTimeout(30_000);

const PHONES = { a: '+491709990001' };
const hoursAgo = (h: number) => new Date(Date.now() - h * 3_600_000);

describe('Queue infrastructure (e2e)', () => {
  let app: INestApplication;
  let redisHealth: RedisHealthIndicator;
  let processor: CheckInExpiryProcessor;
  let queue: Queue;
  let users: Repository<User>;
  let checkIns: Repository<CheckIn>;

  let checkInId: string;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication();
    await app.init();

    redisHealth = app.get(RedisHealthIndicator);
    processor = app.get(CheckInExpiryProcessor);
    queue = app.get<Queue>(getQueueToken(QUEUE_CHECK_IN_EXPIRY));
    users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    checkIns = app.get<Repository<CheckIn>>(getRepositoryToken(CheckIn), {
      strict: false,
    });

    await users.delete({ phone: In(Object.values(PHONES)) });
    const userId = (await users.save(users.create({ phone: PHONES.a }))).id;

    // A check-in whose window is already over but still marked available (needs tidying).
    const checkIn = await checkIns.save(
      checkIns.create({
        userId,
        status: CheckInStatus.Available,
        availabilityStart: hoursAgo(3),
        availabilityEnd: hoursAgo(1),
        preparationMinutes: 20,
      }),
    );
    checkInId = checkIn.id;
  });

  afterAll(async () => {
    await users.delete({ phone: In(Object.values(PHONES)) });
    await app.close();
  });

  it('connects to Redis and reports it healthy (SHOWUP-93)', async () => {
    const result = await redisHealth.isHealthy('redis');
    expect(result.redis.status).toBe('up');
  });

  it('registers the recurring check-in expiry schedule (SHOWUP-94/111)', async () => {
    const schedulers = await queue.getJobSchedulers();
    expect(schedulers.map((s) => s.key)).toContain(
      'check-in-expiry-every-minute',
    );
  });

  it('expires a past-window check-in when the job runs (SHOWUP-111)', async () => {
    await processor.process();
    const after = await checkIns.findOne({ where: { id: checkInId } });
    expect(after?.status).toBe(CheckInStatus.Expired);
  });

  it('is idempotent — running the job again changes nothing (SHOWUP-95)', async () => {
    const result = await processor.process();
    expect(result.expired).toBe(0);
    const after = await checkIns.findOne({ where: { id: checkInId } });
    expect(after?.status).toBe(CheckInStatus.Expired);
  });
});
