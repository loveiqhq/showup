import type { LatLng } from '../location/location.geo';
import { mapPlaceType } from '../dates/util/change-rules';
import {
  Place,
  PlacesProvider,
  PlacesUnavailableError,
} from './places.provider';

const BASE = 'https://places.googleapis.com/v1';
const FIELDS = [
  'id',
  'displayName',
  'shortFormattedAddress',
  'location',
  'primaryType',
  'types',
];
const LIST_MASK = FIELDS.map((f) => `places.${f}`).join(',');
const DETAILS_MASK = FIELDS.join(',');
const NEARBY_RADIUS_M = 2000;
const SEARCH_BIAS_RADIUS_M = 5000;
const MAX_RESULTS = 20;
const TIMEOUT_MS = 5000;

interface GooglePlace {
  id?: string;
  displayName?: { text?: string };
  shortFormattedAddress?: string;
  location?: { latitude?: number; longitude?: number };
  primaryType?: string;
  types?: string[];
}

type FetchFn = (url: string, init: RequestInit) => Promise<Response>;

const circle = (c: LatLng, radius: number) => ({
  center: { latitude: c.lat, longitude: c.lng },
  radius,
});

/**
 * Google Places API (New) over plain `fetch` (R1). The key rides in a header and never reaches a
 * log, an error message or a response. Every failure becomes `PlacesUnavailableError`, so the route
 * answers 503 and nothing from Google (or about the query) leaks into the error.
 */
export class GooglePlacesProvider implements PlacesProvider {
  readonly attribution = 'google' as const;

  constructor(
    private readonly apiKey: string,
    private readonly fetchFn: FetchFn = fetch,
  ) {}

  async nearby(center: LatLng): Promise<Place[]> {
    const body = {
      includedTypes: ['cafe', 'bar', 'restaurant'],
      rankPreference: 'DISTANCE',
      maxResultCount: MAX_RESULTS,
      locationRestriction: { circle: circle(center, NEARBY_RADIUS_M) },
    };
    const json = await this.call<{ places?: GooglePlace[] }>(
      `${BASE}/places:searchNearby`,
      'POST',
      LIST_MASK,
      body,
    );
    return this.toPlaces(json?.places);
  }

  async search(center: LatLng, q: string): Promise<Place[]> {
    const body = {
      textQuery: q,
      pageSize: MAX_RESULTS,
      locationBias: { circle: circle(center, SEARCH_BIAS_RADIUS_M) },
    };
    const json = await this.call<{ places?: GooglePlace[] }>(
      `${BASE}/places:searchText`,
      'POST',
      LIST_MASK,
      body,
    );
    // A text search returns anything, a street address included; only the three types may pass.
    return this.toPlaces(json?.places);
  }

  async details(placeId: string): Promise<Place | null> {
    const json = await this.call<GooglePlace>(
      `${BASE}/places/${encodeURIComponent(placeId)}`,
      'GET',
      DETAILS_MASK,
      undefined,
      true,
    );
    return json ? this.toPlace(json) : null;
  }

  private toPlaces(raw: GooglePlace[] | undefined): Place[] {
    return (raw ?? [])
      .map((p) => this.toPlace(p))
      .filter((p): p is Place => p !== null);
  }

  private toPlace(p: GooglePlace): Place | null {
    const type = mapPlaceType(p.primaryType, p.types);
    const { latitude, longitude } = p.location ?? {};
    if (
      !type ||
      !p.id ||
      !p.displayName?.text ||
      latitude === undefined ||
      longitude === undefined
    ) {
      return null;
    }
    return {
      placeId: p.id,
      name: p.displayName.text,
      type,
      address: p.shortFormattedAddress ?? '',
      location: { lat: latitude, lng: longitude },
    };
  }

  /**
   * One request. A 404 is null only when `notFoundIsNull` (details of an unknown id); for the list
   * calls it is a misconfiguration and must surface as unavailable, not as "no places".
   */
  private async call<T>(
    url: string,
    method: 'GET' | 'POST',
    mask: string,
    body?: unknown,
    notFoundIsNull = false,
  ): Promise<T | null> {
    let res: Response;
    try {
      res = await this.fetchFn(url, {
        method,
        headers: {
          'Content-Type': 'application/json',
          'X-Goog-Api-Key': this.apiKey,
          'X-Goog-FieldMask': mask,
        },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
    } catch {
      // The cause is dropped on purpose: it can echo the URL or the key.
      throw new PlacesUnavailableError('Places request failed');
    }
    if (res.status === 404 && notFoundIsNull) return null;
    if (!res.ok) {
      throw new PlacesUnavailableError(`Places answered ${res.status}`);
    }
    try {
      return (await res.json()) as T;
    } catch {
      throw new PlacesUnavailableError('Places answered unreadably');
    }
  }
}
