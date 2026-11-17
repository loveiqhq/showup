import { Injectable } from '@nestjs/common';

import type { LatLng } from '../location/location.geo';
import { Place, PlacesProvider } from './places.provider';

/**
 * The reference kit's five Kreuzberg venues, for development and tests when no Google key is set
 * (R1). Coordinates are real street positions near Cafe Krone; ids are `fixture:` so they can never
 * collide with a Google place id.
 */
const FIXTURES: Place[] = [
  {
    placeId: 'fixture:bar-lenz',
    name: 'Bar Lenz',
    type: 'bar',
    address: 'Lausitzer Platz 9, Kreuzberg',
    location: { lat: 52.4994, lng: 13.4326 },
  },
  {
    placeId: 'fixture:osteria-nove',
    name: 'Osteria Nove',
    type: 'restaurant',
    address: 'Wiener Str. 14, Kreuzberg',
    location: { lat: 52.5011, lng: 13.4319 },
  },
  {
    placeId: 'fixture:cafe-fraeulein',
    name: 'Café Fräulein',
    type: 'cafe',
    address: 'Manteuffelstr. 30, Kreuzberg',
    location: { lat: 52.5028, lng: 13.4223 },
  },
  {
    placeId: 'fixture:kanal-bar',
    name: 'Kanal Bar',
    type: 'bar',
    address: 'Paul-Lincke-Ufer 21, Kreuzberg',
    location: { lat: 52.4966, lng: 13.4291 },
  },
  {
    placeId: 'fixture:lindenblatt',
    name: 'Lindenblatt',
    type: 'restaurant',
    address: 'Skalitzer Str. 82, Kreuzberg',
    location: { lat: 52.4998, lng: 13.4348 },
  },
];

/** Lower case with accents folded, so "cafe fraulein" finds the Café Fräulein. */
const fold = (s: string): string =>
  s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

@Injectable()
export class FixturePlacesProvider implements PlacesProvider {
  readonly attribution = null;

  // The fixtures are all "nearby" wherever the centre is; the service measures and sorts.
  nearby(): Promise<Place[]> {
    return Promise.resolve([...FIXTURES]);
  }

  search(_center: LatLng, q: string): Promise<Place[]> {
    const needle = fold(q.trim());
    return Promise.resolve(
      FIXTURES.filter((p) => fold(`${p.name} ${p.type}`).includes(needle)),
    );
  }

  details(placeId: string): Promise<Place | null> {
    return Promise.resolve(FIXTURES.find((p) => p.placeId === placeId) ?? null);
  }
}
