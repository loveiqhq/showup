import { DateTimeoutProcessor } from './date-timeout.processor';

describe('DateTimeoutProcessor (SHOWUP-200)', () => {
  it('runs the sweep once and reports how many dates it closed', async () => {
    const timeouts = { closeDue: jest.fn().mockResolvedValue(3) };
    const processor = new DateTimeoutProcessor(timeouts as any);
    await expect(processor.process()).resolves.toEqual({ closed: 3 });
    expect(timeouts.closeDue).toHaveBeenCalledTimes(1);
  });
});
