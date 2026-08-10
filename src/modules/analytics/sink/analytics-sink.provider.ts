import { Provider } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import { ANALYTICS_SINK } from './analytics-sink.interface';
import { LogAnalyticsSink } from './log-analytics-sink';
import { PostHogAnalyticsSink } from './posthog-analytics-sink';

/**
 * Config-driven selection of the analytics sink (same idea as the push/email senders): the real
 * PostHog EU adapter is used only when PostHog is enabled and an API key is present; otherwise the
 * dev log stub. This lets the whole tracking flow run with no PostHog account configured.
 */
export const analyticsSinkProvider: Provider = {
  provide: ANALYTICS_SINK,
  inject: [ConfigService],
  useFactory: (config: ConfigService) => {
    const enabled = config.get<boolean>('posthog.enabled');
    const apiKey = config.get<string>('posthog.apiKey');
    return enabled && apiKey
      ? new PostHogAnalyticsSink(config)
      : new LogAnalyticsSink(config);
  },
};
