import { haversineMeters } from './location.geo';

describe('haversineMeters', () => {
  it('returns 0 for identical points', () => {
    const p = { lat: 52.5163, lng: 13.3777 };
    expect(haversineMeters(p, p)).toBeCloseTo(0, 5);
  });

  it('computes Berlin <-> Cologne ~477 km', () => {
    const brandenburgGate = { lat: 52.5163, lng: 13.3777 };
    const cologneCathedral = { lat: 50.9413, lng: 6.9583 };
    const km = haversineMeters(brandenburgGate, cologneCathedral) / 1000;
    expect(km).toBeGreaterThan(470);
    expect(km).toBeLessThan(485);
  });

  it('computes a short intra-city distance (Brandenburg Gate <-> Reichstag ~270 m)', () => {
    const brandenburgGate = { lat: 52.5163, lng: 13.3777 };
    const reichstag = { lat: 52.5186, lng: 13.3761 };
    const meters = haversineMeters(brandenburgGate, reichstag);
    expect(meters).toBeGreaterThan(150);
    expect(meters).toBeLessThan(450);
  });

  it('is symmetric', () => {
    const a = { lat: 48.8584, lng: 2.2945 };
    const b = { lat: 51.5007, lng: -0.1246 };
    expect(haversineMeters(a, b)).toBeCloseTo(haversineMeters(b, a), 6);
  });
});
