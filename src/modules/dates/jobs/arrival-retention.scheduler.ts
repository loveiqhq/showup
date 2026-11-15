import { InjectQueue } from '@nestjs/bullmq';
import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { Queue } from 'bullmq';

import { QUEUE_ARRIVAL_RETENTION } from '../../../queue/queue.constants';

/**
 * Registers the daily arrival retention sweep (Date 03, R14). `upsertJobScheduler` with a stable id
 * keeps it idempotent across restarts; a Redis blip at boot is logged, not fatal, as the other
 * schedulers do.
 */
@Injectable()
export class ArrivalRetentionScheduler implements OnModuleInit {
  private readonly logger = new Logger(ArrivalRetentionScheduler.name);

  constructor(
    @InjectQueue(QUEUE_ARRIVAL_RETENTION) private readonly queue: Queue,
  ) {}

  async onModuleInit(): Promise<void> {
    try {
      await this.queue.upsertJobScheduler(
        'arrival-retention-daily',
        { every: 86_400_000 },
        { name: 'delete-expired', data: {} },
      );
    } catch (error) {
      this.logger.warn(
        `Could not register the arrival retention schedule: ${(error as Error).message}`,
      );
    }
  }
}
