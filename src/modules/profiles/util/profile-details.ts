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
