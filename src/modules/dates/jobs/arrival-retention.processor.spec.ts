import { ArrivalRetentionProcessor } from './arrival-retention.processor';

describe('ArrivalRetentionProcessor (SHOWUP-195)', () => {
  it('runs the sweep and reports how many rows it removed', async () => {
    const arrivals = { deleteExpired: jest.fn().mockResolvedValue(3) };
    const processor = new ArrivalRetentionProcessor(arrivals as any);
    await expect(processor.process()).resolves.toEqual({ deleted: 3 });
    expect(arrivals.deleteExpired).toHaveBeenCalledTimes(1);
  });
});
