import { createHash } from 'crypto';

/**
 * Server-side identity anonymisation for analytics (Epic 11, SHOWUP-72 / SHOWUP-73).
 *
 * Turns a real user id into a one-way, salted SHA-256 code (`user_id_hashed`) so the external
 * analytics service only ever sees a meaningless string, never the real id or any personal detail.
 *
 * Per the Analytics Architecture & Governance doc, this happens ONLY on the server — never on the
 * mobile client. The salt must be a secret, stable value held server-side: it is what stops the
 * hash from being reproduced (and the user re-identified) by anyone who does not hold it. Because a
 * stable salt keeps a person's events joined up over time, changing it is a deliberate act that
 * breaks the link between their past and future events.
 */
export function hashUserId(userId: string, salt: string): string {
  if (!salt) {
    throw new Error(
      'analytics: a non-empty global salt is required to hash user ids — ' +
        'an empty or missing salt would make the hash reproducible and the user re-identifiable',
    );
  }
  return createHash('sha256').update(`${userId}${salt}`).digest('hex');
}
