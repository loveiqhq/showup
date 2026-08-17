import { Inject, Injectable, NotFoundException } from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';

import { haversineMeters, LatLng } from './location.geo';
import { TRAVEL_TIME_ESTIMATOR } from './travel-estimator.provider';
// `import type`: it is only a type here, and a type in a decorated constructor signature must be
// imported this way when isolatedModules and emitDecoratorMetadata are both on.
import type { TravelTimeEstimator } from './util/travel';
import { pickBalancedVenue } from './util/venue-choice';

/**
 * How many nearby venues are priced for travel time. A real routing lookup is billed per
 * origin→destination pair, so this is two lookups per candidate per date. Small enough to stay cheap,
 * wide enough that the fair option is not filtered out before it is considered.
 */
const VENUE_SHORTLIST_SIZE = 8;

/**
 * Location & proximity logic (Epic 5 foundation).
 *
 * PRIVACY: ShowUp stores point locations only for active check-in / proximity logic. There is no
 * continuous live tracking, and raw coordinates are never exposed to other users or sent to
 * analytics — only derived results (distance buckets, within-radius booleans) leave this layer.
 */
@Injectable()
export class LocationService {
  constructor(
    @InjectDataSource() private readonly dataSource: DataSource,
    @Inject(TRAVEL_TIME_ESTIMATOR)
    private readonly travel: TravelTimeEstimator,
  ) {}

  /** Quick, DB-free great-circle estimate (meters). */
  estimateDistanceMeters(a: LatLng, b: LatLng): number {
    return haversineMeters(a, b);
  }

  /** Authoritative distance (meters) computed by PostGIS on the WGS84 spheroid. */
  async distanceMeters(a: LatLng, b: LatLng): Promise<number> {
    const rows = await this.dataSource.query<Array<{ meters: number }>>(
      `SELECT ST_Distance(
         ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
         ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography
       ) AS meters`,
      [a.lng, a.lat, b.lng, b.lat],
    );
    return Number(rows[0].meters);
  }

  /** Whether two points are within `radiusMeters` of each other (uses the spatial index). */
  async isWithin(a: LatLng, b: LatLng, radiusMeters: number): Promise<boolean> {
    const rows = await this.dataSource.query<Array<{ within: boolean }>>(
      `SELECT ST_DWithin(
         ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
         ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography,
         $5
       ) AS within`,
      [a.lng, a.lat, b.lng, b.lat, radiusMeters],
    );
    return Boolean(rows[0].within);
  }

  /**
   * SHOWUP-43 — distance (metres) from a point to a stored venue. Reusable by meeting-spot
   * suggestions and, later, arrival confirmation (SHOWUP-44). Throws if the venue does not exist.
   */
  async distanceToVenueMeters(point: LatLng, venueId: string): Promise<number> {
    const rows = await this.dataSource.query<Array<{ meters: number }>>(
      `SELECT ST_Distance(
         v.location,
         ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
       ) AS meters
       FROM venues v WHERE v.id = $3`,
      [point.lng, point.lat, venueId],
    );
    if (rows.length === 0) throw new NotFoundException('Venue not found');
    return Number(rows[0].meters);
  }

  /**
   * SHOWUP-43 — whether a point is within `radiusMeters` of a venue. The threshold is a caller
   * argument (configurable/tunable, not hard-coded). Throws if the venue does not exist.
   */
  async isWithinVenue(
    point: LatLng,
    venueId: string,
    radiusMeters: number,
  ): Promise<boolean> {
    const rows = await this.dataSource.query<Array<{ within: boolean }>>(
      `SELECT ST_DWithin(
         v.location,
         ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
         $4
       ) AS within
       FROM venues v WHERE v.id = $3`,
      [point.lng, point.lat, venueId, radiusMeters],
    );
    if (rows.length === 0) throw new NotFoundException('Venue not found');
    return Boolean(rows[0].within);
  }

  /**
   * The venue that is fairest to BOTH people, or null if there are no venues.
   *
   * "Fairest" means both people travel for as close to the same TIME as possible — the venue sits
   * between them in travel terms, not in map terms. Time rather than distance because a city is not
   * flat: two kilometres along a direct train line is a shorter trip than one kilometre across a
   * river with no bridge, and the person doing the crossing is the one who feels it.
   *
   * Deliberately not the nearest venue to the midpoint. A midpoint can still sit much closer to one
   * person, and it optimises map geometry rather than the thing anyone actually experiences.
   *
   * Two steps, for cost reasons. A shortlist of the closest candidates is taken in SQL (free), and
   * only those are priced for travel time, because a real routing lookup is billed per
   * origin→destination pair. Straight-line distance is a good enough filter to find candidates and a
   * poor one to choose between them, which is exactly how it is used here.
   *
   * Returns derived distances and times only, never coordinates, matching `nearestVenue`.
   */
  async fairestVenue(
    a: LatLng,
    b: LatLng,
  ): Promise<{
    id: string;
    minutesA: number;
    minutesB: number;
    distanceMetersA: number;
    distanceMetersB: number;
  } | null> {
    const rows = await this.dataSource.query<
      Array<{
        id: string;
        lat: string | number;
        lng: string | number;
        a_meters: number;
        b_meters: number;
      }>
    >(
      // Shortlist by combined straight-line distance: cheap, and enough to discard anything far from
      // both people. The real choice between these is made on travel time below.
      `SELECT id, lat, lng, a_meters, b_meters
         FROM (
           SELECT v.id AS id,
                  ST_Y(v.location::geometry) AS lat,
                  ST_X(v.location::geometry) AS lng,
                  ST_Distance(v.location, ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography) AS a_meters,
                  ST_Distance(v.location, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography) AS b_meters
             FROM venues v
         ) d
        ORDER BY (a_meters + b_meters) ASC
        LIMIT ${VENUE_SHORTLIST_SIZE}`,
      [a.lng, a.lat, b.lng, b.lat],
    );
    if (rows.length === 0) return null;

    const priced = await Promise.all(
      rows.map(async (r) => {
        const at = { lat: Number(r.lat), lng: Number(r.lng) };
        const [minutesA, minutesB] = await Promise.all([
          this.travel.estimateMinutes(a, at),
          this.travel.estimateMinutes(b, at),
        ]);
        return { id: r.id, minutesA, minutesB, row: r };
      }),
    );

    const chosen = pickBalancedVenue(priced);
    if (!chosen) return null;
    const winner = priced.find((p) => p.id === chosen.id) ?? priced[0];
    return {
      id: winner.id,
      minutesA: winner.minutesA,
      minutesB: winner.minutesB,
      distanceMetersA: Number(winner.row.a_meters),
      distanceMetersB: Number(winner.row.b_meters),
    };
  }

  /**
   * The venue nearest to a point, or null if there are no venues. Kept for the one-sided fallback in
   * date creation, when only one of the two people has a usable location — prefer `fairestVenue`
   * whenever both are known. Returns a derived distance, never coordinates.
   */
  async nearestVenue(
    point: LatLng,
  ): Promise<{ id: string; distanceMeters: number } | null> {
    const rows = await this.dataSource.query<
      Array<{ id: string; meters: number }>
    >(
      `SELECT v.id AS id,
              ST_Distance(v.location, ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography) AS meters
         FROM venues v
        ORDER BY ST_Distance(v.location, ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography) ASC
        LIMIT 1`,
      [point.lng, point.lat],
    );
    if (rows.length === 0) return null;
    return { id: rows[0].id, distanceMeters: Number(rows[0].meters) };
  }
}
