import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Epic 12 — Safety, Moderation & Admin schema.
 *  - moderation_standing_enum + a `moderation_standing` column on users, profiles, photos (SHOWUP-79)
 *  - blocks: one person blocking another, soft-unblock for traceability (SHOWUP-77)
 *  - moderation_status_changes: append-only history of every standing change (SHOWUP-79)
 *  - verification_attempts: selfie-verification records; raw never stored, only consent + template (SHOWUP-110)
 */
export class Epic12Safety1717000010000 implements MigrationInterface {
  name = 'Epic12Safety1717000010000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    // Shared safety standing, attached to accounts, profiles and photos.
    await queryRunner.query(
      `CREATE TYPE "moderation_standing_enum" AS ENUM ('active', 'pending_review', 'rejected', 'limited', 'banned');`,
    );
    await queryRunner.query(
      `ALTER TABLE "users" ADD COLUMN "moderation_standing" "moderation_standing_enum" NOT NULL DEFAULT 'active';`,
    );
    await queryRunner.query(
      `ALTER TABLE "profiles" ADD COLUMN "moderation_standing" "moderation_standing_enum" NOT NULL DEFAULT 'active';`,
    );
    await queryRunner.query(
      `ALTER TABLE "profile_photos" ADD COLUMN "moderation_standing" "moderation_standing_enum" NOT NULL DEFAULT 'active';`,
    );

    // Blocks (SHOWUP-77).
    await queryRunner.query(`
      CREATE TABLE "blocks" (
        "id"           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "blocker_id"   uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "blocked_id"   uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "created_at"   timestamptz NOT NULL DEFAULT now(),
        "unblocked_at" timestamptz,
        CONSTRAINT "uq_blocks_pair" UNIQUE ("blocker_id", "blocked_id")
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_blocks_blocker" ON "blocks" ("blocker_id");`,
    );
    await queryRunner.query(
      `CREATE INDEX "idx_blocks_blocked" ON "blocks" ("blocked_id");`,
    );

    // Moderation standing history (SHOWUP-79).
    await queryRunner.query(
      `CREATE TYPE "moderation_subject_enum" AS ENUM ('user', 'profile', 'photo');`,
    );
    await queryRunner.query(`
      CREATE TABLE "moderation_status_changes" (
        "id"                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "subject_type"       "moderation_subject_enum" NOT NULL,
        "subject_id"         uuid NOT NULL,
        "from_standing"      "moderation_standing_enum",
        "to_standing"        "moderation_standing_enum" NOT NULL,
        "changed_by_user_id" uuid REFERENCES "users"("id") ON DELETE SET NULL,
        "reason"             varchar(500),
        "created_at"         timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_mod_changes_subject" ON "moderation_status_changes" ("subject_type", "subject_id");`,
    );

    // Selfie verification (SHOWUP-110).
    await queryRunner.query(
      `CREATE TYPE "verification_outcome_enum" AS ENUM ('verified', 'rejected');`,
    );
    await queryRunner.query(`
      CREATE TABLE "verification_attempts" (
        "id"               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_id"          uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "provider"         varchar(60) NOT NULL,
        "liveness_passed"  boolean NOT NULL,
        "face_matched"     boolean NOT NULL,
        "outcome"          "verification_outcome_enum" NOT NULL,
        "match_score"      numeric(5,2),
        "template_hash"    varchar(255),
        "consent_given_at" timestamptz NOT NULL,
        "raw_deleted_at"   timestamptz,
        "created_at"       timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_verification_attempts_user" ON "verification_attempts" ("user_id");`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "verification_attempts";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "verification_outcome_enum";`);
    await queryRunner.query(
      `DROP TABLE IF EXISTS "moderation_status_changes";`,
    );
    await queryRunner.query(`DROP TYPE IF EXISTS "moderation_subject_enum";`);
    await queryRunner.query(`DROP TABLE IF EXISTS "blocks";`);
    await queryRunner.query(
      `ALTER TABLE "profile_photos" DROP COLUMN IF EXISTS "moderation_standing";`,
    );
    await queryRunner.query(
      `ALTER TABLE "profiles" DROP COLUMN IF EXISTS "moderation_standing";`,
    );
    await queryRunner.query(
      `ALTER TABLE "users" DROP COLUMN IF EXISTS "moderation_standing";`,
    );
    await queryRunner.query(`DROP TYPE IF EXISTS "moderation_standing_enum";`);
  }
}
