import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Check-In 05b, Filters v2 (SHOWUP-189): a Dealbreaker switch on Age range and on Distance.
 *
 * - `filter_settings.age_dealbreaker` / `distance_dealbreaker` -- enums §10, bool, DEFAULT FALSE
 *   "for every user, including existing filter records". Off, the setting is a preference: people
 *   just outside it may appear, ranked lower. On, nobody outside it is ever shown. Gender gets no
 *   switch; it is always strict.
 *
 * What "just outside" means is matching's (SHOWUP-190), not this record's: these are the user's
 * choice, stored with the rest of their filters, and read by the ranker.
 */
export class FilterDealbreakers1717000028000 implements MigrationInterface {
  name = 'FilterDealbreakers1717000028000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "filter_settings"
        ADD COLUMN "age_dealbreaker" boolean NOT NULL DEFAULT false,
        ADD COLUMN "distance_dealbreaker" boolean NOT NULL DEFAULT false;
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "filter_settings"
        DROP COLUMN IF EXISTS "distance_dealbreaker",
        DROP COLUMN IF EXISTS "age_dealbreaker";
    `);
  }
}
