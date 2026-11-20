import { InjectQueue } from '@nestjs/bullmq';
import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { Queue } from 'bullmq';

import { QUEUE_CHANGE_EXPIRY } from '../../../queue/queue.constants';

/**
 * Registers the recurring change expiry (Date 05, SHOWUP-196): once a minute, expire every change
 * suggestion still pending at its deadline. The coordinate unlock's shape: a stable scheduler id keeps
 * it idempotent across restarts, and a Redis blip at boot is logged rather than fatal.
 */
@Injectable()
export class ChangeExpiryScheduler implements OnModuleInit {
  private readonly logger = new Logger(ChangeExpiryScheduler.name);

  constructor(
    @InjectQueue(QUEUE_CHANGE_EXPIRY) private readonly queue: Queue,
  ) {}

  async onModuleInit(): Promise<void> {
    try {
      await this.queue.upsertJobScheduler(
        'change-expiry-every-minute',
        { every: 60_000 },
        { name: 'expire-due', data: {} },
      );
    } catch (error) {
      this.logger.warn(
        `Could not register the change expiry schedule: ${(error as Error).message}`,
      );
    }
  }
}
