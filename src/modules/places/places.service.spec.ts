import {
  ConflictException,
  ForbiddenException,
  NotFoundException,
  ServiceUnavailableException,
} from '@nestjs/common';

import { FixturePlacesProvider } from './fixture-places.provider';
import { GooglePlacesProvider } from './google-places.provider';
import { choosePlacesProvider } from './places.binding';
import {
  Place,
  PlacesProvider,
  PlacesUnavailableError,
} from './places.provider';
import { PlacesService } from './places.service';

const BINDING = { lat: 52.5, lng: 13.42 };
const place = (
  placeId: string,
  lat: number,
  lng: number,
  over: Partial<Place> = {},
): Place => ({
  placeId,
  name: placeId,
  type: 'bar',
  address: 'addr',
  location: { lat, lng },
  ...over,
});

function build(opts: {
  date?: object | null;
  venue?: {
    lat: number;
    lng: number;
    name?: string;
    place_id?: string | null;
  } | null;
  provider: Partial<PlacesProvider>;
}) {
  const date =
    opts.date === undefined
      ? { id: 'd1', userAId: 'u1', userBId: 'u2', venueId: 'v1' }
      : opts.date;
  const db = {
    getRepository: () => ({ findOne: jest.fn().mockResolvedValue(date) }),
    query: jest.fn().mockResolvedValue(
      opts.venue === null
        ? []
        : [
            {
              name: 'Cafe Krone',
              place_id: null,
              ...(opts.venue ?? BINDING),
            },
          ],
    ),
  };
  const provider = { attribution: null, ...opts.provider } as PlacesProvider;
  return { service: new PlacesService(db as never, provider), db };
}

describe('PlacesService.listFor', () => {
  it('sorts by distance from the binding venue, in whole metres', async () => {
    const nearby = jest
      .fn()
      .mockResolvedValue([
        place('far', 52.51, 13.42),
        place('near', 52.5005, 13.42),
      ]);
    const { service } = build({ provider: { nearby } });

    const out = await service.listFor('d1', 'u1');

    expect(nearby).toHaveBeenCalledWith(BINDING);
    expect(out.attribution).toBeNull();
    expect(out.places.map((p) => p.placeId)).toEqual(['near', 'far']);
    expect(out.places[0].distanceMeters).toBe(
      Math.round(out.places[0].distanceMeters),
    );
    expect(out.places[0].distanceMeters).toBeGreaterThan(40);
    expect(out.places[0].distanceMeters).toBeLessThan(70);
    expect(out.places[0]).not.toHaveProperty('location');
  });

  it('trims q: empty is nearby, otherwise a search', async () => {
    const nearby = jest.fn().mockResolvedValue([]);
    const search = jest.fn().mockResolvedValue([place('x', 52.5, 13.42)]);
    const { service } = build({ provider: { nearby, search } });

    await service.listFor('d1', 'u1', '   ');
    expect(nearby).toHaveBeenCalledTimes(1);
    expect(search).not.toHaveBeenCalled();

    await service.listFor('d1', 'u1', '  lenz ');
    expect(search).toHaveBeenCalledWith(BINDING, 'lenz');
  });

  it('only the Google provider sets the attribution', async () => {
    const { service } = build({
      provider: {
        attribution: 'google',
        nearby: jest.fn().mockResolvedValue([]),
      },
    });
    expect((await service.listFor('d1', 'u1')).attribution).toBe('google');
  });

  it('leaves the binding venue out: by place id when it has one', async () => {
    const nearby = jest
      .fn()
      .mockResolvedValue([
        place('g-here', 52.5, 13.42),
        place('g-other', 52.5, 13.42),
      ]);
    const { service } = build({
      venue: { ...BINDING, place_id: 'g-here' },
      provider: { nearby },
    });
    expect(
      (await service.listFor('d1', 'u1')).places.map((p) => p.placeId),
    ).toEqual(['g-other']);
  });

  it('leaves the binding venue out: without an id, by the same name within 50 m', async () => {
    const nearby = jest
      .fn()
      .mockResolvedValue([
        place('same', 52.5001, 13.42, { name: 'Cafe Krone' }),
        place('namesake-far', 52.51, 13.42, { name: 'Cafe Krone' }),
        place('neighbour', 52.5001, 13.42, { name: 'Bar Lenz' }),
      ]);
    const { service } = build({ provider: { nearby } });
    expect(
      (await service.listFor('d1', 'u1')).places.map((p) => p.placeId).sort(),
    ).toEqual(['namesake-far', 'neighbour']);
  });

  it('only keeps the three types', async () => {
    const nearby = jest
      .fn()
      .mockResolvedValue([
        place('ok', 52.5, 13.42),
        place('odd', 52.5, 13.42, { type: 'park' as never }),
      ]);
    const { service } = build({ provider: { nearby } });
    expect(
      (await service.listFor('d1', 'u1')).places.map((p) => p.placeId),
    ).toEqual(['ok']);
  });

  it('404 for an unknown date, 403 for a stranger', async () => {
    const nearby = jest.fn();
    await expect(
      build({ date: null, provider: { nearby } }).service.listFor('d1', 'u1'),
    ).rejects.toBeInstanceOf(NotFoundException);
    await expect(
      build({ provider: { nearby } }).service.listFor('d1', 'stranger'),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(nearby).not.toHaveBeenCalled();
  });

  it('409 when there is no binding venue location to measure from', async () => {
    const nearby = jest.fn();
    await expect(
      build({
        date: { id: 'd1', userAId: 'u1', userBId: 'u2', venueId: null },
        provider: { nearby },
      }).service.listFor('d1', 'u1'),
    ).rejects.toBeInstanceOf(ConflictException);
    await expect(
      build({ venue: null, provider: { nearby } }).service.listFor('d1', 'u1'),
    ).rejects.toBeInstanceOf(ConflictException);
    expect(nearby).not.toHaveBeenCalled();
  });

  it('503 when the provider fails', async () => {
    const nearby = jest
      .fn()
      .mockRejectedValue(new PlacesUnavailableError('down'));
    await expect(
      build({ provider: { nearby } }).service.listFor('d1', 'u1'),
    ).rejects.toBeInstanceOf(ServiceUnavailableException);
  });
});

describe('choosePlacesProvider', () => {
  it('Google when the key is set, in any environment', () => {
    expect(
      choosePlacesProvider({ key: 'k', nodeEnv: 'production' }),
    ).toBeInstanceOf(GooglePlacesProvider);
    expect(choosePlacesProvider({ key: 'k', nodeEnv: 'local' })).toBeInstanceOf(
      GooglePlacesProvider,
    );
  });

  it('fixtures with no key outside production', () => {
    expect(choosePlacesProvider({ key: '', nodeEnv: 'local' })).toBeInstanceOf(
      FixturePlacesProvider,
    );
    expect(
      choosePlacesProvider({ key: undefined, nodeEnv: 'staging' }),
    ).toBeInstanceOf(FixturePlacesProvider);
  });

  it('production with no key is unavailable, never fixtures', async () => {
    const provider = choosePlacesProvider({
      key: undefined,
      nodeEnv: 'production',
    });
    await expect(provider.nearby(BINDING)).rejects.toBeInstanceOf(
      PlacesUnavailableError,
    );
  });
});
