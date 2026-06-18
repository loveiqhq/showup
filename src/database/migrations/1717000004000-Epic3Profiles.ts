import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Epic 3 — Profile & Media Management schema:
 *  - users.role (minimal admin role, seeds Epic 19)
 *  - profiles (dating profile + verification state)
 *  - profile_photos (photo metadata + moderation state)
 */
export class Epic3Profiles1717000004000 implements MigrationInterface {
  name = 'Epic3Profiles1717000004000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    // Minimal user role for admin-only endpoints (e.g. updating verification).
    await queryRunner.query(
      `CREATE TYPE "users_role_enum" AS ENUM ('user', 'admin');`,
    );
    await queryRunner.query(
      `ALTER TABLE "users" ADD COLUMN "role" "users_role_enum" NOT NULL DEFAULT 'user';`,
    );

    await queryRunner.query(
      `CREATE TYPE "profiles_verification_status_enum" AS ENUM ('none', 'pending', 'verified', 'rejected');`,
    );
    await queryRunner.query(`
      CREATE TABLE "profiles" (
        "id"                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_id"                   uuid NOT NULL UNIQUE REFERENCES "users"("id") ON DELETE CASCADE,
        "display_name"              varchar(80),
        "date_of_birth"             date,
        "bio"                       varchar(500),
        "gender"                    varchar(40),
        "looking_for"               varchar(40),
        "is_visible"                boolean NOT NULL DEFAULT true,
        "is_complete"               boolean NOT NULL DEFAULT false,
        "verification_status"       "profiles_verification_status_enum" NOT NULL DEFAULT 'none',
        "verification_requested_at" timestamptz,
        "verified_at"               timestamptz,
        "created_at"                timestamptz NOT NULL DEFAULT now(),
        "updated_at"                timestamptz NOT NULL DEFAULT now()
      );
    `);

    await queryRunner.query(
      `CREATE TYPE "photos_moderation_status_enum" AS ENUM ('pending', 'approved', 'rejected');`,
    );
    await queryRunner.query(`
      CREATE TABLE "profile_photos" (
        "id"                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_id"           uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "storage_key"       varchar(512) NOT NULL,
        "content_type"      varchar(100) NOT NULL,
        "position"          int NOT NULL DEFAULT 0,
        "moderation_status" "photos_moderation_status_enum" NOT NULL DEFAULT 'pending',
        "created_at"        timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_profile_photos_user_id" ON "profile_photos" ("user_id");`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "profile_photos";`);
    await queryRunner.query(
      `DROP TYPE IF EXISTS "photos_moderation_status_enum";`,
    );
    await queryRunner.query(`DROP TABLE IF EXISTS "profiles";`);
    await queryRunner.query(
      `DROP TYPE IF EXISTS "profiles_verification_status_enum";`,
    );
    await queryRunner.query(`ALTER TABLE "users" DROP COLUMN "role";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "users_role_enum";`);
  }
}
