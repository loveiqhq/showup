import 'reflect-metadata';
import { AppDataSource } from '../data-source';

/**
 * Local seed script. Inserts a few sample venues with real coordinates so the location logic and
 * the nearby/within-radius queries have data to work against. Idempotent (upsert on name).
 * Run with: npm run seed
 */
const VENUES = [
  {
    name: 'Brandenburg Gate',
    address: 'Pariser Platz, Mitte',
    lng: 13.3777,
    lat: 52.5163,
  },
  {
    name: 'Reichstag',
    address: 'Platz der Republik 1, Tiergarten',
    lng: 13.3761,
    lat: 52.5186,
  },
  {
    name: 'Berlin TV Tower',
    address: 'Panoramastrasse 1A, Mitte',
    lng: 13.4094,
    lat: 52.5208,
  },
  {
    name: 'Cologne Cathedral',
    address: 'Domkloster 4, Altstadt-Nord',
    lng: 6.9583,
    lat: 50.9413,
  },
  // The Date screen reference's own venue (SHOWUP-193), so a local date looks like the spec sheet.
  {
    name: 'Cafe Krone',
    address: 'Oranienstrasse 42, Kreuzberg',
    lng: 13.4186,
    lat: 52.5016,
  },
];

async function main() {
  await AppDataSource.initialize();
  try {
    for (const v of VENUES) {
      await AppDataSource.query(
        `INSERT INTO venues (name, address, location)
         VALUES ($1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography)
         ON CONFLICT (name) DO UPDATE SET location = EXCLUDED.location, address = EXCLUDED.address`,
        [v.name, v.address, v.lng, v.lat],
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
