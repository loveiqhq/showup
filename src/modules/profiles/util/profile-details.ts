/**
 * The "Share some details" answers -- the closed value sets the detail screens offer
 * (SHOWUP-167 to SHOWUP-173).
 *
 * MIRRORED FROM THE TRACKING REGISTRY, NOT INVENTED HERE. Every list below is `enums.json` §1 for
 * its `field_id`, in the registry's order, and `profile-details.spec.ts` reads the registry and
 * fails if the two disagree. The same string is the column value, the API value and the analytics
 * value, which is the whole point of a registry: the screen label can be reworded without
 * touching a stored row, because the label is never what is stored.
 *
 * ORDER IS MEANINGFUL. The screens render these in this order, and dating languages are stored
 * "in list order" (Profile 17), so a client cannot make two users with the same answers look
 * different by ticking them in a different sequence.
 *
 * Class 2 fields -- gender, orientation, religion, politics -- are special-category data in the
 * tracking registry. Orientation, religion and politics are returned only to their owner
 * (`OwnProfileDto` on `/me/profile`). Gender is the exception and predates these screens: it is on
 * the shared `ProfileDto` the admin verification view uses, and on the discovery card, where it is
 * withheld once the person hides it. All four are redacted from logs and error reports by key
 * name (`common/privacy/prohibited-fields.ts`).
 */

/** Height bounds in whole centimetres, as the screen validates them (Profile 14). */
export const HEIGHT_CM_MIN = 120;
export const HEIGHT_CM_MAX = 230;

/** §1 `gender`. Singular because the user describes themselves; a search filter pluralises. */
export const GENDERS = ['woman', 'man', 'non_binary', 'other'] as const;
export type Gender = (typeof GENDERS)[number];

/** §1 `orientation`. `gay` and `lesbian` are separate values, not one clinical term. */
export const ORIENTATIONS = [
  'straight',
  'gay',
  'lesbian',
  'bisexual',
  'pansexual',
  'other',
] as const;
export type Orientation = (typeof ORIENTATIONS)[number];

/** §1 `dating_language`. A closed list of eight -- not BCP 47. */
export const DATING_LANGUAGES = [
  'german',
  'english',
  'spanish',
  'italian',
  'french',
  'turkish',
  'russian',
  'arabic',
] as const;
export type DatingLanguage = (typeof DATING_LANGUAGES)[number];

/** §1 `education`. */
export const EDUCATIONS = [
  'a_levels_abitur',
  'apprenticeship',
  'university_degree',
  'phd',
] as const;
export type Education = (typeof EDUCATIONS)[number];

/** §1 `religion`. `muslim` added 5 Oct 2026 (registry 1.4.14). */
export const RELIGIONS = [
  'protestant',
  'catholic',
  'orthodox',
  'muslim',
  'jewish',
  'buddhist',
  'hindu',
  'atheist',
  'spiritual_other',
] as const;
export type Religion = (typeof RELIGIONS)[number];

/**
 * §1 `politics`. Linear since 5 Oct 2026 (registry 1.4.15): the left-to-right spectrum, then the
 * three off-axis answers.
 */
export const POLITICS = [
  'left',
  'mid_left',
  'middle',
  'mid_right',
  'right',
  'conservative',
  'libertarian',
  'apolitical',
] as const;
export type Politics = (typeof POLITICS)[number];

/**
 * Upper bound on a submitted language array, as a payload guard rather than a vocabulary one --
 * the same reasoning as `MAX_SUBMITTED_HIDDEN_FIELDS`. Duplicates are collapsed below, so the cap
 * only stops an absurd payload; the allow-list is what keeps values correct.
 */
export const MAX_SUBMITTED_DATING_LANGUAGES = 64;

/**
 * A submitted language set, de-duplicated and put into list order.
 *
 * "Saved in list order" (Profile 17) is enforced HERE rather than trusted from the client, so the
 * stored row is canonical whatever sequence the boxes were ticked in. Unknown values are not
 * filtered out silently: validation rejects them before this runs, and anything that slipped past
 * would be dropped rather than stored, because an unreadable value is worse than a missing one.
 */
export function canonicalDatingLanguages(
  values: readonly string[],
): DatingLanguage[] {
  const chosen = new Set(values);
  return DATING_LANGUAGES.filter((language) => chosen.has(language));
}

// ---------------------------------------------------------------------------------------------
// INTERESTS (Profile 21, SHOWUP-174) -- "How I live" and "What I'm into".
// ---------------------------------------------------------------------------------------------

/**
 * §1 `activity`. The six "How I live" fields are single answers, each optional on its own.
 * Alcohol and smoking came over from the retired habits screen (8 Oct 2026, registry 1.4.16) and
 * keep its Class 2: substance use is special-category data even though the screen shows the
 * answer publicly.
 */
export const ACTIVITIES = [
  'couch_lover',
  'moderately_active',
  'active',
  'sport_is_my_life',
] as const;
export type Activity = (typeof ACTIVITIES)[number];

/** §1 `diet`. */
export const DIETS = [
  'everything',
  'flexitarian',
  'vegetarian',
  'vegan',
] as const;
export type Diet = (typeof DIETS)[number];

/** §1 `alcohol`. Class 2. */
export const ALCOHOL = ['never', 'rarely', 'socially', 'regularly'] as const;
export type Alcohol = (typeof ALCOHOL)[number];

/** §1 `smoking`. Class 2. */
export const SMOKING = ['no', 'sometimes', 'yes'] as const;
export type Smoking = (typeof SMOKING)[number];

/** §1 `rhythm`. */
export const RHYTHMS = ['early_bird', 'night_owl'] as const;
export type Rhythm = (typeof RHYTHMS)[number];

/** §1 `social`, the "Social battery" group. */
export const SOCIAL_BATTERIES = [
  'cozy_at_home',
  'balanced',
  'always_out',
] as const;
export type SocialBattery = (typeof SOCIAL_BATTERIES)[number];

/**
 * §3 `tag_id`, by §3 `section`, in the registry's order. The section is presentation (the screen's
 * groups, and `section_id` on `show_more_expanded`); only the tag is stored.
 */
export const INTEREST_SECTIONS = {
  move: [
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
  ],
  food: [
    'cooking',
    'baking',
    'trying_new_restaurants',
    'brunch',
    'street_food_markets',
    'wine',
    'craft_beer',
    'coffee_spots',
    'cocktail_bars',
  ],
  culture: [
    'live_music',
    'museums_exhibitions',
    'theatre',
    'cinema',
    'reading',
    'comedy',
    'festivals',
    'making_art_music',
  ],
  home: [
    'tv_series',
    'movie_nights',
    'gaming',
    'board_games',
    'podcasts',
    'plants_gardening',
  ],
  out: [
    'bars',
    'clubbing',
    'dinner_parties',
    'quiz_nights',
    'karaoke',
    'concerts_with_friends',
    'volunteering',
  ],
  travel: [
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
} as const;

/** Every §3 tag_id, flat, in list order: sections in order, tags in order within each. */
export const INTEREST_TAGS = Object.values(INTEREST_SECTIONS).flat();
export type InterestTag = (typeof INTEREST_TAGS)[number];

/**
 * "Capped at 10" (Profile 21). Unlike the language cap above, this one IS the rule: an eleventh
 * pick is refused on the screen, by validation and by a CHECK, so no path can store more.
 */
export const MAX_INTERESTS = 10;

/** A submitted interest list, de-duplicated and put into list order, as `canonicalDatingLanguages`. */
export function canonicalInterests(values: readonly string[]): InterestTag[] {
  const chosen = new Set(values);
  return INTEREST_TAGS.filter((tag) => chosen.has(tag));
}

// ---------------------------------------------------------------------------------------------
// LIFE NOW and LIFE AHEAD (Profiles 22 and 23, SHOWUP-175 / 176) -- matching only, never shown.
// ---------------------------------------------------------------------------------------------

/**
 * One §4 question: its values in list order, and the values that cannot be combined with anything
 * else in the same answer (§4 `exclusive`).
 *
 * MATCHING-ONLY DATA. None of these sets is on any response but the owner's own (`OwnProfileDto`):
 * "the public profile API never returns these fields" (Profiles 22, 23).
 */
export interface LifeQuestion {
  readonly values: readonly string[];
  readonly exclusive: readonly string[];
}

/** §4 `partnership`. Class 2. */
export const LIFE_PARTNERSHIP = [
  'single',
  'open_relationship',
  'ethically_non_monogamous',
  'divorced',
] as const;
export type LifePartnership = (typeof LIFE_PARTNERSHIP)[number];

/** §4 `family`. `no_children` is exclusive. */
export const LIFE_FAMILY = [
  'no_children',
  'has_children',
  'single_parent',
  'co_parenting',
  'empty_nester',
] as const;
export type LifeFamily = (typeof LIFE_FAMILY)[number];

/** §4 `living`. */
export const LIFE_LIVING = [
  'living_alone',
  'living_with_roommates',
  'living_with_family',
  'living_with_children',
] as const;
export type LifeLiving = (typeof LIFE_LIVING)[number];

/** §4 `family_ahead`. Every value is exclusive, so the answer holds one value at most. */
export const FUTURE_FAMILY = [
  'want_kids',
  'dont_want_kids',
  'find_out_together',
] as const;
export type FutureFamily = (typeof FUTURE_FAMILY)[number];

/** §4 `partnership_ahead`. Class 2. `find_out_together` is exclusive. */
export const FUTURE_PARTNERSHIP = [
  'monogamous_relationship',
  'open_relationship',
  'ethically_non_monogamous',
  'find_out_together',
] as const;
export type FuturePartnership = (typeof FUTURE_PARTNERSHIP)[number];

/** §4 `living_ahead`. `find_out_together` is exclusive. */
export const FUTURE_LIVING = [
  'stay_local',
  'expat_life',
  'move_abroad',
  'find_out_together',
] as const;
export type FutureLiving = (typeof FUTURE_LIVING)[number];

/** The six questions by §4 `question_id`. */
export const LIFE_QUESTIONS = {
  partnership: { values: LIFE_PARTNERSHIP, exclusive: [] },
  family: { values: LIFE_FAMILY, exclusive: ['no_children'] },
  living: { values: LIFE_LIVING, exclusive: [] },
  family_ahead: { values: FUTURE_FAMILY, exclusive: FUTURE_FAMILY },
  partnership_ahead: {
    values: FUTURE_PARTNERSHIP,
    exclusive: ['find_out_together'],
  },
  living_ahead: { values: FUTURE_LIVING, exclusive: ['find_out_together'] },
} as const satisfies Record<string, LifeQuestion>;
export type LifeQuestionId = keyof typeof LIFE_QUESTIONS;

/**
 * Upper bound on a submitted life answer, as a payload guard: the largest question has five values
 * and duplicates are collapsed, so this only stops an absurd payload.
 */
export const MAX_SUBMITTED_LIFE_VALUES = 16;

/**
 * A submitted answer to one §4 question, de-duplicated and put into list order ("persist each
 * answered question as its §4 values in list order, not tap order", Profile 22).
 *
 * EMPTY IS A REAL ANSWER HERE, unlike dating languages: Continue saves "an unanswered question as
 * empty", so `[]` means the person walked past it, and NULL means they never reached the screen.
 */
export function canonicalLifeAnswer(
  question: LifeQuestion,
  values: readonly string[],
): string[] {
  const chosen = new Set(values);
  return question.values.filter((value) => chosen.has(value));
}

/**
 * Whether an answer combines an exclusive value with anything else: "I want kids" together with
 * "I don't want kids", or "No children" together with "Single parent".
 *
 * REFUSED, NOT REPAIRED. The screens' `toggleLifePick` cannot produce such a set, so one that
 * arrives came from a broken or hostile client, and there is no right way to guess which half the
 * person meant. Measured on the canonical set, so a repeated value does not count twice.
 */
export function isContradictoryLifeAnswer(
  question: LifeQuestion,
  values: readonly string[],
): boolean {
  const canonical = canonicalLifeAnswer(question, values);
  return (
    canonical.length > 1 &&
    canonical.some((value) => question.exclusive.includes(value))
  );
}
