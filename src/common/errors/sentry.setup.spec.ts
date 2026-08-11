import { REDACTED } from '../logging/redact';
import { initSentry } from './sentry.setup';

describe('initSentry', () => {
  let init: jest.Mock;

  beforeEach(() => {
    init = jest.fn();
  });

  it('does nothing at all when the switch is off', () => {
    const started = initSentry(
      { enabled: false, dsn: 'https://key@example.ingest.sentry.io/1', environment: 'local' },
      init,
    );

    expect(started).toBe(false);
    expect(init).not.toHaveBeenCalled();
  });

  it('does nothing when switched on but no destination is configured', () => {
    const started = initSentry({ enabled: true, dsn: undefined, environment: 'local' }, init);

    expect(started).toBe(false);
    expect(init).not.toHaveBeenCalled();
  });

  it('starts when switched on with a destination, tagged with the environment', () => {
    const started = initSentry(
      { enabled: true, dsn: 'https://key@example.ingest.sentry.io/1', environment: 'staging' },
      init,
    );

    expect(started).toBe(true);
    expect(init).toHaveBeenCalledTimes(1);
    expect(init.mock.calls[0][0]).toMatchObject({
      dsn: 'https://key@example.ingest.sentry.io/1',
      environment: 'staging',
      sendDefaultPii: false,
    });
  });

  it('scrubs every outgoing report through beforeSend', () => {
    initSentry(
      { enabled: true, dsn: 'https://key@example.ingest.sentry.io/1', environment: 'production' },
      init,
    );

    const { beforeSend } = init.mock.calls[0][0];
    const scrubbed: any = beforeSend({
      request: { headers: { authorization: 'Bearer x' }, data: { phone: '+49' } },
      user: { id: 'u1', email: 'a@b.c' },
    });

    expect(scrubbed.request.headers.authorization).toBeUndefined();
    expect(scrubbed.request.data.phone).toBe(REDACTED);
    expect(scrubbed.user.email).toBeUndefined();
    expect(scrubbed.user.id).toBe('u1');
  });
});
