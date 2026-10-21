import { EntityManager, FindOperator } from 'typeorm';

import { FLOW_POSITION_COMPLETE } from '../../profiles/util/flow-position';
import { CheckInWindow } from '../entities/check-in-window.entity';
import { ensureCheckInWindow } from './check-in-window';

/**
 * Which Check-In window is the user's, without a database (SHOWUP-177).
 *
 * `test/check-in-flow.e2e-spec.ts` runs the same function against real Postgres under the profile
 * row lock. These pin the BRANCHES of the rule decided on 21 October 2026: an unfinished window has
 * no clock and stays the user's however old it is; a finished one stays theirs for its 48 hours,
 * "over" including the closing instant; and a new window -- unfinished, no clock -- opens only once
 * profile creation is complete.
 *
 * The repository is a hand-rolled fake over an array (as in `prompts.service.spec.ts`), and it reads
 * the clauses the function actually sends -- `IsNull` on `committedAt` OR `MoreThan` on `closesAt` --
 * rather than answering a canned value, so a change to the query fails here.
 */
describe('ensureCheckInWindow', () => {
  const USER = 'user-1';
  const NOW = new Date('2026-10-13T16:20:00.000Z');
  const HOUR = 60 * 60 * 1000;
  let rows: CheckInWindow[];
  let saves: number;

  /** An unfinished window: no clock. */
  const unfinished = (
    id: string,
    openedAt: Date,
    userId = USER,
  ): CheckInWindow =>
    ({
      id,
      userId,
      openedAt,
      closesAt: null,
      committedAt: null,
      motivation: null,
      vibes: null,
      uplift: null,
    }) as CheckInWindow;

  /** A finished window: committed, closing 48 hours later. */
  const finished = (id: string, committedAt: Date): CheckInWindow => ({
    ...unfinished(id, new Date(committedAt.getTime() - HOUR)),
    committedAt,
    closesAt: new Date(committedAt.getTime() + 48 * HOUR),
    motivation: 'friends',
  });

  type Clause = {
    userId: string;
    committedAt?: FindOperator<unknown>;
    closesAt?: FindOperator<Date>;
  };

  /** A clause as Postgres reads it: a null `closes_at` is never "more than" anything. */
  const holds = (r: CheckInWindow, c: Clause): boolean => {
    if (r.userId !== c.userId) return false;
    if (c.committedAt) {
      expect(c.committedAt.type).toBe('isNull');
      if (r.committedAt !== null) return false;
    }
    if (c.closesAt) {
      expect(c.closesAt.type).toBe('moreThan');
      if (r.closesAt === null) return false;
      if (r.closesAt.getTime() <= c.closesAt.value.getTime()) return false;
    }
    return true;
  };

  const repo = {
    findOne: ({
      where,
      order,
    }: {
      where: Clause[];
      order: { openedAt: 'DESC' };
    }) => {
      expect(Array.isArray(where)).toBe(true);
      expect(where).toHaveLength(2);
      expect(order).toEqual({ openedAt: 'DESC' });
      const current = rows
        .filter((r) => where.some((c) => holds(r, c)))
        .sort((a, b) => b.openedAt.getTime() - a.openedAt.getTime());
      return Promise.resolve(current[0] ?? null);
    },
    create: (row: Partial<CheckInWindow>) => ({ ...row }) as CheckInWindow,
    save: (row: CheckInWindow) => {
      saves += 1;
      const created = { ...row, id: `w-${rows.length + 1}` };
      rows.push(created);
      return Promise.resolve(created);
    },
  };
  const em = {
    getRepository: (entity: unknown) => {
      expect(entity).toBe(CheckInWindow);
      return repo;
    },
  } as unknown as EntityManager;

  const current = () =>
    ensureCheckInWindow(em, USER, FLOW_POSITION_COMPLETE, NOW);

  beforeEach(() => {
    rows = [];
    saves = 0;
  });

  it('keeps an unfinished window however long ago it opened, and writes nothing', async () => {
    // The Check-In was left halfway four days ago: nothing has been counted, so it is still theirs.
    const left = unfinished('w-left', new Date(NOW.getTime() - 100 * HOUR));
    rows.push(left);
    expect(await current()).toBe(left);
    expect(saves).toBe(0);
  });

  it('keeps an unfinished window even when the flow position is not complete', async () => {
    // A window opened at completion stays the user's window if the position is later edited back.
    const left = unfinished('w-left', NOW);
    rows.push(left);
    expect(await ensureCheckInWindow(em, USER, 'basics', NOW)).toBe(left);
    expect(await ensureCheckInWindow(em, USER, null, NOW)).toBe(left);
    expect(saves).toBe(0);
  });

  it('keeps a finished window for the 48 hours after Find my date', async () => {
    const done = finished('w-done', new Date(NOW.getTime() - 47 * HOUR));
    rows.push(done);
    expect(await current()).toBe(done);
    expect(saves).toBe(0);
  });

  it('answers the latest current window when two are current', async () => {
    const older = unfinished('w-older', new Date(NOW.getTime() - 10 * HOUR));
    const newer = unfinished('w-newer', new Date(NOW.getTime() - HOUR));
    rows.push(older, newer);
    expect(await current()).toBe(newer);
  });

  it('never answers a window of another user', async () => {
    rows.push(unfinished('w-other', NOW, 'user-2'));
    const got = await current();
    expect(got?.id).not.toBe('w-other');
    expect(got?.userId).toBe(USER);
  });

  it("opens a new unfinished window, with no clock, once the last Check-In's 48 hours are over", async () => {
    rows.push(finished('w-over', new Date(NOW.getTime() - 50 * HOUR)));
    const got = await current();
    expect(got).toMatchObject({
      userId: USER,
      openedAt: NOW,
      closesAt: null,
      committedAt: null,
      motivation: null,
      vibes: null,
      uplift: null,
    });
    expect(got?.id).not.toBe('w-over');
    expect(saves).toBe(1);
  });

  it('treats a Check-In closing at this instant as over', async () => {
    // `MoreThan`, not `MoreThanOrEqual`: at 48:00:00 after Find my date the Check-In is over.
    rows.push(finished('w-edge', new Date(NOW.getTime() - 48 * HOUR)));
    const got = await current();
    expect(got?.id).not.toBe('w-edge');
    expect(saves).toBe(1);
  });

  it('opens nothing before profile creation is complete', async () => {
    expect(await ensureCheckInWindow(em, USER, 'basics', NOW)).toBeNull();
    expect(await ensureCheckInWindow(em, USER, null, NOW)).toBeNull();
    expect(saves).toBe(0);
  });

  it('measures from the current time when no clock is passed', async () => {
    const before = Date.now();
    const got = await ensureCheckInWindow(em, USER, FLOW_POSITION_COMPLETE);
    const after = Date.now();
    expect(got?.openedAt.getTime()).toBeGreaterThanOrEqual(before);
    expect(got?.openedAt.getTime()).toBeLessThanOrEqual(after);
    expect(got?.closesAt).toBeNull();
  });
});
