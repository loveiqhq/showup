import { Module } from '@nestjs/common';

import { AnalyticsService } from './analytics.service';
import { analyticsSinkProvider } from './sink/analytics-sink.provider';

/**
 * Analytics & tracking (Epic 11). Provides the single gated tracking path (AnalyticsService) and
 * the config-selected sink (PostHog EU real adapter, or a dev log stub). Exported so feature
 * modules can record events once the per-feature tracking stories are built.
 */
@Module({
  providers: [analyticsSinkProvider, AnalyticsService],
  exports: [AnalyticsService],
})
export class AnalyticsModule {}
