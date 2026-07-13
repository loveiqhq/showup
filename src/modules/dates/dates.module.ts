import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { CheckInsModule } from '../check-ins/check-ins.module';
import { LocationModule } from '../location/location.module';
import { DatesController } from './dates.controller';
import { DatesService } from './dates.service';
import { DateStatusChange } from './entities/date-status-change.entity';
import { DateEntity } from './entities/date.entity';

/** Date scheduling & lifecycle (Epic 7). */
@Module({
  imports: [
    TypeOrmModule.forFeature([DateEntity, DateStatusChange]),
    CheckInsModule,
    LocationModule,
  ],
  controllers: [DatesController],
  providers: [DatesService],
  exports: [DatesService],
})
export class DatesModule {}
