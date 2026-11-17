import { Processor, WorkerHost } from '@nestjs/bullmq';
import { Logger } from '@nestjs/common';

import { QUEUE_ARRIVAL_RETENTION } from '../../../queue/queue.constants';
import { ArrivalCheckInsService } from '../arrival-check-ins.service';

/**
 * Worker for the arrival retention queue (Date 03, R14): deletes check-ins, coordinates included,
 * older than a year. Idempotent: a second run finds nothing left to delete.
 */
@Processor(QUEUE_ARRIVAL_RETENTION)
export class ArrivalRetentionProcessor extends WorkerHost {
  private readonly logger = new Logger(ArrivalRetentionProcessor.name);

  constructor(private readonly arrivals: ArrivalCheckInsService) {
    super();
  }

  async process(): Promise<{ deleted: number }> {
    const deleted = await this.arrivals.deleteExpired();
    if (deleted > 0) {
      this.logger.log(`Deleted ${deleted} expired arrival check-in(s)`);
    }
    return { deleted };
  }
}
