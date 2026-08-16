import { isProhibitedKey } from '../../../common/privacy/prohibited-fields';

/**
 * Enforces the governance document's prohibited-data rules (§3) at the point every event is built
 * (Epic 11, SHOWUP-73). This is a hard backstop: even if a caller accidentally attaches banned
 * information, it is stripped before the record leaves the app.
 *
 * The list of prohibited field names lives in common/privacy/prohibited-fields.ts, shared with the
 * log redaction (Epic 16), so analytics and logs can never disagree about what must be withheld.
 */

/** Returns a new object with any prohibited keys removed; never mutates the input. */
export function sanitizeProperties(
  properties: Record<string, unknown>,
): Record<string, unknown> {
  const clean: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(properties)) {
    if (!isProhibitedKey(key)) {
      clean[key] = value;
    }
  }
  return clean;
}
