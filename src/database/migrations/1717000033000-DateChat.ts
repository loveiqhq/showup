import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Date 02, the Coordinate chat (SHOWUP-194).
 *
 * - `date_chat_reason_enum` takes the tracking registry's §40 `chat_reason` values: four renamed in
 *   place (`change_meet_time → change_time`, `change_location → change_place`, `cant_find_you →
 *   cant_find`, `cant_make_it_today → cant_make_it`) and two added (`arrived`, `when_arrive`). Every
 *   existing row keeps its meaning under the new name.
 * - `date_chat_messages.reason` becomes nullable: a message typed with no chip selected carries no
 *   reason.
 * - `dates_status_enum` gains `ended_in_chat`, terminal: after an "I can't make it today" message
 *   either person ended the date from the chat. Not a cancel, so it has its own columns:
 *   `ended_by_id`, `ended_at`.
 * - `a_chat_tutorial_at` / `b_chat_tutorial_at`: each person tapped "Open the chat" on this date's
 *   tutorial. `a_end_seen_at` / `b_end_seen_at`: each person has seen their outcome screen (G / H).
 */
export class DateChat1717000033000 implements MigrationInterface {
  name = 'DateChat1717000033000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    for (const [from, to] of [
      ['change_meet_time', 'change_time'],
      ['change_location', 'change_place'],
      ['cant_find_you', 'cant_find'],
      ['cant_make_it_today', 'cant_make_it'],
    ]) {
      await queryRunner.query(
        `ALTER TYPE "date_chat_reason_enum" RENAME VALUE '${from}' TO '${to}';`,
      );
    }
    await queryRunner.query(
      `ALTER TYPE "date_chat_reason_enum" ADD VALUE IF NOT EXISTS 'arrived';`,
    );
    await queryRunner.query(
      `ALTER TYPE "date_chat_reason_enum" ADD VALUE IF NOT EXISTS 'when_arrive';`,
    );
    await queryRunner.query(
      `ALTER TABLE "date_chat_messages" ALTER COLUMN "reason" DROP NOT NULL;`,
    );

    await queryRunner.query(
      `ALTER TYPE "dates_status_enum" ADD VALUE IF NOT EXISTS 'ended_in_chat';`,
    );
    await queryRunner.query(`
      ALTER TABLE "dates"
        ADD COLUMN "ended_by_id" uuid REFERENCES "users"("id") ON DELETE SET NULL,
        ADD COLUMN "ended_at" timestamptz,
        ADD COLUMN "a_chat_tutorial_at" timestamptz,
        ADD COLUMN "b_chat_tutorial_at" timestamptz,
        ADD COLUMN "a_end_seen_at" timestamptz,
        ADD COLUMN "b_end_seen_at" timestamptz;
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "dates"
        DROP COLUMN IF EXISTS "b_end_seen_at",
        DROP COLUMN IF EXISTS "a_end_seen_at",
        DROP COLUMN IF EXISTS "b_chat_tutorial_at",
        DROP COLUMN IF EXISTS "a_chat_tutorial_at",
        DROP COLUMN IF EXISTS "ended_at",
        DROP COLUMN IF EXISTS "ended_by_id";
    `);
    // A date ended in the chat goes back to `cancelled`: the old states have no other ending that
    // releases both people without a review.
    await queryRunner.query(`
      ALTER TABLE "dates" ALTER COLUMN "status" DROP DEFAULT;
      ALTER TABLE "dates" ALTER COLUMN "status" TYPE text;
      UPDATE "dates" SET "status" = 'cancelled' WHERE "status" = 'ended_in_chat';
      DROP TYPE "dates_status_enum";
      CREATE TYPE "dates_status_enum" AS ENUM ('confirmed', 'cancelled', 'completed', 'no_show_reported', 'disputed');
      ALTER TABLE "dates" ALTER COLUMN "status" TYPE "dates_status_enum" USING "status"::"dates_status_enum";
      ALTER TABLE "dates" ALTER COLUMN "status" SET DEFAULT 'confirmed';
    `);
    // The old enum had no reason-less message and no `arrived` / `when_arrive`: they become
    // `on_my_way`, the nearest it had.
    await queryRunner.query(`
      ALTER TABLE "date_chat_messages" ALTER COLUMN "reason" TYPE text;
      UPDATE "date_chat_messages" SET "reason" = 'on_my_way'
        WHERE "reason" IS NULL OR "reason" IN ('arrived', 'when_arrive');
      UPDATE "date_chat_messages" SET "reason" = CASE "reason"
          WHEN 'change_time' THEN 'change_meet_time'
          WHEN 'change_place' THEN 'change_location'
          WHEN 'cant_find' THEN 'cant_find_you'
          WHEN 'cant_make_it' THEN 'cant_make_it_today'
          ELSE "reason" END;
      DROP TYPE "date_chat_reason_enum";
      CREATE TYPE "date_chat_reason_enum" AS ENUM ('change_meet_time', 'change_location', 'on_my_way', 'running_late', 'cant_find_you', 'cant_make_it_today');
      ALTER TABLE "date_chat_messages" ALTER COLUMN "reason" TYPE "date_chat_reason_enum" USING "reason"::"date_chat_reason_enum";
      ALTER TABLE "date_chat_messages" ALTER COLUMN "reason" SET NOT NULL;
    `);
  }
}
