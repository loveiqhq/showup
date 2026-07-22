import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { DataSource, In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { CheckInsService } from './../src/modules/check-ins/check-ins.service';
import { DatesService } from './../src/modules/dates/dates.service';
import { DateEntity } from './../src/modules/dates/entities/date.entity';
import { DateStatus } from './../src/modules/dates/util/date-lifecycle';
import { MatchingService } from './../src/modules/matching/matching.service';
import { Profile } from './../src/modules/profiles/entities/profile.entity';
import { User, UserStatus } from './../src/modules/users/entities/user.entity';

const PHONES = {
  a: '+491707770001',
  b: '+491707770002',
  c: '+491707770003',
};
const VENUE_NAME = 'E2E Dates Venue';

const BRANDENBURG_GATE = { lat: 52.5163, lng: 13.3777 };
const REICHSTAG = { lat: 52.5186, lng: 13.3761 };

const hoursFromNow = (h: number) =>
  new Date(Date.now() + h * 3_600_000).toISOString();

describe('Dates (e2e)', () => {
  let app: INestApplication;
  let dates: DatesService;
  let matching: MatchingService;
  let checkIns: CheckInsService;
  let users: Repository<User>;
  let profiles: Repository<Profile>;
  let dateRepo: Repository<DateEntity>;
  let dataSource: DataSource;

  const id: Record<string, string> = {};

  const seedUser = async (phone: string) =>
    (await users.save(users.create({ phone, status: UserStatus.Active }))).id;

  const clearDates = () =>
    dateRepo
      .createQueryBuilder()
      .delete()
      .where('user_a_id IN (:...ids) OR user_b_id IN (:...ids)', {
        ids: Object.values(id),
      })
      .execute();

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication();
    await app.init();

    dates = app.get(DatesService);
    matching = app.get(MatchingService);
    checkIns = app.get(CheckInsService);
    users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    profiles = app.get<Repository<Profile>>(getRepositoryToken(Profile), {
      strict: false,
    });
    dateRepo = app.get<Repository<DateEntity>>(getRepositoryToken(DateEntity), {
      strict: false,
    });
    dataSource = app.get(DataSource);

    await users.delete({ phone: In(Object.values(PHONES)) });
    await dataSource.query(`DELETE FROM venues WHERE name = $1`, [VENUE_NAME]);

    id.a = await seedUser(PHONES.a);
    id.b = await seedUser(PHONES.b);
    id.c = await seedUser(PHONES.c);
    await profiles.save(
      profiles.create({ userId: id.a, displayName: 'Ana', isVisible: true }),
    );
    await profiles.save(
      profiles.create({ userId: id.b, displayName: 'Bea', isVisible: true }),
    );
    await profiles.save(
      profiles.create({ userId: id.c, displayName: 'Cara', isVisible: true }),
    );

    // A and B are both available now, near each other; a venue sits nearby.
    await checkIns.create(id.a, {
      availabilityStart: hoursFromNow(0),
      availabilityEnd: hoursFromNow(3),
      preparationMinutes: 20,
      latitude: BRANDENBURG_GATE.lat,
      longitude: BRANDENBURG_GATE.lng,
    });
    await checkIns.create(id.b, {
      availabilityStart: hoursFromNow(0),
      availabilityEnd: hoursFromNow(3),
      preparationMinutes: 20,
      latitude: REICHSTAG.lat,
      longitude: REICHSTAG.lng,
    });
    await dataSource.query(
      `INSERT INTO venues (name, location) VALUES ($1, ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography)`,
      [VENUE_NAME, REICHSTAG.lng, REICHSTAG.lat],
    );
  });

  beforeEach(async () => {
    await clearDates();
  });

  afterAll(async () => {
    await dataSource.query(`DELETE FROM venues WHERE name = $1`, [VENUE_NAME]);
    await users.delete({ phone: In(Object.values(PHONES)) });
    await app.close();
  });

  it('creates a confirmed date for a matched pair, with a suggested venue', async () => {
    const date = await dates.createConfirmedDateForMatch(id.a, id.b);
    expect(date).not.toBeNull();
    expect(date!.status).toBe(DateStatus.Confirmed);
    expect(date!.venueId).toBeTruthy();

    const listed = await dates.listForUser(id.a);
    expect(listed.map((d) => d.id)).toContain(date!.id);
  });

  it('cancelling records the canceller and moves the date to cancelled', async () => {
    const date = await dates.createConfirmedDateForMatch(id.a, id.b);
    const cancelled = await dates.cancel(date!.id, id.a, 'Something came up');
    expect(cancelled.status).toBe(DateStatus.Cancelled);
    expect(cancelled.cancelledById).toBe(id.a);
    expect(cancelled.cancelReason).toBe('Something came up');
  });

  it('completes only when BOTH people confirm it happened', async () => {
    const date = await dates.createConfirmedDateForMatch(id.a, id.b);

    const afterOne = await dates.confirmHappened(date!.id, id.a, 5);
    expect(afterOne.status).toBe(DateStatus.Confirmed); // still not complete

    const afterBoth = await dates.confirmHappened(date!.id, id.b, 4);
    expect(afterBoth.status).toBe(DateStatus.Completed);
  });

  it('rejects acting on a date you are not part of', async () => {
    const date = await dates.createConfirmedDateForMatch(id.a, id.b);
    await expect(dates.cancel(date!.id, id.c)).rejects.toThrow();
  });

  it('blocks an invalid stage move (confirming a cancelled date)', async () => {
    const date = await dates.createConfirmedDateForMatch(id.a, id.b);
    await dates.cancel(date!.id, id.a);
    await expect(dates.confirmHappened(date!.id, id.b, 5)).rejects.toThrow();
  });

  it('a mutual like automatically produces a confirmed date (via matching)', async () => {
    await matching.sendLike(id.a, { targetUserId: id.b });
    await matching.sendLike(id.b, { targetUserId: id.a });

    const listed = await dates.listForUser(id.a);
    const confirmed = listed.filter((d) => d.status === DateStatus.Confirmed);
    expect(confirmed.length).toBeGreaterThanOrEqual(1);
    const d = confirmed[0];
    expect([d.userAId, d.userBId].sort()).toEqual([id.a, id.b].sort());
  });
});
