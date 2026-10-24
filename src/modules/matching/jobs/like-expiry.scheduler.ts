import { InjectQueue } from '@nestjs/bullmq';
import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { Queue } from 'bullmq';

import { QUEUE_LIKE_EXPIRY } from '../../../queue/queue.constants';

/**
 * Registers the recurring like expiry job (Search 03, built with Search 01): once a minute, close
 * every open like whose two Check-Ins have no meet hour left. A stable scheduler id makes it
 * idempotent across restarts, and a Redis blip at boot is logged rather than fatal -- the same
 * shape as `CheckInExpiryScheduler`.
 */
@Injectable()
export class LikeExpiryScheduler implements OnModuleInit {
  private readonly logger = new Logger(LikeExpiryScheduler.name);

  constructor(@InjectQueue(QUEUE_LIKE_EXPIRY) private readonly queue: Queue) {}

  async onModuleInit(): Promise<void> {
    try {
      await this.queue.upsertJobScheduler(
        'like-expiry-every-minute',
        { every: 60_000 },
        { name: 'withdraw-no-overlap', data: {} },
      );
    } catch (error) {
      this.logger.warn(
        `Could not register the like expiry schedule: ${(error as Error).message}`,
      );
    }
  }
}
