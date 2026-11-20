import { changeDeadline, cutoffPassed, mapPlaceType } from './change-rules';

const at = (iso: string) => new Date(iso);

describe('changeDeadline', () => {
  it('is half an hour before the binding time', () => {
    expect(changeDeadline(at('2026-11-17T19:00:00Z'))).toEqual(
      at('2026-11-17T18:30:00Z'),
    );
  });
});

describe('cutoffPassed', () => {
  const binding = at('2026-11-17T19:00:00Z');
  it('is open until meet - 0:30 and closed from it', () => {
    expect(cutoffPassed(binding, at('2026-11-17T18:29:59Z'))).toBe(false);
    expect(cutoffPassed(binding, at('2026-11-17T18:30:00Z'))).toBe(true);
    expect(cutoffPassed(binding, at('2026-11-17T20:00:00Z'))).toBe(true);
  });
});

describe('mapPlaceType', () => {
  it('maps the three broad types', () => {
    expect(mapPlaceType('cafe', [])).toBe('cafe');
    expect(mapPlaceType('bar', [])).toBe('bar');
    expect(mapPlaceType('restaurant', [])).toBe('restaurant');
  });

  it('reads the primary type first, then the list in order', () => {
    expect(mapPlaceType('bar', ['restaurant', 'cafe'])).toBe('bar');
    expect(mapPlaceType('point_of_interest', ['restaurant', 'cafe'])).toBe(
      'restaurant',
    );
    expect(mapPlaceType(null, ['store', 'cafe'])).toBe('cafe');
  });

  it('maps common narrow types', () => {
    expect(mapPlaceType('coffee_shop', [])).toBe('cafe');
    expect(mapPlaceType('pub', [])).toBe('bar');
  });

  it('drops anything else, street addresses included', () => {
    expect(mapPlaceType('street_address', ['route'])).toBeNull();
    expect(mapPlaceType(undefined, undefined)).toBeNull();
    expect(mapPlaceType('park', ['tourist_attraction'])).toBeNull();
  });
});
