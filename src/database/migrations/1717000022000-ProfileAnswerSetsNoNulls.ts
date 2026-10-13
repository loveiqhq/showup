import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * No NULL inside an answer set (found by the 13 October 2026 review of SHOWUP-174 to SHOWUP-176).
 *
 * The CHECKs on the answer sets count elements -- `cardinality("interests") BETWEEN 1 AND 10`,
 * `cardinality("family_ahead") <= 1` -- and a NULL element counts. `'{NULL}'` passed every one of
 * them, and the apps decode each set as a list of strings, so such a row would break the owner's
 * own profile read rather than show one odd answer. The API cannot produce it (class-validator
 * refuses a null member); these CHECKs are the wall for every other path, which is the reason the
 * others exist (1717000021000's header).
 *
 * A SEPARATE MIGRATION, not an edit to 1717000021000: that one has already run on local databases,
 * and a migration that has run is never rewritten. `dating_languages` (1717000020000) had the same
 * gap and gets the same CHECK. `hidden_fields` is not here: it is a text set validated against
 * `util/hidden-fields.ts`, with its own rules.
 */
export class ProfileAnswerSetsNoNulls1717000022000 implements MigrationInterface {
  name = 'ProfileAnswerSetsNoNulls1717000022000';

  /** Every enum-array answer column on `profiles`. */
  private static readonly SETS = [
    'dating_languages',
    'interests',
    'partnership',
    'family',
    'living',
    'family_ahead',
    'partnership_ahead',
    'living_ahead',
  ];

  public async up(queryRunner: QueryRunner): Promise<void> {
    for (const column of ProfileAnswerSetsNoNulls1717000022000.SETS) {
      await queryRunner.query(`
        DO $$ BEGIN
          ALTER TABLE "profiles" ADD CONSTRAINT "profiles_${column}_no_null_member"
            CHECK ("${column}" IS NULL OR array_position("${column}", NULL) IS NULL);
        EXCEPTION WHEN duplicate_object THEN NULL; END $$;
      `);
    }
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    for (const column of [
      ...ProfileAnswerSetsNoNulls1717000022000.SETS,
    ].reverse()) {
      await queryRunner.query(
        `ALTER TABLE "profiles" DROP CONSTRAINT IF EXISTS "profiles_${column}_no_null_member"`,
      );
    }
  }
}
