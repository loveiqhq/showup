import { ChangeExpiryProcessor } from './change-expiry.processor';

describe('ChangeExpiryProcessor (SHOWUP-196)', () => {
  it('runs the expiry and reports how many suggestions it expired', async () => {
    const changes = { expireDue: jest.fn().mockResolvedValue(2) };
    const processor = new ChangeExpiryProcessor(changes as any);
    await expect(processor.process()).resolves.toEqual({ expired: 2 });
    expect(changes.expireDue).toHaveBeenCalledTimes(1);
  });
});
