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
    stampFails?: boolean;
    alreadyStamped?: boolean;
  }) {
    const calls: string[] = [];
    const scheduledAt = meetIn(opts.minutes);
    const date = {
      id: 'd1',
      userAId: 'a',
      userBId: 'b',
      status: DateStatus.Confirmed,
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
    const service = new ArrivalCheckInsService(
      rows as never,
      dates as never,
      profiles as never,
      analytics as never,
      dispatch as never,
    );
    return { service, calls, tracked, query };
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
