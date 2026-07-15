import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { CheckInsService } from './../src/modules/check-ins/check-ins.service';
import { MatchingService } from './../src/modules/matching/matching.service';
import {
  Profile,
  ProfileVerificationStatus,
} from './../src/modules/profiles/entities/profile.entity';
import { ModerationService } from './../src/modules/safety/moderation.service';
import { SafetyService } from './../src/modules/safety/safety.service';
import {
  ModerationStanding,
  ModerationSubjectType,
} from './../src/modules/safety/util/safety';
import { VerificationService } from './../src/modules/safety/verification.service';
import {
  User,
  UserRole,
  UserStatus,
} from './../src/modules/users/entities/user.entity';

const PHONES = {
  a: '+491707770001',
  b: '+491707770002',
  c: '+491707770003',
  v: '+491707770004',
  admin: '+491707770005',
};

const BRANDENBURG_GATE = { lat: 52.5163, lng: 13.3777 };
const REICHSTAG = { lat: 52.5186, lng: 13.3761 }; // ~270 m away

const hoursFromNow = (h: number) =>
  new Date(Date.now() + h * 3_600_000).toISOString();

describe('Safety (e2e)', () => {
  let app: INestApplication;
  let safety: SafetyService;
  let moderation: ModerationService;
  let verification: VerificationService;
  let matching: MatchingService;
  let checkIns: CheckInsService;
  let users: Repository<User>;
  let profiles: Repository<Profile>;

  const id: Record<string, string> = {};

  const seedUser = async (
    phone: string,
    role = UserRole.User,
    status = UserStatus.Active,
  ) => (await users.save(users.create({ phone, role, status }))).id;

  const seedProfile = (userId: string, displayName: string) =>
    profiles.save(profiles.create({ userId, displayName, isVisible: true }));

  const seedCheckIn = (userId: string, point: { lat: number; lng: number }) =>
    checkIns.create(userId, {
      availabilityStart: hoursFromNow(0),
      availabilityEnd: hoursFromNow(3),
      preparationMinutes: 20,
      latitude: point.lat,
      longitude: point.lng,
    });

  const discoveredIds = async (userId: string) =>
    (await matching.getDiscovery(userId)).map((f) => f.profile.userId);

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication();
    await app.init();

    safety = app.get(SafetyService);
    moderation = app.get(ModerationService);
    verification = app.get(VerificationService);
    matching = app.get(MatchingService);
    checkIns = app.get(CheckInsService);
    users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    profiles = app.get<Repository<Profile>>(getRepositoryToken(Profile), {
      strict: false,
    });

    await users.delete({ phone: In(Object.values(PHONES)) });

    id.a = await seedUser(PHONES.a);
    id.b = await seedUser(PHONES.b);
    id.c = await seedUser(PHONES.c);
    id.v = await seedUser(PHONES.v);
    id.admin = await seedUser(PHONES.admin, UserRole.Admin);

    await seedProfile(id.a, 'Ana');
    await seedProfile(id.b, 'Bea');
    await seedProfile(id.c, 'Cara');
    await seedProfile(id.v, 'Vera');

    // A, B, C all available now near the same spot.
    await seedCheckIn(id.a, BRANDENBURG_GATE);
    await seedCheckIn(id.b, REICHSTAG);
    await seedCheckIn(id.c, REICHSTAG);
  });

  afterAll(async () => {
    await users.delete({ phone: In(Object.values(PHONES)) });
    await app.close();
  });

  it('hides a blocked user from discovery in BOTH directions', async () => {
    await safety.block(id.a, id.b);

    expect(await discoveredIds(id.a)).not.toContain(id.b); // A no longer sees B
    expect(await discoveredIds(id.b)).not.toContain(id.a); // B no longer sees A
    expect(await discoveredIds(id.a)).toContain(id.c); // unrelated user unaffected
  });

  it('restores visibility after unblocking', async () => {
    await safety.unblock(id.a, id.b);
    expect(await discoveredIds(id.a)).toContain(id.b);
  });

  it('refuses to let a user block themselves', async () => {
    await expect(safety.block(id.a, id.a)).rejects.toThrow();
  });

  it('is idempotent — blocking twice yields the same block', async () => {
    const first = await safety.block(id.a, id.c);
    const second = await safety.block(id.a, id.c);
    expect(second.id).toBe(first.id);
  });

  it('rejects a like to a user blocked in either direction', async () => {
    // (A still blocks C from the previous test.)
    await expect(
      matching.sendLike(id.a, { targetUserId: id.c }),
    ).rejects.toThrow();
    await safety.unblock(id.a, id.c);
  });

  it('hides a limited/banned user from discovery and blocks liking them', async () => {
    await moderation.setUserStanding(
      id.b,
      ModerationStanding.Banned,
      id.admin,
      'test ban',
    );

    expect(await discoveredIds(id.a)).not.toContain(id.b);
    await expect(
      matching.sendLike(id.a, { targetUserId: id.b }),
    ).rejects.toThrow();

    // Reinstating brings them back.
    await moderation.setUserStanding(id.b, ModerationStanding.Active, id.admin);
    expect(await discoveredIds(id.a)).toContain(id.b);
  });

  it('records every standing change in the append-only history', async () => {
    const history = await moderation.history(ModerationSubjectType.User, id.b);
    // Banned then re-activated = at least two recorded changes.
    expect(history.length).toBeGreaterThanOrEqual(2);
    expect(history[0].toStanding).toBe(ModerationStanding.Active); // newest first
  });

  it('lists users by standing and assembles a user safety context for staff', async () => {
    await moderation.setUserStanding(
      id.c,
      ModerationStanding.Limited,
      id.admin,
    );
    const limited = await moderation.listUsersByStanding(
      ModerationStanding.Limited,
    );
    expect(limited.map((u) => u.id)).toContain(id.c);

    await safety.block(id.b, id.c); // B blocks C, so C is "blockedBy" B
    expect(await safety.blockedBy(id.c)).toContain(id.b);

    await moderation.setUserStanding(id.c, ModerationStanding.Active, id.admin);
    await safety.unblock(id.b, id.c);
  });

  it('verifies a user via the stub provider and marks the profile verified', async () => {
    const attempt = await verification.submit(id.v, true);
    expect(attempt.outcome).toBe('verified');
    expect(attempt.rawDeletedAt).not.toBeNull(); // raw discarded after checking

    const profile = await profiles.findOne({ where: { userId: id.v } });
    expect(profile?.verificationStatus).toBe(
      ProfileVerificationStatus.Verified,
    );

    const latest = await verification.latest(id.v);
    expect(latest?.id).toBe(attempt.id);
  });
});
