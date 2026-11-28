import { ConflictException } from '@nestjs/common';

import { ArrivalCheckInsService } from './arrival-check-ins.service';
import { DateStatus } from './util/date-lifecycle';

/**
 * The commit's "first check-in opens the chat" side (Date 03, 16 Nov). The statements themselves are
 * proven against a real database in test/arrival.e2e-spec.ts; here the order and the once-only
 * rules are proven with the database stubbed.
 */
describe('ArrivalCheckInsService.commit opens the chat', () => {
  const NOW = new Date('2026-11-16T12:00:00Z');
  const meetIn = (minutes: number) =>
    new Date(NOW.getTime() + minutes * 60_000);

  function setup(opts: {
    minutes: number;
    committedRows?: number;
    status?: DateStatus;
    stampFails?: boolean;
    alreadyStamped?: boolean;
  }) {
    const calls: string[] = [];
    const scheduledAt = meetIn(opts.minutes);
    const date = {
      id: 'd1',
      userAId: 'a',
      userBId: 'b',
      status: opts.status ?? DateStatus.Confirmed,
      scheduledAt,
    };
    const stampable =
      !opts.alreadyStamped &&
      scheduledAt.getTime() > NOW.getTime() + 90 * 60_000;
    const query = jest.fn((sql: string) => {
      if (sql.includes('UPDATE date_arrival_check_ins')) {
        return Promise.resolve(
          Array.from({ length: opts.committedRows ?? 1 }, () => ({ id: 'r' })),
        );
      }
      calls.push('stamp');
      if (opts.stampFails) return Promise.reject(new TypeError('boom'));
      return Promise.resolve(
        stampable
          ? [
              [
                {
                  user_a_id: 'a',
                  user_b_id: 'b',
                  scheduled_at: scheduledAt,
                },
              ],
              1,
            ]
          : [[], 0],
      );
    });
    const rows = { query, exists: jest.fn().mockResolvedValue(true) };
    const dates = {
      findOne: jest.fn().mockResolvedValue(date),
      query,
    };
    const profiles = { findOne: jest.fn().mockResolvedValue(null) };
    const tracked: Array<{ userId: string; properties?: object }> = [];
    const analytics = {
      trackServerEvent: jest.fn(
        (e: { userId: string; properties?: object }) => {
          calls.push('event');
          tracked.push(e);
          return Promise.resolve();
        },
      ),
    };
    const dispatch = {
      sendPush: jest.fn(() => {
        calls.push('push');
        return Promise.resolve();
      }),
    };
    const locks: unknown[] = [];
    const em = {
      findOne: jest.fn((_: unknown, o: { lock?: unknown }) => {
        locks.push(o.lock);
        return Promise.resolve(date);
      }),
      query,
    };
    const transaction = jest.fn((cb: (e: unknown) => unknown) => cb(em));
    const service = new ArrivalCheckInsService(
      rows as never,
      dates as never,
      profiles as never,
      analytics as never,
      dispatch as never,
      { transaction } as never,
    );
    return {
      service,
      calls,
      tracked,
      query,
      dispatch,
      locks,
      transaction,
      rows,
    };
  }

  it('stamps and fires arrival_check_in for both people BEFORE the push, at meet − 3 h', async () => {
    const { service, calls, tracked } = setup({ minutes: 180 });
    await service.commit('d1', 'a', NOW);
    expect(calls).toEqual(['stamp', 'event', 'event', 'push']);
    expect(tracked.map((e) => e.userId).sort()).toEqual(['a', 'b']);
    for (const e of tracked) {
      expect(e).toMatchObject({
        eventName: 'coordinate_unlocked',
        properties: { cause: 'arrival_check_in', minutes_before_start: 180 },
      });
    }
  });

  it('leaves a commit inside the time window to the job: no event', async () => {
    const { service, calls, tracked } = setup({ minutes: 60 });
    await service.commit('d1', 'a', NOW);
    expect(tracked).toEqual([]);
    expect(calls).toEqual(['stamp', 'push']);
  });

  it('fires nothing when the date was already stamped (the partner committed first)', async () => {
    const { service, tracked, calls } = setup({
      minutes: 180,
      alreadyStamped: true,
    });
    await service.commit('d1', 'b', NOW);
    expect(tracked).toEqual([]);
    expect(calls).toEqual(['stamp', 'push']);
  });

  it('an already-committed commit stamps nothing and fires nothing', async () => {
    const { service, calls, tracked } = setup({
      minutes: 180,
      committedRows: 0,
    });
    await service.commit('d1', 'a', NOW);
    expect(calls).toEqual([]);
    expect(tracked).toEqual([]);
  });

  it('a failed stamp does not fail the commit, and the push still goes', async () => {
    const { service, calls, tracked } = setup({
      minutes: 180,
      stampFails: true,
    });
    await expect(service.commit('d1', 'a', NOW)).resolves.toBeUndefined();
    expect(tracked).toEqual([]);
    expect(calls).toEqual(['stamp', 'push']);
  });
});

describe('the arrival guards against a timed-out date (SHOWUP-200)', () => {
  const NOW = new Date('2026-11-28T20:00:00Z');
  const refused = async (p: Promise<unknown>) => {
    try {
      await p;
    } catch (e) {
      return (e as ConflictException).getResponse() as {
        statusCode: number;
        code: string;
      };
    }
    throw new Error('expected a refusal');
  };

  it('commit re-checks the status under a FOR SHARE lock inside its transaction', async () => {
    const { service, locks, transaction } = setupGuard({});
    await service.commit('d1', 'a', NOW);
    expect(transaction).toHaveBeenCalledTimes(1);
    expect(locks).toEqual([{ mode: 'pessimistic_read' }]);
  });

  it('commit on a date that is no longer confirmed is 409 date_not_active, with no push and no chat open', async () => {
    const t = setupGuard({ status: DateStatus.TimedOut });
    const body = await refused(t.service.commit('d1', 'a', NOW));
    expect(body).toMatchObject({ statusCode: 409, code: 'date_not_active' });
    expect(t.dispatch.sendPush).not.toHaveBeenCalled();
    expect(t.query).not.toHaveBeenCalled();
  });

  it('commit that finds the row already committed is still a no-op, and a missing row 404s', async () => {
    const noop = setupGuard({ committedRows: 0, exists: true });
    await expect(noop.service.commit('d1', 'a', NOW)).resolves.toBeUndefined();
    expect(noop.dispatch.sendPush).not.toHaveBeenCalled();
    const missing = setupGuard({ committedRows: 0, exists: false });
    await expect(missing.service.commit('d1', 'a', NOW)).rejects.toThrow(
      'No check-in to commit',
    );
  });

  it('create on a non-confirmed date is 409 date_not_active', async () => {
    const t = setupGuard({ status: DateStatus.TimedOut });
    const body = await refused(
      t.service.create(
        'd1',
        'a',
        {
          latitude: 1,
          longitude: 2,
          entry: 'date_screen',
          clientId: 'c',
        } as never,
        NOW,
      ),
    );
    expect(body).toMatchObject({ statusCode: 409, code: 'date_not_active' });
    expect(t.query).not.toHaveBeenCalled();
  });

  it('undo on a non-confirmed date is 409 date_not_active and deletes nothing', async () => {
    const t = setupGuard({ status: DateStatus.TimedOut });
    const body = await refused(t.service.undo('d1', 'a', NOW));
    expect(body).toMatchObject({ statusCode: 409, code: 'date_not_active' });
    expect(t.query).not.toHaveBeenCalled();
  });

  it('undo on a confirmed date still deletes', async () => {
    const t = setupGuard({});
    await t.service.undo('d1', 'a', NOW);
    expect(t.query).toHaveBeenCalledTimes(1);
  });
});

function setupGuard(opts: {
  status?: DateStatus;
  committedRows?: number;
  exists?: boolean;
}) {
  const date = {
    id: 'd1',
    userAId: 'a',
    userBId: 'b',
    status: opts.status ?? DateStatus.Confirmed,
    scheduledAt: new Date('2026-11-28T18:00:00Z'),
  };
  const query = jest.fn((sql: string) => {
    if (sql.includes('UPDATE date_arrival_check_ins')) {
      return Promise.resolve(
        Array.from({ length: opts.committedRows ?? 1 }, () => ({ id: 'r' })),
      );
    }
    if (sql.includes('DELETE'))
      return Promise.resolve([[{ entry: 'date_screen', at: new Date() }], 1]);
    return Promise.resolve([[], 0]);
  });
  const locks: unknown[] = [];
  const em = {
    findOne: jest.fn((_: unknown, o: { lock?: unknown }) => {
      locks.push(o.lock);
      return Promise.resolve(date);
    }),
    query,
  };
  const transaction = jest.fn((cb: (e: unknown) => unknown) => cb(em));
  const dispatch = { sendPush: jest.fn().mockResolvedValue(undefined) };
  const service = new ArrivalCheckInsService(
    {
      query,
      exists: jest.fn().mockResolvedValue(opts.exists ?? true),
    } as never,
    { findOne: jest.fn().mockResolvedValue(date), query } as never,
    { findOne: jest.fn().mockResolvedValue(null) } as never,
    { trackServerEvent: jest.fn() } as never,
    dispatch as never,
    { transaction } as never,
  );
  return { service, query, dispatch, locks, transaction };
}
