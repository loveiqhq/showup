import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';

import { haversineMeters, LatLng } from './location.geo';
import { venueSearchArea } from './util/venue-search-area';

/**
 * Location & proximity logic (Epic 5 foundation).
 *
 * PRIVACY: ShowUp stores point locations only for active check-in / proximity logic. There is no
 * continuous live tracking, and raw coordinates are never exposed to other users or sent to
 * analytics — only derived results (distance buckets, within-radius booleans) leave this layer.
 */
@Injectable()
export class LocationService {
  constructor(@InjectDataSource() private readonly dataSource: DataSource) {}

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
   * The venue to propose for a date between two people, or null if there are no venues.
   *
   * The rule: **of the venues near the two of them, choose the one where whoever has furthest to go
   * has the shortest journey.** Ties go to the more even split.
   *
   * That single rule delivers both things we want. It naturally lands in the middle when something is
   * there — sending one person 4 km while the other walks 0 km is exactly the case it rejects, since
   * the worst journey is 4 km. But it will not send two people 10 km each just to be perfectly
   * symmetrical when there is a spot 1 km from both, because 10 km is a worse "worst journey" than
   * 1 km. Being even is the point; being even *and far* is just worse for everybody.
   *
   * (An earlier version ranked purely on evenness and had exactly that bug: a perfectly equidistant
   * venue 10 km away beat one 1.0 km / 1.2 km away, because 0 m of imbalance sorted ahead of 200 m.)
   *
   * Distance, deliberately, and not travel time. Travel time can only be estimated by assuming how
   * someone gets there — and we do not know that. Guessing public transport for a person who walks,
   * cycles or drives would quietly make the split unfair again, while distance is the same fact for
   * everyone regardless of how they choose to travel.
   *
   * Candidates are limited to a circle between the two people (see `venueSearchArea`), which is what
   * lets this use the GiST index on `venues.location` instead of measuring every venue in the table.
   * If nothing at all falls inside — two people unusually far apart, or a thin venue list — it
   * retries without the limit rather than proposing no venue, because correctness matters more than
   * speed in a case this rare.
   *
   * Returns derived distances only, never coordinates, matching `nearestVenue`.
   */
  async fairestVenue(
    a: LatLng,
    b: LatLng,
  ): Promise<{
    id: string;
    distanceMetersA: number;
    distanceMetersB: number;
  } | null> {
    const { mid, radiusMeters } = venueSearchArea(a, b);

    // The midpoint and radius are computed in TypeScript and passed as plain values, so ST_DWithin
    // gets a constant to work with — that is the form the spatial index can be used for.
    const withinArea = await this.rankedVenues(a, b, mid, radiusMeters);
    if (withinArea) return withinArea;
    return this.rankedVenues(a, b, mid, null);
  }

  /**
   * Shared body of `fairestVenue`: the ranking, optionally narrowed to a circle. `radiusMeters` of
   * null means "consider every venue", used only as the fallback when the circle came up empty.
   */
  private async rankedVenues(
    a: LatLng,
    b: LatLng,
    mid: LatLng,
    radiusMeters: number | null,
  ): Promise<{
    id: string;
    distanceMetersA: number;
    distanceMetersB: number;
  } | null> {
    const rows = await this.dataSource.query<
      Array<{ id: string; a_meters: number; b_meters: number }>
    >(
      // Distances are computed once in the subquery so the ordering can refer to them by name.
      // GREATEST = the worst of the two journeys, which is what gets minimised; ABS(difference)
      // breaks ties towards the more even option.
      `SELECT id, a_meters, b_meters
         FROM (
           SELECT v.id AS id,
                  ST_Distance(v.location, ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography) AS a_meters,
                  ST_Distance(v.location, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography) AS b_meters
             FROM venues v
            WHERE $7::float8 IS NULL
               OR ST_DWithin(
                    v.location,
                    ST_SetSRID(ST_MakePoint($5, $6), 4326)::geography,
                    $7::float8
                  )
         ) d
        ORDER BY GREATEST(a_meters, b_meters) ASC, ABS(a_meters - b_meters) ASC
        LIMIT 1`,
      [a.lng, a.lat, b.lng, b.lat, mid.lng, mid.lat, radiusMeters],
    );
    if (rows.length === 0) return null;
    return {
      id: rows[0].id,
      distanceMetersA: Number(rows[0].a_meters),
      distanceMetersB: Number(rows[0].b_meters),
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
