export interface LatLng {
  lat: number;
  lng: number;
}

// Mean Earth radius (meters), per IUGG — matches PostGIS sphere assumptions closely enough
// for sanity checks. PostGIS ST_Distance on geography uses the WGS84 spheroid (more precise).
const EARTH_RADIUS_M = 6_371_008.8;

const toRad = (deg: number): number => (deg * Math.PI) / 180;

/**
 * Great-circle distance between two points in meters (haversine). Pure and dependency-free, so
 * it is safe to unit-test and to use for quick client-side-style estimates. For authoritative
 * spatial math use LocationService.distanceMeters (PostGIS).
 */
export function haversineMeters(a: LatLng, b: LatLng): number {
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);

  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;

  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
}
