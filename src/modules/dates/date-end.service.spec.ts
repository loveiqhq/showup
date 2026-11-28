import {
  ConflictException,
  ForbiddenException,
  Logger,
  NotFoundException,
} from '@nestjs/common';

import { DateEndService } from './date-end.service';
import { DateEntity } from './entities/date.entity';
import { Match } from '../matching/entities/match.entity';
import { EndEntry, EndReason } from './util/date-end';
import { DateStatus } from './util/date-lifecycle';

const ME = '00000000-0000-4000-8000-000000000001';
const THEM = '00000000-0000-4000-8000-000000000002';
const NOW = new Date('2026-11-21T19:30:00Z');
const dateRow = (over: Record<string, unknown> = {}) => ({
  id: 'd1',
  userAId: ME,
  userBId: THEM,
  status: DateStatus.Confirmed,
  scheduledAt: new Date('2026-11-21T19:00:00Z'),
  ...over,
});

function build(
  opts: {
    row?: Record<string, unknown>;
    committed?: string[];
    affected?: number;
    flag?: string;
    nodeEnv?: string;
    pushFails?: boolean;
  } = {},
) {
  const row = opts.row ?? dateRow();
  const em = {
    findOne: jest.fn().mockResolvedValue(row),
    find: jest
      .fn()
      .mockResolvedValue((opts.committed ?? []).map((userId) => ({ userId }))),
    update: jest.fn().mockResolvedValue({ affected: opts.affected ?? 1 }),
    create: jest.fn((_: unknown, v: unknown) => v),
    save: jest.fn().mockResolvedValue(undefined),
  };
  const analytics = { trackServerEvent: jest.fn() };
  const flags = {
    create: jest.fn((v: unknown) => v),
    save: jest.fn().mockResolvedValue(undefined),
  };
  const config = {
    get: jest.fn((key: string) =>
      key === 'DATE_ENDS_ENABLED' ? opts.flag : (opts.nodeEnv ?? 'test'),
    ),
  };
  const dispatch = {
    sendPush: opts.pushFails
      ? jest.fn().mockRejectedValue(new Error('push down'))
      : jest.fn().mockResolvedValue({ status: 'sent', delivered: 1 }),
  };
  const names: Record<string, string> = { [ME]: 'Max', [THEM]: 'Leonie' };
  const service = new DateEndService(
    { transaction: (cb: (e: unknown) => unknown) => cb(em) } as never,
    { findOne: jest.fn().mockResolvedValue(row) } as never,
    flags as never,
    {
      findOne: jest.fn(({ where }: { where: { userId: string } }) =>
        Promise.resolve({ displayName: names[where.userId] }),
      ),
    } as never,
    analytics as never,
    config as never,
    dispatch as never,
  );
  return { service, em, analytics, flags, dispatch };
}

const body = (reason: EndReason, entry = EndEntry.EndDateBar) => ({
  reason,
  entry,
});
const code = async (p: Promise<unknown>) => {
  try {
    await p;
  } catch (e) {
    return (e as ConflictException).getResponse() as {
      code?: string;
      statusCode?: number;
    };
  }
  throw new Error('did not throw');
};

describe('DateEndService.end', () => {
  it('stores the reason, entry, ender, time and both check-in states, and closes the match', async () => {
    const { service, em } = build({ committed: [ME] });
    const out = await service.end(
      'd1',
      ME,
      body(EndReason.Uncomfortable, EndEntry.Report),
      NOW,
    );
    expect(out).toEqual({
      dateId: 'd1',
      name: 'Leonie',
      endedByYou: true,
      reason: 'uncomfortable',
    });
    expect(em.update).toHaveBeenCalledWith(
      DateEntity,
      { id: 'd1', status: DateStatus.Confirmed },
      {
        status: DateStatus.Ended,
        endedById: ME,
        endedAt: NOW,
        endReason: EndReason.Uncomfortable,
        endEntry: EndEntry.Report,
        enderCheckedIn: true,
        partnerCheckedIn: false,
      },
    );
    expect(em.update).toHaveBeenCalledWith(
      Match,
      expect.objectContaining({ status: 'active' }),
      { status: 'cancelled' },
    );
  });

  it('fires date_ended once, with the six fields and checked_in_count', async () => {
    const { service, analytics } = build({ committed: [ME, THEM] });
    await service.end('d1', ME, body(EndReason.PartnerNoShow), NOW);
    expect(analytics.trackServerEvent).toHaveBeenCalledTimes(1);
    expect(analytics.trackServerEvent).toHaveBeenCalledWith({
      userId: ME,
      eventName: 'date_ended',
      properties: {
        date_id: 'd1',
        end_reason: 'partner_no_show',
        entry: 'end_date_bar',
        checked_in_count: 2,
        ender_checked_in: true,
        partner_checked_in: true,
        minutes_vs_start: 30,
      },
    });
  });

  it('sends date_partner_ended to the match only, once, after the end commits', async () => {
    const { service, dispatch, em } = build({ committed: [ME] });
    await service.end('d1', ME, body(EndReason.Uncomfortable), NOW);
    expect(dispatch.sendPush).toHaveBeenCalledTimes(1);
    expect(dispatch.sendPush).toHaveBeenCalledWith(
      THEM,
      'date_partner_ended',
      { name: 'Max' },
      { type: 'date_partner_ended', dateId: 'd1' },
    );
    expect(dispatch.sendPush).not.toHaveBeenCalledWith(
      ME,
      expect.anything(),
      expect.anything(),
      expect.anything(),
    );
    expect(em.update.mock.invocationCallOrder[0]).toBeLessThan(
      dispatch.sendPush.mock.invocationCallOrder[0],
    );
  });

  it('a failed push never fails the end', async () => {
    const warn = jest.spyOn(Logger.prototype, 'warn').mockImplementation();
    const { service } = build({ pushFails: true });
    await expect(
      service.end('d1', ME, body(EndReason.Met), NOW),
    ).resolves.toMatchObject({ reason: 'met' });
    expect(warn).toHaveBeenCalledWith(expect.not.stringContaining('Leonie'));
    warn.mockRestore();
  });

  it('a refused end pushes nothing', async () => {
    const ended = build({ row: dateRow({ status: DateStatus.Ended }) });
    await code(ended.service.end('d1', ME, body(EndReason.Met), NOW));
    const raced = build({ affected: 0 });
    await code(raced.service.end('d1', ME, body(EndReason.Met), NOW));
    const early = build({ committed: [ME] });
    await code(
      early.service.end(
        'd1',
        ME,
        body(EndReason.PartnerNoShow),
        new Date('2026-11-21T18:00:00Z'),
      ),
    );
    const unchecked = build();
    await code(
      unchecked.service.end('d1', ME, body(EndReason.Uncomfortable), NOW),
    );
    for (const b of [ended, raced, early, unchecked]) {
      expect(b.dispatch.sendPush).not.toHaveBeenCalled();
    }
  });

  it('refuses a date that is not confirmed with 409 date_not_active, firing nothing', async () => {
    const { service, analytics, em } = build({
      row: dateRow({ status: DateStatus.Ended }),
    });
    const res = await code(service.end('d1', ME, body(EndReason.Met), NOW));
    expect(res).toMatchObject({ statusCode: 409, code: 'date_not_active' });
    expect(em.update).not.toHaveBeenCalled();
    expect(analytics.trackServerEvent).not.toHaveBeenCalled();
  });

  it('answers a lost race (the conditional update changed nothing) as date_not_active', async () => {
    const { service, analytics } = build({ affected: 0 });
    const res = await code(service.end('d1', ME, body(EndReason.Met), NOW));
    expect(res.code).toBe('date_not_active');
    expect(analytics.trackServerEvent).not.toHaveBeenCalled();
  });

  it('refuses a no-show before the agreed time, and a no-show or discomfort end with no check-in', async () => {
    const early = new Date('2026-11-21T18:00:00Z');
    expect(
      (
        await code(
          build({ committed: [ME] }).service.end(
            'd1',
            ME,
            body(EndReason.PartnerNoShow),
            early,
          ),
        )
      ).code,
    ).toBe('before_meet');
    expect(
      (
        await code(
          build().service.end('d1', ME, body(EndReason.PartnerNoShow), NOW),
        )
      ).code,
    ).toBe('not_checked_in');
    expect(
      (
        await code(
          build({ committed: [THEM] }).service.end(
            'd1',
            ME,
            body(EndReason.Uncomfortable),
            NOW,
          ),
        )
      ).code,
    ).toBe('not_checked_in');
  });

  it('lets We met end with no check-in at all', async () => {
    const { service, analytics } = build();
    await expect(
      service.end('d1', ME, body(EndReason.Met), NOW),
    ).resolves.toMatchObject({ reason: 'met' });
    expect(analytics.trackServerEvent).toHaveBeenCalledTimes(1);
  });

  it('is a 404 with the switch off, a 403 for a stranger', async () => {
    await expect(
      build({ flag: 'false' }).service.end('d1', ME, body(EndReason.Met), NOW),
    ).rejects.toBeInstanceOf(NotFoundException);
    await expect(
      build().service.end(
        'd1',
        '00000000-0000-4000-8000-000000000009',
        body(EndReason.Met),
        NOW,
      ),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });
});

describe('DateEndService.flagDiscomfort', () => {
  it('stores one row per call and fires problem_stay_flagged each time', async () => {
    const { service, flags, analytics } = build();
    await service.flagDiscomfort('d1', ME, NOW);
    await service.flagDiscomfort('d1', ME, NOW);
    expect(flags.save).toHaveBeenCalledTimes(2);
    expect(flags.save).toHaveBeenCalledWith({ dateId: 'd1', userId: ME });
    expect(analytics.trackServerEvent).toHaveBeenCalledTimes(2);
    expect(analytics.trackServerEvent).toHaveBeenCalledWith({
      userId: ME,
      eventName: 'problem_stay_flagged',
      properties: { date_id: 'd1', minutes_vs_start: 30 },
    });
  });

  it('is a 404 with the switch off and a 409 once the date is over', async () => {
    await expect(
      build({ flag: 'false' }).service.flagDiscomfort('d1', ME, NOW),
    ).rejects.toBeInstanceOf(NotFoundException);
    const over = build({ row: dateRow({ status: DateStatus.Cancelled }) });
    expect((await code(over.service.flagDiscomfort('d1', ME, NOW))).code).toBe(
      'date_not_active',
    );
    expect(over.flags.save).not.toHaveBeenCalled();
  });
});
