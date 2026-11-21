import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Date 06 end / report a problem and Date 07 cancel (SHOWUP-197, SHOWUP-198).
 *
 * - `dates_status_enum` gains `ended`: one person ended the date from Date 06. Not a cancel, so it
 *   reuses `ended_by_id` / `ended_at` (Date 02). Neither it nor `cancelled` locks matching.
 * - `dates` gains `end_reason` (registry e43) and `end_entry` (e45), each person's committed check-in
 *   state at the moment of an end or a cancel (`ender_checked_in`, `partner_checked_in`; for a cancel
 *   the canceller is the "ender"), and the cancel's record: `hours_notice` (to the agreed time, negative
 *   after it), `penalty_points` (from the rate config), `cancel_change_requested`.
 * - `date_discomfort_flags`: one row per "Stay for now" tap. Internal evidence for flow 10, never shown.
 * - `date_pair_blocks`: the permanent block between two people after a red-link cancel, one row per
 *   pair (`user_low` < `user_high`), nothing deletes it. Written and read by Date 07.
 */
export class DateEnds1717000037000 implements MigrationInterface {
  name = 'DateEnds1717000037000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TYPE "dates_status_enum" ADD VALUE IF NOT EXISTS 'ended';`,
    );
    await queryRunner.query(`
      CREATE TYPE "date_end_reason_enum" AS ENUM ('met', 'partner_no_show', 'uncomfortable');
    `);
    await queryRunner.query(`
      CREATE TYPE "end_entry_enum" AS ENUM ('end_date_bar', 'checkin_bar_link', 'report');
    `);
    await queryRunner.query(`
      ALTER TABLE "dates"
        ADD COLUMN "end_reason" "date_end_reason_enum",
        ADD COLUMN "end_entry" "end_entry_enum",
        ADD COLUMN "ender_checked_in" boolean,
        ADD COLUMN "partner_checked_in" boolean,
        ADD COLUMN "cancel_change_requested" boolean,
        ADD COLUMN "hours_notice" numeric(8,2),
        ADD COLUMN "penalty_points" integer;
    `);
    await queryRunner.query(`
      CREATE TABLE "date_discomfort_flags" (
        "id"         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "date_id"    uuid NOT NULL REFERENCES "dates"("id") ON DELETE CASCADE,
        "user_id"    uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "created_at" timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_date_discomfort_flags_date" ON "date_discomfort_flags" ("date_id");`,
    );
    await queryRunner.query(`
      CREATE TABLE "date_pair_blocks" (
        "id"         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_low"   uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "user_high"  uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "date_id"    uuid NOT NULL REFERENCES "dates"("id") ON DELETE CASCADE,
        "reason"     varchar(32) NOT NULL,
        "created_at" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "UQ_date_pair_blocks_pair" UNIQUE ("user_low", "user_high"),
        CONSTRAINT "CHK_date_pair_blocks_order" CHECK ("user_low" < "user_high")
      );
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "date_pair_blocks";`);
    await queryRunner.query(`DROP TABLE IF EXISTS "date_discomfort_flags";`);
    await queryRunner.query(`
      ALTER TABLE "dates"
        DROP COLUMN IF EXISTS "penalty_points",
        DROP COLUMN IF EXISTS "hours_notice",
        DROP COLUMN IF EXISTS "cancel_change_requested",
        DROP COLUMN IF EXISTS "partner_checked_in",
        DROP COLUMN IF EXISTS "ender_checked_in",
        DROP COLUMN IF EXISTS "end_entry",
        DROP COLUMN IF EXISTS "end_reason";
    `);
    await queryRunner.query(`DROP TYPE IF EXISTS "end_entry_enum";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "date_end_reason_enum";`);
    // A date ended from Date 06 goes back to `ended_in_chat`: the nearest state that releases both
    // people, with the same `ended_by_id` / `ended_at` columns.
    await queryRunner.query(`
      ALTER TABLE "dates" ALTER COLUMN "status" DROP DEFAULT;
      ALTER TABLE "dates" ALTER COLUMN "status" TYPE text;
      UPDATE "dates" SET "status" = 'ended_in_chat' WHERE "status" = 'ended';
      DROP TYPE "dates_status_enum";
      CREATE TYPE "dates_status_enum" AS ENUM ('confirmed', 'cancelled', 'completed', 'no_show_reported', 'disputed', 'ended_in_chat');
      ALTER TABLE "dates" ALTER COLUMN "status" TYPE "dates_status_enum" USING "status"::"dates_status_enum";
      ALTER TABLE "dates" ALTER COLUMN "status" SET DEFAULT 'confirmed';
    `);
  }
}
