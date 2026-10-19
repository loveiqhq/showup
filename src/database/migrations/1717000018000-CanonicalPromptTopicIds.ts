import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Rewrites six prompt topic ids to the ones the tracking registry dictates (SHOWUP-158).
 *
 * WHY THERE WERE TWO SETS OF IDS IN THE FIRST PLACE. The ticket's dependency list asks for "a
 * stable topic id per topic. Store the id, not the display string, or every copy edit orphans
 * existing prompts." The design reference carries the fifteen DISPLAY STRINGS and no ids at all,
 * and §17 of `tracking/enums.json` — the dictionary that does carry them — did not exist at
 * registry 1.3.0, which is what the clients were written against. So ids were assigned locally,
 * and when §17 arrived, six of the fifteen did not match.
 *
 * WHY THIS IS A MIGRATION AND NOT A RENAME. `topic_id` is the join key in two different places at
 * once: it is what the analytics warehouse groups topic demand by, and it is what this table
 * stores. Correcting only the client would leave every prompt already written filed under an id
 * that no longer appears in the dictionary — which is precisely the orphaning the ticket's
 * dependency exists to prevent, just arrived at from the other direction.
 *
 * SAFE TO RUN ON A TABLE THAT ALREADY HOLDS ROWS. Each statement rewrites one old id, and a
 * database where nobody used the old ids simply updates nothing. The unique index on
 * (user_id, topic_id) cannot be violated by this: the six new ids are not in the old set, so no
 * row can collide with a sibling that already holds the target id.
 *
 * REVERSIBLE, and the down migration is the exact inverse — worth having because the ids are what
 * a rollback of the client build would go back to looking for.
 */
export class CanonicalPromptTopicIds1717000018000 implements MigrationInterface {
  name = 'CanonicalPromptTopicIds1717000018000';

  /** Local id -> registry §17 id. The nine not listed here already agreed. */
  private static readonly RENAMES: ReadonlyArray<readonly [string, string]> = [
    ['first_date', 'first_date_usually'],
    ['ideal_thirty', 'ideal_30_min'],
    ['cross_town', 'cross_town_for'],
    ['spontaneous', 'spontaneous_plan'],
    ['thirty_feels', 'thirty_min_feels'],
    ['real_life_more', 'in_real_life_more'],
  ];

  public async up(queryRunner: QueryRunner): Promise<void> {
    for (const [from, to] of CanonicalPromptTopicIds1717000018000.RENAMES) {
      await queryRunner.query(
        `UPDATE "profile_prompts" SET "topic_id" = $1 WHERE "topic_id" = $2`,
        [to, from],
      );
    }
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    for (const [from, to] of CanonicalPromptTopicIds1717000018000.RENAMES) {
      await queryRunner.query(
        `UPDATE "profile_prompts" SET "topic_id" = $1 WHERE "topic_id" = $2`,
        [from, to],
      );
    }
  }
}
