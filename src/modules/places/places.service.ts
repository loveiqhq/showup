import {
  ConflictException,
  ForbiddenException,
  Inject,
  Injectable,
  NotFoundException,
  ServiceUnavailableException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';

import { DateEntity } from '../dates/entities/date.entity';
import { PLACE_TYPES } from '../dates/util/change-rules';
import {
  isBindingVenue,
  type VenueIdentity,
} from '../dates/util/change-suggestions';
import { haversineMeters } from '../location/location.geo';
import {
  PLACES_PROVIDER,
  Place,
  type PlacesProvider,
  PlacesUnavailableError,
} from './places.provider';

export interface PlaceOption {
  placeId: string;
  name: string;
  type: Place['type'];
  address: string;
  /** Whole metres from the binding meet place. Never in analytics. */
  distanceMeters: number;
}

export interface PlaceOptions {
  attribution: 'google' | null;
  places: PlaceOption[];
}

/** The meeting places a person can suggest for a date (Date 05, frames D and E). */
@Injectable()
export class PlacesService {
  constructor(
    @InjectDataSource() private readonly db: DataSource,
    @Inject(PLACES_PROVIDER) private readonly provider: PlacesProvider,
  ) {}

  /**
   * Cafes, bars and restaurants near the date's CURRENT meet place (`dates.venue_id`), nearest
   * first. An empty `q` lists what is nearby; a typed one searches. 404 unknown date, 403 not yours,
   * 409 when the date has no venue with a location to measure from, 503 when the provider fails.
   * Nothing here is logged: places and distances are personal data.
   */
  async listFor(
    dateId: string,
    userId: string,
    q?: string,
  ): Promise<PlaceOptions> {
    const date = await this.db
      .getRepository(DateEntity)
      .findOne({ where: { id: dateId } });
    if (!date) throw new NotFoundException('Date not found');
    if (date.userAId !== userId && date.userBId !== userId) {
      throw new ForbiddenException('You are not part of this date');
    }
    const binding = await this.bindingVenue(date.venueId);
    if (!binding) {
      throw new ConflictException(
        'This date has no meeting place to search from',
      );
    }

    const origin = binding.location;
    const query = (q ?? '').trim();
    let found: Place[];
    try {
      found = query
        ? await this.provider.search(origin, query)
        : await this.provider.nearby(origin);
    } catch (err) {
      if (err instanceof PlacesUnavailableError) {
        throw new ServiceUnavailableException('Places are unavailable');
      }
      throw err;
    }

    const places = found
      .filter((p) => (PLACE_TYPES as readonly string[]).includes(p.type))
      // The date's current place is not a place to move to; a suggestion of it is refused too.
      .filter((p) => !isBindingVenue(binding, p))
      .map((p) => ({
        placeId: p.placeId,
        name: p.name,
        type: p.type,
        address: p.address,
        distanceMeters: Math.round(haversineMeters(origin, p.location)),
      }))
      .sort((a, b) => a.distanceMeters - b.distanceMeters);
    return { attribution: this.provider.attribution, places };
  }

  private async bindingVenue(
    venueId: string | null,
  ): Promise<VenueIdentity | null> {
    if (!venueId) return null;
    const rows = await this.db.query<
      Array<{
        name: string;
        place_id: string | null;
        lat: number;
        lng: number;
      }>
    >(
      `SELECT "name", "place_id", ST_Y("location"::geometry) AS "lat", ST_X("location"::geometry) AS "lng"
         FROM "venues" WHERE "id" = $1 AND "location" IS NOT NULL`,
      [venueId],
    );
    return rows[0]
      ? {
          name: rows[0].name,
          placeId: rows[0].place_id,
          location: { lat: Number(rows[0].lat), lng: Number(rows[0].lng) },
        }
      : null;
  }
}
