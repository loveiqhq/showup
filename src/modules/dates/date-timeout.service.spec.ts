import { Logger } from '@nestjs/common';

import { Match } from '../matching/entities/match.entity';
import { DateTimeoutService } from './date-timeout.service';
import { DateArrivalCheckIn } from './entities/date-arrival-check-in.entity';
import { DateStatusChange } from './entities/date-status-change.entity';
import { DateEntity } from './entities/date.entity';
import { EndEntry, EndReason } from './util/date-end';
import { DateStatus } from './util/date-lifecycle';

const A = '00000000-0000-4000-8000-000000000001';
const B = '00000000-0000-4000-8000-000000000002';
const MEET = new Date('2026-11-28T18:00:00Z');
const NOW = new Date(MEET.getTime() + 2 * 3_600_000);
const row = (over: Record<string, unknown> = {}) => ({
  id: 'd1',
  userAId: A,
  userBId: B,
  status: DateStatus.Confirmed,
  scheduledAt: MEET,
  ...over,
});

function build(
  opts: {
    flag?: string;
    ids?: string[];
    rows?: Record<string, Record<string, unknown> | null>;
    committed?: string[];
    affected?: number;
    failOn?: string;
  } = {},
) {
  const ids = opts.ids ?? ['d1'];
  const locks: unknown[] = [];
  const em = {
    findOne: jest.fn(
      (_: unknown, o: { where: { id: string }; lock: unknown }) => {
        locks.push(o.lock);
        if (opts.failOn === o.where.id) {
          return Promise.reject(new TypeError('db down'));
        }
        const rows = opts.rows ?? {};
        return Promise.resolve(
          o.where.id in rows ? rows[o.where.id] : row({ id: o.where.id }),
        );
      },
    ),
    find: jest
      .fn()
      .mockResolvedValue((opts.committed ?? []).map((userId) => ({ userId }))),
    update: jest.fn().mockResolvedValue({ affected: opts.affected ?? 1 }),
    create: jest.fn((_: unknown, v: unknown) => v),
    save: jest.fn().mockResolvedValue(undefined),
  };
  const query = jest.fn().mockResolvedValue(ids.map((id) => ({ id })));
  const transaction = jest.fn((cb: (e: unknown) => unknown) => cb(em));
  const analytics = { trackServerEvent: jest.fn() };
  const config = { get: jest.fn(() => opts.flag) };
  const service = new DateTimeoutService(
    { transaction, query } as never,
    analytics as never,
    config as never,
  );
  return { service, em, query, transaction, analytics, config, locks };
}

describe('DateTimeoutService.closeDue (SHOWUP-200)', () => {
  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation();
    jest.spyOn(Logger.prototype, 'warn').mockImplementation();
  });
  afterEach(() => jest.restoreAllMocks());

  it('with the switch off closes nothing and runs no query', async () => {
    for (const flag of [undefined, 'false', '1']) {
      const t = build({ flag });
      await expect(t.service.closeDue(NOW)).resolves.toBe(0);
      expect(t.query).not.toHaveBeenCalled();
      expect(t.transaction).not.toHaveBeenCalled();
    }
  });

  it('reads the candidates by status, cutoff and limit, oldest first', async () => {
    const t = build({ flag: 'true', ids: [] });
    await expect(t.service.closeDue(NOW)).resolves.toBe(0);
    const [sql, params] = t.query.mock.calls[0];
    expect(sql).toMatch(/status = 'confirmed'/);
    expect(sql).toMatch(/scheduled_at <= \$1/);
    expect(sql).toMatch(/ORDER BY scheduled_at/);
    expect(sql).toMatch(/LIMIT \$2/);
    expect(params).toEqual([MEET, 100]);
  });

  it('closes each date in its own transaction under a write lock', async () => {
    const t = build({ flag: 'true', ids: ['d1', 'd2'] });
    await expect(t.service.closeDue(NOW)).resolves.toBe(2);
    expect(t.transaction).toHaveBeenCalledTimes(2);
    expect(t.locks).toEqual([
      { mode: 'pessimistic_write' },
      { mode: 'pessimistic_write' },
    ]);
  });

  it('sets the outcome with no ender, cancels the match and writes a status change', async () => {
    const t = build({ flag: 'true', committed: [A] });
    await t.service.closeDue(NOW);
    expect(t.em.update).toHaveBeenCalledWith(
      DateEntity,
      { id: 'd1', status: DateStatus.Confirmed },
      {
        status: DateStatus.TimedOut,
        endedById: null,
        endedAt: NOW,
        endReason: EndReason.TimedOut,
        endEntry: EndEntry.AutoSwitch,
      },
    );
    expect(t.em.update).toHaveBeenCalledWith(
      Match,
      { userAId: A, userBId: B, status: 'active' },
      { status: 'cancelled' },
    );
    expect(t.em.create).toHaveBeenCalledWith(DateStatusChange, {
      dateId: 'd1',
      fromStatus: DateStatus.Confirmed,
      toStatus: DateStatus.TimedOut,
      changedByUserId: null,
    });
    expect(t.em.find).toHaveBeenCalledWith(
      DateArrivalCheckIn,
      expect.objectContaining({
        where: expect.objectContaining({ dateId: 'd1' }),
      }),
    );
  });

  it('fires date_ended once after the commit, for participant A, with the count', async () => {
    const t = build({ flag: 'true', committed: [A, B] });
    await t.service.closeDue(NOW);
    expect(t.analytics.trackServerEvent).toHaveBeenCalledTimes(1);
    const sent = t.analytics.trackServerEvent.mock.calls[0][0];
    expect(sent.userId).toBe(A);
    expect(sent.eventName).toBe('date_ended');
    expect(sent.properties).toEqual({
      date_id: 'd1',
      end_reason: 'timed_out',
      entry: 'auto_switch',
      checked_in_count: 2,
      minutes_vs_start: 120,
    });
  });

  it('skips a locked row that is no longer confirmed', async () => {
    const t = build({
      flag: 'true',
      rows: { d1: row({ status: DateStatus.Ended }) },
    });
    await expect(t.service.closeDue(NOW)).resolves.toBe(0);
    expect(t.em.update).not.toHaveBeenCalled();
    expect(t.analytics.trackServerEvent).not.toHaveBeenCalled();
  });

  it('skips a row that is gone, and one whose time moved so it is no longer due', async () => {
    const t = build({
      flag: 'true',
      ids: ['d1', 'd2'],
      rows: {
        d1: null,
        d2: row({ id: 'd2', scheduledAt: new Date(MEET.getTime() + 60_000) }),
      },
    });
    await expect(t.service.closeDue(NOW)).resolves.toBe(0);
    expect(t.em.update).not.toHaveBeenCalled();
    expect(t.analytics.trackServerEvent).not.toHaveBeenCalled();
  });

  it('skips a lost conditional update and fires nothing', async () => {
    const t = build({ flag: 'true', affected: 0 });
    await expect(t.service.closeDue(NOW)).resolves.toBe(0);
    expect(t.analytics.trackServerEvent).not.toHaveBeenCalled();
    expect(t.em.save).not.toHaveBeenCalled();
  });

  it('one date throwing does not stop the next, and the log names only the id and error', async () => {
    const warn = jest.spyOn(Logger.prototype, 'warn').mockImplementation();
    const t = build({ flag: 'true', ids: ['d1', 'd2'], failOn: 'd1' });
    await expect(t.service.closeDue(NOW)).resolves.toBe(1);
    expect(t.analytics.trackServerEvent).toHaveBeenCalledTimes(1);
    const logged = warn.mock.calls.join(' ');
    expect(logged).toContain('d1');
    expect(logged).toContain('TypeError');
    expect(logged).not.toContain('db down');
  });

  it('sends no push of any kind: the service takes no dispatcher', () => {
    expect(DateTimeoutService.length).toBe(3);
  });
});
