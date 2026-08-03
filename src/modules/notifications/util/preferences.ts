/**
 * Pure notification-preference logic (SHOWUP-66/68). No framework, no I/O — just the policy that
 * decides whether a given message may be sent to a user with a given set of preferences. Kept pure
 * so the gate is trivially testable and identical everywhere it is applied.
 */

/** The three preference groups a user can control. Marketing is off until consent is given. */
export type NotificationCategory = 'essential' | 'engagement' | 'marketing';

/** Every kind of message the backend can send. Add new kinds here + in the maps below + the catalog. */
export type NotificationType =
  | 'date_reminder'
  | 'check_in_expiry'
  | 'new_match'
  | 'email_verification'
  | 'password_reset'
  | 'account_closure'
  | 'security_alert'
  | 'safety_alert'
  | 'marketing_generic';

/** A user's on/off choice per category (one row per user in `notification_preferences`). */
export interface NotificationPreferenceState {
  essential: boolean;
  engagement: boolean;
  marketing: boolean;
}

/** Sensible starting point for a new user: essentials + engagement on, marketing off (consent-gated). */
export const DEFAULT_PREFERENCES: NotificationPreferenceState = {
  essential: true,
  engagement: true,
  marketing: false,
};

const CATEGORY_BY_TYPE: Record<NotificationType, NotificationCategory> = {
  date_reminder: 'engagement',
  check_in_expiry: 'engagement',
  new_match: 'engagement',
  email_verification: 'essential',
  password_reset: 'essential',
  account_closure: 'essential',
  security_alert: 'essential',
  safety_alert: 'essential',
  marketing_generic: 'marketing',
};

/**
 * Critical messages bypass the toggles entirely — they are mandatory service/safety messages
 * (password reset, email verification, account closure, security/safety alerts) that a user cannot
 * opt out of. Everything else respects its category toggle.
 */
const CRITICAL_TYPES: ReadonlySet<NotificationType> = new Set<NotificationType>(
  [
    'email_verification',
    'password_reset',
    'account_closure',
    'security_alert',
    'safety_alert',
  ],
);

export function categoryForType(type: NotificationType): NotificationCategory {
  return CATEGORY_BY_TYPE[type];
}

export function isCriticalType(type: NotificationType): boolean {
  return CRITICAL_TYPES.has(type);
}

/**
 * The single source of truth for "may we send this?". Critical types always send; otherwise the
 * message is allowed only if its category is enabled. (Marketing additionally requires the
 * `marketing` toggle, which is off by default and only flipped on with explicit consent.)
 */
export function shouldSend(
  type: NotificationType,
  prefs: NotificationPreferenceState,
): boolean {
  if (isCriticalType(type)) return true;
  return prefs[categoryForType(type)];
}
