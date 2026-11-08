import { Processor, WorkerHost } from '@nestjs/bullmq';
import { Logger } from '@nestjs/common';

import { QUEUE_COORDINATE_UNLOCK } from '../../../queue/queue.constants';
import { DatesService } from '../dates.service';

/** Worker for the coordinate unlock queue: `unlockDueCoordinates`, stamped and safe to retry. */
@Processor(QUEUE_COORDINATE_UNLOCK)
export class CoordinateUnlockProcessor extends WorkerHost {
  private readonly logger = new Logger(CoordinateUnlockProcessor.name);

  constructor(private readonly dates: DatesService) {
    super();
  }

  async process(): Promise<{ unlocked: number }> {
    const unlocked = await this.dates.unlockDueCoordinates();
    if (unlocked > 0) {
      this.logger.log(`Unlocked Coordinate on ${unlocked} date(s)`);
    }
    return { unlocked };
  }
}
