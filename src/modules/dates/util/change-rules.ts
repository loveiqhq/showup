/** Date 05 (SHOWUP-196): the pure rules for changing a date's meet time or place. */

const MINUTE_MS = 60_000;

/** A change can be suggested, and agreed, until this long before the meet time (R7). */
export const CHANGE_CUTOFF_MINUTES = 30;

/** The meet time (or place) in force when the suggestion is made: the last agreed one, else the date's own. */
export function changeDeadline(bindingAt: Date): Date {
  return new Date(bindingAt.getTime() - CHANGE_CUTOFF_MINUTES * MINUTE_MS);
}

/** True from the deadline on: `meet - 0:30` itself is already too late. */
export function cutoffPassed(bindingAt: Date, now: Date): boolean {
  return now.getTime() >= changeDeadline(bindingAt).getTime();
}

/** The only kinds of place a date can move to (R4). */
export const PLACE_TYPES = ['cafe', 'bar', 'restaurant'] as const;
export type PlaceType = (typeof PLACE_TYPES)[number];

/**
 * Google's type names that mean one of ours. Google has many finer ones (`coffee_shop`, `pub`,
 * `italian_restaurant`...), so the broad three and the common narrow ones are listed; anything else
 * (a street address, a park, a shop) is not a place to meet and maps to null.
 */
const TYPE_ALIASES: Record<string, PlaceType> = {
  cafe: 'cafe',
  coffee_shop: 'cafe',
  bar: 'bar',
  pub: 'bar',
  wine_bar: 'bar',
  cocktail_bar: 'bar',
  restaurant: 'restaurant',
};

/**
 * The type of a place from Google's `primaryType` and `types` (R4): the primary type wins, then the
 * list in its own order. Null when none of them is a café, bar or restaurant.
 */
export function mapPlaceType(
  primaryType: string | null | undefined,
  types: readonly string[] | null | undefined,
): PlaceType | null {
  for (const candidate of [primaryType, ...(types ?? [])]) {
    if (!candidate) continue;
    const mapped = TYPE_ALIASES[candidate];
    if (mapped) return mapped;
  }
  return null;
}
