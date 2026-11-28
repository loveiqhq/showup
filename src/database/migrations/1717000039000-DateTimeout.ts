import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Date 09, the +2 h switch (SHOWUP-200): a confirmed date nobody ended closes itself two hours after
 * its meet time. `dates_status_enum` gains `timed_out`, `date_end_reason_enum` gains `timed_out`
 * (registry §43) and `end_entry_enum` gains `auto_switch` (§45). The sweep that writes them is separate.
 */
export class DateTimeout1717000039000 implements MigrationInterface {
  name = 'DateTimeout1717000039000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TYPE "dates_status_enum" ADD VALUE IF NOT EXISTS 'timed_out';`,
    );
    await queryRunner.query(
      `ALTER TYPE "date_end_reason_enum" ADD VALUE IF NOT EXISTS 'timed_out';`,
    );
    await queryRunner.query(
      `ALTER TYPE "end_entry_enum" ADD VALUE IF NOT EXISTS 'auto_switch';`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    // A timed-out date goes back to confirmed with no reason or entry; the types are rebuilt without
    // the new values (Postgres cannot drop one from an enum).
    await queryRunner.query(`
      UPDATE "dates"
         SET "status" = 'confirmed', "end_reason" = NULL, "end_entry" = NULL
       WHERE "status" = 'timed_out';
      UPDATE "dates" SET "end_reason" = NULL WHERE "end_reason" = 'timed_out';
      UPDATE "dates" SET "end_entry" = NULL WHERE "end_entry" = 'auto_switch';
    `);
    await queryRunner.query(`
      ALTER TABLE "dates" ALTER COLUMN "end_reason" TYPE text;
      DROP TYPE "date_end_reason_enum";
      CREATE TYPE "date_end_reason_enum" AS ENUM ('met', 'partner_no_show', 'uncomfortable');
      ALTER TABLE "dates" ALTER COLUMN "end_reason" TYPE "date_end_reason_enum" USING "end_reason"::"date_end_reason_enum";
    `);
    await queryRunner.query(`
      ALTER TABLE "dates" ALTER COLUMN "end_entry" TYPE text;
      DROP TYPE "end_entry_enum";
      CREATE TYPE "end_entry_enum" AS ENUM ('end_date_bar', 'checkin_bar_link', 'report');
      ALTER TABLE "dates" ALTER COLUMN "end_entry" TYPE "end_entry_enum" USING "end_entry"::"end_entry_enum";
    `);
    await queryRunner.query(`
      ALTER TABLE "dates" ALTER COLUMN "status" DROP DEFAULT;
      ALTER TABLE "dates" ALTER COLUMN "status" TYPE text;
      DROP TYPE "dates_status_enum";
      CREATE TYPE "dates_status_enum" AS ENUM ('confirmed', 'cancelled', 'completed', 'no_show_reported', 'disputed', 'ended_in_chat', 'ended');
      ALTER TABLE "dates" ALTER COLUMN "status" TYPE "dates_status_enum" USING "status"::"dates_status_enum";
      ALTER TABLE "dates" ALTER COLUMN "status" SET DEFAULT 'confirmed';
    `);
  }
}
