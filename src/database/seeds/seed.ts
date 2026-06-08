import 'reflect-metadata';
import { AppDataSource } from '../data-source';

/**
 * Local seed script. Inserts a few sample venues with real coordinates so the location logic and
 * the nearby/within-radius queries have data to work against. Idempotent (upsert on name).
 * Run with: npm run seed
 */
const VENUES = [
  { name: 'Brandenburg Gate', lng: 13.3777, lat: 52.5163 },
  { name: 'Reichstag', lng: 13.3761, lat: 52.5186 },
  { name: 'Berlin TV Tower', lng: 13.4094, lat: 52.5208 },
  { name: 'Cologne Cathedral', lng: 6.9583, lat: 50.9413 },
];

async function main() {
  await AppDataSource.initialize();
  try {
    for (const v of VENUES) {
      await AppDataSource.query(
        `INSERT INTO venues (name, location)
         VALUES ($1, ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography)
         ON CONFLICT (name) DO UPDATE SET location = EXCLUDED.location`,
        [v.name, v.lng, v.lat],
      );
    }
    const [{ c }] = await AppDataSource.query<Array<{ c: number }>>(
      'SELECT COUNT(*)::int AS c FROM venues',
    );
    console.log(`Seed complete. venues table now has ${c} rows.`);
  } finally {
    await AppDataSource.destroy();
  }
}

main().catch((err) => {
  console.error('Seed failed:', err);
  process.exit(1);
});
