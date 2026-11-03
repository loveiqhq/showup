import { BullModule } from '@nestjs/bullmq';
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { QUEUE_COORDINATE_UNLOCK } from '../../queue/queue.constants';
import { AnalyticsModule } from '../analytics/analytics.module';
import { CheckInsModule } from '../check-ins/check-ins.module';
import { LocationModule } from '../location/location.module';
import { DatesController } from './dates.controller';
import { DatesService } from './dates.service';
import { DateChatMessage } from './entities/date-chat-message.entity';
import { DateStatusChange } from './entities/date-status-change.entity';
import { DateEntity } from './entities/date.entity';
import { CoordinateUnlockProcessor } from './jobs/coordinate-unlock.processor';
import { CoordinateUnlockScheduler } from './jobs/coordinate-unlock.scheduler';

/** Date scheduling & lifecycle (Epic 7), and the Date screen's unlock job (Date 01). */
@Module({
  imports: [
    TypeOrmModule.forFeature([DateEntity, DateStatusChange, DateChatMessage]),
    BullModule.registerQueue({ name: QUEUE_COORDINATE_UNLOCK }),
    CheckInsModule,
    LocationModule,
    AnalyticsModule,
  ],
  controllers: [DatesController],
  providers: [
    DatesService,
    CoordinateUnlockScheduler,
    CoordinateUnlockProcessor,
  ],
  exports: [DatesService],
})
export class DatesModule {}
