import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Adds `profile_media`: one 10-second video and one 15-second voice note per account (SHOWUP-161).
 *
 * THE UNIQUE CONSTRAINT IS THE POINT, as it was for prompts. The screen draws exactly two slots,
 * so "a second video" is not something a user can ask for and `Retake` means replace. Enforcing
 * that here rather than in the service is what makes a retried upload over a flaky connection
 * safe: the second attempt updates the row it already wrote instead of leaving the account holding
 * two videos with one of them unreachable from the UI.
 *
 * `media_prompt_id` IS NOT A FOREIGN KEY and there is no prompts table, for the same reason
 * `topic_id` is not: the twelve are design content, registered in `enums.json` section 20 and
 * mirrored in `util/media-prompts.ts`. The id is stored rather than the display string so editing
 * the copy never orphans the recordings made under it.
 *
 * `duration_ms` RATHER THAN SECONDS. The caps are 10 and 15, and a take rounded to a whole second
 * cannot be told apart from one that ran into the cap -- which is the measurement SHOWUP-161 asks
 * for on whether the caps are the right length.
 *
 * NO UPLOAD-STATUS COLUMN. Queued, in flight and failed are states of an upload the client holds;
 * the server never observes them. A row here means the bytes are stored, and that is the only
 * status it can honestly report.
 */
export class ProfileMedia1717000019000 implements MigrationInterface {
  name = 'ProfileMedia1717000019000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      DO $$ BEGIN
        CREATE TYPE "profile_media_kind_enum" AS ENUM ('video', 'voice');
      EXCEPTION WHEN duplicate_object THEN NULL; END $$;
    `);
    await queryRunner.query(`
      DO $$ BEGIN
        CREATE TYPE "media_moderation_status_enum"
          AS ENUM ('pending', 'approved', 'rejected');
      EXCEPTION WHEN duplicate_object THEN NULL; END $$;
    `);
    // `moderation_standing_enum` is NOT created here. 1717000010000-Epic12Safety owns it and
    // always runs first, so it is guaranteed to exist. A defensive CREATE would be inert on every
    // path except the one where it does harm -- firing on a database that somehow lacked the type
    // and inventing a value list that does not match ModerationStanding, which the entity would
    // then fail against. Depend on the earlier migration instead of half-guarding.

    await queryRunner.query(`
      CREATE TABLE IF NOT EXISTS "profile_media" (
        "id" uuid NOT NULL DEFAULT uuid_generate_v4(),
        "user_id" uuid NOT NULL,
        "kind" "profile_media_kind_enum" NOT NULL,
        "storage_key" character varying(512) NOT NULL,
        "content_type" character varying(100) NOT NULL,
        "duration_ms" integer NOT NULL,
        "media_prompt_id" character varying(64) NOT NULL,
        "moderation_status" "media_moderation_status_enum" NOT NULL DEFAULT 'pending',
        "moderation_standing" "moderation_standing_enum" NOT NULL DEFAULT 'active',
        "created_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
        CONSTRAINT "profile_media_pkey" PRIMARY KEY ("id")
      );
    `);
    await queryRunner.query(`
      CREATE UNIQUE INDEX IF NOT EXISTS "profile_media_user_kind_key"
        ON "profile_media" ("user_id", "kind");
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `DROP INDEX IF EXISTS "profile_media_user_kind_key";`,
    );
    await queryRunner.query(`DROP TABLE IF EXISTS "profile_media";`);
    await queryRunner.query(
      `DROP TYPE IF EXISTS "media_moderation_status_enum";`,
    );
    await queryRunner.query(`DROP TYPE IF EXISTS "profile_media_kind_enum";`);
    // `moderation_standing_enum` is NOT dropped: it belongs to Epic12Safety and users, profiles
    // and profile_photos all still have a column of it.
  }
}
