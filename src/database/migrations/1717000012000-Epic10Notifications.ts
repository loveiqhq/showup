import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Epic 10 — Notifications & Email schema.
 *  - users.locale: preferred language for notifications/emails (background jobs have no request).
 *  - notification_preferences: per-user category toggles (marketing off by default).
 *  - push_tokens: per-device FCM registration tokens (unique token → upsert/dedupe).
 *  - notification_log: append-only record of every send attempt (survives user purge).
 */
export class Epic10Notifications1717000012000 implements MigrationInterface {
  name = 'Epic10Notifications1717000012000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "users" ADD COLUMN "locale" varchar(8) NOT NULL DEFAULT 'en';`,
    );

    // SHOWUP-70: track when the "check-in about to expire" reminder was sent (fire once).
    await queryRunner.query(
      `ALTER TABLE "check_ins" ADD COLUMN "expiry_reminder_sent_at" timestamptz;`,
    );

    await queryRunner.query(`
      CREATE TABLE "notification_preferences" (
        "user_id"    uuid PRIMARY KEY REFERENCES "users"("id") ON DELETE CASCADE,
        "essential"  boolean NOT NULL DEFAULT true,
        "engagement" boolean NOT NULL DEFAULT true,
        "marketing"  boolean NOT NULL DEFAULT false,
        "created_at" timestamptz NOT NULL DEFAULT now(),
        "updated_at" timestamptz NOT NULL DEFAULT now()
      );
    `);

    await queryRunner.query(
      `CREATE TYPE "push_tokens_platform_enum" AS ENUM ('ios', 'android');`,
    );
    await queryRunner.query(`
      CREATE TABLE "push_tokens" (
        "id"         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_id"    uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "token"      text NOT NULL,
        "platform"   "push_tokens_platform_enum" NOT NULL,
        "created_at" timestamptz NOT NULL DEFAULT now(),
        "updated_at" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "uq_push_tokens_token" UNIQUE ("token")
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_push_tokens_user" ON "push_tokens" ("user_id");`,
    );

    await queryRunner.query(
      `CREATE TYPE "notification_channel_enum" AS ENUM ('push', 'email');`,
    );
    await queryRunner.query(
      `CREATE TYPE "notification_status_enum" AS ENUM ('sent', 'failed', 'skipped');`,
    );
    await queryRunner.query(`
      CREATE TABLE "notification_log" (
        "id"                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_id"             uuid REFERENCES "users"("id") ON DELETE SET NULL,
        "channel"             "notification_channel_enum" NOT NULL,
        "type"                varchar(64) NOT NULL,
        "category"            varchar(16) NOT NULL,
        "locale"              varchar(8) NOT NULL,
        "status"              "notification_status_enum" NOT NULL,
        "provider_message_id" varchar(255),
        "error"               text,
        "created_at"          timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_notification_log_user_created" ON "notification_log" ("user_id", "created_at");`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "notification_log";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "notification_status_enum";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "notification_channel_enum";`);
    await queryRunner.query(`DROP TABLE IF EXISTS "push_tokens";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "push_tokens_platform_enum";`);
    await queryRunner.query(`DROP TABLE IF EXISTS "notification_preferences";`);
    await queryRunner.query(
      `ALTER TABLE "check_ins" DROP COLUMN IF EXISTS "expiry_reminder_sent_at";`,
    );
    await queryRunner.query(
      `ALTER TABLE "users" DROP COLUMN IF EXISTS "locale";`,
    );
  }
}
