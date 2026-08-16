import * as Sentry from '@sentry/node';

import { scrubSentryEvent } from './scrub';

export interface SentrySettings {
  enabled: boolean;
  dsn?: string;
  environment: string;
}

/**
 * Starts error monitoring, but only when it is switched on and a destination is configured
 * (Epic 16, SHOWUP-90).
 *
 * Off is the default and the safe state: with `SENTRY_ENABLED` false, or with no `SENTRY_DSN`, nothing
 * is initialised and nothing is ever sent, so the backend runs exactly as it does today and no account
 * is needed to build or run it. Every report that does go out passes through `beforeSend`, which strips
 * personal information, and `sendDefaultPii` is off so the SDK never adds any of its own.
 *
 * `init` is injectable so the gating can be tested without starting the real SDK.
 */
export function initSentry(
  settings: SentrySettings,
  init: (options: Sentry.NodeOptions) => unknown = Sentry.init,
): boolean {
  if (!settings.enabled || !settings.dsn) return false;

  init({
    dsn: settings.dsn,
    environment: settings.environment,
    // Never let the SDK attach personal information of its own accord.
    sendDefaultPii: false,
    beforeSend: (event) =>
      scrubSentryEvent(event as unknown as Record<string, unknown>) as typeof event,
  });

  return true;
}
