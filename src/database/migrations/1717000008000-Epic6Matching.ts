import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Epic 6 — Likes & Matching schema.
 *  - likes: one user's interest in another, with an optional premium message; unique per (sender, receiver)
 *  - matches: the mutual connection, stored as a canonical ordered pair, unique per couple
 */
export class Epic6Matching1717000008000 implements MigrationInterface {
  name = 'Epic6Matching1717000008000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `CREATE TYPE "likes_status_enum" AS ENUM ('active', 'matched');`,
    );
    await queryRunner.query(
      `CREATE TYPE "matches_status_enum" AS ENUM ('active', 'expired', 'cancelled');`,
    );

    await queryRunner.query(`
      CREATE TABLE "likes" (
        "id"          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "sender_id"   uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "receiver_id" uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "status"      "likes_status_enum" NOT NULL DEFAULT 'active',
        "message"     varchar(500),
        "created_at"  timestamptz NOT NULL DEFAULT now(),
        "updated_at"  timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "uq_likes_sender_receiver" UNIQUE ("sender_id", "receiver_id")
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_likes_sender" ON "likes" ("sender_id");`,
    );
    await queryRunner.query(
      `CREATE INDEX "idx_likes_receiver_status" ON "likes" ("receiver_id", "status");`,
    );

    await queryRunner.query(`
      CREATE TABLE "matches" (
        "id"         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_a_id"  uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "user_b_id"  uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "status"     "matches_status_enum" NOT NULL DEFAULT 'active',
        "created_at" timestamptz NOT NULL DEFAULT now(),
        "updated_at" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "uq_matches_pair" UNIQUE ("user_a_id", "user_b_id")
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_matches_user_a" ON "matches" ("user_a_id");`,
    );
    await queryRunner.query(
      `CREATE INDEX "idx_matches_user_b" ON "matches" ("user_b_id");`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "matches";`);
    await queryRunner.query(`DROP TABLE IF EXISTS "likes";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "matches_status_enum";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "likes_status_enum";`);
  }
}
