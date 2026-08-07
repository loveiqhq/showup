/**
 * Enforces the governance document's prohibited-data rules (§3) at the point every event is built
 * (Epic 11, SHOWUP-73). This is a hard backstop: even if a caller accidentally attaches banned
 * information, it is stripped before the record leaves the app. Keys are matched by normalised
 * form (lower-case, punctuation removed) so `email`, `Email`, and `e_mail` are all caught, while
 * safe look-alikes such as `city_geohash`, `age_band`, and `token_type` are preserved.
 */

const PROHIBITED_NORMALISED = new Set<string>([
  // Unmasked PII
  'email',
  'phone',
  'phonenumber',
  'mobile',
  'lat',
  'latitude',
  'lng',
  'lon',
  'longitude',
  'gps',
  'coordinates',
  'coords',
  'address',
  'streetaddress',
  // Private chat content
  'message',
  'messagetext',
  'messagebody',
  'text',
  'content',
  // Demographics (must be bucketed into age_band instead)
  'age',
  'dob',
  'birthdate',
  'dateofbirth',
  // Secrets
  'password',
  'passcode',
  'pwd',
  'token',
  'accesstoken',
  'refreshtoken',
  'pushtoken',
  'authtoken',
  'apitoken',
  'sessionkey',
  'secret',
  'apikey',
  'card',
  'cardnumber',
  'creditcard',
  'cvv',
  'cvc',
  'pan',
]);

function normalise(key: string): string {
  return key.toLowerCase().replace(/[^a-z0-9]/g, '');
}

/** Returns a new object with any prohibited keys removed; never mutates the input. */
export function sanitizeProperties(
  properties: Record<string, unknown>,
): Record<string, unknown> {
  const clean: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(properties)) {
    if (!PROHIBITED_NORMALISED.has(normalise(key))) {
      clean[key] = value;
    }
  }
  return clean;
}
