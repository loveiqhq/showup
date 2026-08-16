import { isProhibitedKey } from '../privacy/prohibited-fields';

/** What a withheld value is replaced with, so it is visible that a field existed. */
export const REDACTED = '[redacted]';

/**
 * Replaces prohibited values anywhere in a log payload, however deeply nested (Epic 16, SHOWUP-91).
 * Logs are stored and read by people, so contact details, secrets, coordinates and private message
 * content must never reach them. Identifiers and outcomes — the useful part of a log line — are kept.
 *
 * Unlike the analytics strip, which drops the key entirely, this leaves a marker behind so it is clear
 * a field was present and withheld rather than never sent. Never mutates the input.
 */
export function redactForLog(value: unknown): unknown {
  if (Array.isArray(value)) return value.map((item) => redactForLog(item));

  if (value instanceof Date) return value.toISOString();

  if (value !== null && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [key, inner] of Object.entries(
      value as Record<string, unknown>,
    )) {
      out[key] = isProhibitedKey(key) ? REDACTED : redactForLog(inner);
    }
    return out;
  }

  return value;
}
