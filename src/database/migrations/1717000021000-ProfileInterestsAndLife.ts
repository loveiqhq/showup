import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Interests, Life and Future (SHOWUP-174 to SHOWUP-176, Profiles 21 to 23) -- the last three steps
 * of "Share some details".
 *
 * ENUMS, AS FOR EVERY OTHER ANSWER (see 1717000020000). The lists are `enums.json` §1 (the six
 * "How I live" fields), §3 (interest tags) and §4 (the six life questions), mirrored in
 * `util/profile-details.ts` and drift-guarded there.
 *
 * TWO MEANINGS OF EMPTY, DELIBERATELY DIFFERENT:
 * - `interests` refuses an empty set, like `dating_languages`: Continue with nothing picked is a
 *   Skip and saves nothing (Profile 21), so `{}` could only arrive by mistake. It also refuses more
 *   than ten -- "capped at 10" is a rule, not a payload guard.
 * - The six life sets ACCEPT an empty set: Continue "saves an unanswered question as empty"
 *   (Profile 22), so `{}` is "walked past it" and NULL is "never reached the screen".
 *
 * EXCLUSIVE VALUES ARE CHECKED HERE TOO. "No children" cannot sit beside "Single parent", one
 * family vision at most, "I want to find out together" alone. The screens cannot produce such a set
 * and the API refuses one; the CHECK is the wall for every other path (an admin script, a future
 * endpoint), which is the same reasoning as the height range.
 *
 * `profiles_life_*` and `profiles_future_*` are separate types even where values repeat
 * (`open_relationship` is in both partnership lists): they are different questions and can change
 * apart.
 */
export class ProfileInterestsAndLife1717000021000 implements MigrationInterface {
  name = 'ProfileInterestsAndLife1717000021000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    const types: [string, string[]][] = [
      [
        'profiles_activity_enum',
        ['couch_lover', 'moderately_active', 'active', 'sport_is_my_life'],
      ],
      [
        'profiles_diet_enum',
        ['everything', 'flexitarian', 'vegetarian', 'vegan'],
      ],
      ['profiles_alcohol_enum', ['never', 'rarely', 'socially', 'regularly']],
      ['profiles_smoking_enum', ['no', 'sometimes', 'yes']],
      ['profiles_rhythm_enum', ['early_bird', 'night_owl']],
      ['profiles_social_enum', ['cozy_at_home', 'balanced', 'always_out']],
      [
        'profiles_interest_enum',
        [
          'gym',
          'running',
          'hyrox_crossfit',
          'yoga_pilates',
          'bouldering',
          'cycling',
          'swimming',
          'hiking',
          'team_sports',
          'tennis_padel',
          'martial_arts',
          'winter_sports',
          'cooking',
          'baking',
          'trying_new_restaurants',
          'brunch',
          'street_food_markets',
          'wine',
          'craft_beer',
          'coffee_spots',
          'cocktail_bars',
          'live_music',
          'museums_exhibitions',
          'theatre',
          'cinema',
          'reading',
          'comedy',
          'festivals',
          'making_art_music',
          'tv_series',
          'movie_nights',
          'gaming',
          'board_games',
          'podcasts',
          'plants_gardening',
          'bars',
          'clubbing',
          'dinner_parties',
          'quiz_nights',
          'karaoke',
          'concerts_with_friends',
          'volunteering',
          'city_breaks',
          'road_trips',
          'beach_relax',
          'all_inclusive',
          'backpacking',
          'surf_adventure',
          'wellness_spa',
          'camping',
          'cruises',
        ],
      ],
      [
        'profiles_life_partnership_enum',
        ['single', 'open_relationship', 'ethically_non_monogamous', 'divorced'],
      ],
      [
        'profiles_life_family_enum',
        [
          'no_children',
          'has_children',
          'single_parent',
          'co_parenting',
          'empty_nester',
        ],
      ],
      [
        'profiles_life_living_enum',
        [
          'living_alone',
          'living_with_roommates',
          'living_with_family',
          'living_with_children',
        ],
      ],
      [
        'profiles_future_family_enum',
        ['want_kids', 'dont_want_kids', 'find_out_together'],
      ],
      [
        'profiles_future_partnership_enum',
        [
          'monogamous_relationship',
          'open_relationship',
          'ethically_non_monogamous',
          'find_out_together',
        ],
      ],
      [
        'profiles_future_living_enum',
        ['stay_local', 'expat_life', 'move_abroad', 'find_out_together'],
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

    await queryRunner.query(`
      ALTER TABLE "profiles"
        ADD COLUMN IF NOT EXISTS "activity" "profiles_activity_enum",
        ADD COLUMN IF NOT EXISTS "diet" "profiles_diet_enum",
        ADD COLUMN IF NOT EXISTS "alcohol" "profiles_alcohol_enum",
        ADD COLUMN IF NOT EXISTS "smoking" "profiles_smoking_enum",
        ADD COLUMN IF NOT EXISTS "rhythm" "profiles_rhythm_enum",
        ADD COLUMN IF NOT EXISTS "social" "profiles_social_enum",
        ADD COLUMN IF NOT EXISTS "interests" "profiles_interest_enum"[],
        ADD COLUMN IF NOT EXISTS "partnership" "profiles_life_partnership_enum"[],
        ADD COLUMN IF NOT EXISTS "family" "profiles_life_family_enum"[],
        ADD COLUMN IF NOT EXISTS "living" "profiles_life_living_enum"[],
        ADD COLUMN IF NOT EXISTS "family_ahead" "profiles_future_family_enum"[],
        ADD COLUMN IF NOT EXISTS "partnership_ahead" "profiles_future_partnership_enum"[],
        ADD COLUMN IF NOT EXISTS "living_ahead" "profiles_future_living_enum"[];
    `);

    // The constraints, each named so `down` and a re-run can find it. The value literals are cast
    // to the column's own type so the comparison is enum-to-enum.
    const checks: [string, string][] = [
      [
        'profiles_interests_one_to_ten',
        `"interests" IS NULL OR cardinality("interests") BETWEEN 1 AND 10`,
      ],
      [
        'profiles_family_no_children_alone',
        `"family" IS NULL OR NOT ('no_children'::"profiles_life_family_enum" = ANY("family") AND cardinality("family") > 1)`,
      ],
      [
        'profiles_family_ahead_at_most_one',
        `"family_ahead" IS NULL OR cardinality("family_ahead") <= 1`,
      ],
      [
        'profiles_partnership_ahead_find_out_alone',
        `"partnership_ahead" IS NULL OR NOT ('find_out_together'::"profiles_future_partnership_enum" = ANY("partnership_ahead") AND cardinality("partnership_ahead") > 1)`,
      ],
      [
        'profiles_living_ahead_find_out_alone',
        `"living_ahead" IS NULL OR NOT ('find_out_together'::"profiles_future_living_enum" = ANY("living_ahead") AND cardinality("living_ahead") > 1)`,
      ],
    ];
    for (const [name, condition] of checks) {
      await queryRunner.query(`
        DO $$ BEGIN
          ALTER TABLE "profiles" ADD CONSTRAINT "${name}" CHECK (${condition});
        EXCEPTION WHEN duplicate_object THEN NULL; END $$;
      `);
    }
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "profiles"
        DROP CONSTRAINT IF EXISTS "profiles_living_ahead_find_out_alone",
        DROP CONSTRAINT IF EXISTS "profiles_partnership_ahead_find_out_alone",
        DROP CONSTRAINT IF EXISTS "profiles_family_ahead_at_most_one",
        DROP CONSTRAINT IF EXISTS "profiles_family_no_children_alone",
        DROP CONSTRAINT IF EXISTS "profiles_interests_one_to_ten",
        DROP COLUMN IF EXISTS "living_ahead",
        DROP COLUMN IF EXISTS "partnership_ahead",
        DROP COLUMN IF EXISTS "family_ahead",
        DROP COLUMN IF EXISTS "living",
        DROP COLUMN IF EXISTS "family",
        DROP COLUMN IF EXISTS "partnership",
        DROP COLUMN IF EXISTS "interests",
        DROP COLUMN IF EXISTS "social",
        DROP COLUMN IF EXISTS "rhythm",
        DROP COLUMN IF EXISTS "smoking",
        DROP COLUMN IF EXISTS "alcohol",
        DROP COLUMN IF EXISTS "diet",
        DROP COLUMN IF EXISTS "activity";
    `);
    for (const name of [
      'profiles_future_living_enum',
      'profiles_future_partnership_enum',
      'profiles_future_family_enum',
      'profiles_life_living_enum',
      'profiles_life_family_enum',
      'profiles_life_partnership_enum',
      'profiles_interest_enum',
      'profiles_social_enum',
      'profiles_rhythm_enum',
      'profiles_smoking_enum',
      'profiles_alcohol_enum',
      'profiles_diet_enum',
      'profiles_activity_enum',
    ]) {
      await queryRunner.query(`DROP TYPE IF EXISTS "${name}";`);
    }
  }
}
