/**
 * SHOWUP-97 — the safety check that stands in front of the end-to-end database reset.
 *
 * Kept separate from the reset itself, and free of any I/O or reads of `process.env`, so that it can
 * be tested directly. A guard whose refusal path has never actually been executed is not a guard —
 * and this one is the only thing standing between a mistyped `DB_HOST` and an emptied database.
 */

/** localhost in its various spellings, plus `postgres` — the service container's name in CI. */
export const DISPOSABLE_HOSTS = ['localhost', '127.0.0.1', '::1', 'postgres'];

/** Only a database named for the suite may be emptied — see `e2e-database.ts`. */
export const TEST_DATABASE_SUFFIX = '_test';

/**
 * Throws unless the target is an obviously local or throwaway database. Takes everything it needs as
 * arguments rather than reading the environment, so both refusal paths are reachable from a test.
 */
export function assertSafeTarget(
  host: string,
  database: string,
  nodeEnv: string | undefined,
): void {
  if (nodeEnv === 'production') {
    throw new Error(
      `Refusing to reset the database: NODE_ENV is "production". The end-to-end suite empties ` +
        `every table and must never be pointed at a real environment.`,
    );
  }
  // THE DEVELOPMENT DATABASE IS NOT A TEST DATABASE, even on localhost. Until 8 October 2026 the
  // suite emptied `showup` itself, and every local account went with it — the app stayed signed in
  // to a user that no longer existed, and every save failed with no hint why.
  if (!database.endsWith(TEST_DATABASE_SUFFIX)) {
    throw new Error(
      `Refusing to reset the database: "${database}" is not a test database. The end-to-end suite ` +
        `empties every table, so it only runs against a database whose name ends in ` +
        `"${TEST_DATABASE_SUFFIX}" (DB_TEST_NAME, or <DB_NAME>${TEST_DATABASE_SUFFIX}) — never ` +
        `the one you develop against.`,
    );
  }
  if (!DISPOSABLE_HOSTS.includes(host)) {
    throw new Error(
      `Refusing to reset the database: DB_HOST is "${host}" (database "${database}"), which is ` +
        `not a local or throwaway host. The end-to-end suite empties every table, so it only runs ` +
        `against ${DISPOSABLE_HOSTS.join(', ')}. Check your .env before re-running.`,
    );
  }
}
