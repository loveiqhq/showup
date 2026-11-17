import { FixturePlacesProvider } from './fixture-places.provider';
import { GooglePlacesProvider } from './google-places.provider';
import {
  Place,
  PlacesProvider,
  PlacesUnavailableError,
} from './places.provider';

/** Production without a key: say so on every call rather than quietly serving fixtures to real people. */
class UnavailablePlacesProvider implements PlacesProvider {
  readonly attribution = null;

  private fail(): Promise<never> {
    return Promise.reject(
      new PlacesUnavailableError('GOOGLE_PLACES_API_KEY is not set'),
    );
  }
  nearby(): Promise<Place[]> {
    return this.fail();
  }
  search(): Promise<Place[]> {
    return this.fail();
  }
  details(): Promise<Place | null> {
    return this.fail();
  }
}

/**
 * Google when a key is set; otherwise fixtures outside production; in production with no key, a
 * provider that always fails, so the routes answer 503 (frame M).
 */
export function choosePlacesProvider(input: {
  key: string | undefined;
  nodeEnv: string | undefined;
}): PlacesProvider {
  if (input.key) return new GooglePlacesProvider(input.key);
  if (input.nodeEnv === 'production') return new UnavailablePlacesProvider();
  return new FixturePlacesProvider();
}
