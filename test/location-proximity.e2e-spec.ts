import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { DataSource, In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { CheckInsService } from './../src/modules/check-ins/check-ins.service';
import { LocationService } from './../src/modules/location/location.service';
import { User, UserStatus } from './../src/modules/users/entities/user.entity';

// Distinct phone range so this suite never collides with the others. Users are seeded directly
// via the repository (not the throttled /auth flow), so the suite is deterministic in parallel.
const PHONES = {
  a: '+491705550001',
  b: '+491705550002',
  c: '+491705550003',
  d: '+491705550004',
  s: '+491705550005',
};

// Berlin landmarks: A (viewer) and the venue at the Reichstag; B/D/S near it; C in Cologne (~477 km).
const BRANDENBURG_GATE = { lat: 52.5163, lng: 13.3777 };
const REICHSTAG = { lat: 52.5186, lng: 13.3761 }; // ~270 m from Brandenburg Gate
const COLOGNE = { lat: 50.9413, lng: 6.9583 };

const hoursFromNow = (h: number) =>
  new Date(Date.now() + h * 3_600_000).toISOString();

describe('Location & proximity (e2e)', () => {
  let app: INestApplication;
  let checkInsService: CheckInsService;
  let locationService: LocationService;
  let users: Repository<User>;
  let dataSource: DataSource;

  const ids: Record<string, string> = {};

  const seedUser = async (phone: string): Promise<string> => {
    const user = await users.save(
      users.create({ phone, status: UserStatus.Active }),
    );
    return user.id;
  };

  const seedCheckIn = (
    userId: string,
    point: { lat: number; lng: number },
    startIso: string,
    endIso: string,
  ) =>
    checkInsService.create(userId, {
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

    checkInsService = app.get(CheckInsService);
    locationService = app.get(LocationService);
    users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    dataSource = app.get(DataSource);

    // Clean slate — deleting the users cascades their check-ins.
    await users.delete({ phone: In(Object.values(PHONES)) });

    ids.a = await seedUser(PHONES.a);
    ids.b = await seedUser(PHONES.b);
    ids.c = await seedUser(PHONES.c);
    ids.d = await seedUser(PHONES.d);
    ids.s = await seedUser(PHONES.s);

    // A (viewer) and B: near + active now. C: far + active. D: near but window is in the future.
    // S: near + active, then the account is suspended.
    await seedCheckIn(ids.a, REICHSTAG, hoursFromNow(0), hoursFromNow(3));
    await seedCheckIn(ids.b, REICHSTAG, hoursFromNow(0), hoursFromNow(3));
    await seedCheckIn(ids.c, COLOGNE, hoursFromNow(0), hoursFromNow(3));
    await seedCheckIn(ids.d, REICHSTAG, hoursFromNow(2), hoursFromNow(5));
    await seedCheckIn(ids.s, REICHSTAG, hoursFromNow(0), hoursFromNow(3));
    await users.update({ id: ids.s }, { status: UserStatus.Suspended });
  });

  afterAll(async () => {
    await dataSource.query(`DELETE FROM venues WHERE name = $1`, [
      'E2E Reichstag Venue',
    ]);
    await users.delete({ phone: In(Object.values(PHONES)) });
    await app.close();
  });

  const nearby = (
    overrides: Partial<
      Parameters<CheckInsService['findNearbyAvailable']>[0]
    > = {},
  ) =>
    checkInsService.findNearbyAvailable({
      center: BRANDENBURG_GATE,
      radiusMeters: 2000,
      excludeUserId: ids.a,
      ...overrides,
    });

  it('finds a nearby, available user and excludes self', async () => {
    const foundIds = (await nearby()).map((r) => r.userId);
    expect(foundIds).toContain(ids.b);
    expect(foundIds).not.toContain(ids.a); // self excluded
  });

  it('excludes users who are too far away', async () => {
    expect((await nearby()).map((r) => r.userId)).not.toContain(ids.c);
  });

  it('excludes users whose availability window is not open yet', async () => {
    expect((await nearby()).map((r) => r.userId)).not.toContain(ids.d);
  });

  it('excludes suspended accounts', async () => {
    expect((await nearby()).map((r) => r.userId)).not.toContain(ids.s);
  });

  it('excludes blocked users (Epic 12 will supply the list)', async () => {
    const foundIds = (await nearby({ blockedUserIds: [ids.b] })).map(
      (r) => r.userId,
    );
    expect(foundIds).not.toContain(ids.b);
  });

  it('returns a derived, rounded distance and the window — never coordinates', async () => {
    const b = (await nearby()).find((r) => r.userId === ids.b);
    expect(b).toBeDefined();
    expect(typeof b!.distanceMeters).toBe('number');
    expect(b!.distanceMeters).toBeGreaterThanOrEqual(0);
    expect(b!.distanceMeters).toBeLessThanOrEqual(2000);
    expect(b!.availabilityStart).toBeInstanceOf(Date);
    expect(b!.availabilityEnd).toBeInstanceOf(Date);
    // The shape must not leak coordinates.
    expect(Object.keys(b!).sort()).toEqual(
      [
        'availabilityEnd',
        'availabilityStart',
        'distanceMeters',
        'userId',
      ].sort(),
    );
  });

  it('measures distance to a venue and honours a configurable threshold', async () => {
    const [{ id: venueId }] = await dataSource.query<Array<{ id: string }>>(
      `INSERT INTO venues (name, location)
       VALUES ($1, ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography)
       RETURNING id`,
      ['E2E Reichstag Venue', REICHSTAG.lng, REICHSTAG.lat],
    );

    const meters = await locationService.distanceToVenueMeters(
      BRANDENBURG_GATE,
      venueId,
    );
    expect(meters).toBeGreaterThan(150);
    expect(meters).toBeLessThan(450);

    expect(
      await locationService.isWithinVenue(BRANDENBURG_GATE, venueId, 500),
    ).toBe(true);
    expect(
      await locationService.isWithinVenue(BRANDENBURG_GATE, venueId, 100),
    ).toBe(false);
  });
});
