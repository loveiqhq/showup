import { FixturePlacesProvider } from './fixture-places.provider';

const CENTER = { lat: 52.5016, lng: 13.4186 };

describe('FixturePlacesProvider', () => {
  const provider = new FixturePlacesProvider();

  it('is not Google, so no attribution', () => {
    expect(provider.attribution).toBeNull();
  });

  it("nearby is the kit's five Kreuzberg venues", async () => {
    const places = await provider.nearby(CENTER);
    expect(places.map((p) => p.name).sort()).toEqual([
      'Bar Lenz',
      'Café Fräulein',
      'Kanal Bar',
      'Lindenblatt',
      'Osteria Nove',
    ]);
    expect(places.every((p) => p.placeId.startsWith('fixture:'))).toBe(true);
    expect(new Set(places.map((p) => p.type))).toEqual(
      new Set(['bar', 'restaurant', 'cafe']),
    );
  });

  it('search filters by name or type, ignoring case and accents', async () => {
    expect((await provider.search(CENTER, 'LENZ')).map((p) => p.name)).toEqual([
      'Bar Lenz',
    ]);
    expect(
      (await provider.search(CENTER, 'cafe fraulein')).map((p) => p.name),
    ).toEqual(['Café Fräulein']);
    expect(
      (await provider.search(CENTER, 'restaurant')).map((p) => p.name).sort(),
    ).toEqual(['Lindenblatt', 'Osteria Nove']);
    expect(await provider.search(CENTER, 'zzz')).toEqual([]);
  });

  it('details finds one by id, or null', async () => {
    expect((await provider.details('fixture:bar-lenz'))?.name).toBe('Bar Lenz');
    expect(await provider.details('fixture:nope')).toBeNull();
  });
});
