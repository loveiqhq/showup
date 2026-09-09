/**
 * Per-field profile visibility — which fields a user has chosen not to display.
 *
 * WHAT THIS IS NOT
 *
 * This is emphatically NOT `Profile.isVisible`. That flag decides whether the profile appears in
 * discovery at all; this set decides which *values on a shown profile* are rendered. Hiding a field
 * must never remove anyone from discovery or from matching — the age is still passed to the
 * matching algorithm when it is hidden, because the choice hides a value, it never excludes the
 * user. Wiring a field-visibility control to `isVisible` would silently delete the user from
 * discovery, which is the one outcome the product decision forbids.
 *
 * WHY A SET AND NOT A BOOLEAN PER FIELD
 *
 * The taxonomy's `field_display_opted_out` covers nine controls — the seven "Share some details"
 * screens, the habits switch, and age on the date-of-birth step — and the analytics side already
 * reads current state off a `hidden_fields` user property. Nine booleans would be nine migrations
 * and nine DTO fields; one set is one of each, and a tenth control costs a line in
 * [HIDEABLE_FIELDS].
 *
 * The values are `field_id`s from the tracking registry's §1 vocabulary, so the same string names
 * the column here, the payload of the analytics event, and the row in the registry.
 */

/**
 * The fields a user may hide, keyed by registry `field_id`.
 *
 * Add to this list to support another control — no migration is required, because the column is a
 * free-form text array and this list is the validation. A value not in here is rejected with 400
 * rather than stored, so a typo cannot become a permanent unreadable row.
 *
 * `age` ships first (SHOWUP-154). The remaining eight are named in the registry but their screens
 * are unbuilt, so they are deliberately absent: accepting a value nothing can yet set would make
 * this list a wish rather than a contract.
 */
export const HIDEABLE_FIELDS = ['age'] as const;

export type HideableField = (typeof HIDEABLE_FIELDS)[number];

export function isHideableField(value: string): value is HideableField {
  return (HIDEABLE_FIELDS as readonly string[]).includes(value);
}

/**
 * Canonicalise a submitted set: de-duplicated and sorted.
 *
 * Sorted so that two clients sending the same choices in a different order produce the same row,
 * which keeps `updated_at` honest and makes the column comparable in a test without sorting at
 * every call site.
 */
export function normaliseHiddenFields(fields: readonly string[]): string[] {
  return [...new Set(fields)].sort();
}

/** The unknown values in a submitted set, for the 400 message. */
export function unknownHiddenFields(fields: readonly string[]): string[] {
  return [...new Set(fields.filter((f) => !isHideableField(f)))].sort();
}
