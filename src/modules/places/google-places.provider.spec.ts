import { GooglePlacesProvider } from './google-places.provider';
import { PlacesUnavailableError } from './places.provider';

const CENTER = { lat: 52.5016, lng: 13.4186 };
const MASK =
  'places.id,places.displayName,places.shortFormattedAddress,places.location,places.primaryType,places.types';

const googlePlace = (over: Record<string, unknown> = {}) => ({
  id: 'ChIJ1',
  displayName: { text: 'Bar Lenz' },
  shortFormattedAddress: 'Lausitzer Pl. 9, Berlin',
  location: { latitude: 52.4994, longitude: 13.4326 },
  primaryType: 'bar',
  types: ['bar', 'point_of_interest'],
  ...over,
});

function mockFetch(
  body: unknown,
  init: { ok?: boolean; status?: number } = {},
) {
  return jest.fn().mockResolvedValue({
    ok: init.ok ?? true,
    status: init.status ?? 200,
    json: () => Promise.resolve(body),
  });
}

describe('GooglePlacesProvider', () => {
  it('is the Google provider, so results carry the attribution', () => {
    expect(new GooglePlacesProvider('KEY', mockFetch({})).attribution).toBe(
      'google',
    );
  });

  it('nearby posts the searchNearby body with the key and the field mask', async () => {
    const fetchFn = mockFetch({ places: [googlePlace()] });
    const places = await new GooglePlacesProvider('KEY', fetchFn).nearby(
      CENTER,
    );

    const [url, init] = fetchFn.mock.calls[0];
    expect(url).toBe('https://places.googleapis.com/v1/places:searchNearby');
    expect(init.method).toBe('POST');
    expect(init.headers['X-Goog-Api-Key']).toBe('KEY');
    expect(init.headers['X-Goog-FieldMask']).toBe(MASK);
    expect(JSON.parse(init.body)).toEqual({
      includedTypes: ['cafe', 'bar', 'restaurant'],
      rankPreference: 'DISTANCE',
      maxResultCount: 20,
      locationRestriction: {
        circle: {
          center: { latitude: CENTER.lat, longitude: CENTER.lng },
          radius: 2000,
        },
      },
    });
    expect(places).toEqual([
      {
        placeId: 'ChIJ1',
        name: 'Bar Lenz',
        type: 'bar',
        address: 'Lausitzer Pl. 9, Berlin',
        location: { lat: 52.4994, lng: 13.4326 },
      },
    ]);
  });

  it('search posts searchText with a 5 km bias and keeps only cafe, bar and restaurant results', async () => {
    const fetchFn = mockFetch({
      places: [
        googlePlace(),
        googlePlace({
          id: 'street',
          primaryType: 'street_address',
          types: ['street_address'],
        }),
        googlePlace({
          id: 'cafe',
          primaryType: undefined,
          types: ['store', 'cafe'],
        }),
      ],
    });
    const places = await new GooglePlacesProvider('KEY', fetchFn).search(
      CENTER,
      'lenz',
    );

    const [url, init] = fetchFn.mock.calls[0];
    expect(url).toBe('https://places.googleapis.com/v1/places:searchText');
    expect(JSON.parse(init.body)).toEqual({
      textQuery: 'lenz',
      pageSize: 20,
      locationBias: {
        circle: {
          center: { latitude: CENTER.lat, longitude: CENTER.lng },
          radius: 5000,
        },
      },
    });
    expect(places.map((p) => [p.placeId, p.type])).toEqual([
      ['ChIJ1', 'bar'],
      ['cafe', 'cafe'],
    ]);
  });

  it('details reads places/{id} with the mask without the places. prefix; a non-venue is null', async () => {
    const fetchFn = mockFetch(googlePlace());
    const place = await new GooglePlacesProvider('KEY', fetchFn).details(
      'ChIJ 1',
    );

    const [url, init] = fetchFn.mock.calls[0];
    expect(url).toBe('https://places.googleapis.com/v1/places/ChIJ%201');
    expect(init.method).toBe('GET');
    expect(init.headers['X-Goog-FieldMask']).toBe(
      'id,displayName,shortFormattedAddress,location,primaryType,types',
    );
    expect(place?.placeId).toBe('ChIJ1');

    const street = mockFetch(
      googlePlace({ primaryType: 'route', types: ['route'] }),
    );
    expect(
      await new GooglePlacesProvider('KEY', street).details('x'),
    ).toBeNull();
  });

  it('details answers null for an unknown id (404)', async () => {
    const fetchFn = mockFetch({}, { ok: false, status: 404 });
    expect(
      await new GooglePlacesProvider('KEY', fetchFn).details('gone'),
    ).toBeNull();
  });

  it('an empty answer is an empty list', async () => {
    const fetchFn = mockFetch({});
    expect(
      await new GooglePlacesProvider('KEY', fetchFn).nearby(CENTER),
    ).toEqual([]);
  });

  it('maps an HTTP error, a network failure and a timeout to PlacesUnavailableError without leaking the key', async () => {
    const http = mockFetch({}, { ok: false, status: 403 });
    await expect(
      new GooglePlacesProvider('SECRET', http).nearby(CENTER),
    ).rejects.toBeInstanceOf(PlacesUnavailableError);

    const down = jest
      .fn()
      .mockRejectedValue(new Error('connect ECONNREFUSED SECRET'));
    const err = await new GooglePlacesProvider('SECRET', down)
      .search(CENTER, 'x')
      .catch((e: Error) => e);
    expect(err).toBeInstanceOf(PlacesUnavailableError);
    expect((err as Error).message).not.toContain('SECRET');
  });
});
