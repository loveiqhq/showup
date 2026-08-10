/** DI token for the analytics sink implementation (Epic 11, SHOWUP-72/73). */
export const ANALYTICS_SINK = Symbol('ANALYTICS_SINK');

/** One fully-assembled analytics record, ready to hand to the outside analytics service. */
export interface AnalyticsCapture {
  /** Who the event belongs to: the hashed user id when signed in, else the anonymous id. */
  distinctId: string;
  /** The event name (object_action, snake_case). */
  event: string;
  /** The full standard envelope plus the event's own properties. */
  properties: Record<string, unknown>;
}

/**
 * Sends analytics records onward. Implemented now by a dev stub that logs; a real PostHog EU
 * adapter is swapped in via config with no changes to callers — same seam pattern as the push and
 * email senders.
 */
export interface AnalyticsSink {
  capture(event: AnalyticsCapture): Promise<void>;
}
