import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Adds `profile_prompts`: up to three written answers per account (SHOWUP-158).
 *
 * WHY A TABLE AND NOT A COLUMN ON `profiles`
 *
 * Three rows is small enough that a `jsonb` column on the profile would work, and it is still the
 * wrong shape. Each prompt is addressed on its own by the API -- `PUT /me/prompts/{topicId}`,
 * `DELETE /me/prompts/{topicId}` -- because that is how the screen edits them, one at a time. With
 * a column every save would be a read-modify-write of the whole set, which is a lost update the
 * moment anything else touches the profile in the same moment. A row per prompt makes the unique
 * constraint below do that work instead.
 *
 * `topic_id` IS NOT A FOREIGN KEY and there is no topics table. The fifteen topics are design
 * content that lives in the app; see the entity for the argument at length.
 *
 * THE UNIQUE CONSTRAINT IS THE POINT. One prompt per topic per user is what makes the PUT
 * idempotent, and idempotent is what "Save overwrites" on the write sheet means. Without it, a
 * double-tap on Save writes two rows for one question.
 *
 * `varchar(200)` for a 160-character product limit: the DTO rejects at 160, and the slack means a
 * request that somehow got past it fails validation rather than the database, which are two very
 * different responses to return to a client.
 */
export class ProfilePrompts1717000017000 implements MigrationInterface {
  name = 'ProfilePrompts1717000017000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE IF NOT EXISTS "profile_prompts" (
        "id" uuid NOT NULL DEFAULT uuid_generate_v4(),
        "user_id" uuid NOT NULL,
        "topic_id" character varying(64) NOT NULL,
        "answer" character varying(200) NOT NULL,
        "position" integer NOT NULL DEFAULT 0,
        "created_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
        "updated_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
        CONSTRAINT "profile_prompts_pkey" PRIMARY KEY ("id")
      );
    `);
    await queryRunner.query(`
      CREATE UNIQUE INDEX IF NOT EXISTS "profile_prompts_user_topic_unique"
        ON "profile_prompts" ("user_id", "topic_id");
    `);
    // Every read is "this user's prompts, in order", so the index carries the sort as well as the
    // lookup -- three rows do not need it today and the plan stops being a sequential scan of the
    // whole table the moment there is more than one account.
    await queryRunner.query(`
      CREATE INDEX IF NOT EXISTS "profile_prompts_user_position_idx"
        ON "profile_prompts" ("user_id", "position");
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `DROP INDEX IF EXISTS "profile_prompts_user_position_idx";`,
    );
    await queryRunner.query(
      `DROP INDEX IF EXISTS "profile_prompts_user_topic_unique";`,
    );
    await queryRunner.query(`DROP TABLE IF EXISTS "profile_prompts";`);
  }
}
