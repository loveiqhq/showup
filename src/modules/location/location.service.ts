import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';

import { haversineMeters, LatLng } from './location.geo';

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
   * The venue that is fairest to BOTH people, or null if there are no venues.
   *
   * "Fairest" means both people have the same distance to travel: if they are 4 km apart, the aim is
   * roughly 2 km each rather than 0 km and 4 km. Only approximate, because venues are a fixed
   * curated list — the app picks the closest thing to even from what actually exists, which is why
   * this returns both distances so a caller can see how even it turned out.
   *
   * Distance, deliberately, and not travel time. Travel time can only be estimated by assuming how
   * someone gets there — and we do not know that. Guessing public transport for a person who walks,
   * cycles or drives would quietly make the split unfair again, while distance is the same fact for
   * everyone regardless of how they choose to travel.
   *
   * Also deliberately not "the venue nearest the midpoint". That is a different question: a venue can
   * be near the midpoint on the map yet still be much closer to one of the two people. Comparing the
   * two distances directly targets the imbalance itself.
   *
   * The tie-break matters: among equally even options the closer pair wins, so "both travel 5 km" can
   * never beat "both travel 1 km". Equal-but-far is not fairer, only worse for both people.
   *
   * Returns derived distances only, never coordinates, matching `nearestVenue`.
   *
   * Trade-off: ordering on the difference between two distances cannot use the spatial index, so this
   * scans the venue table. Fine for a small curated list; prefilter by a bounding box around the two
   * people if it ever grows to thousands.
   */
  async fairestVenue(
    a: LatLng,
    b: LatLng,
  ): Promise<{
    id: string;
    distanceMetersA: number;
    distanceMetersB: number;
  } | null> {
    const rows = await this.dataSource.query<
      Array<{ id: string; a_meters: number; b_meters: number }>
    >(
      // Distances are computed once in the subquery so the ordering can refer to them by name.
      // ABS(difference) first = the most even split; total second = the tie-break described above.
      `SELECT id, a_meters, b_meters
         FROM (
           SELECT v.id AS id,
                  ST_Distance(v.location, ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography) AS a_meters,
                  ST_Distance(v.location, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography) AS b_meters
             FROM venues v
         ) d
        ORDER BY ABS(a_meters - b_meters) ASC, (a_meters + b_meters) ASC
        LIMIT 1`,
      [a.lng, a.lat, b.lng, b.lat],
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
