import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Date 06 (SHOWUP-197): the arrival gate gains its fourth entry, `wrapup` -- End date → My date isn't
 * showing up, before a check-in (registry §41). Without it the server refused the gate's check-in from
 * the End date sheet, and the app showed "Couldn't check you in".
 *
 * Postgres cannot drop one value from an enum type, so `down` rebuilds the type without it; a row that
 * already uses `wrapup` is moved to `report`, the entry that shares its sheet and note.
 */
export class ArrivalEntryWrapup1717000038000 implements MigrationInterface {
  name = 'ArrivalEntryWrapup1717000038000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TYPE "arrival_entry" ADD VALUE IF NOT EXISTS 'wrapup'`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `UPDATE "date_arrival_check_ins" SET "entry" = 'report' WHERE "entry" = 'wrapup'`,
    );
    await queryRunner.query(
      `ALTER TYPE "arrival_entry" RENAME TO "arrival_entry_old"`,
    );
    await queryRunner.query(
      `CREATE TYPE "arrival_entry" AS ENUM ('date_screen', 'chat_chip', 'report')`,
    );
    await queryRunner.query(
      `ALTER TABLE "date_arrival_check_ins" ALTER COLUMN "entry" TYPE "arrival_entry" USING "entry"::text::"arrival_entry"`,
    );
    await queryRunner.query(`DROP TYPE "arrival_entry_old"`);
  }
}
