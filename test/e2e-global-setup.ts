/**
 * Once per end-to-end run: makes sure the suite's own database exists and carries every migration.
 *
 * Created on first use, so a fresh checkout and CI need no extra step, and migrated with the same
 * `migration:run` the app uses -- pending migrations only, so a second run costs a few seconds. The
 * reset guard is checked BEFORE anything is created, so a misconfigured host fails here first.
 */
import { execSync } from 'child_process';

import { config as loadEnv } from 'dotenv';
import { Client } from 'pg';

import {
  assertSeparateFromDevelopment,
  testDatabaseName,
} from './e2e-database';
import { assertSafeTarget } from './reset-guard';

export default async function globalSetup(): Promise<void> {
  loadEnv({ quiet: true });
  const host = process.env.DB_HOST ?? 'localhost';
  const database = testDatabaseName(process.env);
  assertSeparateFromDevelopment(database, process.env.DB_NAME ?? 'showup');
  assertSafeTarget(host, database, process.env.NODE_ENV);

  // Connect to the maintenance database to create the test one; CREATE DATABASE cannot run inside
  // the database it creates, and has no IF NOT EXISTS.
  const admin = new Client({
    host,
    port: parseInt(process.env.DB_PORT ?? '5432', 10),
    user: process.env.DB_USER ?? 'showup',
    password: process.env.DB_PASSWORD ?? 'showup',
    database: 'postgres',
    ssl: process.env.DB_SSL === 'true',
  });
  await admin.connect();
  try {
    const { rowCount } = await admin.query(
      'SELECT 1 FROM pg_database WHERE datname = $1',
      [database],
    );
    if (!rowCount) {
      // The name is ours (`<DB_NAME>_test` or DB_TEST_NAME), quoted as an identifier.
      await admin.query(`CREATE DATABASE "${database.replace(/"/g, '""')}"`);
    }
  } finally {
    await admin.end();
  }

  execSync('npm run migration:run', {
    env: { ...process.env, DB_NAME: database },
    stdio: 'pipe',
  });
}
