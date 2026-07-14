import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Epic 7 — Date Scheduling & Lifecycle schema.
 *  - dates: a real-world date between two matched people, created already "confirmed"
 *  - date_status_changes: append-only history of every stage change (the state machine's audit log)
 *  - date_chat_messages: pre-date chat (SHOWUP-115) — preset reason + optional free text, per date
 */
export class Epic7Dates1717000009000 implements MigrationInterface {
  name = 'Epic7Dates1717000009000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `CREATE TYPE "dates_status_enum" AS ENUM ('confirmed', 'cancelled', 'completed', 'no_show_reported', 'disputed');`,
    );

    await queryRunner.query(`
      CREATE TABLE "dates" (
        "id"                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_a_id"            uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "user_b_id"            uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "match_id"             uuid REFERENCES "matches"("id") ON DELETE SET NULL,
        "scheduled_at"         timestamptz NOT NULL,
        "venue_id"             uuid REFERENCES "venues"("id") ON DELETE SET NULL,
        "status"               "dates_status_enum" NOT NULL DEFAULT 'confirmed',
        "a_confirmed_happened" boolean NOT NULL DEFAULT false,
        "b_confirmed_happened" boolean NOT NULL DEFAULT false,
        "a_rating"             smallint,
        "b_rating"             smallint,
        "cancelled_by_id"      uuid REFERENCES "users"("id") ON DELETE SET NULL,
        "cancel_reason"        varchar(500),
        "cancelled_at"         timestamptz,
        "created_at"           timestamptz NOT NULL DEFAULT now(),
        "updated_at"           timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_dates_user_a" ON "dates" ("user_a_id");`,
    );
    await queryRunner.query(
      `CREATE INDEX "idx_dates_user_b" ON "dates" ("user_b_id");`,
    );
    await queryRunner.query(
      `CREATE INDEX "idx_dates_status" ON "dates" ("status");`,
    );

    await queryRunner.query(`
      CREATE TABLE "date_status_changes" (
        "id"                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "date_id"             uuid NOT NULL REFERENCES "dates"("id") ON DELETE CASCADE,
        "from_status"         varchar(32),
        "to_status"           varchar(32) NOT NULL,
        "changed_by_user_id"  uuid REFERENCES "users"("id") ON DELETE SET NULL,
        "created_at"          timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_date_status_changes_date" ON "date_status_changes" ("date_id");`,
    );

    await queryRunner.query(
      `CREATE TYPE "date_chat_reason_enum" AS ENUM ('change_meet_time', 'change_location', 'on_my_way', 'running_late', 'cant_find_you', 'cant_make_it_today');`,
    );
    await queryRunner.query(`
      CREATE TABLE "date_chat_messages" (
        "id"          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "date_id"     uuid NOT NULL REFERENCES "dates"("id") ON DELETE CASCADE,
        "sender_id"   uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "reason"      "date_chat_reason_enum" NOT NULL,
        "body"        varchar(1000),
        "created_at"  timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_date_chat_messages_date" ON "date_chat_messages" ("date_id");`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "date_chat_messages";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "date_chat_reason_enum";`);
    await queryRunner.query(`DROP TABLE IF EXISTS "date_status_changes";`);
    await queryRunner.query(`DROP TABLE IF EXISTS "dates";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "dates_status_enum";`);
  }
}
