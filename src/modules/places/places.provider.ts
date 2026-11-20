import type { LatLng } from '../location/location.geo';
import type { PlaceType } from '../dates/util/change-rules';

export type { PlaceType };

/** A café, bar or restaurant a date can move to (Date 05, R1). */
export interface Place {
  placeId: string;
  name: string;
  type: PlaceType;
  address: string;
  location: LatLng;
}

/**
 * Where meeting places come from. Google Places in production, fixtures in development, so the
 * rest of the server (and the clients) never know which one answered.
 */
export interface PlacesProvider {
  /** 'google' only when Google's terms ask for its attribution next to the results. */
  readonly attribution: 'google' | null;
  /** Cafés, bars and restaurants close to a point, nearest first. */
  nearby(center: LatLng): Promise<Place[]>;
  /** The same three types matching a typed query, biased towards a point. */
  search(center: LatLng, q: string): Promise<Place[]>;
  /** One place by id, or null when it is unknown or not one of the three types. */
  details(placeId: string): Promise<Place | null>;
}

/** Injection token for the active provider. */
export const PLACES_PROVIDER = Symbol('PLACES_PROVIDER');

/**
 * The provider cannot answer: no key in production, a network failure, a timeout or an HTTP error.
 * The message never carries the key, a query or a place; the route turns it into a 503.
 */
export class PlacesUnavailableError extends Error {
  constructor(message = 'Places unavailable') {
    super(message);
    this.name = 'PlacesUnavailableError';
  }
}
