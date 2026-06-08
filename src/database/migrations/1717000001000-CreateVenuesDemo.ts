import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Creates a small `venues` table to exercise PostGIS geography columns + a GIST spatial index.
 * It doubles as the placeholder venue model referenced by proximity/date features (Epics 5 & 7)
 * and gives the seed + distance verification scripts a concrete table to work against.
 */
export class CreateVenuesDemo1717000001000 implements MigrationInterface {
  name = 'CreateVenuesDemo1717000001000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE IF NOT EXISTS venues (
        id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        name       varchar(200) NOT NULL UNIQUE,
        location   geography(Point, 4326) NOT NULL,
        created_at timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX IF NOT EXISTS idx_venues_location ON venues USING GIST (location);`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP INDEX IF EXISTS idx_venues_location;`);
    await queryRunner.query(`DROP TABLE IF EXISTS venues;`);
  }
}
