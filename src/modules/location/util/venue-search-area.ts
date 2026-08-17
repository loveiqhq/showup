import { haversineMeters, LatLng } from '../location.geo';

/**
 * Where to look for a venue when proposing a date, given where the two people are.
 *
 * Two jobs, and both matter.
 *
 * **Speed.** Narrowing to a circle lets the query use the spatial index on `venues.location`, which
 * discards most of the table without examining it. Comparing distances across the whole table cannot
 * use the index, because the answer depends on which pair is being matched, so there is nothing to
 * pre-sort. Irrelevant with a few hundred venues; essential if the list ever grows large.
 *
 * **Sense.** It also stops the app suggesting somewhere neither person wants to go. Without a bound,
 * a venue exactly equidistant from both would beat a much closer one that is only slightly uneven —
 * so two people a kilometre apart could be sent ten kilometres each purely for the symmetry. Nobody
 * wants a fair journey; they want a short one that is also fair.
 *
 * The radius scales with the separation, so close pairs get close venues and distant pairs get a
 * wider search, with a floor so there is always a real choice and a ceiling so it never goes silly.
 */

/** Extra reach beyond half the separation, so venues just past either person still qualify. */
export const VENUE_SEARCH_MARGIN_METERS = 1_000;

/** Smallest search radius. Two people standing together still need somewhere to choose from. */
export const VENUE_SEARCH_FLOOR_METERS = 2_000;

/** Largest search radius. Beyond this it is not a date suggestion, it is an expedition. */
export const VENUE_SEARCH_CEILING_METERS = 15_000;

export interface VenueSearchArea {
  /** Centre of the search: the point between the two people. */
  mid: LatLng;
  /** How far to search from that centre. */
  radiusMeters: number;
  /** How far apart the two people are — useful for logging and for the caller's own checks. */
  apartMeters: number;
}

/**
 * The circle to search for a venue. Symmetric in its arguments: the same pair always gets the same
 * area, whichever way round they are passed, so the suggestion can never depend on who liked first.
 *
 * The midpoint is a plain average of the coordinates. Over city distances that is accurate to a few
 * metres, which is meaningless against a radius measured in kilometres — precision here would buy
 * nothing.
 */
export function venueSearchArea(a: LatLng, b: LatLng): VenueSearchArea {
  const apartMeters = haversineMeters(a, b);
  const radiusMeters = Math.min(
    Math.max(
      apartMeters / 2 + VENUE_SEARCH_MARGIN_METERS,
      VENUE_SEARCH_FLOOR_METERS,
    ),
    VENUE_SEARCH_CEILING_METERS,
  );
  return {
    mid: { lat: (a.lat + b.lat) / 2, lng: (a.lng + b.lng) / 2 },
    radiusMeters,
    apartMeters,
  };
}
