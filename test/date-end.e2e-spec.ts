import { INestApplication, ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import request from 'supertest';
import { DataSource, In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { AnalyticsService } from './../src/modules/analytics/analytics.service';
import { DEV_PARTNER_PHONE } from './../src/modules/date-screen/date-dev.service';
import { DateEntity } from './../src/modules/dates/entities/date.entity';
import { DateStatus } from './../src/modules/dates/util/date-lifecycle';
import { Profile } from './../src/modules/profiles/entities/profile.entity';
import { User } from './../src/modules/users/entities/user.entity';

/**
 * Date 06 (SHOWUP-197): end the date and "Stay for now", through the real app and database. The
 * pure rules are `date-end.spec.ts`, the service's mocked paths `date-end.service.spec.ts`.
 */
const PHONES = { you: '+491701970101', leonie: '+491701970102' };
const MINUTE = 60_000;

interface SentEvent {
  userId: string;
  eventName: string;
  properties?: Record<string, unknown>;
}

describe('Date 06 end and report (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let db: DataSource;
  let dates: Repository<DateEntity>;
  let config: ConfigService;
  const token: Record<string, string> = {};
  const id: Record<string, string> = {};
  let dateId: string;
  let events: SentEvent[];

  const as = (who: string) => `Bearer ${token[who]}`;
  const named = (name: string) => events.filter((e) => e.eventName === name);
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
  const end = (who: string, reason: string, entry = 'end_date_bar') =>
    request(server)
      .post(`/dates/${dateId}/end`)
      .set('Authorization', as(who))
      .send({ reason, entry });
  const discomfort = (who: string) =>
    request(server)
      .post(`/dates/${dateId}/discomfort`)
      .set('Authorization', as(who));
  const me = (who: string) =>
    request(server).get('/me/date').set('Authorization', as(who)).expect(200);
  const seen = (who: string) =>
    request(server)
      .post(`/dates/${dateId}/ended/seen`)
      .set('Authorization', as(who))
      .expect(204);
  const dateRow = () => dates.findOneByOrFail({ id: dateId });
  const checkIn = (who: string) =>
    db.query(
      `INSERT INTO date_arrival_check_ins
         (date_id, user_id, location, entry, client_id, committed_at)
       VALUES ($1, $2, ST_SetSRID(ST_MakePoint(13.42, 52.5), 4326)::geography,
               'date_screen', $3, now())`,
      [dateId, id[who], randomUUID()],
    );
  /** A confirmed date whose agreed time is `minutes` from now (negative: already past). */
  const newDate = async (minutes: number) => {
    await dates
      .createQueryBuilder()
      .delete()
      .where('user_a_id IN (:...ids) OR user_b_id IN (:...ids)', {
        ids: [id.you, id.leonie],
      })
      .execute();
    await db.query(`DELETE FROM matches WHERE user_a_id = ANY($1::uuid[])`, [
      [id.you, id.leonie],
    ]);
    const [a, b] = [id.you, id.leonie].sort();
    await db.query(
      `INSERT INTO matches (user_a_id, user_b_id, status) VALUES ($1, $2, 'active')`,
      [a, b],
    );
    dateId = (
      await dates.save(
        dates.create({
          userAId: id.you,
          userBId: id.leonie,
          scheduledAt: new Date(Date.now() + minutes * MINUTE),
          status: DateStatus.Confirmed,
        }),
      )
    ).id;
  };
  const matchStatus = async () =>
    (
      await db.query<Array<{ status: string }>>(
        `SELECT status FROM matches WHERE user_a_id = ANY($1::uuid[])`,
        [[id.you, id.leonie]],
      )
    )[0].status;

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
    config = app.get(ConfigService);
    dates = app.get<Repository<DateEntity>>(getRepositoryToken(DateEntity), {
      strict: false,
    });
    const users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });
    await users.delete({
      phone: In([...Object.values(PHONES), DEV_PARTNER_PHONE]),
    });
    for (const who of Object.keys(PHONES) as Array<keyof typeof PHONES>) {
      await signIn(who);
    }
    const profiles = db.getRepository(Profile);
    for (const [who, name] of [
      ['you', 'Max'],
      ['leonie', 'Leonie'],
    ] as const) {
      await profiles.save(
        profiles.create({
          userId: id[who],
          displayName: name,
          dateOfBirth: '1996-01-01',
          isVisible: true,
        }),
      );
    }
    jest
      .spyOn(app.get(AnalyticsService), 'trackServerEvent')
      .mockImplementation((p) => {
        events.push(p);
        return Promise.resolve();
      });
  });

  beforeEach(async () => {
    events = [];
    jest.spyOn(config, 'get').mockRestore();
    await newDate(-30);
  });

  afterAll(async () => {
    await dates
      .createQueryBuilder()
      .delete()
      .where('user_a_id IN (:...ids) OR user_b_id IN (:...ids)', {
        ids: [id.you, id.leonie],
      })
      .execute();
    await db.query(`DELETE FROM matches WHERE user_a_id = ANY($1::uuid[])`, [
      [id.you, id.leonie],
    ]);
    await app
      .get<Repository<User>>(getRepositoryToken(User), { strict: false })
      .delete({ phone: In([...Object.values(PHONES), DEV_PARTNER_PHONE]) });
    await app.close();
  });

  describe('We met', () => {
    it('ends with no check-in, stores the evidence, closes the match and fires date_ended once', async () => {
      const res = await end('you', 'met', 'end_date_bar').expect(200);
      expect(res.body).toEqual({
        dateId,
        name: 'Leonie',
        endedByYou: true,
        reason: 'met',
      });
      const row = await dateRow();
      expect(row).toMatchObject({
        status: DateStatus.Ended,
        endedById: id.you,
        endReason: 'met',
        endEntry: 'end_date_bar',
        enderCheckedIn: false,
        partnerCheckedIn: false,
      });
      expect(row.endedAt).toBeInstanceOf(Date);
      expect(await matchStatus()).toBe('cancelled');
      expect(named('date_ended')).toHaveLength(1);
      expect(named('date_ended')[0]).toMatchObject({
        userId: id.you,
        properties: {
          date_id: dateId,
          end_reason: 'met',
          entry: 'end_date_bar',
          ender_checked_in: false,
          partner_checked_in: false,
          minutes_vs_start: 30,
        },
      });
    });

    it('a second end, by either person, is 409 date_not_active and fires nothing more', async () => {
      await end('you', 'met').expect(200);
      for (const who of ['you', 'leonie']) {
        const res = await end(who, 'met').expect(409);
        expect(res.body).toMatchObject({ code: 'date_not_active' });
      }
      expect(named('date_ended')).toHaveLength(1);
    });

    it('two simultaneous ends: one 200, one 409, one event', async () => {
      const [a, b] = await Promise.all([
        end('you', 'met'),
        end('leonie', 'met'),
      ]);
      expect([a.status, b.status].sort()).toEqual([200, 409]);
      expect(named('date_ended')).toHaveLength(1);
    });
  });

  describe('My date is not showing up', () => {
    it('is 409 before_meet before the agreed time, even when checked in', async () => {
      await newDate(30);
      await checkIn('you');
      const res = await end('you', 'partner_no_show', 'report').expect(409);
      expect(res.body).toMatchObject({ statusCode: 409, code: 'before_meet' });
      expect((await dateRow()).status).toBe(DateStatus.Confirmed);
      expect(named('date_ended')).toHaveLength(0);
    });

    it('is 409 not_checked_in without a committed check-in', async () => {
      await checkIn('leonie'); // the match's check-in does not count for the ender
      const res = await end('you', 'partner_no_show').expect(409);
      expect(res.body).toMatchObject({ code: 'not_checked_in' });
      expect(named('date_ended')).toHaveLength(0);
    });

    it('ends from the agreed time once checked in, and the match gets you_no_showed whether or not she checked in', async () => {
      await checkIn('you');
      await end('you', 'partner_no_show', 'report').expect(200);
      expect(await dateRow()).toMatchObject({
        status: DateStatus.Ended,
        endReason: 'partner_no_show',
        endEntry: 'report',
        enderCheckedIn: true,
        partnerCheckedIn: false,
      });
      const body = (await me('leonie')).body;
      expect(body.ended).toMatchObject({
        reason: 'partner_no_show',
        partnerScreen: 'you_no_showed',
        endedByYou: false,
        name: 'Max',
      });
    });

    it('still says you_no_showed when the match had checked in too', async () => {
      await checkIn('you');
      await checkIn('leonie');
      await end('you', 'partner_no_show').expect(200);
      expect((await dateRow()).partnerCheckedIn).toBe(true);
      expect((await me('leonie')).body.ended.partnerScreen).toBe(
        'you_no_showed',
      );
    });
  });

  describe('I do not feel comfortable', () => {
    it('is 409 not_checked_in without a check-in, at any time', async () => {
      await newDate(60);
      const res = await end('you', 'uncomfortable', 'report').expect(409);
      expect(res.body).toMatchObject({ code: 'not_checked_in' });
    });

    it('ends when checked in, before the agreed time too', async () => {
      await newDate(60);
      await checkIn('you');
      await end('you', 'uncomfortable', 'report').expect(200);
      expect((await dateRow()).endReason).toBe('uncomfortable');
      expect((await me('leonie')).body.ended).toMatchObject({
        reason: 'uncomfortable',
        partnerScreen: 'date_ended_discomfort',
      });
    });

    it('Stay for now stores one flag per tap, fires each time and leaves the date on', async () => {
      await discomfort('you').expect(204);
      await discomfort('you').expect(204);
      const [{ n }] = await db.query<Array<{ n: number }>>(
        `SELECT count(*)::int AS n FROM date_discomfort_flags WHERE date_id = $1 AND user_id = $2`,
        [dateId, id.you],
      );
      expect(n).toBe(2);
      expect(named('problem_stay_flagged')).toHaveLength(2);
      expect(named('problem_stay_flagged')[0].properties).toEqual({
        date_id: dateId,
        minutes_vs_start: 30,
      });
      expect((await dateRow()).status).toBe(DateStatus.Confirmed);
      expect((await me('you')).body.date).not.toBeNull();
    });

    it('Stay for now on an ended date is 409 date_not_active', async () => {
      await end('you', 'met').expect(200);
      const res = await discomfort('you').expect(409);
      expect(res.body).toMatchObject({ code: 'date_not_active' });
    });
  });

  describe('the ended payload (R3)', () => {
    it('is reported to both people, each until they mark it seen', async () => {
      await end('you', 'met').expect(200);
      const ender = (await me('you')).body;
      expect(ender.date).toBeNull();
      expect(ender.ended).toMatchObject({
        dateId,
        reason: 'met',
        endedByYou: true,
        name: 'Leonie',
      });
      expect(ender.ended.partnerScreen).toBeUndefined();
      const match = (await me('leonie')).body;
      expect(match.ended).toMatchObject({
        reason: 'met',
        endedByYou: false,
        partnerScreen: 'date_closed_positive',
      });

      await seen('you');
      expect((await me('you')).body.ended).toBeNull();
      expect((await me('leonie')).body.ended).not.toBeNull();
      await seen('leonie');
      expect((await me('leonie')).body.ended).toBeNull();
    });

    it('still reports a date ended in the chat as reason chat, with no partner screen', async () => {
      await db.query(
        `UPDATE dates SET status = 'ended_in_chat', ended_by_id = $2, ended_at = now() WHERE id = $1`,
        [dateId, id.you],
      );
      const body = (await me('leonie')).body;
      expect(body.ended).toMatchObject({ reason: 'chat', endedByYou: false });
      expect(body.ended.partnerScreen).toBeUndefined();
    });

    it('carries endsEnabled and cancelPenaltyPoints on the active date', async () => {
      const body = (await me('you')).body;
      expect(body.date.endsEnabled).toBe(true);
      expect(body.date.cancelPenaltyPoints).toBe(10);
    });

    it('frees both people: an ended date is not an active one', async () => {
      await end('you', 'met').expect(200);
      expect((await me('you')).body.date).toBeNull();
      expect((await me('leonie')).body.date).toBeNull();
    });
  });

  describe('the DATE_ENDS_ENABLED switch (R1)', () => {
    const switchOff = () => {
      const real = config.get.bind(config);
      jest
        .spyOn(config, 'get')
        .mockImplementation((key: string, ...rest: unknown[]) =>
          key === 'DATE_ENDS_ENABLED'
            ? 'false'
            : (real as (k: string, ...r: unknown[]) => unknown)(key, ...rest),
        );
    };

    it('answers 404 on both routes, stores nothing, and reports endsEnabled false', async () => {
      switchOff();
      await end('you', 'met').expect(404);
      await discomfort('you').expect(404);
      expect((await dateRow()).status).toBe(DateStatus.Confirmed);
      expect(named('date_ended')).toHaveLength(0);
      const [{ n }] = await db.query<Array<{ n: number }>>(
        `SELECT count(*)::int AS n FROM date_discomfort_flags WHERE date_id = $1`,
        [dateId],
      );
      expect(n).toBe(0);
      expect((await me('you')).body.date.endsEnabled).toBe(false);
    });
  });

  it('refuses a stranger and a bad body', async () => {
    const stranger = '+491701970103';
    const start = await request(server)
      .post('/auth/phone/start')
      .send({ phone: stranger })
      .expect(200);
    const verify = await request(server)
      .post('/auth/phone/verify')
      .send({ phone: stranger, code: start.body.devCode })
      .expect(200);
    await request(server)
      .post(`/dates/${dateId}/end`)
      .set('Authorization', `Bearer ${verify.body.accessToken}`)
      .send({ reason: 'met', entry: 'end_date_bar' })
      .expect(403);
    await end('you', 'left_early').expect(400);
    await request(server)
      .post(`/dates/${dateId}/end`)
      .set('Authorization', as('you'))
      .send({ reason: 'met', entry: 'nowhere' })
      .expect(400);
    await app
      .get<Repository<User>>(getRepositoryToken(User), { strict: false })
      .delete({ phone: stranger });
  });
});
