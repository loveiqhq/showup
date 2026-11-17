import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import request from 'supertest';
import { DataSource, In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { DateChangeService } from './../src/modules/dates/date-change.service';
import { DateEntity } from './../src/modules/dates/entities/date.entity';
import { DateStatus } from './../src/modules/dates/util/date-lifecycle';
import {
  PLACES_PROVIDER,
  PlacesProvider,
  PlacesUnavailableError,
} from './../src/modules/places/places.provider';
import { User } from './../src/modules/users/entities/user.entity';

/**
 * Date 05 foundations (SHOWUP-196) through the real app and database: the change-places route and
 * the match's slot list. The pure rules are `change-slots.spec.ts` and `change-rules.spec.ts`.
 */
const PHONES = {
  you: '+491701960001',
  leonie: '+491701960002',
  stranger: '+491701960003',
};
const TZ = 'Europe/Berlin';
// Cafe Krone, the fixtures' neighbourhood.
const KRONE = { lat: 52.5016, lng: 13.4186 };
const VENUE_NAME = 'E2E Date Change Cafe';
// 19:00 Berlin summer time.
const BINDING = new Date('2030-06-15T19:00:00+02:00');
const at = (hhmm: string, day = '2030-06-15') =>
  new Date(`${day}T${hhmm}:00+02:00`);

describe('Date 05 foundations (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let db: DataSource;
  let dates: Repository<DateEntity>;
  const token: Record<string, string> = {};
  const id: Record<string, string> = {};
  let venueId: string;
  let dateId: string;

  const as = (who: string) => `Bearer ${token[who]}`;
  const signIn = async (who: keyof typeof PHONES) => {
    const start = await request(server)
      .post('/auth/phone/start')
      .send({ phone: PHONES[who] })
      .expect(200);
    const verify = await request(server)
      .post('/auth/phone/verify')
      .send({ phone: PHONES[who], code: start.body.devCode })
      .expect(200);
    token[who] = verify.body.accessToken;
    id[who] = verify.body.user.id;
  };
  const places = (who: string, q?: string, date = dateId) =>
    request(server)
      .get(`/dates/${date}/change/places`)
      .query(q === undefined ? {} : { q })
      .set('Authorization', as(who));

  const addWindow = async (
    userId: string,
    committedAt: Date,
    runs: Array<[Date, Date]>,
  ) => {
    const [w] = await db.query<Array<{ id: string }>>(
      `INSERT INTO check_in_windows (user_id, committed_at, closes_at, motivation, vibes)
       VALUES ($1, $2, $3, 'long_term', ARRAY['coffee_chat']::check_in_vibe_enum[]) RETURNING id`,
      [userId, committedAt, new Date(committedAt.getTime() + 48 * 3_600_000)],
    );
    for (const [start, end] of runs) {
      await db.query(
        `INSERT INTO check_ins (user_id, availability_start, availability_end, check_in_window_id)
         VALUES ($1, $2, $3, $4)`,
        [userId, start, end, w.id],
      );
    }
  };

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication();
    app.useGlobalPipes(
      new ValidationPipe({ whitelist: true, transform: true }),
    );
    await app.init();
    server = app.getHttpServer();
    db = app.get(DataSource);
    dates = app.get<Repository<DateEntity>>(getRepositoryToken(DateEntity), {
      strict: false,
    });
    const users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    await users.delete({ phone: In(Object.values(PHONES)) });
    for (const who of Object.keys(PHONES) as Array<keyof typeof PHONES>) {
      await signIn(who);
    }

    await db.query(`DELETE FROM venues WHERE name = $1`, [VENUE_NAME]);
    const [venue] = await db.query<Array<{ id: string }>>(
      `INSERT INTO venues (name, address, location)
       VALUES ($1, 'Oranienstrasse 42, Kreuzberg', ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography)
       RETURNING id`,
      [VENUE_NAME, KRONE.lng, KRONE.lat],
    );
    venueId = venue.id;
    dateId = (
      await dates.save(
        dates.create({
          userAId: id.you,
          userBId: id.leonie,
          scheduledAt: BINDING,
          venueId,
          status: DateStatus.Confirmed,
        }),
      )
    ).id;

    // Leonie's older window (ignored) and her latest committed one (two runs, 17:00 to 22:00).
    await addWindow(id.leonie, new Date('2030-06-13T10:00:00Z'), [
      [at('12:00'), at('23:00')],
    ]);
    await addWindow(id.leonie, new Date('2030-06-14T10:00:00Z'), [
      [at('17:00'), at('20:00')],
      [at('20:00'), at('22:00')],
    ]);
    // The viewer's own hours must not count: the slots are the MATCH's.
    await addWindow(id.you, new Date('2030-06-14T10:00:00Z'), [
      [at('10:00'), at('23:00')],
    ]);
  });

  afterAll(async () => {
    await dates.delete({ id: dateId });
    await db.query(`DELETE FROM venues WHERE name = $1`, [VENUE_NAME]);
    await app
      .get<Repository<User>>(getRepositoryToken(User), { strict: false })
      .delete({ phone: In(Object.values(PHONES)) });
    await app.close();
  });

  afterEach(() => jest.restoreAllMocks());

  describe('GET /dates/:id/change/places', () => {
    it('lists the three types nearest first, in whole metres, with no attribution for fixtures', async () => {
      const res = await places('you').expect(200);
      expect(res.body.attribution).toBeNull();
      const list = res.body.places as Array<{
        placeId: string;
        type: string;
        distanceMeters: number;
      }>;
      expect(list).toHaveLength(5);
      expect(new Set(list.map((p) => p.type))).toEqual(
        new Set(['cafe', 'bar', 'restaurant']),
      );
      const metres = list.map((p) => p.distanceMeters);
      expect(metres).toEqual([...metres].sort((a, b) => a - b));
      expect(metres.every(Number.isInteger)).toBe(true);
      expect(Object.keys(res.body.places[0]).sort()).toEqual([
        'address',
        'distanceMeters',
        'name',
        'placeId',
        'type',
      ]);
    });

    it('searches by a trimmed q, and an empty q is nearby', async () => {
      const found = await places('leonie', '  lenz ').expect(200);
      expect(found.body.places.map((p: { name: string }) => p.name)).toEqual([
        'Bar Lenz',
      ]);
      const empty = await places('you', '   ').expect(200);
      expect(empty.body.places).toHaveLength(5);
    });

    it('answers 401 without a token, 403 to a stranger, 404 for an unknown date', async () => {
      await request(server).get(`/dates/${dateId}/change/places`).expect(401);
      await places('stranger').expect(403);
      await places('you', undefined, randomUUID()).expect(404);
    });

    it('answers 409 when the date has no meet place to measure from', async () => {
      const bare = await dates.save(
        dates.create({
          userAId: id.you,
          userBId: id.stranger,
          scheduledAt: BINDING,
          venueId: null,
          status: DateStatus.Confirmed,
        }),
      );
      try {
        await places('you', undefined, bare.id).expect(409);
      } finally {
        await dates.delete({ id: bare.id });
      }
    });

    it('answers 503 when the provider cannot answer', async () => {
      const provider = app.get<PlacesProvider>(PLACES_PROVIDER);
      jest
        .spyOn(provider, 'nearby')
        .mockRejectedValue(new PlacesUnavailableError('down'));
      await places('you').expect(503);
    });
  });

  describe('DateChangeService.matchSlots', () => {
    it("is the match's hours from their latest committed window: 19:15 to 21:30", async () => {
      const service = app.get(DateChangeService);
      const date = await dates.findOneByOrFail({ id: dateId });

      const slots = await service.matchSlots(date, id.you, TZ);

      const hm = (d: Date) =>
        new Intl.DateTimeFormat('en-GB', {
          timeZone: TZ,
          hour: '2-digit',
          minute: '2-digit',
          hourCycle: 'h23',
        }).format(d);
      expect(slots).toHaveLength(10);
      expect(hm(slots[0])).toBe('19:15');
      expect(hm(slots[slots.length - 1])).toBe('21:30');
    });

    it('is empty for the match when the viewer is the other person and has no later hours', async () => {
      const service = app.get(DateChangeService);
      const date = await dates.findOneByOrFail({ id: dateId });
      // From Leonie's side the match is you, whose run reaches 23:00: 19:15 to 22:30.
      const slots = await service.matchSlots(date, id.leonie, TZ);
      expect(slots[slots.length - 1]).toEqual(at('22:30'));
    });
  });
});
