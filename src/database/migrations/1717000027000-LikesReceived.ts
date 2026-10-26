import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Date search, stories 02 and 03 (SHOWUP-185, SHOWUP-186): the Premium grid and the Likes tab.
 *
 * - `likes.status` gains `declined`: `Not interested` on a person who liked you closes their like
 *   for good (Search 02 and 03). It is a DECISION, so it is not `withdrawn` -- that status means a
 *   like closed without one, and fires `like_withdrawn`. A declined like is never counted, never
 *   listed, never reopened by a retry, and the liker is not told.
 * - `likes.closed_seen_at`: when the recipient was first shown the like as closed (Too late / Out of
 *   time). "A closed card is in the payload until the visit after the one where it was first shown"
 *   -- the Likes list sets it on load, the availability re-check and a refused confirm set it when
 *   they close the card in front of the user, and the next load leaves it out.
 *
 * WHY NO CHECK NAMES `declined`: a value added to an enum cannot be used in the transaction that
 * adds it, and migrations run in one (the same reason as `DateSearch1717000026000`).
 */
export class LikesReceived1717000027000 implements MigrationInterface {
  name = 'LikesReceived1717000027000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TYPE "likes_status_enum" ADD VALUE IF NOT EXISTS 'declined';`,
    );
    await queryRunner.query(
      `ALTER TABLE "likes" ADD COLUMN "closed_seen_at" timestamptz;`,
    );
    // A seen mark is only ever set on a closed like.
    await queryRunner.query(`
      ALTER TABLE "likes"
        ADD CONSTRAINT "likes_seen_only_when_closed"
        CHECK ("closed_seen_at" IS NULL OR "closed_at" IS NOT NULL);
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "likes" DROP CONSTRAINT IF EXISTS "likes_seen_only_when_closed";`,
    );
    await queryRunner.query(
      `ALTER TABLE "likes" DROP COLUMN IF EXISTS "closed_seen_at";`,
    );
    // The old enum has no place for a declined like: `withdrawn` needs a reason it does not have
    // (`likes_closed_together`), and `active` would reopen it. It is removed instead -- the decision
    // it recorded is also the `not_interested` pass, which stays and keeps the liker out of the
    // viewer's count.
    await queryRunner.query(`
      DELETE FROM "likes" WHERE "status" = 'declined';
      ALTER TABLE "likes" ALTER COLUMN "status" DROP DEFAULT;
      ALTER TABLE "likes" ALTER COLUMN "status" TYPE text;
      DROP TYPE "likes_status_enum";
      CREATE TYPE "likes_status_enum" AS ENUM ('active', 'matched', 'withdrawn');
      ALTER TABLE "likes" ALTER COLUMN "status" TYPE "likes_status_enum" USING "status"::"likes_status_enum";
      ALTER TABLE "likes" ALTER COLUMN "status" SET DEFAULT 'active';
    `);
  }
}
