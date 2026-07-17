import { InjectQueue } from '@nestjs/bullmq';
import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { Queue } from 'bullmq';

import { QUEUE_CHECK_IN_EXPIRY } from '../../../queue/queue.constants';

/**
 * Registers the recurring check-in expiry job (SHOWUP-111): roughly once a minute, run `expire-due`.
 *
 * Uses `upsertJobScheduler` with a stable id so it is idempotent (SHOWUP-95) — restarting the app
 * never stacks duplicate schedules. A Redis blip at boot is logged rather than fatal, so the app
 * still starts (SHOWUP-93 graceful failure); the schedule is re-established on the next clean boot.
 */
@Injectable()
export class CheckInExpiryScheduler implements OnModuleInit {
  private readonly logger = new Logger(CheckInExpiryScheduler.name);

  constructor(
    @InjectQueue(QUEUE_CHECK_IN_EXPIRY) private readonly queue: Queue,
  ) {}

  async onModuleInit(): Promise<void> {
    try {
      await this.queue.upsertJobScheduler(
        'check-in-expiry-every-minute',
        { every: 60_000 },
        { name: 'expire-due', data: {} },
      );
    } catch (error) {
      this.logger.warn(
        `Could not register the check-in expiry schedule: ${(error as Error).message}`,
      );
    }
  }
}
