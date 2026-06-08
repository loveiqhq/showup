import 'reflect-metadata';
import { AppDataSource } from '../data-source';

/**
 * Verifies PostGIS end-to-end against the live database:
 *  1. PostGIS extension is installed.
 *  2. ST_Distance computes the expected great-circle distance (Berlin ↔ Cologne ≈ 477 km).
 *  3. ST_DWithin finds seeded venues within a radius.
 * Exits non-zero on failure so it can gate CI. Run with: npm run verify:postgis
 */
async function main() {
  await AppDataSource.initialize();
  try {
    const [{ version }] = await AppDataSource.query<Array<{ version: string }>>(
      'SELECT PostGIS_Version() AS version',
    );
    console.log(`PostGIS version: ${version}`);

    // Brandenburg Gate (Berlin) -> Cologne Cathedral
    const [{ meters }] = await AppDataSource.query<Array<{ meters: number }>>(
      `SELECT ST_Distance(
         ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
         ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography
       ) AS meters`,
      [13.3777, 52.5163, 6.9583, 50.9413],
    );
    const km = Number(meters) / 1000;
    console.log(`Berlin <-> Cologne distance: ${km.toFixed(1)} km`);
    if (!(km > 470 && km < 485)) {
      throw new Error(
        `Distance ${km.toFixed(1)} km outside expected ~477 km range`,
      );
    }

    // Nearby venues (requires seed). Non-fatal if the table is empty.
    const nearby = await AppDataSource.query<
      Array<{ name: string; meters: number }>
    >(
      `SELECT name,
              round(ST_Distance(location, ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography)) AS meters
       FROM venues
       WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography, $3)
       ORDER BY meters`,
      [13.3777, 52.5163, 5000],
    );
    console.log(
      `Venues within 5 km of Brandenburg Gate: ${nearby.length ? '' : '(none — run `npm run seed`)'}`,
    );
    for (const row of nearby) console.log(`  - ${row.name}: ${row.meters} m`);

    console.log('PostGIS verification passed.');
  } finally {
    await AppDataSource.destroy();
  }
}

main().catch((err) => {
  console.error('PostGIS verification FAILED:', err);
  process.exit(1);
});
