import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Date search, story 01 (SHOWUP-184, epic SHOWUP-187): what the Search tab reads and writes.
 *
 * - `users.plan_tier` -- Freemium or Premium (epic rule 2: "the plan picks the surface"). Nothing
 *   sells Premium yet (the likes upsell and billing are their own stories), so every account is
 *   Freemium until a person switches it; the dev menu does that outside production.
 * - `users.search_coach_hidden` -- the actions coach's one stored act, `Don't show again`. A
 *   server flag "so it never shows again on any device"; false for every existing user.
 * - `check_in_windows.district` -- the neighbourhood the Search hero names (`~3 km · Kreuzberg`).
 *   Named by the phone's own geocoder at `Find my date` and sent with the commit: the server never
 *   geocodes and holds no third-party geocoding key. Null when the phone could not name one, and
 *   the hero then shows the distance alone.
 * - `likes`: a like can now close without a decision (epic rule 9, §36). `withdrawn` joins the
 *   status, `closed_reason` says why (`matched`: either person's other like became mutual first;
 *   `no_overlap`: the two Check-Ins have no meet time left), `match_id` names the match that
 *   caused it. `match_id` is SET NULL on a deleted match: the like stays closed either way.
 * - `search_passes` -- `Not now` and `Not interested`. `not_now` is stored against the viewer's
 *   Check-In window, so the profile comes back at the next one; `not_interested` has no window and
 *   is permanent and one-way. The CHECK makes that the only shape a row can have.
 *
 * WHY NO CHECK TIES `closed_reason` TO `status = 'withdrawn'`: a value added to an enum cannot be
 * used in the transaction that adds it, and migrations run in one. The service writes the three
 * columns together; the CHECK below holds what can be said without naming the new value.
 *
 * Deletion: `search_passes` cascades from `users`, as `likes` does. The account scrub UPDATEs the
 * user row rather than deleting it, so neither cascade fires today -- the same deferred gap as
 * every other table (SHOWUP-15).
 */
export class DateSearch1717000026000 implements MigrationInterface {
  name = 'DateSearch1717000026000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `CREATE TYPE "users_plan_tier_enum" AS ENUM ('freemium', 'premium');`,
    );
    await queryRunner.query(`
      ALTER TABLE "users"
        ADD COLUMN "plan_tier" "users_plan_tier_enum" NOT NULL DEFAULT 'freemium',
        ADD COLUMN "search_coach_hidden" boolean NOT NULL DEFAULT false;
    `);

    await queryRunner.query(`
      ALTER TABLE "check_in_windows" ADD COLUMN "district" varchar(80);
      ALTER TABLE "check_in_windows"
        ADD CONSTRAINT "check_in_windows_district_not_blank"
        CHECK ("district" IS NULL OR length(btrim("district")) > 0);
    `);

    await queryRunner.query(
      `ALTER TYPE "likes_status_enum" ADD VALUE IF NOT EXISTS 'withdrawn';`,
    );
    await queryRunner.query(
      `CREATE TYPE "likes_closed_reason_enum" AS ENUM ('matched', 'no_overlap');`,
    );
    await queryRunner.query(`
      ALTER TABLE "likes"
        ADD COLUMN "closed_reason" "likes_closed_reason_enum",
        ADD COLUMN "closed_at" timestamptz,
        ADD COLUMN "match_id" uuid REFERENCES "matches"("id") ON DELETE SET NULL;
      ALTER TABLE "likes"
        ADD CONSTRAINT "likes_closed_together"
        CHECK (("closed_reason" IS NULL) = ("closed_at" IS NULL));
    `);

    await queryRunner.query(
      `CREATE TYPE "search_pass_type_enum" AS ENUM ('not_now', 'not_interested');`,
    );
    await queryRunner.query(`
      CREATE TABLE "search_passes" (
        "id"         uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
        "viewer_id"  uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "target_id"  uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "pass_type"  "search_pass_type_enum" NOT NULL,
        "window_id"  uuid REFERENCES "check_in_windows"("id") ON DELETE CASCADE,
        "created_at" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "search_passes_not_self" CHECK ("viewer_id" <> "target_id"),
        CONSTRAINT "search_passes_window_iff_not_now"
          CHECK (("pass_type" = 'not_now') = ("window_id" IS NOT NULL))
      );
    `);
    // One row per decision: a repeated tap (a retry) lands on the same row.
    await queryRunner.query(`
      CREATE UNIQUE INDEX "search_passes_not_interested_key"
        ON "search_passes" ("viewer_id", "target_id") WHERE "pass_type" = 'not_interested';
      CREATE UNIQUE INDEX "search_passes_not_now_key"
        ON "search_passes" ("viewer_id", "target_id", "window_id") WHERE "pass_type" = 'not_now';
      CREATE INDEX "search_passes_viewer_idx" ON "search_passes" ("viewer_id");
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "search_passes";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "search_pass_type_enum";`);

    // A withdrawn like goes back to `active`: the old enum has no closed state, and `matched`
    // would claim a match the like never made.
    await queryRunner.query(
      `ALTER TABLE "likes" DROP CONSTRAINT IF EXISTS "likes_closed_together";`,
    );
    await queryRunner.query(`
      ALTER TABLE "likes"
        DROP COLUMN IF EXISTS "match_id",
        DROP COLUMN IF EXISTS "closed_at",
        DROP COLUMN IF EXISTS "closed_reason";
    `);
    await queryRunner.query(`DROP TYPE IF EXISTS "likes_closed_reason_enum";`);
    await queryRunner.query(`
      ALTER TABLE "likes" ALTER COLUMN "status" DROP DEFAULT;
      ALTER TABLE "likes" ALTER COLUMN "status" TYPE text;
      UPDATE "likes" SET "status" = 'active' WHERE "status" = 'withdrawn';
      DROP TYPE "likes_status_enum";
      CREATE TYPE "likes_status_enum" AS ENUM ('active', 'matched');
      ALTER TABLE "likes" ALTER COLUMN "status" TYPE "likes_status_enum" USING "status"::"likes_status_enum";
      ALTER TABLE "likes" ALTER COLUMN "status" SET DEFAULT 'active';
    `);

    await queryRunner.query(`
      ALTER TABLE "check_in_windows" DROP CONSTRAINT IF EXISTS "check_in_windows_district_not_blank";
      ALTER TABLE "check_in_windows" DROP COLUMN IF EXISTS "district";
    `);

    await queryRunner.query(`
      ALTER TABLE "users"
        DROP COLUMN IF EXISTS "search_coach_hidden",
        DROP COLUMN IF EXISTS "plan_tier";
    `);
    await queryRunner.query(`DROP TYPE IF EXISTS "users_plan_tier_enum";`);
  }
}
