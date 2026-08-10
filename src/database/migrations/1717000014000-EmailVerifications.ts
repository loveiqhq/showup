import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Email verification challenges (design build item 08). Mirrors phone_verifications but is keyed by
 * the logged-in user. The users.email / email_verified_at columns already exist, so only this table
 * is new.
 */
export class EmailVerifications1717000014000 implements MigrationInterface {
  name = 'EmailVerifications1717000014000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE "email_verifications" (
        "id"          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_id"     uuid NOT NULL,
        "email"       varchar(320) NOT NULL,
        "code_hash"   varchar(128) NOT NULL,
        "expires_at"  timestamptz NOT NULL,
        "attempts"    int NOT NULL DEFAULT 0,
        "consumed_at" timestamptz,
        "created_at"  timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_email_verifications_user_id" ON "email_verifications" ("user_id");`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "email_verifications";`);
  }
}
