import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { CheckInsService } from './../src/modules/check-ins/check-ins.service';
import { MatchingService } from './../src/modules/matching/matching.service';
import { Profile } from './../src/modules/profiles/entities/profile.entity';
import { User, UserStatus } from './../src/modules/users/entities/user.entity';

const PHONES = {
  a: '+491706660001',
  b: '+491706660002',
  c: '+491706660003',
  d: '+491706660004',
  s: '+491706660005',
};

const BRANDENBURG_GATE = { lat: 52.5163, lng: 13.3777 };
const REICHSTAG = { lat: 52.5186, lng: 13.3761 }; // ~270 m away

const hoursFromNow = (h: number) =>
  new Date(Date.now() + h * 3_600_000).toISOString();

describe('Matching (e2e)', () => {
  let app: INestApplication;
  let matching: MatchingService;
  let checkIns: CheckInsService;
  let users: Repository<User>;
  let profiles: Repository<Profile>;

  const id: Record<string, string> = {};
  let likeAtoB: string | undefined;

  const seedUser = async (phone: string, status = UserStatus.Active) =>
    (await users.save(users.create({ phone, status }))).id;

  // Discovery only surfaces COMPLETE profiles (Epic 3/6), so the seed must set it.
  const seedProfile = (userId: string, displayName: string) =>
    profiles.save(
      profiles.create({
        userId,
        displayName,
        isVisible: true,
        isComplete: true,
      }),
    );

  const seedCheckIn = (
    userId: string,
    point: { lat: number; lng: number },
    startIso: string,
    endIso: string,
  ) =>
    checkIns.create(userId, {
      availabilityStart: startIso,
      availabilityEnd: endIso,
      preparationMinutes: 20,
      latitude: point.lat,
      longitude: point.lng,
    });

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication();
    await app.init();

    matching = app.get(MatchingService);
    checkIns = app.get(CheckInsService);
    users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    profiles = app.get<Repository<Profile>>(getRepositoryToken(Profile), {
      strict: false,
    });

    // Clean slate: deleting the users cascades their check-ins, likes, matches (and profiles).
    await users.delete({ phone: In(Object.values(PHONES)) });

    id.a = await seedUser(PHONES.a);
    id.b = await seedUser(PHONES.b);
    id.c = await seedUser(PHONES.c);
    id.d = await seedUser(PHONES.d);
    id.s = await seedUser(PHONES.s, UserStatus.Suspended);

    await seedProfile(id.a, 'Ana');
    await seedProfile(id.b, 'Bea');
    await seedProfile(id.c, 'Cara');
    await seedProfile(id.d, 'Dana');
    await seedProfile(id.s, 'Sam');

    // A, B, C available now near the same spot. D is near but only available in 2 hours.
    await seedCheckIn(id.a, BRANDENBURG_GATE, hoursFromNow(0), hoursFromNow(3));
    await seedCheckIn(id.b, REICHSTAG, hoursFromNow(0), hoursFromNow(3));
    await seedCheckIn(id.c, REICHSTAG, hoursFromNow(0), hoursFromNow(3));
    await seedCheckIn(id.d, REICHSTAG, hoursFromNow(2), hoursFromNow(5));
  });

  afterAll(async () => {
    await users.delete({ phone: In(Object.values(PHONES)) });
    await app.close();
  });

  it('discovery shows nearby, available profiles and excludes self and the unavailable', async () => {
    const feed = await matching.getDiscovery(id.a);
    const ids = feed.map((f) => f.profile.userId);
    expect(ids).toContain(id.b);
    expect(ids).toContain(id.c);
    expect(ids).not.toContain(id.a); // self
    expect(ids).not.toContain(id.d); // not available yet
    expect(feed[0].distanceMeters).toBeGreaterThanOrEqual(0);
  });

  it('a one-way like does not create a match', async () => {
    const { like, match } = await matching.sendLike(id.a, {
      targetUserId: id.b,
    });
    likeAtoB = like.id;
    expect(match).toBeUndefined();
  });

  // Checked before the mutual like below, because once a match creates a confirmed date the sender is
  // locked out of matching until they review it.
  it('a repeated like is idempotent (no error, same like)', async () => {
    const { like } = await matching.sendLike(id.a, { targetUserId: id.b });
    expect(like.id).toBe(likeAtoB);
  });

  it('discovery excludes people already liked', async () => {
    const ids = (await matching.getDiscovery(id.a)).map(
      (f) => f.profile.userId,
    );
    expect(ids).not.toContain(id.b); // already liked
    expect(ids).toContain(id.c);
  });

  it('a mutual like between available users creates a match', async () => {
    const { match } = await matching.sendLike(id.b, { targetUserId: id.a });
    expect(match).toBeDefined();

    const aMatches = (await matching.listMatches(id.a)).map((m) =>
      MatchOther(m, id.a),
    );
    const bMatches = (await matching.listMatches(id.b)).map((m) =>
      MatchOther(m, id.b),
    );
    expect(aMatches).toContain(id.b);
    expect(bMatches).toContain(id.a);
  });

  it('locks the matched people out of matching until they review the date', async () => {
    // The mutual like above auto-created a confirmed date for A and B, so neither may look further.
    await expect(
      matching.sendLike(id.a, { targetUserId: id.c }),
    ).rejects.toThrow(/review/i);
    expect(await matching.getDiscovery(id.a)).toEqual([]);
  });

  it('rejects liking yourself', async () => {
    await expect(
      matching.sendLike(id.a, { targetUserId: id.a }),
    ).rejects.toThrow();
  });

  it('rejects liking a suspended user', async () => {
    // Sent by C, not A: A is locked by the date above, which would refuse the like first and make
    // this pass for the wrong reason.
    await expect(
      matching.sendLike(id.c, { targetUserId: id.s }),
    ).rejects.toThrow();
  });

  it('a mutual like does NOT match when one person is not currently available', async () => {
    await matching.sendLike(id.d, { targetUserId: id.c }); // D is not available now
    const { match } = await matching.sendLike(id.c, { targetUserId: id.d });
    expect(match).toBeUndefined();

    const cMatchOthers = (await matching.listMatches(id.c)).map((m) =>
      MatchOther(m, id.c),
    );
    expect(cMatchOthers).not.toContain(id.d);
  });
});

// Helper: the "other" participant of a match, from a viewer's perspective.
function MatchOther(
  m: { userAId: string; userBId: string },
  viewer: string,
): string {
  return m.userAId === viewer ? m.userBId : m.userAId;
}
