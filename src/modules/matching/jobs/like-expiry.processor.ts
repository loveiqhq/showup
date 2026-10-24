import { Processor, WorkerHost } from '@nestjs/bullmq';
import { Logger } from '@nestjs/common';

import { QUEUE_LIKE_EXPIRY } from '../../../queue/queue.constants';
import { MatchingService } from '../matching.service';

/** Worker for the like expiry queue: `withdrawNoOverlap`, state-guarded and safe to retry. */
@Processor(QUEUE_LIKE_EXPIRY)
export class LikeExpiryProcessor extends WorkerHost {
  private readonly logger = new Logger(LikeExpiryProcessor.name);

  constructor(private readonly matching: MatchingService) {
    super();
  }

  async process(): Promise<{ withdrawn: number }> {
    const withdrawn = await this.matching.withdrawNoOverlap();
    if (withdrawn > 0) {
      this.logger.log(`Closed ${withdrawn} like(s) with no meet time left`);
    }
    return { withdrawn };
  }
}
