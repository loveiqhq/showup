/**
 * The single list of field names that must never leave the backend in a record a human or an outside
 * service can read — analytics events (Epic 11) and log lines (Epic 16) both key off it, so the rule
 * cannot drift between them.
 *
 * Keys are matched by normalised form (lower-case, punctuation removed) so `email`, `Email` and
 * `e_mail` are all caught, while safe look-alikes such as `city_geohash`, `age_band` and `token_type`
 * are preserved.
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
  // Secrets. Note `code`: our one-time sign-in and email-verification payloads name the secret
  // `code` (VerifyOtpDto.code, VerifyEmailDto.code), so the bare name must be withheld. An API error
  // code is a different thing and must be named `error_code` so it is not caught by this rule.
  'code',
  'otp',
  'otpcode',
  'smscode',
  'verificationcode',
  'pin',
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

/** Lower-case and strip punctuation so naming style cannot slip a prohibited field through. */
export function normaliseKey(key: string): string {
  return key.toLowerCase().replace(/[^a-z0-9]/g, '');
}

/** Whether a field with this name must be withheld. */
export function isProhibitedKey(key: string): boolean {
  return PROHIBITED_NORMALISED.has(normaliseKey(key));
}
