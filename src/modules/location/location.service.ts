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
}
