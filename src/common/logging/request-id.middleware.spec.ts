import { RequestIdMiddleware } from './request-id.middleware';
import { getRequestId } from './request-context';

describe('RequestIdMiddleware', () => {
  const run = (headers: Record<string, unknown>) => {
    const req: any = { headers };
    const res: any = { setHeader: jest.fn() };
    let seen: string | undefined;
    new RequestIdMiddleware().use(req, res, () => {
      seen = getRequestId();
    });
    return { res, seen };
  };

  it('generates an id and makes it available to everything downstream', () => {
    const { seen } = run({});
    expect(seen).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  });

  it('returns the id on the response, so a caller can quote it in a bug report', () => {
    const { res, seen } = run({});
    expect(res.setHeader).toHaveBeenCalledWith('x-request-id', seen);
  });

  it('honours an incoming x-request-id so one trace can span callers', () => {
    const { seen } = run({ 'x-request-id': 'from-the-client' });
    expect(seen).toBe('from-the-client');
  });

  it('ignores a blank incoming header and generates its own', () => {
    const { seen } = run({ 'x-request-id': '   ' });
    expect(seen).not.toBe('   ');
    expect(seen).toBeTruthy();
  });

  it('does not leak the id after the request finishes', () => {
    run({});
    expect(getRequestId()).toBeUndefined();
  });
});
