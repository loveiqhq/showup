import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Matching 01 (SHOWUP-190): when a like last became open, for `like_expired.hours_open`.
 *
 * A like that closed and is sent again later re-opens the SAME row (`uq_likes_sender_receiver`), so
 * `created_at` is the first send and counting from it put the days the like lay closed into
 * `hours_open` -- the negative label for the like-back estimate. `opened_at` is stamped on the first
 * send and again on every re-open; existing rows start from their `created_at`.
 */
export class LikeOpenedAt1717000030000 implements MigrationInterface {
  name = 'LikeOpenedAt1717000030000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "likes" ADD COLUMN "opened_at" timestamptz;`,
    );
    await queryRunner.query(`UPDATE "likes" SET "opened_at" = "created_at";`);
    await queryRunner.query(`
      ALTER TABLE "likes"
        ALTER COLUMN "opened_at" SET NOT NULL,
        ALTER COLUMN "opened_at" SET DEFAULT now();
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "likes" DROP COLUMN IF EXISTS "opened_at";`,
    );
  }
}
