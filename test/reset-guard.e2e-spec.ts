import {
  assertSeparateFromDevelopment,
  testDatabaseName,
} from './e2e-database';
import { assertSafeTarget, DISPOSABLE_HOSTS } from './reset-guard';

/**
 * Lives with the end-to-end configuration because it protects the end-to-end reset, but it touches
 * no database and runs in milliseconds.
 */
describe('End-to-end database reset guard', () => {
  it.each(DISPOSABLE_HOSTS)('allows the throwaway host %s', (host) => {
    expect(() => assertSafeTarget(host, 'showup_test', 'local')).not.toThrow();
  });

  it('refuses the development database, even on localhost', () => {
    // The one that used to be emptied by every run, taking every local account with it.
    expect(() => assertSafeTarget('localhost', 'showup', 'local')).toThrow(
      /"showup" is not a test database/,
    );
  });

  it('refuses a host that is not local or throwaway', () => {
    expect(() =>
      assertSafeTarget('db.production.example.com', 'showup_test', 'local'),
    ).toThrow(/Refusing to reset the database/);
  });

  it('names the offending host so the mistake is obvious', () => {
    expect(() =>
      assertSafeTarget('db.production.example.com', 'showup_test', 'local'),
    ).toThrow(/db\.production\.example\.com/);
  });

  it('refuses in production even when the host looks local', () => {
    expect(() =>
      assertSafeTarget('localhost', 'showup_test', 'production'),
    ).toThrow(/NODE_ENV is "production"/);
  });
});

describe('End-to-end database name', () => {
  it('is <DB_NAME>_test by default', () => {
    expect(testDatabaseName({ DB_NAME: 'showup' })).toBe('showup_test');
  });

  it('takes DB_TEST_NAME when it is set', () => {
    expect(
      testDatabaseName({ DB_NAME: 'showup', DB_TEST_NAME: 'ci_run_test' }),
    ).toBe('ci_run_test');
  });

  it('is idempotent, so a second setup step cannot make showup_test_test', () => {
    expect(testDatabaseName({ DB_NAME: 'showup_test' })).toBe('showup_test');
  });
});

describe('End-to-end database versus the development database', () => {
  it('runs when the two differ', () => {
    expect(() =>
      assertSeparateFromDevelopment('showup_test', 'showup'),
    ).not.toThrow();
  });

  it('refuses a development database that already ends in _test', () => {
    // DB_NAME=showup_test: the derived name is kept as is, so it would be the same database.
    expect(() =>
      assertSeparateFromDevelopment(
        testDatabaseName({ DB_NAME: 'showup_test' }),
        'showup_test',
      ),
    ).toThrow(/is the one DB_NAME points the app at/);
  });

  it('refuses a DB_TEST_NAME copied from DB_NAME', () => {
    const env = { DB_NAME: 'mine_test', DB_TEST_NAME: 'mine_test' };
    expect(() =>
      assertSeparateFromDevelopment(testDatabaseName(env), env.DB_NAME),
    ).toThrow(/Refusing to run the end-to-end suite/);
  });
});
