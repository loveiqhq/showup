import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { AnalyticsModule } from '../analytics/analytics.module';
import { CheckInsModule } from '../check-ins/check-ins.module';
import { LocationModule } from '../location/location.module';
import { DatesController } from './dates.controller';
import { DatesService } from './dates.service';
import { DateChatMessage } from './entities/date-chat-message.entity';
import { DateStatusChange } from './entities/date-status-change.entity';
import { DateEntity } from './entities/date.entity';

/** Date scheduling & lifecycle (Epic 7). */
@Module({
  imports: [
    TypeOrmModule.forFeature([DateEntity, DateStatusChange, DateChatMessage]),
    CheckInsModule,
    LocationModule,
    AnalyticsModule,
  ],
  controllers: [DatesController],
  providers: [DatesService],
  exports: [DatesService],
})
export class DatesModule {}
