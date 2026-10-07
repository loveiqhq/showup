import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * The "Share some details" answers and the saved flow position (SHOWUP-165 to SHOWUP-173).
 *
 * ENUMS, NOT FREE TEXT. Each detail ticket asks for its column to be "an enum of the §1 values, so
 * the column and the screen cannot drift". The value lists are `enums.json` §1, mirrored in
 * `util/profile-details.ts` and drift-guarded there; changing one needs a migration here too,
 * which is the point -- a new answer is a product decision, not a silent string.
 *
 * GENDER CHANGES TYPE, AND ITS OLD VALUES ARE MAPPED, NOT CAST. It was `varchar(40)` (Epic 3) and
 * accepted any string; the only writer so far was a test sending 'male'. A plain cast would fail
 * on the first value outside the four, so the obvious synonyms are mapped first and anything else
 * becomes NULL. NULL rather than 'other' deliberately: 'other' is an answer a person chooses, and
 * inventing it for them would be a fabricated record. Gender is mandatory (Profile 15), so a NULL
 * simply routes that account back to the gender step, where the person answers for themselves.
 *
 * THE "HIDE ON PROFILE" FLAGS NEED NO COLUMNS: they are entries in the existing `hidden_fields`
 * set (1717000016000), whose vocabulary is validated in code.
 *
 * `flow_position` IS VARCHAR, NOT AN ENUM, unlike the answers. It is a §2 step_id and grows by one
 * value every time a screen ships; an enum would turn each new screen into a migration with a
 * non-transactional `ALTER TYPE ... ADD VALUE`. It is validated against the list in
 * `util/flow-position.ts` instead.
 *
 * HEIGHT is `smallint` with the screen's own bounds as a CHECK, so a value the screen would refuse
 * cannot arrive by any other path either.
 */
export class ProfileDetails1717000020000 implements MigrationInterface {
  name = 'ProfileDetails1717000020000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    const types: [string, string[]][] = [
      ['profiles_gender_enum', ['woman', 'man', 'non_binary', 'other']],
      [
        'profiles_orientation_enum',
        ['straight', 'gay', 'lesbian', 'bisexual', 'pansexual', 'other'],
      ],
      [
        'profiles_dating_language_enum',
        [
          'german',
          'english',
          'spanish',
          'italian',
          'french',
          'turkish',
          'russian',
          'arabic',
        ],
      ],
      [
        'profiles_education_enum',
        ['a_levels_abitur', 'apprenticeship', 'university_degree', 'phd'],
      ],
      [
        'profiles_religion_enum',
        [
          'protestant',
          'catholic',
          'orthodox',
          'muslim',
          'jewish',
          'buddhist',
          'hindu',
          'atheist',
          'spiritual_other',
        ],
      ],
      [
        'profiles_politics_enum',
        [
          'left',
          'mid_left',
          'middle',
          'mid_right',
          'right',
          'conservative',
          'libertarian',
          'apolitical',
        ],
      ],
    ];
    for (const [name, values] of types) {
      const list = values.map((v) => `'${v}'`).join(', ');
      await queryRunner.query(`
        DO $$ BEGIN
          CREATE TYPE "${name}" AS ENUM (${list});
        EXCEPTION WHEN duplicate_object THEN NULL; END $$;
      `);
    }

    // Map, then cast. Anything that is not recognisably one of the four becomes NULL -- see above.
    //
    // ONLY WHILE THE COLUMN IS STILL TEXT, like every other step here being safe to run twice.
    // Once it is the enum, `btrim` has no enum overload and the block would fail outright, so a
    // second run (a half-applied deploy re-run by hand) checks the type first and leaves it alone.
    await queryRunner.query(`
      DO $$ BEGIN
        IF EXISTS (
          SELECT 1 FROM information_schema.columns
           WHERE table_schema = current_schema()
             AND table_name = 'profiles'
             AND column_name = 'gender'
             AND data_type = 'character varying'
        ) THEN
          UPDATE "profiles" SET "gender" = CASE lower(btrim("gender"))
            WHEN 'woman' THEN 'woman'
            WHEN 'female' THEN 'woman'
            WHEN 'man' THEN 'man'
            WHEN 'male' THEN 'man'
            WHEN 'non_binary' THEN 'non_binary'
            WHEN 'non-binary' THEN 'non_binary'
            WHEN 'nonbinary' THEN 'non_binary'
            WHEN 'other' THEN 'other'
            ELSE NULL
          END
          WHERE "gender" IS NOT NULL;
          ALTER TABLE "profiles"
            ALTER COLUMN "gender" TYPE "profiles_gender_enum"
            USING "gender"::"profiles_gender_enum";
        END IF;
      END $$;
    `);

    await queryRunner.query(`
      ALTER TABLE "profiles"
        ADD COLUMN IF NOT EXISTS "height_cm" smallint,
        ADD COLUMN IF NOT EXISTS "orientation" "profiles_orientation_enum",
        ADD COLUMN IF NOT EXISTS "dating_languages" "profiles_dating_language_enum"[],
        ADD COLUMN IF NOT EXISTS "education" "profiles_education_enum",
        ADD COLUMN IF NOT EXISTS "religion" "profiles_religion_enum",
        ADD COLUMN IF NOT EXISTS "politics" "profiles_politics_enum",
        ADD COLUMN IF NOT EXISTS "flow_position" character varying(32);
    `);
    await queryRunner.query(`
      DO $$ BEGIN
        ALTER TABLE "profiles" ADD CONSTRAINT "profiles_height_cm_range"
          CHECK ("height_cm" IS NULL OR "height_cm" BETWEEN 120 AND 230);
      EXCEPTION WHEN duplicate_object THEN NULL; END $$;
    `);
    // A set with nothing in it is not an answer. Skip saves nothing (rule 0), so an empty array
    // could only arrive by mistake, and storing it would read as "answered: no languages".
    await queryRunner.query(`
      DO $$ BEGIN
        ALTER TABLE "profiles" ADD CONSTRAINT "profiles_dating_languages_not_empty"
          CHECK ("dating_languages" IS NULL OR cardinality("dating_languages") > 0);
      EXCEPTION WHEN duplicate_object THEN NULL; END $$;
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "profiles"
        DROP CONSTRAINT IF EXISTS "profiles_dating_languages_not_empty",
        DROP CONSTRAINT IF EXISTS "profiles_height_cm_range",
        DROP COLUMN IF EXISTS "flow_position",
        DROP COLUMN IF EXISTS "politics",
        DROP COLUMN IF EXISTS "religion",
        DROP COLUMN IF EXISTS "education",
        DROP COLUMN IF EXISTS "dating_languages",
        DROP COLUMN IF EXISTS "orientation",
        DROP COLUMN IF EXISTS "height_cm";
    `);
    // Back to free text. The mapped values survive as the strings they now are.
    await queryRunner.query(`
      ALTER TABLE "profiles"
        ALTER COLUMN "gender" TYPE character varying(40) USING "gender"::text;
    `);
    for (const name of [
      'profiles_politics_enum',
      'profiles_religion_enum',
      'profiles_education_enum',
      'profiles_dating_language_enum',
      'profiles_orientation_enum',
      'profiles_gender_enum',
    ]) {
      await queryRunner.query(`DROP TYPE IF EXISTS "${name}";`);
    }
  }
}
