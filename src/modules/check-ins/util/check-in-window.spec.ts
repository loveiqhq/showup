import { EntityManager, FindOperator } from 'typeorm';

import { FLOW_POSITION_COMPLETE } from '../../profiles/util/flow-position';
import { CheckInWindow } from '../entities/check-in-window.entity';
import { CHECK_IN_WINDOW_HOURS } from './check-in-flow';
import { ensureCheckInWindow } from './check-in-window';

/**
 * When a Check-In window opens, without a database (SHOWUP-177).
 *
 * `test/check-in-flow.e2e-spec.ts` runs the same function against real Postgres under the profile
 * row lock. These pin the BRANCHES: an open window is kept whatever the flow position, a closed one
 * is replaced only once profile creation is complete, and "closed" includes the closing instant.
 *
 * The repository is a hand-rolled fake over an array (as in `prompts.service.spec.ts`), and it reads
 * the `MoreThan` operator the function actually sends rather than answering a canned value -- so a
 * change to the query that stopped filtering on `closesAt` fails here.
 */
describe('ensureCheckInWindow', () => {
  const USER = 'user-1';
  const NOW = new Date('2026-10-13T16:20:00.000Z');
  const HOUR = 60 * 60 * 1000;
  let rows: CheckInWindow[];
  let saves: number;

  const window = (
    id: string,
    openedAt: Date,
    closesAt: Date,
    userId = USER,
  ): CheckInWindow =>
    ({
      id,
      userId,
      openedAt,
      closesAt,
      committedAt: null,
      motivation: null,
      vibes: null,
      uplift: null,
    }) as CheckInWindow;

  const repo = {
    findOne: ({
      where,
      order,
    }: {
      where: { userId: string; closesAt: FindOperator<Date> };
      order: { openedAt: 'DESC' };
    }) => {
      expect(where.closesAt).toBeInstanceOf(FindOperator);
      expect(where.closesAt.type).toBe('moreThan');
      expect(order).toEqual({ openedAt: 'DESC' });
      const after = where.closesAt.value.getTime();
      const open = rows
        .filter(
          (r) => r.userId === where.userId && r.closesAt.getTime() > after,
        )
        .sort((a, b) => b.openedAt.getTime() - a.openedAt.getTime());
      return Promise.resolve(open[0] ?? null);
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

  beforeEach(() => {
    rows = [];
    saves = 0;
  });

  it('keeps the open window and writes nothing', async () => {
    const open = window(
      'w-open',
      new Date(NOW.getTime() - HOUR),
      new Date(NOW.getTime() + 47 * HOUR),
    );
    rows.push(open);
    const got = await ensureCheckInWindow(
      em,
      USER,
      FLOW_POSITION_COMPLETE,
      NOW,
    );
    expect(got).toBe(open);
    expect(saves).toBe(0);
  });

  it('keeps an open window even when the flow position is not complete', async () => {
    // A window opened at completion stays the user's window if the position is later edited back.
    const open = window('w-open', NOW, new Date(NOW.getTime() + HOUR));
    rows.push(open);
    expect(await ensureCheckInWindow(em, USER, 'basics', NOW)).toBe(open);
    expect(await ensureCheckInWindow(em, USER, null, NOW)).toBe(open);
    expect(saves).toBe(0);
  });

  it('answers the latest open window when two are open', async () => {
    const older = window(
      'w-older',
      new Date(NOW.getTime() - 10 * HOUR),
      new Date(NOW.getTime() + 38 * HOUR),
    );
    const newer = window(
      'w-newer',
      new Date(NOW.getTime() - HOUR),
      new Date(NOW.getTime() + 47 * HOUR),
    );
    rows.push(older, newer);
    expect(
      await ensureCheckInWindow(em, USER, FLOW_POSITION_COMPLETE, NOW),
    ).toBe(newer);
  });

  it('never answers a window of another user', async () => {
    rows.push(window('w-other', NOW, new Date(NOW.getTime() + HOUR), 'user-2'));
    const got = await ensureCheckInWindow(
      em,
      USER,
      FLOW_POSITION_COMPLETE,
      NOW,
    );
    expect(got?.id).not.toBe('w-other');
    expect(got?.userId).toBe(USER);
  });

  it('opens a fresh 48-hour window once the last has closed', async () => {
    rows.push(
      window(
        'w-closed',
        new Date(NOW.getTime() - 50 * HOUR),
        new Date(NOW.getTime() - 2 * HOUR),
      ),
    );
    const got = await ensureCheckInWindow(
      em,
      USER,
      FLOW_POSITION_COMPLETE,
      NOW,
    );
    expect(got).toMatchObject({
      userId: USER,
      openedAt: NOW,
      closesAt: new Date(NOW.getTime() + CHECK_IN_WINDOW_HOURS * HOUR),
      committedAt: null,
      motivation: null,
      vibes: null,
      uplift: null,
    });
    expect(got?.id).not.toBe('w-closed');
    expect(saves).toBe(1);
  });

  it('treats a window closing at this instant as closed', async () => {
    // `MoreThan`, not `MoreThanOrEqual`: at 48:00:00 the window is over, as the expiry job says.
    rows.push(window('w-edge', new Date(NOW.getTime() - 48 * HOUR), NOW));
    const got = await ensureCheckInWindow(
      em,
      USER,
      FLOW_POSITION_COMPLETE,
      NOW,
    );
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
    expect(got!.closesAt.getTime() - got!.openedAt.getTime()).toBe(
      CHECK_IN_WINDOW_HOURS * HOUR,
    );
  });
});
