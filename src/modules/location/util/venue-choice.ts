/**
 * Epic 7 — choosing WHICH venue to propose for a date, once travel times are known.
 *
 * The rule: the venue where both people travel for as close to the same time as possible. Nobody
 * should be the one crossing town while the other walks around the corner — the venue is proposed by
 * the app, not chosen by either person, so the app has to be visibly even-handed about it.
 *
 * A tie is broken by the shorter combined journey, which matters more than it looks: without it, two
 * equally-balanced options would be indistinguishable, and "both travel 30 minutes" would be treated
 * as just as good as "both travel 10 minutes". Equal-but-further is not fairer, it is only worse for
 * both people.
 *
 * Kept as a pure function, separate from the database and from the travel-time source, so the
 * fairness rule itself can be tested directly against plain numbers.
 */

export interface VenueTravel {
  id: string;
  minutesA: number;
  minutesB: number;
}

/** The fairest of the candidates, or null if there are none. */
export function pickBalancedVenue(
  candidates: VenueTravel[],
): VenueTravel | null {
  if (candidates.length === 0) return null;

  const gap = (c: VenueTravel) => Math.abs(c.minutesA - c.minutesB);
  const total = (c: VenueTravel) => c.minutesA + c.minutesB;

  return candidates.reduce((best, c) => {
    if (gap(c) !== gap(best)) return gap(c) < gap(best) ? c : best;
    return total(c) < total(best) ? c : best;
  });
}
