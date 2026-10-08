/**
 * The end-to-end suite's OWN database -- never the one you develop against.
 *
 * Until 8 October 2026 the suite ran against `DB_NAME` itself, the `showup` database the local app
 * uses, and `reset-db.ts` empties every table before each file. Running the backend tests therefore
 * deleted every local account: the phone or emulator stayed signed in to a user that no longer
 * existed, and every save on it failed with "We couldn't save that just now". Nothing about that
 * failure pointed at the tests.
 *
 * Now the suite derives a separate name -- `DB_TEST_NAME` when set, otherwise `<DB_NAME>_test` --
 * creates it on first use, migrates it, and only ever empties that. `reset-guard.ts` refuses any
 * database whose name does not end in `_test`, so a misconfiguration fails loudly instead.
 */

/** A name the reset guard accepts. */
export const TEST_DATABASE_SUFFIX = '_test';

/** The database the suite uses for an environment. Idempotent: a `_test` name is kept as is. */
export function testDatabaseName(env: NodeJS.ProcessEnv): string {
  const explicit = env.DB_TEST_NAME;
  if (explicit) return explicit;
  const base = env.DB_NAME ?? 'showup';
  return base.endsWith(TEST_DATABASE_SUFFIX)
    ? base
    : `${base}${TEST_DATABASE_SUFFIX}`;
}

/**
 * Refuses a test database that IS the development database.
 *
 * The suffix rule alone does not cover it: a developer whose own database is already called
 * `something_test`, or a DB_TEST_NAME copied from DB_NAME, would hand the suite the very database
 * the app is using, and the reset guard would accept it -- the name does end in `_test`. Checked
 * once per run, in the global setup, where DB_NAME is still the developer's own value
 * (`e2e-env.ts` repoints it only inside the test workers).
 */
export function assertSeparateFromDevelopment(
  testDatabase: string,
  developmentDatabase: string,
): void {
  if (testDatabase === developmentDatabase) {
    throw new Error(
      `Refusing to run the end-to-end suite: its database "${testDatabase}" is the one DB_NAME ` +
        `points the app at, and the suite empties every table. Set DB_TEST_NAME to a different ` +
        `name ending in "${TEST_DATABASE_SUFFIX}".`,
    );
  }
}
