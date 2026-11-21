import { SafetyService } from './safety.service';

function make(opts: {
  byViewer?: string[];
  ofViewer?: string[];
  pairs?: Array<{ userLow: string; userHigh: string }>;
}) {
  const blocks = {
    find: jest.fn((q: { where: { blockerId?: string } }) =>
      Promise.resolve(
        q.where.blockerId
          ? (opts.byViewer ?? []).map((id) => ({ blockedId: id }))
          : (opts.ofViewer ?? []).map((id) => ({ blockerId: id })),
      ),
    ),
  };
  const pairBlocks = { find: jest.fn().mockResolvedValue(opts.pairs ?? []) };
  return new SafetyService(blocks as never, pairBlocks as never);
}

describe('SafetyService.blockedUserIdsFor', () => {
  it('unions a date pair block when the viewer is the low side', async () => {
    const svc = make({ pairs: [{ userLow: 'me', userHigh: 'her' }] });
    expect(await svc.blockedUserIdsFor('me')).toEqual(['her']);
  });
  it('unions a date pair block when the viewer is the high side', async () => {
    const svc = make({ pairs: [{ userLow: 'her', userHigh: 'me' }] });
    expect(await svc.blockedUserIdsFor('me')).toEqual(['her']);
  });
  it('de-duplicates with ordinary blocks', async () => {
    const svc = make({
      byViewer: ['her'],
      ofViewer: ['x'],
      pairs: [{ userLow: 'her', userHigh: 'me' }],
    });
    expect((await svc.blockedUserIdsFor('me')).sort()).toEqual(['her', 'x']);
  });
});
