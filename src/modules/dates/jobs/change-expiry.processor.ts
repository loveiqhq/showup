import { Processor, WorkerHost } from '@nestjs/bullmq';
import { Logger } from '@nestjs/common';

import { QUEUE_CHANGE_EXPIRY } from '../../../queue/queue.constants';
import { ChangeSuggestionsService } from '../change-suggestions.service';

/** Worker for the change expiry queue: `expireDue`, one conditional UPDATE and safe to retry. */
@Processor(QUEUE_CHANGE_EXPIRY)
export class ChangeExpiryProcessor extends WorkerHost {
  private readonly logger = new Logger(ChangeExpiryProcessor.name);

  constructor(private readonly changes: ChangeSuggestionsService) {
    super();
  }

  async process(): Promise<{ expired: number }> {
    const expired = await this.changes.expireDue();
    if (expired > 0) {
      this.logger.log(`Expired ${expired} change suggestion(s)`);
    }
    return { expired };
  }
}
