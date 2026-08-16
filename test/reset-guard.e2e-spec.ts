import { assertSafeTarget, DISPOSABLE_HOSTS } from './reset-guard';

/**
 * Lives with the end-to-end configuration because it protects the end-to-end reset, but it touches
 * no database and runs in milliseconds.
 */
describe('End-to-end database reset guard', () => {
  it.each(DISPOSABLE_HOSTS)('allows the throwaway host %s', (host) => {
    expect(() => assertSafeTarget(host, 'showup', 'local')).not.toThrow();
  });

  it('refuses a host that is not local or throwaway', () => {
    expect(() =>
      assertSafeTarget('db.production.example.com', 'showup', 'local'),
    ).toThrow(/Refusing to reset the database/);
  });

  it('names the offending host so the mistake is obvious', () => {
    expect(() =>
      assertSafeTarget('db.production.example.com', 'showup', 'local'),
    ).toThrow(/db\.production\.example\.com/);
  });

  it('refuses in production even when the host looks local', () => {
    expect(() => assertSafeTarget('localhost', 'showup', 'production')).toThrow(
      /NODE_ENV is "production"/,
    );
  });
});
