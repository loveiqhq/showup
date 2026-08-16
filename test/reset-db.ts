/**
 * SHOWUP-97 — resets the database to a known-empty state before each end-to-end file runs.
 *
 * Before this existed, every spec cleared only its own rows, matching on the phone number or venue
 * name it happened to use. That worked, but it was a convention rather than a guarantee: a spec
 * that failed part-way through left its rows behind, and a new spec that forgot to clean up would
 * be silently polluted by whatever ran before it. Discovery and matching are especially exposed,
 * because they list *other* users — so data left over from an unrelated spec can change their
 * results. Those are exactly the conditions that produce a test which passes alone and fails in the
 * suite.
 *
 * Registered through `setupFilesAfterEnv`, so this `beforeAll` is at the root scope and runs before
 * the `beforeAll` inside each spec's `describe` block. Every file therefore starts from empty and
 * builds the data it needs. Safe with the suite's `maxWorkers: 1`, which is what makes a shared
 * database workable in the first place.
 *
 * ─── Why the guard below is not optional ───
 * There is no separate test database: DB_NAME is the same `showup` database used for local
 * development. Truncating is therefore already destructive by design, and would be catastrophic if
 * the connection details ever pointed somewhere real. The guard refuses to run against anything
 * that is not an obviously local or throwaway host, so a misconfigured DB_HOST fails loudly instead
 * of quietly emptying a live database.
 */

import { config as loadEnv } from 'dotenv';
import { Client } from 'pg';

import { assertSafeTarget } from './reset-guard';

// `quiet` suppresses dotenv's startup banner, which would otherwise print once per spec file.
loadEnv({ quiet: true });

/** Never emptied: TypeORM's own bookkeeping, and PostGIS's reference data. */
const PRESERVE = ['migrations', 'spatial_ref_sys'];

beforeAll(async () => {
  const host = process.env.DB_HOST ?? 'localhost';
  const database = process.env.DB_NAME ?? 'showup';
  assertSafeTarget(host, database, process.env.NODE_ENV);

  const client = new Client({
    host,
    port: parseInt(process.env.DB_PORT ?? '5432', 10),
    user: process.env.DB_USER ?? 'showup',
    password: process.env.DB_PASSWORD ?? 'showup',
    database,
    ssl: process.env.DB_SSL === 'true',
  });

  await client.connect();
  try {
    // Read the table list from the database rather than keeping a hand-written one, so tables added
    // by future migrations are cleared automatically instead of being quietly missed.
    const { rows } = await client.query<{ tablename: string }>(
      `SELECT tablename FROM pg_tables
        WHERE schemaname = 'public' AND tablename <> ALL($1::text[])`,
      [PRESERVE],
    );

    // Nothing to do on a database whose migrations have not been applied yet.
    if (rows.length === 0) return;

    // One statement so it is a single transaction: either everything is cleared or nothing is.
    // CASCADE handles the foreign keys between them; RESTART IDENTITY resets sequences so ids do
    // not creep upward across runs.
    const tables = rows.map((r) => `"${r.tablename}"`).join(', ');
    await client.query(`TRUNCATE TABLE ${tables} RESTART IDENTITY CASCADE`);
  } finally {
    await client.end();
  }
});
