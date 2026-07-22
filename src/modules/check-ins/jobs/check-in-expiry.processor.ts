import { Processor, WorkerHost } from '@nestjs/bullmq';
import { Logger } from '@nestjs/common';

import { QUEUE_CHECK_IN_EXPIRY } from '../../../queue/queue.constants';
import { CheckInsService } from '../check-ins.service';

/**
 * Worker for the check-in expiry queue (SHOWUP-111). Runs `expireDue`, which marks past-window
 * available check-ins as expired. Idempotent (SHOWUP-95): the update is state-guarded, so a retry
 * or an accidental double-run simply affects zero rows the second time.
 */
@Processor(QUEUE_CHECK_IN_EXPIRY)
export class CheckInExpiryProcessor extends WorkerHost {
  private readonly logger = new Logger(CheckInExpiryProcessor.name);

  constructor(private readonly checkIns: CheckInsService) {
    super();
  }

  async process(): Promise<{ expired: number }> {
    const expired = await this.checkIns.expireDue();
    if (expired > 0) {
      this.logger.log(`Expired ${expired} past-window check-in(s)`);
    }
    return { expired };
  }
}
