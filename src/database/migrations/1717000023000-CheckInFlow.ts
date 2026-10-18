import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * The 48hrs Check-In flow (SHOWUP-177, stories 178 to 183).
 *
 * THREE NEW TABLES AND TWO COLUMNS:
 *
 * - `check_in_windows` -- a Check-In window, the `window_id` every Check-In event carries. One opens
 *   when profile creation completes (Profile 23) and lasts 48 hours: today and tomorrow. A
 *   committed Check-In is a window with `committed_at` set, holding the motivation (§30), the vibes
 *   (§31) and the uplift text. "One Check-In per window_id" is the commit's idempotency key.
 * - `check_in_profiles` -- one row per user: whether the intro has been passed (the gate reopens at
 *   Today once it has, story 01) and the last accepted Motivation and Vibe, which pre-select those
 *   steps on the next Check-In (epic rule 3). Never written on a tap or a close; only on an
 *   accepted Continue.
 * - `filter_settings` -- one row per user: who they want to see. ONE STORE with Search's filter
 *   sheet (story 05). `meet_gender` uses the profile's own gender type: §10 is "the §1 gender set,
 *   one vocabulary, no mapping table". `max_age` null is 65+, "no upper limit".
 * - `check_ins.check_in_window_id` and `check_ins.tier` -- the committed hours are written as the
 *   existing availability windows (Epic 4), so discovery and matching read them unchanged. Each run
 *   of consecutive hours of one tier is one row; `tier` keeps the 1st-tap / 2nd-tap distinction
 *   ("both tiers are stored and used", story 02). Rows written before this migration are
 *   `preferred`: they were explicit windows.
 *
 * Every CHECK here repeats a rule the API already refuses, as the profile answers do: a wall for
 * every other path.
 */
export class CheckInFlow1717000023000 implements MigrationInterface {
  name = 'CheckInFlow1717000023000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    const types: [string, string[]][] = [
      [
        'check_in_motivation_enum',
        ['long_term', 'non_monogamy', 'short_term', 'casual', 'friends'],
      ],
      [
        'check_in_vibe_enum',
        [
          'coffee_chat',
          'casual_drink',
          'grab_food',
          'nature_walk',
          'bouldering_table_tennis',
          'workout',
          'sunset',
          'drinks_music',
          'calm',
          'deep_talk',
          'playful',
          'cozy',
          'spontaneous',
          'curious',
          'quick_hello',
          'low_key',
          'high_energy',
          'no_rush',
          'early_night',
          'meet_now',
        ],
      ],
      ['check_ins_tier_enum', ['preferred', 'available']],
    ];
    for (const [name, values] of types) {
      const list = values.map((v) => `'${v}'`).join(', ');
      await queryRunner.query(`
        DO $$ BEGIN
          CREATE TYPE "${name}" AS ENUM (${list});
        EXCEPTION WHEN duplicate_object THEN NULL; END $$;
      `);
    }

    await queryRunner.query(`
      CREATE TABLE IF NOT EXISTS "check_in_windows" (
        "id"           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_id"      uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "opened_at"    timestamptz NOT NULL DEFAULT now(),
        "closes_at"    timestamptz NOT NULL,
        "committed_at" timestamptz,
        "motivation"   "check_in_motivation_enum",
        "vibes"        "check_in_vibe_enum"[],
        "uplift"       text,
        "created_at"   timestamptz NOT NULL DEFAULT now(),
        "updated_at"   timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "check_in_windows_closes_after_open" CHECK ("closes_at" > "opened_at"),
        CONSTRAINT "check_in_windows_vibes_one_to_five" CHECK (
          "vibes" IS NULL OR (
            cardinality("vibes") BETWEEN 1 AND 5 AND array_position("vibes", NULL) IS NULL
          )
        ),
        CONSTRAINT "check_in_windows_committed_has_answers" CHECK (
          "committed_at" IS NULL OR ("motivation" IS NOT NULL AND "vibes" IS NOT NULL)
        ),
        -- 100 graphemes is checked in the service; a grapheme can be several code points (a family
        -- emoji is five), so the wall is generous rather than exact. Never stored as "".
        CONSTRAINT "check_in_windows_uplift_not_empty" CHECK (
          "uplift" IS NULL OR char_length("uplift") BETWEEN 1 AND 1000
        )
      );
    `);
    await queryRunner.query(
      `CREATE INDEX IF NOT EXISTS "idx_check_in_windows_user_closes" ON "check_in_windows" ("user_id", "closes_at");`,
    );

    await queryRunner.query(`
      CREATE TABLE IF NOT EXISTS "check_in_profiles" (
        "user_id"         uuid PRIMARY KEY REFERENCES "users"("id") ON DELETE CASCADE,
        "intro_passed_at" timestamptz,
        "last_motivation" "check_in_motivation_enum",
        "last_vibes"      "check_in_vibe_enum"[],
        "created_at"      timestamptz NOT NULL DEFAULT now(),
        "updated_at"      timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "check_in_profiles_vibes_one_to_five" CHECK (
          "last_vibes" IS NULL OR (
            cardinality("last_vibes") BETWEEN 1 AND 5 AND array_position("last_vibes", NULL) IS NULL
          )
        )
      );
    `);

    await queryRunner.query(`
      CREATE TABLE IF NOT EXISTS "filter_settings" (
        "user_id"     uuid PRIMARY KEY REFERENCES "users"("id") ON DELETE CASCADE,
        "meet_gender" "profiles_gender_enum"[] NOT NULL,
        "min_age"     smallint NOT NULL DEFAULT 18,
        "max_age"     smallint,
        "distance_km" smallint NOT NULL DEFAULT 10,
        "created_at"  timestamptz NOT NULL DEFAULT now(),
        "updated_at"  timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "filter_settings_gender_at_least_one" CHECK (
          cardinality("meet_gender") BETWEEN 1 AND 4 AND array_position("meet_gender", NULL) IS NULL
        ),
        CONSTRAINT "filter_settings_min_age" CHECK ("min_age" BETWEEN 18 AND 65),
        -- 65 is "no upper limit" and is stored as null, so a stored maximum is at most 64.
        CONSTRAINT "filter_settings_max_age" CHECK (
          "max_age" IS NULL OR ("max_age" BETWEEN 18 AND 64 AND "max_age" >= "min_age")
        ),
        CONSTRAINT "filter_settings_distance" CHECK ("distance_km" BETWEEN 1 AND 50)
      );
    `);

    await queryRunner.query(`
      ALTER TABLE "check_ins"
        ADD COLUMN IF NOT EXISTS "check_in_window_id" uuid
          REFERENCES "check_in_windows"("id") ON DELETE SET NULL,
        ADD COLUMN IF NOT EXISTS "tier" "check_ins_tier_enum" NOT NULL DEFAULT 'preferred';
    `);
    await queryRunner.query(
      `CREATE INDEX IF NOT EXISTS "idx_check_ins_window" ON "check_ins" ("check_in_window_id");`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP INDEX IF EXISTS "idx_check_ins_window";`);
    await queryRunner.query(`
      ALTER TABLE "check_ins"
        DROP COLUMN IF EXISTS "tier",
        DROP COLUMN IF EXISTS "check_in_window_id";
    `);
    await queryRunner.query(`DROP TABLE IF EXISTS "filter_settings";`);
    await queryRunner.query(`DROP TABLE IF EXISTS "check_in_profiles";`);
    await queryRunner.query(`DROP TABLE IF EXISTS "check_in_windows";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "check_ins_tier_enum";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "check_in_vibe_enum";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "check_in_motivation_enum";`);
  }
}
