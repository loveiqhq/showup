/**
 * Builds the standard "background details" envelope stamped onto every analytics record
 * (Epic 11, SHOWUP-72). Every record carries a consistent set of global properties so features
 * don't each have to remember to add them. Property names follow the Master Event Taxonomy
 * (snake_case), and reserved envelope fields always win over any event-specific property.
 */

export const SCHEMA_VERSION = '1.0.0';

/** Global context attached to every event. Most fields originate on the client. */
export interface AnalyticsContext {
  source: 'client' | 'server';
  anonymousId: string;
  /** Salted server-side hash of the user id; null before sign-in. */
  userIdHashed?: string | null;
  sessionId?: string;
  screenId?: string;
  planTier?: 'free' | 'premium';
  appVersion?: string;
  buildNumber?: number;
  osVersion?: string;
  deviceModel?: string;
  locale?: string;
  cityGeohash?: string;
  networkType?: string;
  entryPoint?: string;
  experimentIds?: string[];
  isTestAccount?: boolean;
}

export interface AnalyticsEnvelopeInput {
  eventName: string;
  eventId: string;
  timestampUtc: string;
  context: AnalyticsContext;
  properties?: Record<string, unknown>;
}

export function buildEnvelope(
  input: AnalyticsEnvelopeInput,
): Record<string, unknown> {
  const { eventName, eventId, timestampUtc, context, properties } = input;

  const reserved: Record<string, unknown> = {
    event_id: eventId,
    event_name: eventName,
    event_source: context.source,
    schema_version: SCHEMA_VERSION,
    timestamp_utc: timestampUtc,
    session_id: context.sessionId,
    user_id_hashed: context.userIdHashed ?? null,
    anonymous_id: context.anonymousId,
    screen_id: context.screenId,
    plan_tier: context.planTier,
    app_version: context.appVersion,
    build_number: context.buildNumber,
    os_version: context.osVersion,
    device_model: context.deviceModel,
    locale: context.locale,
    city_geohash: context.cityGeohash,
    network_type: context.networkType,
    entry_point: context.entryPoint,
    experiment_ids: context.experimentIds,
    is_test_account: context.isTestAccount ?? false,
  };

  // Event properties first, reserved fields second → reserved always wins on a key collision.
  const merged: Record<string, unknown> = {
    ...(properties ?? {}),
    ...reserved,
  };

  // Drop undefined so optional fields that weren't provided don't become noise.
  return Object.fromEntries(
    Object.entries(merged).filter(([, value]) => value !== undefined),
  );
}
