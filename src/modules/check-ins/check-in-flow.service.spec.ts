import {
  BadRequestException,
  NotFoundException,
  UnprocessableEntityException,
} from '@nestjs/common';
import { DataSource, FindOperator } from 'typeorm';

import { AnalyticsService } from '../analytics/analytics.service';
import { Profile } from '../profiles/entities/profile.entity';
import { FLOW_POSITION_COMPLETE } from '../profiles/util/flow-position';
import { canonicalVibes, CheckInFlowService } from './check-in-flow.service';
import {
  CommitCheckInDto,
  SaveCheckInAnswersDto,
  SaveFilterSettingsDto,
} from './dto/check-in-flow.dto';
import { CheckIn } from './entities/check-in.entity';
import { CheckInProfile } from './entities/check-in-profile.entity';
import { CheckInWindow } from './entities/check-in-window.entity';
import { FilterSettings } from './entities/filter-settings.entity';
import { CheckInStatus, DEFAULT_PREPARATION_MINUTES } from './util/check-in';
import { MEET_GENDERS, UPLIFT_CONTACT_ERROR_CODE } from './util/check-in-flow';

/**
 * The 48hrs Check-In service's decisions, without a database (SHOWUP-177, stories 178 to 183).
 *
 * `test/check-in-flow.e2e-spec.ts` proves the SQL, the row lock and the CHECK constraints against
 * real Postgres. These pin the BRANCHES that decide what a user gets: which window a commit lands
 * in, that a replay writes and fires nothing, which hours are dropped, what the filter defaults are
 * before anything is saved, and that every refusal happens before a transaction opens.
 *
 * The data source is a hand-rolled fake over arrays (as in `prompts.service.spec.ts`). It reads the
 * `where` the service actually sends -- `MoreThan`, `Not(IsNull())` included -- and hands out copies,
 * so a mutated row that is never saved is not silently "stored". Raw SQL it cannot run is recorded
 * with its parameters, and the tests assert on those.
 */

type Row = Record<string, unknown>;
type Entity = abstract new (...args: never[]) => unknown;

function matches(row: Row, where: Row): boolean {
  return Object.entries(where).every(([key, want]) =>
    want instanceof FindOperator ? holds(row[key], want) : row[key] === want,
  );
}

function holds(got: unknown, op: FindOperator<unknown>): boolean {
  switch (op.type) {
    case 'moreThan':
      return (got as Date).getTime() > (op.value as Date).getTime();
    case 'isNull':
      return got == null;
    case 'not':
      return !holds(got, op.child as FindOperator<unknown>);
    default:
      throw new Error(`the fake does not read the ${op.type} operator`);
  }
}

class FakeDb {
  private readonly tables = new Map<Entity, Row[]>([
    [CheckInWindow, []],
    [CheckIn, []],
    [CheckInProfile, []],
    [FilterSettings, []],
    [Profile, []],
  ]);
  /** Rows keyed by the user: one record per user, as the tables' primary keys say. */
  private readonly perUser = new Set<Entity>([
    CheckInProfile,
    FilterSettings,
    Profile,
  ]);
  readonly queries: { sql: string; params: unknown[] }[] = [];
  readonly locked: string[] = [];
  transactions = 0;
  private ids = 0;

  rows<T>(entity: Entity): T[] {
    return this.tables.get(entity) as T[];
  }

  seed(entity: Entity, row: Row): void {
    this.rows<Row>(entity).push({ ...row });
  }

  repo(entity: Entity) {
    const rows = this.rows<Row>(entity);
    const keyOf = this.perUser.has(entity) ? 'userId' : 'id';
    return {
      findOne: (opts: {
        where: Row;
        order?: Record<string, 'ASC' | 'DESC'>;
        lock?: { mode: string };
      }) => {
        if (opts.lock?.mode === 'pessimistic_write') {
          this.locked.push(opts.where.userId as string);
        }
        const hits = rows.filter((r) => matches(r, opts.where));
        const [[field, dir] = []] = Object.entries(opts.order ?? {});
        if (field) {
          hits.sort((a, b) => {
            const d =
              (a[field] as Date).getTime() - (b[field] as Date).getTime();
            return dir === 'DESC' ? -d : d;
          });
        }
        return Promise.resolve(hits[0] ? { ...hits[0] } : null);
      },
      create: (row: Row) => ({ ...row }),
      save: (row: Row) => {
        const stored = { ...row };
        if (keyOf === 'id' && stored.id == null) {
          this.ids += 1;
          stored.id = `id-${this.ids}`;
        }
        const at = rows.findIndex((r) => r[keyOf] === stored[keyOf]);
        if (at >= 0) rows[at] = stored;
        else rows.push(stored);
        return Promise.resolve({ ...stored });
      },
    };
  }

  private readonly query = (sql: string, params: unknown[]) => {
    this.queries.push({ sql, params });
    return Promise.resolve([]);
  };

  readonly em = {
    getRepository: (entity: Entity) => this.repo(entity),
    findOne: (entity: Entity, opts: { where: Row; lock?: { mode: string } }) =>
      this.repo(entity).findOne(opts),
    count: (entity: Entity, opts: { where: Row }) =>
      Promise.resolve(
        this.rows<Row>(entity).filter((r) => matches(r, opts.where)).length,
      ),
    query: this.query,
  };

  readonly dataSource = {
    transaction: <T>(work: (em: FakeDb['em']) => Promise<T>) => {
      this.transactions += 1;
      return work(this.em);
    },
    query: this.query,
    getRepository: (entity: Entity) => this.repo(entity),
  };

  sqlTouching(table: string): { sql: string; params: unknown[] }[] {
    return this.queries.filter((q) => q.sql.includes(`"${table}"`));
  }
}

describe('CheckInFlowService', () => {
  const USER = 'user-1';
  const HOUR = 60 * 60 * 1000;
  /** The kit's clock: 4:20pm on the 13th, as on every sheet. */
  const NOW = new Date('2026-10-13T16:20:00.000Z');
  const at = (iso: string) => new Date(iso);

  let db: FakeDb;
  let track: jest.Mock;
  let service: CheckInFlowService;

  const profile = (overrides: Partial<Profile> = {}) =>
    db.seed(Profile, {
      userId: USER,
      flowPosition: FLOW_POSITION_COMPLETE,
      gender: 'woman',
      orientation: 'straight',
      ...overrides,
    });

  const openWindow = (overrides: Partial<CheckInWindow> = {}) => {
    const row = {
      id: 'w-open',
      userId: USER,
      openedAt: new Date(NOW.getTime() - HOUR),
      closesAt: new Date(NOW.getTime() + 47 * HOUR),
      committedAt: null,
      motivation: null,
      vibes: null,
      uplift: null,
      ...overrides,
    };
    db.seed(CheckInWindow, row);
    return row;
  };

  const commitDto = (
    overrides: Partial<CommitCheckInDto> = {},
  ): CommitCheckInDto => ({
    windowId: 'w-open',
    slots: [
      { startsAt: '2026-10-13T15:00:00.000Z', tier: 'preferred' }, // started: dropped
      { startsAt: '2026-10-13T17:00:00.000Z', tier: 'preferred' },
      { startsAt: '2026-10-13T18:00:00.000Z', tier: 'preferred' },
      { startsAt: '2026-10-13T20:00:00.000Z', tier: 'available' },
    ],
    motivation: 'long_term',
    vibes: ['playful', 'coffee_chat'],
    uplift: '  I will take you out on my boat  ',
    ...overrides,
  });

  const windows = () => db.rows<CheckInWindow>(CheckInWindow);
  const checkIns = () => db.rows<CheckIn>(CheckIn);

  beforeEach(() => {
    db = new FakeDb();
    track = jest.fn().mockResolvedValue(undefined);
    service = new CheckInFlowService(
      db.dataSource as unknown as DataSource,
      { trackServerEvent: track } as unknown as AnalyticsService,
    );
  });

  describe('canonicalVibes', () => {
    it('drops repeats and answers in list order, whatever order they were tapped', () => {
      expect(
        canonicalVibes(['no_rush', 'coffee_chat', 'playful', 'coffee_chat']),
      ).toEqual(['coffee_chat', 'playful', 'no_rush']);
    });
  });

  describe('state', () => {
    it('opens the first window once profile creation is complete, under the profile lock', async () => {
      profile();
      const state = await service.state(USER, NOW);
      expect(windows()).toHaveLength(1);
      expect(state).toEqual({
        windowId: windows()[0].id,
        introPassed: false,
        active: false,
        firstCheckIn: true,
        lastMotivation: null,
        lastVibes: [],
        // Story 05's pre-fill: a straight woman meets men.
        filters: {
          meetGender: ['man'],
          minAge: 18,
          maxAge: null,
          distanceKm: 10,
        },
        filtersSaved: false,
      });
      expect(db.locked).toEqual([USER]);
    });

    it('has no window, and the widest filter, before a profile exists', async () => {
      const state = await service.state(USER, NOW);
      expect(state.windowId).toBeNull();
      expect(state.active).toBe(false);
      expect(state.filters.meetGender).toEqual([...MEET_GENDERS]);
      expect(windows()).toHaveLength(0);
    });

    it('reads the intro flag, the last answers and the saved filters', async () => {
      profile();
      openWindow();
      db.seed(CheckInProfile, {
        userId: USER,
        introPassedAt: at('2026-10-10T09:00:00.000Z'),
        lastMotivation: 'friends',
        lastVibes: ['calm'],
      });
      db.seed(FilterSettings, {
        userId: USER,
        meetGender: ['woman', 'non_binary'],
        minAge: 25,
        maxAge: 35,
        distanceKm: 15,
      });
      const state = await service.state(USER, NOW);
      expect(state).toMatchObject({
        windowId: 'w-open',
        introPassed: true,
        lastMotivation: 'friends',
        lastVibes: ['calm'],
        filters: {
          meetGender: ['woman', 'non_binary'],
          minAge: 25,
          maxAge: 35,
          distanceKm: 15,
        },
        filtersSaved: true,
      });
    });

    it('is active only while the CURRENT window is committed', async () => {
      profile();
      // An earlier, closed, committed window makes this not the first Check-In -- but not active.
      openWindow({
        id: 'w-done',
        openedAt: new Date(NOW.getTime() - 60 * HOUR),
        closesAt: new Date(NOW.getTime() - 12 * HOUR),
        committedAt: new Date(NOW.getTime() - 50 * HOUR),
      });
      const state = await service.state(USER, NOW);
      expect(state.windowId).not.toBe('w-done');
      expect(state.active).toBe(false);
      expect(state.firstCheckIn).toBe(false);

      windows().find((w) => w.id === state.windowId)!.committedAt = NOW;
      expect((await service.state(USER, NOW)).active).toBe(true);
    });
  });

  describe('markIntroPassed', () => {
    it('writes the flag for this user only', async () => {
      await service.markIntroPassed(USER);
      const [write] = db.sqlTouching('check_in_profiles');
      expect(write.params).toEqual([USER]);
      // The first time is kept: a second tap never moves it.
      expect(write.sql).toContain('COALESCE');
    });
  });

  describe('saveAnswers', () => {
    it('opens no transaction when there is nothing to save', async () => {
      await service.saveAnswers(USER, {});
      // JSON can send an explicit null, which the DTO's optional fields let through.
      await service.saveAnswers(USER, {
        motivation: null,
        vibes: null,
      } as unknown as SaveCheckInAnswersDto);
      expect(db.transactions).toBe(0);
    });

    it('creates the record on the first answer and leaves the other answer empty', async () => {
      await service.saveAnswers(USER, { motivation: 'casual' });
      expect(db.rows(CheckInProfile)).toEqual([
        {
          userId: USER,
          introPassedAt: null,
          lastMotivation: 'casual',
          lastVibes: null,
        },
      ]);
    });

    it('stores vibes in list order and keeps them through a later Motivation save', async () => {
      await service.saveAnswers(USER, { vibes: ['no_rush', 'calm', 'calm'] });
      await service.saveAnswers(USER, { motivation: 'friends' });
      expect(db.rows(CheckInProfile)).toEqual([
        expect.objectContaining({
          lastMotivation: 'friends',
          lastVibes: ['calm', 'no_rush'],
        }),
      ]);
    });
  });

  describe('saveFilters', () => {
    const filters = (
      overrides: Partial<SaveFilterSettingsDto> = {},
    ): SaveFilterSettingsDto => ({
      meetGender: ['woman'],
      minAge: 25,
      maxAge: 35,
      distanceKm: 15,
      ...overrides,
    });

    it('stores 65, and no maximum, as no upper limit', async () => {
      expect(
        (await service.saveFilters(USER, filters({ maxAge: 65 }))).maxAge,
      ).toBeNull();
      expect(
        (await service.saveFilters(USER, filters({ maxAge: null }))).maxAge,
      ).toBeNull();
      // One record per user: the second save replaced the first.
      expect(db.rows(FilterSettings)).toHaveLength(1);
    });

    it('refuses a maximum below the minimum and writes nothing', async () => {
      await expect(
        service.saveFilters(USER, filters({ minAge: 25, maxAge: 24 })),
      ).rejects.toBeInstanceOf(BadRequestException);
      expect(db.rows(FilterSettings)).toHaveLength(0);
    });

    it('stores each gender once and answers what was stored', async () => {
      const saved = await service.saveFilters(
        USER,
        filters({
          meetGender: ['woman', 'man', 'woman'],
          minAge: 18,
          maxAge: 30,
          distanceKm: 50,
        }),
      );
      expect(saved).toEqual({
        meetGender: ['woman', 'man'],
        minAge: 18,
        maxAge: 30,
        distanceKm: 50,
      });
    });
  });

  describe('commit: refusals happen before anything is written', () => {
    const refusesUntouched = async (
      dto: CommitCheckInDto,
      kind: abstract new (...args: never[]) => Error,
    ) => {
      await expect(service.commit(USER, dto, NOW)).rejects.toBeInstanceOf(kind);
      expect(db.transactions).toBe(0);
      expect(track).not.toHaveBeenCalled();
    };

    it('refuses a reason over 100 characters', async () => {
      await refusesUntouched(
        commitDto({ uplift: 'a'.repeat(101) }),
        BadRequestException,
      );
    });

    it('counts the reason in characters as a person sees them, not code units', async () => {
      // 100 accented letters, each an e plus a combining accent: 200 code units, 100 characters.
      profile();
      openWindow();
      const result = await service.commit(
        USER,
        commitDto({ uplift: 'e\u0301'.repeat(100) }),
        NOW,
      );
      expect(result.created).toBe(true);
    });

    it('refuses contact info with its own error, not a failed save', async () => {
      const refusal = service.commit(
        USER,
        commitDto({ uplift: 'Write me at someone@example.com' }),
        NOW,
      );
      await expect(refusal).rejects.toBeInstanceOf(
        UnprocessableEntityException,
      );
      await expect(refusal).rejects.toMatchObject({
        response: { statusCode: 422, error: UPLIFT_CONTACT_ERROR_CODE },
      });
      expect(db.transactions).toBe(0);
    });

    it('refuses a latitude without a longitude', async () => {
      await refusesUntouched(
        commitDto({ latitude: 52.5 }),
        BadRequestException,
      );
    });

    it('refuses an hour that does not start on the hour', async () => {
      await refusesUntouched(
        commitDto({
          slots: [{ startsAt: '2026-10-13T17:07:00.000Z', tier: 'preferred' }],
        }),
        BadRequestException,
      );
    });
  });

  describe('commit', () => {
    it('writes the upcoming hours, the answers and the event, once', async () => {
      profile();
      openWindow();
      const result = await service.commit(USER, commitDto(), NOW);

      // 3pm had started and is dropped; 5pm and 6pm (same tier, back to back) are one span.
      expect(
        checkIns().map((c) => ({
          start: c.availabilityStart.toISOString(),
          end: c.availabilityEnd.toISOString(),
          tier: c.tier,
        })),
      ).toEqual([
        {
          start: '2026-10-13T17:00:00.000Z',
          end: '2026-10-13T19:00:00.000Z',
          tier: 'preferred',
        },
        {
          start: '2026-10-13T20:00:00.000Z',
          end: '2026-10-13T21:00:00.000Z',
          tier: 'available',
        },
      ]);
      for (const c of checkIns()) {
        expect(c).toMatchObject({
          userId: USER,
          status: CheckInStatus.Available,
          preparationMinutes: DEFAULT_PREPARATION_MINUTES,
          windowId: 'w-open',
        });
      }

      expect(windows()[0]).toMatchObject({
        committedAt: NOW,
        motivation: 'long_term',
        vibes: ['coffee_chat', 'playful'],
        uplift: 'I will take you out on my boat',
      });

      // An earlier Check-In's live hours end: every other window's, not this one's.
      const [retire] = db.sqlTouching('check_ins');
      expect(retire.params).toEqual([
        CheckInStatus.Cancelled,
        USER,
        CheckInStatus.Available,
        NOW,
        'w-open',
      ]);
      // The committed answers become the last answers.
      const [lastAnswers] = db.sqlTouching('check_in_profiles');
      expect(lastAnswers.params).toEqual([
        USER,
        'long_term',
        ['coffee_chat', 'playful'],
      ]);
      expect(db.queries.some((q) => q.sql.includes('ST_MakePoint'))).toBe(
        false,
      );

      expect(track).toHaveBeenCalledTimes(1);
      expect(track).toHaveBeenCalledWith({
        userId: USER,
        eventName: 'check_in_created',
        properties: { has_location: false },
      });
      expect(result.created).toBe(true);
      expect(result.state).toMatchObject({ windowId: 'w-open', active: true });
    });

    it('stores a blank reason as no reason', async () => {
      profile();
      openWindow();
      await service.commit(USER, commitDto({ uplift: '   ' }), NOW);
      expect(windows()[0].uplift).toBeNull();
    });

    it('pins the location on the hours of this window when it is sent', async () => {
      profile();
      openWindow();
      await service.commit(
        USER,
        commitDto({ latitude: 52.52, longitude: 13.405 }),
        NOW,
      );
      const pin = db.queries.find((q) => q.sql.includes('ST_MakePoint'));
      // Longitude first: ST_MakePoint takes x, y.
      expect(pin?.params).toEqual([13.405, 52.52, 'w-open']);
      expect(track).toHaveBeenCalledWith(
        expect.objectContaining({ properties: { has_location: true } }),
      );
    });

    it('answers a replay with the same state and writes and fires nothing', async () => {
      profile();
      openWindow();
      const first = await service.commit(USER, commitDto(), NOW);
      const written = checkIns().length;
      const queries = db.queries.length;

      const again = await service.commit(USER, commitDto(), NOW);
      expect(again.created).toBe(false);
      expect(again.state).toEqual(first.state);
      expect(checkIns()).toHaveLength(written);
      expect(db.queries).toHaveLength(queries);
      expect(track).toHaveBeenCalledTimes(1);
    });

    it('refuses a window that does not exist, or belongs to another user', async () => {
      profile();
      openWindow({ id: 'w-theirs', userId: 'user-2' });
      await expect(
        service.commit(USER, commitDto({ windowId: 'w-missing' }), NOW),
      ).rejects.toBeInstanceOf(NotFoundException);
      await expect(
        service.commit(USER, commitDto({ windowId: 'w-theirs' }), NOW),
      ).rejects.toBeInstanceOf(NotFoundException);
      expect(checkIns()).toHaveLength(0);
      expect(track).not.toHaveBeenCalled();
    });

    it('commits an expired window into the current one', async () => {
      profile();
      openWindow({
        id: 'w-expired',
        openedAt: new Date(NOW.getTime() - 49 * HOUR),
        closesAt: new Date(NOW.getTime() - HOUR),
      });
      const result = await service.commit(
        USER,
        commitDto({ windowId: 'w-expired' }),
        NOW,
      );
      const expired = windows().find((w) => w.id === 'w-expired')!;
      const current = windows().find((w) => w.id !== 'w-expired')!;
      expect(expired.committedAt).toBeNull();
      expect(current.committedAt).toEqual(NOW);
      expect(checkIns().every((c) => c.windowId === current.id)).toBe(true);
      expect(result.state).toMatchObject({
        windowId: current.id,
        active: true,
      });
    });

    it('commits into the expired window itself when no new one can open', async () => {
      // The flow position was edited back after the window opened: no new window is due.
      profile({ flowPosition: 'basics' });
      openWindow({
        id: 'w-expired',
        openedAt: new Date(NOW.getTime() - 49 * HOUR),
        closesAt: new Date(NOW.getTime() - HOUR),
      });
      const result = await service.commit(
        USER,
        commitDto({ windowId: 'w-expired' }),
        NOW,
      );
      expect(result.created).toBe(true);
      expect(windows()).toHaveLength(1);
      expect(windows()[0].committedAt).toEqual(NOW);
      // The answer reads the current state, so it never claims the closed window is live.
      expect(result.state.active).toBe(false);
    });

    it('treats an expired window that was committed as a replay', async () => {
      profile();
      openWindow({
        id: 'w-expired',
        openedAt: new Date(NOW.getTime() - 49 * HOUR),
        closesAt: new Date(NOW.getTime() - HOUR),
        committedAt: new Date(NOW.getTime() - 30 * HOUR),
      });
      const result = await service.commit(
        USER,
        commitDto({ windowId: 'w-expired' }),
        NOW,
      );
      expect(result.created).toBe(false);
      expect(checkIns()).toHaveLength(0);
      expect(track).not.toHaveBeenCalled();
    });

    it('refuses a Check-In whose every hour has started, and writes nothing', async () => {
      // The flow sat open past every pick: committing would unlock Search with no hours at all.
      profile();
      openWindow();
      await expect(
        service.commit(
          USER,
          commitDto({
            slots: [
              { startsAt: '2026-10-13T14:00:00.000Z', tier: 'preferred' },
              { startsAt: '2026-10-13T16:00:00.000Z', tier: 'available' },
            ],
          }),
          NOW,
        ),
      ).rejects.toBeInstanceOf(BadRequestException);
      await expect(
        service.commit(USER, commitDto({ slots: [] }), NOW),
      ).rejects.toBeInstanceOf(BadRequestException);
      expect(windows()[0].committedAt).toBeNull();
      expect(checkIns()).toHaveLength(0);
      expect(db.queries).toHaveLength(0);
      expect(track).not.toHaveBeenCalled();
    });

    it('still answers a replay after every hour has started', async () => {
      // A retry of a Check-In that saved answers with it, however late it arrives.
      profile();
      openWindow();
      await service.commit(USER, commitDto(), NOW);
      const later = new Date(NOW.getTime() + 6 * HOUR);
      const again = await service.commit(USER, commitDto(), later);
      expect(again.created).toBe(false);
      expect(again.state.active).toBe(true);
    });
  });
});
