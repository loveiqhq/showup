import { QueryFailedError } from 'typeorm';

import { ChangeSuggestionsService } from './change-suggestions.service';
import {
  ChangeEntry,
  ChangeKind,
  ChangeStatus,
} from './entities/date-change-suggestion.entity';

const DATE = { id: 'd1', userAId: 'me', userBId: 'them' };
const stored = (over: Record<string, unknown> = {}) => ({
  id: 's1',
  dateId: 'd1',
  senderId: 'them',
  kind: ChangeKind.Time,
  valueTime: new Date('2026-11-17T19:30:00Z'),
  placeId: null,
  placeName: null,
  placeType: null,
  placeAddress: null,
  wasTime: new Date('2026-11-17T19:00:00Z'),
  wasPlaceName: 'Cafe',
  status: ChangeStatus.Pending,
  deadline: new Date('2026-11-17T18:30:00Z'),
  createdAt: new Date('2026-11-17T17:00:00Z'),
  answeredAt: null,
  expiredAt: null,
  expiredBindingTime: null,
  expiredBindingPlaceName: null,
  ...over,
});

function build(opts: {
  transaction: (cb: (em: unknown) => unknown) => Promise<unknown>;
  rowsFindOne?: jest.Mock;
}) {
  const analytics = { trackServerEvent: jest.fn() };
  const dispatch = { sendPush: jest.fn() };
  const scheduler = { rescheduleDateReminder: jest.fn() };
  const service = new ChangeSuggestionsService(
    { transaction: opts.transaction } as never,
    { findOne: opts.rowsFindOne ?? jest.fn() } as never,
    { findOne: jest.fn().mockResolvedValue(DATE) } as never,
    { findOne: jest.fn().mockResolvedValue(null) } as never,
    { details: jest.fn() } as never,
    {} as never,
    analytics as never,
    dispatch as never,
    scheduler as never,
  );
  return { service, analytics, dispatch, scheduler };
}

const uniqueViolation = () => {
  const err = new QueryFailedError('INSERT', [], new Error('dup'));
  (err as { driverError: unknown }).driverError = { code: '23505' };
  return err;
};

describe('ChangeSuggestionsService.suggest, concurrent resend', () => {
  const dto = {
    kind: ChangeKind.Time,
    time: '2026-11-17T19:30:00Z',
    entry: ChangeEntry.ChatChip,
    clientId: '11111111-1111-4111-8111-111111111111',
  };

  it('answers the winner on a (sender, client id) unique violation: no event, no push', async () => {
    // First look (before the lock): nothing yet. After the violation: the winner's row.
    const rowsFindOne = jest
      .fn()
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce(stored({ senderId: 'me' }));
    const { service, analytics, dispatch } = build({
      transaction: () => Promise.reject(uniqueViolation()),
      rowsFindOne,
    });
    const out = await service.suggest('d1', 'me', dto);
    expect(out.id).toBe('s1');
    expect(out.byYou).toBe(true);
    expect(analytics.trackServerEvent).not.toHaveBeenCalled();
    expect(dispatch.sendPush).not.toHaveBeenCalled();
  });

  it('rethrows any other failure, and a violation with no stored row', async () => {
    const boom = new Error('db down');
    await expect(
      build({
        transaction: () => Promise.reject(boom),
        rowsFindOne: jest.fn().mockResolvedValue(null),
      }).service.suggest('d1', 'me', dto),
    ).rejects.toBe(boom);
    await expect(
      build({
        transaction: () => Promise.reject(uniqueViolation()),
        rowsFindOne: jest.fn().mockResolvedValue(null),
      }).service.suggest('d1', 'me', dto),
    ).rejects.toBeInstanceOf(QueryFailedError);
  });
});

describe('ChangeSuggestionsService.agree, a repeat of an agree', () => {
  const run = (row: Record<string, unknown>, viewer = 'me') => {
    const em = {
      findOne: jest
        .fn()
        .mockResolvedValueOnce({ ...DATE, status: 'confirmed' })
        .mockResolvedValueOnce(row),
    };
    const built = build({ transaction: (cb) => Promise.resolve(cb(em)) });
    return {
      ...built,
      em,
      result: built.service.agree('d1', 's1', viewer, {
        surface: 'thread',
      }),
    };
  };

  it('the receiver agreeing again to an agreed one gets it back, firing nothing', async () => {
    const t = run(
      stored({
        status: ChangeStatus.Agreed,
        answeredAt: new Date('2026-11-17T17:10:00Z'),
      }),
    );
    const out = await t.result;
    expect(out.status).toBe('agreed');
    expect(out.answeredAt).toBe('2026-11-17T17:10:00.000Z');
    expect(t.analytics.trackServerEvent).not.toHaveBeenCalled();
    expect(t.dispatch.sendPush).not.toHaveBeenCalled();
    expect(t.scheduler.rescheduleDateReminder).not.toHaveBeenCalled();
  });

  it('a replaced or expired one is still a 409', async () => {
    await expect(
      run(stored({ status: ChangeStatus.Replaced })).result,
    ).rejects.toMatchObject({ status: 409 });
    await expect(
      run(stored({ status: ChangeStatus.Expired })).result,
    ).rejects.toMatchObject({ status: 409 });
  });

  it('the sender agreeing to their own agreed one is a 409', async () => {
    await expect(
      run(stored({ status: ChangeStatus.Agreed, senderId: 'me' })).result,
    ).rejects.toMatchObject({ status: 409 });
  });
});
