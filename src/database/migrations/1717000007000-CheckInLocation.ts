import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * SHOWUP-41 — adds the check-in's location for proximity matching.
 *
 * `location` is a PostGIS geography point (WGS84 / SRID 4326). It is a per-check-in snapshot of where
 * the person is at the moment they check in, taken from the device, not a meeting place they choose:
 * it is used to find nearby people and to pick a nearby venue. The venue for a date is chosen by the
 * backend and recommended to both people; nobody selects where to meet. It is deliberately NOT mapped
 * on the TypeORM entity: it is written and read only via parameterised spatial SQL, never returned to
 * other users, and never sent to analytics.
 *
 * The GiST index makes ST_DWithin ("who is within N metres") fast.
 */
export class CheckInLocation1717000007000 implements MigrationInterface {
  name = 'CheckInLocation1717000007000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "check_ins" ADD COLUMN "location" geography(Point, 4326);`,
    );
    await queryRunner.query(
      `CREATE INDEX "idx_check_ins_location" ON "check_ins" USING GIST ("location");`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP INDEX IF EXISTS "idx_check_ins_location";`);
    await queryRunner.query(
      `ALTER TABLE "check_ins" DROP COLUMN IF EXISTS "location";`,
    );
  }
}
