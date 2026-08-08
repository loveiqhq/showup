import { BullModule } from '@nestjs/bullmq';
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import {
  QUEUE_CHECK_IN_EXPIRY,
  QUEUE_CHECK_IN_EXPIRY_NOTIFY,
} from '../../queue/queue.constants';
import { AnalyticsModule } from '../analytics/analytics.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { CheckInsController } from './check-ins.controller';
import { CheckInsService } from './check-ins.service';
import { CheckIn } from './entities/check-in.entity';
import { CheckInExpiryNotifyProcessor } from './jobs/check-in-expiry-notify.processor';
import { CheckInExpiryNotifyScheduler } from './jobs/check-in-expiry-notify.scheduler';
import { CheckInExpiryProcessor } from './jobs/check-in-expiry.processor';
import { CheckInExpiryScheduler } from './jobs/check-in-expiry.scheduler';

/**
 * Check-in & availability (Epic 4): create / fetch-active / cancel. Two background jobs run on the
 * shared queue (Epic 17): one tidies past-window check-ins' status (SHOWUP-111), the other reminds
 * users whose window is about to end (Epic 10, SHOWUP-70) via the notifications dispatcher.
 */
@Module({
  imports: [
    TypeOrmModule.forFeature([CheckIn]),
    BullModule.registerQueue(
      { name: QUEUE_CHECK_IN_EXPIRY },
      { name: QUEUE_CHECK_IN_EXPIRY_NOTIFY },
    ),
    NotificationsModule,
    AnalyticsModule,
  ],
  controllers: [CheckInsController],
  providers: [
    CheckInsService,
    CheckInExpiryProcessor,
    CheckInExpiryScheduler,
    CheckInExpiryNotifyProcessor,
    CheckInExpiryNotifyScheduler,
  ],
  exports: [CheckInsService],
})
export class CheckInsModule {}
