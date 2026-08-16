import { getRequestId, runWithRequestId } from './request-context';

describe('request context', () => {
  it('has no request id outside a request', () => {
    expect(getRequestId()).toBeUndefined();
  });

  it('exposes the id to everything run inside the request', () => {
    const seen = runWithRequestId('req-1', () => getRequestId());
    expect(seen).toBe('req-1');
  });

  it('keeps the id across asynchronous work, which is the whole point', async () => {
    const seen = await runWithRequestId('req-2', async () => {
      await new Promise((resolve) => setTimeout(resolve, 1));
      return getRequestId();
    });
    expect(seen).toBe('req-2');
  });

  it('keeps concurrent requests separate', async () => {
    const [a, b] = await Promise.all([
      runWithRequestId('req-a', async () => {
        await new Promise((resolve) => setTimeout(resolve, 5));
        return getRequestId();
      }),
      runWithRequestId('req-b', async () => getRequestId()),
    ]);

    expect(a).toBe('req-a');
    expect(b).toBe('req-b');
  });

  it('does not leak the id after the request finishes', () => {
    runWithRequestId('req-3', () => getRequestId());
    expect(getRequestId()).toBeUndefined();
  });
});
