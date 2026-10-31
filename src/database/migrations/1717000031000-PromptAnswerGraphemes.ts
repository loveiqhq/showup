import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Prompt answers are capped at 160 GRAPHEMES, not 160 UTF-16 units (SHOWUP-191 item 6).
 *
 * `varchar(200)` was sized for a 160 cap counted the way a JS length counts. Counted the way a
 * person sees it, a flag is one character and two code points, and Postgres's varchar limit counts
 * code points -- so 160 flags would pass validation and fail the INSERT. The DTO's wall is 1,000
 * UTF-16 units, which is at most 1,000 code points, so this column can never refuse what
 * validation passed.
 *
 * Widening a varchar is a catalog change in Postgres, not a table rewrite.
 */
export class PromptAnswerGraphemes1717000031000 implements MigrationInterface {
  name = 'PromptAnswerGraphemes1717000031000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "profile_prompts" ALTER COLUMN "answer" TYPE character varying(1000);`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    // An answer longer than 200 code points is cut rather than refusing the rollback.
    await queryRunner.query(`
      ALTER TABLE "profile_prompts"
        ALTER COLUMN "answer" TYPE character varying(200) USING left("answer", 200);
    `);
  }
}
