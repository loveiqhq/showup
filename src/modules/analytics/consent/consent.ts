/**
 * The analytics consent gate (Epic 11, SHOWUP-73). Whether the app may record analytics for a
 * given person depends on their consent choice. The exact posture — opt-in (track only after an
 * explicit yes) vs opt-out (track until they say no) — is a legal/product decision still pending
 * review, so it is NOT hard-coded here: it is expressed by `defaultWhenUnset`, driven from config.
 */

export interface ConsentOptions {
  /** false = opt-in posture; true = opt-out posture. Applied only when consent is unset. */
  defaultWhenUnset: boolean;
}

export function isAnalyticsAllowed(
  consent: boolean | null | undefined,
  options: ConsentOptions,
): boolean {
  if (consent === true) return true;
  if (consent === false) return false;
  return options.defaultWhenUnset;
}
