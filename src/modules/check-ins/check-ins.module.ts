import { BullModule } from '@nestjs/bullmq';
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import {
  QUEUE_CHECK_IN_EXPIRY,
  QUEUE_CHECK_IN_EXPIRY_NOTIFY,
} from '../../queue/queue.constants';
import { AnalyticsModule } from '../analytics/analytics.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { Profile } from '../profiles/entities/profile.entity';
import { CheckInFlowController } from './check-in-flow.controller';
import { CheckInFlowService } from './check-in-flow.service';
import { CheckInsController } from './check-ins.controller';
import { CheckInsService } from './check-ins.service';
import { CheckIn } from './entities/check-in.entity';
import { CheckInProfile } from './entities/check-in-profile.entity';
import { CheckInWindow } from './entities/check-in-window.entity';
import { FilterSettings } from './entities/filter-settings.entity';
import { CheckInExpiryNotifyProcessor } from './jobs/check-in-expiry-notify.processor';
import { CheckInExpiryNotifyScheduler } from './jobs/check-in-expiry-notify.scheduler';
import { CheckInExpiryProcessor } from './jobs/check-in-expiry.processor';
import { CheckInExpiryScheduler } from './jobs/check-in-expiry.scheduler';

/**
 * Check-in & availability (Epic 4): create / fetch-active / cancel. Two background jobs run on the
 * shared queue (Epic 17): one tidies past-window check-ins' status (SHOWUP-111), the other reminds
 * users whose window is about to end (Epic 10, SHOWUP-70) via the notifications dispatcher.
 *
 * And the 48hrs Check-In flow (SHOWUP-177): its window, the intro flag, the last answers, the filter
 * settings and the commit, which writes its hours as the Epic 4 availability windows above.
 */
@Module({
  imports: [
    TypeOrmModule.forFeature([
      CheckIn,
      CheckInWindow,
      CheckInProfile,
      FilterSettings,
      Profile,
    ]),
    BullModule.registerQueue(
      { name: QUEUE_CHECK_IN_EXPIRY },
      { name: QUEUE_CHECK_IN_EXPIRY_NOTIFY },
    ),
    NotificationsModule,
    AnalyticsModule,
  ],
  controllers: [CheckInsController, CheckInFlowController],
  providers: [
    CheckInsService,
    CheckInFlowService,
    CheckInExpiryProcessor,
    CheckInExpiryScheduler,
    CheckInExpiryNotifyProcessor,
    CheckInExpiryNotifyScheduler,
  ],
  exports: [CheckInsService, CheckInFlowService],
})
export class CheckInsModule {}
