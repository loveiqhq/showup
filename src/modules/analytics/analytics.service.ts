import { Inject, Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { randomUUID } from 'crypto';

import { isAnalyticsAllowed } from './consent/consent';
import { buildEnvelope, type AnalyticsContext } from './events/envelope';
import { sanitizeProperties } from './events/privacy';
import { hashUserId } from './identity/hash-user-id';
import {
  ANALYTICS_SINK,
  type AnalyticsSink,
} from './sink/analytics-sink.interface';

export interface TrackInput {
  eventName: string;
  context: AnalyticsContext;
  properties?: Record<string, unknown>;
  /** Raw user id; hashed server-side. Overrides context.userIdHashed when present. */
  userId?: string | null;
  /** The user's analytics consent choice (undefined/null = not chosen). */
  consent?: boolean | null;
  eventId?: string;
  timestampUtc?: string;
}

/**
 * The single gated path for recording analytics (Epic 11, SHOWUP-73). Every event goes through
 * here so the rules can never be skipped: check consent, scramble the user id, stamp the standard
 * envelope, strip any prohibited data, then hand off to the sink. It never throws — a tracking
 * failure must never break the app.
 */
@Injectable()
export class AnalyticsService {
  private readonly logger = new Logger('Analytics');
  private readonly salt: string;
  private readonly consentDefault: boolean;

  constructor(
    @Inject(ANALYTICS_SINK) private readonly sink: AnalyticsSink,
    config: ConfigService,
  ) {
    this.salt = config.get<string>('analytics.salt') ?? '';
    this.consentDefault =
      config.get<boolean>('analytics.consentDefault') ?? false;
  }

  async track(input: TrackInput): Promise<void> {
    try {
      if (
        !isAnalyticsAllowed(input.consent, {
          defaultWhenUnset: this.consentDefault,
        })
      ) {
        return;
      }

      let userIdHashed = input.context.userIdHashed ?? null;
      if (input.userId) {
        userIdHashed = hashUserId(input.userId, this.salt);
      }
      const context: AnalyticsContext = { ...input.context, userIdHashed };

      const envelope = buildEnvelope({
        eventName: input.eventName,
        eventId: input.eventId ?? randomUUID(),
        timestampUtc: input.timestampUtc ?? new Date().toISOString(),
        context,
        properties: sanitizeProperties(input.properties ?? {}),
      });

      // Identity stitching: signed-in users are keyed by their hashed id, otherwise the anonymous id.
      const distinctId = userIdHashed ?? context.anonymousId;
      if (!distinctId) return; // nothing to attribute the event to (no user id, no anonymous id)

      await this.sink.capture({
        distinctId,
        event: input.eventName,
        properties: envelope,
      });
    } catch (err) {
      this.logger.warn(
        `analytics track failed for "${input.eventName}": ${(err as Error).message}`,
      );
    }
  }

  /**
   * Convenience for backend-emitted ("server") events about a signed-in user. Builds the server
   * context (no client anonymous id) and routes through the same gated track() path. Consent is
   * threaded through when known; until per-user consent is captured it defaults to the configured
   * posture, so server events stay gated exactly like every other event.
   */
  trackServerEvent(params: {
    userId: string;
    eventName: string;
    properties?: Record<string, unknown>;
    consent?: boolean | null;
  }): Promise<void> {
    return this.track({
      eventName: params.eventName,
      context: { source: 'server' },
      properties: params.properties,
      userId: params.userId,
      consent: params.consent,
    });
  }
}
