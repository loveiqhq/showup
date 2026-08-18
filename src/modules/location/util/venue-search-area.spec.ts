import {
  VENUE_SEARCH_CEILING_METERS,
  VENUE_SEARCH_FLOOR_METERS,
  VENUE_SEARCH_MARGIN_METERS,
  venueSearchArea,
} from './venue-search-area';

// Two points on the same latitude are the easiest to reason about: the midpoint is the average.
const west = { lat: 52.52, lng: 13.38 };
const east = { lat: 52.52, lng: 13.42 }; // ~2.7 km away

describe('venueSearchArea', () => {
  it('centres the search between the two people', () => {
    const { mid } = venueSearchArea(west, east);
    expect(mid.lat).toBeCloseTo(52.52, 5);
    expect(mid.lng).toBeCloseTo(13.4, 5);
  });

  it('is symmetric — the order of the two people does not matter', () => {
    expect(venueSearchArea(west, east)).toEqual(venueSearchArea(east, west));
  });

  it('reaches past each person, so venues near either of them are still options', () => {
    const { radiusMeters, apartMeters } = venueSearchArea(west, east);
    // Each person sits half the separation from the midpoint; the radius must clear that, or the
    // only candidates would be ones in the middle.
    expect(radiusMeters).toBeGreaterThan(apartMeters / 2);
  });

  it('widens as the two people get further apart', () => {
    const near = venueSearchArea(west, { lat: 52.52, lng: 13.39 });
    const far = venueSearchArea(west, { lat: 52.52, lng: 13.6 });
    expect(far.radiusMeters).toBeGreaterThan(near.radiusMeters);
  });

  it('never searches a tiny area, even for two people standing together', () => {
    const { radiusMeters } = venueSearchArea(west, west);
    expect(radiusMeters).toBe(VENUE_SEARCH_FLOOR_METERS);
  });

  it('never searches an absurd area, however far apart they are', () => {
    // Berlin to Cologne — nobody is travelling that for a date, so the search stays bounded.
    const { radiusMeters } = venueSearchArea(west, {
      lat: 50.9413,
      lng: 6.9583,
    });
    expect(radiusMeters).toBe(VENUE_SEARCH_CEILING_METERS);
  });

  it('adds the margin to half the separation between the floor and the ceiling', () => {
    const { radiusMeters, apartMeters } = venueSearchArea(west, east);
    expect(radiusMeters).toBe(apartMeters / 2 + VENUE_SEARCH_MARGIN_METERS);
  });

  it('reports how far apart the two people are', () => {
    const { apartMeters } = venueSearchArea(west, east);
    expect(apartMeters).toBeGreaterThan(2500);
    expect(apartMeters).toBeLessThan(2900);
  });
});
