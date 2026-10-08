/**
 * Runs before every end-to-end file, ahead of any module that reads the environment: points
 * `DB_NAME` at the suite's own database (see `e2e-database.ts`).
 *
 * `.env` is loaded first so its `DB_NAME` is the base the test name is derived from. Neither dotenv
 * nor Nest's ConfigModule overrides a variable that is already set, so everything that connects
 * later -- the app, `reset-db.ts`, a spec's own client -- sees the test database.
 */
import { config as loadEnv } from 'dotenv';

import { testDatabaseName } from './e2e-database';

loadEnv({ quiet: true });
process.env.DB_NAME = testDatabaseName(process.env);
