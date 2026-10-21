import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * A Check-In's 48 hours start at `Find my date`, not when its window opens (SHOWUP-177).
 *
 * Decided by the product owner on 21 October 2026: a Check-In left halfway starts again from the
 * beginning next time, so the window is not counted until the Check-In is finished. Until now a
 * window opened with `closes_at = opened_at + 48h` the first time the user entered the Check-In, so
 * a Check-In finished 40 hours later went inactive 8 hours after it was made -- before the hours it
 * had just picked, while the server still matched on them.
 *
 * - `closes_at` becomes nullable: an unfinished window has no clock.
 * - Older unfinished windows are removed, keeping each user's newest window. They were abandoned
 *   attempts the old clock had already closed, and with no clock they would all read as current.
 *   They hold nothing: the hours and the uplift exist only on the device until the commit, and no
 *   `check_ins` row points at an unfinished window.
 * - Finished windows close 48 hours after `committed_at`, the rule the code now applies.
 * - A CHECK keeps it that way: a clock if and only if the Check-In is finished.
 *
 * 48 is `CHECK_IN_WINDOW_HOURS` at the time of writing; a migration is a snapshot, so it is a
 * literal here.
 */
export class CheckInWindowFromCommit1717000025000 implements MigrationInterface {
  name = 'CheckInWindowFromCommit1717000025000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "check_in_windows" ALTER COLUMN "closes_at" DROP NOT NULL;`,
    );
    await queryRunner.query(`
      DELETE FROM "check_in_windows" w
       WHERE w."committed_at" IS NULL
         AND EXISTS (
           SELECT 1 FROM "check_in_windows" n
            WHERE n."user_id" = w."user_id" AND n."opened_at" > w."opened_at"
         );
    `);
    await queryRunner.query(
      `UPDATE "check_in_windows" SET "closes_at" = NULL WHERE "committed_at" IS NULL;`,
    );
    await queryRunner.query(
      `UPDATE "check_in_windows" SET "closes_at" = "committed_at" + interval '48 hours'
        WHERE "committed_at" IS NOT NULL;`,
    );
    await queryRunner.query(`
      ALTER TABLE "check_in_windows"
        ADD CONSTRAINT "check_in_windows_clock_starts_at_commit"
        CHECK (("closes_at" IS NULL) = ("committed_at" IS NULL));
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "check_in_windows" DROP CONSTRAINT IF EXISTS "check_in_windows_clock_starts_at_commit";`,
    );
    // The removed attempts are not restored: they held nothing. Unfinished windows get the old
    // clock back, counted from when they opened.
    await queryRunner.query(
      `UPDATE "check_in_windows" SET "closes_at" = "opened_at" + interval '48 hours'
        WHERE "closes_at" IS NULL;`,
    );
    await queryRunner.query(
      `ALTER TABLE "check_in_windows" ALTER COLUMN "closes_at" SET NOT NULL;`,
    );
  }
}
