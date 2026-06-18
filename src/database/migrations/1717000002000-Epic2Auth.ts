import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Epic 2 — Authentication & Account Management schema:
 *  - users (account model + lifecycle status enum)
 *  - refresh_tokens (rotating, hashed)
 *  - phone_verifications (OTP challenges, hashed)
 *  - audit_logs (security/account events)
 */
export class Epic2Auth1717000002000 implements MigrationInterface {
  name = 'Epic2Auth1717000002000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TYPE "users_status_enum" AS ENUM (
        'registered','verified','active','restricted','suspended','deletion_pending','deleted'
      );
    `);

    await queryRunner.query(`
      CREATE TABLE "users" (
        "id"                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "phone"                 varchar(32) UNIQUE,
        "phone_verified_at"     timestamptz,
        "email"                 varchar(320) UNIQUE,
        "email_verified_at"     timestamptz,
        "apple_user_id"         varchar(255) UNIQUE,
        "google_user_id"        varchar(255) UNIQUE,
        "display_name"          varchar(120),
        "status"                "users_status_enum" NOT NULL DEFAULT 'registered',
        "last_login_at"         timestamptz,
        "created_at"            timestamptz NOT NULL DEFAULT now(),
        "updated_at"            timestamptz NOT NULL DEFAULT now(),
        "deletion_requested_at" timestamptz,
        "deleted_at"            timestamptz
      );
    `);

    await queryRunner.query(`
      CREATE TABLE "refresh_tokens" (
        "id"            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_id"       uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "token_hash"    varchar(64) NOT NULL UNIQUE,
        "device_id"     varchar(255),
        "user_agent"    varchar(512),
        "expires_at"    timestamptz NOT NULL,
        "revoked_at"    timestamptz,
        "rotated_to_id" uuid,
        "created_at"    timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_refresh_tokens_user_id" ON "refresh_tokens" ("user_id");`,
    );

    await queryRunner.query(`
      CREATE TABLE "phone_verifications" (
        "id"          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "phone"       varchar(32) NOT NULL,
        "code_hash"   varchar(128) NOT NULL,
        "expires_at"  timestamptz NOT NULL,
        "attempts"    int NOT NULL DEFAULT 0,
        "consumed_at" timestamptz,
        "created_at"  timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_phone_verifications_phone" ON "phone_verifications" ("phone");`,
    );

    await queryRunner.query(`
      CREATE TABLE "audit_logs" (
        "id"         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_id"    uuid REFERENCES "users"("id") ON DELETE SET NULL,
        "action"     varchar(100) NOT NULL,
        "metadata"   jsonb,
        "created_at" timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_audit_logs_user_id" ON "audit_logs" ("user_id");`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "audit_logs";`);
    await queryRunner.query(`DROP TABLE IF EXISTS "phone_verifications";`);
    await queryRunner.query(`DROP TABLE IF EXISTS "refresh_tokens";`);
    await queryRunner.query(`DROP TABLE IF EXISTS "users";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "users_status_enum";`);
  }
}
