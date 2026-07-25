import { BullModule } from '@nestjs/bullmq';
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { QUEUE_CHECK_IN_EXPIRY } from '../../queue/queue.constants';
import { CheckInsController } from './check-ins.controller';
import { CheckInsService } from './check-ins.service';
import { CheckIn } from './entities/check-in.entity';
import { CheckInExpiryProcessor } from './jobs/check-in-expiry.processor';
import { CheckInExpiryScheduler } from './jobs/check-in-expiry.scheduler';

/**
 * Check-in & availability (Epic 4): create / fetch-active / cancel. Past-window check-ins are
 * treated as inactive immediately; the background queue (Epic 17, SHOWUP-111) tidies their stored
 * status on a schedule.
 */
@Module({
  imports: [
    TypeOrmModule.forFeature([CheckIn]),
    BullModule.registerQueue({ name: QUEUE_CHECK_IN_EXPIRY }),
  ],
  controllers: [CheckInsController],
  providers: [CheckInsService, CheckInExpiryProcessor, CheckInExpiryScheduler],
  exports: [CheckInsService],
})
export class CheckInsModule {}
