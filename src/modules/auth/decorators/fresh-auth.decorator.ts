import { SetMetadata } from '@nestjs/common';

export const FRESH_AUTH_KEY = 'freshAuthMaxAgeSeconds';

/**
 * Marks a route as a sensitive action requiring recent authentication ("step-up").
 * The user must have authenticated within `maxAgeSeconds` (default 5 minutes); otherwise the
 * request is rejected with a `step_up_required` error and the client should re-verify identity
 * (re-run phone OTP or Apple/Google sign-in, which refreshes the auth time).
 *
 * Apply to dangerous operations: account deletion, changing phone/email, payment changes, etc.
 */
export const RequiresFreshAuth = (maxAgeSeconds = 300) =>
  SetMetadata(FRESH_AUTH_KEY, maxAgeSeconds);
