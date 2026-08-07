import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import { AnalyticsCapture, AnalyticsSink } from './analytics-sink.interface';

/**
 * Development analytics stub: logs the event instead of sending it, so the whole tracking flow is
 * testable without a PostHog account. Warns (without content) in production, where a real adapter
 * should be configured instead. Selected automatically when PostHog is not enabled/configured.
 */
@Injectable()
export class LogAnalyticsSink implements AnalyticsSink {
  private readonly logger = new Logger('AnalyticsSink');

  constructor(private readonly config: ConfigService) {}

  capture(event: AnalyticsCapture): Promise<void> {
    if (this.config.get<string>('app.env') === 'production') {
      this.logger.warn(
        'No analytics provider configured (PostHog) — event not sent',
      );
    } else {
      this.logger.log(
        `[DEV] analytics → ${event.event} (distinct_id=${event.distinctId.slice(0, 12)}…)`,
      );
    }
    return Promise.resolve();
  }
}
