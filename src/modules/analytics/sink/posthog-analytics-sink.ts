import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import { AnalyticsCapture, AnalyticsSink } from './analytics-sink.interface';

/**
 * Real analytics sink: sends events to PostHog's EU capture endpoint over HTTPS. Dependency-free —
 * it uses the built-in fetch, so no extra SDK is required. Selected only when PostHog is enabled
 * and an API key is configured (otherwise the log stub is used). Failures are swallowed and logged;
 * analytics must never break the app (the AnalyticsService guards this too).
 */
@Injectable()
export class PostHogAnalyticsSink implements AnalyticsSink {
  private readonly logger = new Logger('AnalyticsSink');
  private readonly host: string;
  private readonly apiKey: string;

  constructor(config: ConfigService) {
    this.host = (
      config.get<string>('posthog.host') ?? 'https://eu.i.posthog.com'
    ).replace(/\/+$/, '');
    this.apiKey = config.get<string>('posthog.apiKey') ?? '';
  }

  async capture(event: AnalyticsCapture): Promise<void> {
    try {
      const res = await fetch(`${this.host}/capture/`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          api_key: this.apiKey,
          event: event.event,
          distinct_id: event.distinctId,
          properties: event.properties,
          timestamp: event.properties['timestamp_utc'],
        }),
        signal: AbortSignal.timeout(3000),
      });
      if (!res.ok) {
        this.logger.warn(`PostHog capture returned HTTP ${res.status}`);
      }
    } catch (err) {
      this.logger.warn(`PostHog capture failed: ${(err as Error).message}`);
    }
  }
}
