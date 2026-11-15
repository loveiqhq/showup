import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Date 03, the arrival check-in (SHOWUP-195).
 *
 * One row per person per date: where they tapped "I'm here" (a PostGIS geography point, WGS84), how
 * accurate the phone said it was, which entry it came from, and the phone's own id for the tap.
 * `committed_at` is set by Close; before that the row can be undone or replaced. `checked_in_at` is
 * indexed for the daily retention sweep (rows older than one year are deleted).
 */
export class DateArrivalCheckIns1717000035000 implements MigrationInterface {
  name = 'DateArrivalCheckIns1717000035000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `CREATE TYPE "arrival_entry" AS ENUM ('date_screen', 'chat_chip', 'report');`,
    );
    await queryRunner.query(`
      CREATE TABLE "date_arrival_check_ins" (
        "id"            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "date_id"       uuid NOT NULL REFERENCES "dates"("id") ON DELETE CASCADE,
        "user_id"       uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "location"      geography(Point, 4326) NOT NULL,
        "accuracy_m"    double precision CHECK ("accuracy_m" >= 0),
        "entry"         "arrival_entry" NOT NULL,
        "client_id"     uuid NOT NULL,
        "checked_in_at" timestamptz NOT NULL DEFAULT now(),
        "committed_at"  timestamptz
      );
    `);
    await queryRunner.query(`
      CREATE UNIQUE INDEX "UQ_date_arrival_check_ins_date_user"
        ON "date_arrival_check_ins" ("date_id", "user_id");
    `);
    await queryRunner.query(`
      CREATE INDEX "idx_date_arrival_check_ins_checked_in_at"
        ON "date_arrival_check_ins" ("checked_in_at");
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `DROP INDEX IF EXISTS "idx_date_arrival_check_ins_checked_in_at";`,
    );
    await queryRunner.query(
      `DROP INDEX IF EXISTS "UQ_date_arrival_check_ins_date_user";`,
    );
    await queryRunner.query(`DROP TABLE IF EXISTS "date_arrival_check_ins";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "arrival_entry";`);
  }
}
