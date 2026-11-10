import { BullModule } from '@nestjs/bullmq';
import { Profile } from '../profiles/entities/profile.entity';
import { Match } from '../matching/entities/match.entity';
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import {
  QUEUE_ARRIVAL_RETENTION,
  QUEUE_COORDINATE_UNLOCK,
} from '../../queue/queue.constants';
import { AnalyticsModule } from '../analytics/analytics.module';
import { CheckInsModule } from '../check-ins/check-ins.module';
import { LocationModule } from '../location/location.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { ArrivalCheckInsService } from './arrival-check-ins.service';
import { DatesController } from './dates.controller';
import { DatesService } from './dates.service';
import { DateArrivalCheckIn } from './entities/date-arrival-check-in.entity';
import { DateChatMessage } from './entities/date-chat-message.entity';
import { DateStatusChange } from './entities/date-status-change.entity';
import { DateEntity } from './entities/date.entity';
import { CoordinateUnlockProcessor } from './jobs/coordinate-unlock.processor';
import { ArrivalRetentionProcessor } from './jobs/arrival-retention.processor';
import { ArrivalRetentionScheduler } from './jobs/arrival-retention.scheduler';
import { CoordinateUnlockScheduler } from './jobs/coordinate-unlock.scheduler';

/** Date scheduling & lifecycle (Epic 7), and the Date screen's unlock job (Date 01). */
@Module({
  imports: [
    TypeOrmModule.forFeature([
      DateEntity,
      DateStatusChange,
      DateChatMessage,
      DateArrivalCheckIn,
      Profile,
      Match,
    ]),
    BullModule.registerQueue({ name: QUEUE_COORDINATE_UNLOCK }),
    BullModule.registerQueue({ name: QUEUE_ARRIVAL_RETENTION }),
    CheckInsModule,
    LocationModule,
    AnalyticsModule,
    NotificationsModule,
  ],
  controllers: [DatesController],
  providers: [
    DatesService,
    ArrivalCheckInsService,
    CoordinateUnlockScheduler,
    CoordinateUnlockProcessor,
    ArrivalRetentionScheduler,
    ArrivalRetentionProcessor,
  ],
  exports: [DatesService, ArrivalCheckInsService],
})
export class DatesModule {}
