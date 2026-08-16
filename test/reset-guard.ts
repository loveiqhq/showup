/**
 * SHOWUP-97 — the safety check that stands in front of the end-to-end database reset.
 *
 * Kept separate from the reset itself, and free of any I/O or reads of `process.env`, so that it can
 * be tested directly. A guard whose refusal path has never actually been executed is not a guard —
 * and this one is the only thing standing between a mistyped `DB_HOST` and an emptied database.
 */

/** localhost in its various spellings, plus `postgres` — the service container's name in CI. */
export const DISPOSABLE_HOSTS = ['localhost', '127.0.0.1', '::1', 'postgres'];

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
  if (!DISPOSABLE_HOSTS.includes(host)) {
    throw new Error(
      `Refusing to reset the database: DB_HOST is "${host}" (database "${database}"), which is ` +
        `not a local or throwaway host. The end-to-end suite empties every table, so it only runs ` +
        `against ${DISPOSABLE_HOSTS.join(', ')}. Check your .env before re-running.`,
    );
  }
}
