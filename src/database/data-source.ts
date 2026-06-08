import 'reflect-metadata';
import { config as loadEnv } from 'dotenv';
import { DataSource } from 'typeorm';

// Standalone DataSource for the TypeORM CLI (migration generate/run/revert) and for seed/verify
// scripts that run outside the Nest runtime. Mirrors DatabaseModule but reads process.env directly.
loadEnv();

export const AppDataSource = new DataSource({
  type: 'postgres',
  host: process.env.DB_HOST ?? 'localhost',
  port: parseInt(process.env.DB_PORT ?? '5432', 10),
  username: process.env.DB_USER ?? 'showup',
  password: process.env.DB_PASSWORD ?? 'showup',
  database: process.env.DB_NAME ?? 'showup',
  // TLS for managed databases; verification stays ON. If your provider uses a private CA,
  // point NODE_EXTRA_CA_CERTS at its bundle rather than disabling verification.
  ssl: process.env.DB_SSL === 'true',
  entities: ['src/**/*.entity.ts'],
  migrations: ['src/database/migrations/*.ts'],
  synchronize: false,
  logging: process.env.DB_LOGGING === 'true',
});

// Note: a single export only — the TypeORM CLI requires exactly one DataSource export per file.
