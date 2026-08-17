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
// For the fair-venue test: a second person ~2.5 km east, with a venue beside each and one between.
const ALEXANDERPLATZ = { lat: 52.5219, lng: 13.4132 };
const TV_TOWER = { lat: 52.5208, lng: 13.4094 }; // beside Alexanderplatz
const BEBELPLATZ = { lat: 52.5169, lng: 13.3958 }; // roughly midway between the two people

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

  it('picks the venue that is fairest to both people, not the one nearest to either', async () => {
    // Start from a clean venue table: the test above seeds a venue at the Reichstag, which would
    // otherwise tie with this test's own west-side venue and make "nearest" ambiguous.
    await dataSource.query(`DELETE FROM venues`);

    // Two people ~2.5 km apart, with a venue beside each of them and one in between.
    const addVenue = async (name: string, p: { lat: number; lng: number }) => {
      const [{ id }] = await dataSource.query<Array<{ id: string }>>(
        `INSERT INTO venues (name, location)
         VALUES ($1, ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography)
         RETURNING id`,
        [name, p.lng, p.lat],
      );
      return id;
    };

    const nearWest = await addVenue('E2E Fair West', REICHSTAG);
    const nearEast = await addVenue('E2E Fair East', TV_TOWER);
    const between = await addVenue('E2E Fair Between', BEBELPLATZ);

    // The old behaviour, kept for the one-sided fallback: nearest to a single point favours
    // whoever that point belongs to, which is exactly the unfairness being fixed.
    expect((await locationService.nearestVenue(BRANDENBURG_GATE))?.id).toBe(
      nearWest,
    );

    const fair = await locationService.fairestVenue(
      BRANDENBURG_GATE,
      ALEXANDERPLATZ,
    );

    expect(fair).not.toBeNull();
    expect(fair!.id).toBe(between);
    expect(fair!.id).not.toBe(nearWest);
    expect(fair!.id).not.toBe(nearEast);

    // The requirement: each person travels roughly half the distance between them, rather than one
    // travelling the whole way. Checked against the actual separation, not a hard-coded number.
    const apart = await locationService.distanceMeters(
      BRANDENBURG_GATE,
      ALEXANDERPLATZ,
    );
    const half = apart / 2;
    for (const own of [fair!.distanceMetersA, fair!.distanceMetersB]) {
      expect(own).toBeGreaterThan(half * 0.7);
      expect(own).toBeLessThan(half * 1.4);
    }

    // And the two distances are close to each other — the even split itself.
    const gap = Math.abs(fair!.distanceMetersA - fair!.distanceMetersB);
    expect(gap).toBeLessThan(apart * 0.2);

    // The longer journey also beats either one-sided option, which is what we replaced.
    const worst = Math.max(fair!.distanceMetersA, fair!.distanceMetersB);
    expect(worst).toBeLessThan(
      await locationService.distanceToVenueMeters(ALEXANDERPLATZ, nearWest),
    );
    expect(worst).toBeLessThan(
      await locationService.distanceToVenueMeters(BRANDENBURG_GATE, nearEast),
    );
  });

  it('prefers the closer option when two venues are equally even', async () => {
    await dataSource.query(`DELETE FROM venues`);

    // Deliberately symmetric so both venues are EXACTLY equidistant (gap 0.0 m): the two people sit
    // at the same latitude either side of longitude 13.40, and both venues sit on that line. Only
    // the tie-break can decide between them, which is the whole point — an earlier version of this
    // test used off-centre points, so the nearer venue won on evenness and the test would have
    // passed even with the tie-break removed.
    const west = { lat: 52.52, lng: 13.38 };
    const east = { lat: 52.52, lng: 13.42 }; // ~2.7 km apart
    const onTheLine = 13.4;

    const addOnLine = async (name: string, lat: number) => {
      const [{ id }] = await dataSource.query<Array<{ id: string }>>(
        `INSERT INTO venues (name, location)
         VALUES ($1, ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography) RETURNING id`,
        [name, onTheLine, lat],
      );
      return id;
    };

    // The FAR one is inserted first on purpose. Both tie on evenness, so without the tie-break the
    // database is free to return either — and it would naturally return this one. Inserting it first
    // means the test genuinely fails if the tie-break is ever dropped.
    await addOnLine('E2E Even Far', 52.45); // ~7.9 km each — just as even, much worse
    const near = await addOnLine('E2E Even Near', 52.52); // ~1.35 km each

    const fair = await locationService.fairestVenue(west, east);
    expect(fair?.id).toBe(near);
    expect(
      Math.abs(fair!.distanceMetersA - fair!.distanceMetersB),
    ).toBeLessThan(5);
    expect(Math.max(fair!.distanceMetersA, fair!.distanceMetersB)).toBeLessThan(
      2000,
    );
  });

  it('does not send both people far away just to make the split exactly even', async () => {
    await dataSource.query(`DELETE FROM venues`);
    const west = { lat: 52.52, lng: 13.38 };
    const east = { lat: 52.52, lng: 13.42 }; // ~2.7 km apart

    // Perfectly equidistant from both — and 7.9 km away from each of them.
    await dataSource.query(
      `INSERT INTO venues (name, location)
       VALUES ($1, ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography)`,
      ['E2E Far But Even', 13.4, 52.45],
    );
    // Close to both (1.29 km / 1.42 km) but 135 m off perfectly even.
    const [{ id: closeEnough }] = await dataSource.query<Array<{ id: string }>>(
      `INSERT INTO venues (name, location)
       VALUES ($1, ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography) RETURNING id`,
      ['E2E Near Nearly Even', 13.399, 52.52],
    );

    // Ranking purely on evenness would choose the far one, because 0 m of imbalance sorts ahead of
    // 135 m. Nobody wants a fair journey; they want a short one that is also fair.
    const fair = await locationService.fairestVenue(west, east);
    expect(fair?.id).toBe(closeEnough);
    expect(Math.max(fair!.distanceMetersA, fair!.distanceMetersB)).toBeLessThan(
      2000,
    );
  });

  it('still proposes a venue when nothing falls inside the search area', async () => {
    await dataSource.query(`DELETE FROM venues`);
    // Two people close together in Berlin, with the only venue in Cologne — far outside any sensible
    // search radius. A suggestion is better than none, so the area limit must not swallow it.
    const [{ id: onlyOne }] = await dataSource.query<Array<{ id: string }>>(
      `INSERT INTO venues (name, location)
       VALUES ($1, ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography) RETURNING id`,
      ['E2E Only Cologne', COLOGNE.lng, COLOGNE.lat],
    );

    const fair = await locationService.fairestVenue(
      BRANDENBURG_GATE,
      ALEXANDERPLATZ,
    );
    expect(fair?.id).toBe(onlyOne);
  });

  it('returns null when there are no venues at all', async () => {
    await dataSource.query(`DELETE FROM venues`);
    expect(
      await locationService.fairestVenue(BRANDENBURG_GATE, ALEXANDERPLATZ),
    ).toBeNull();
  });
});
