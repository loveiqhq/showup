import { InjectQueue } from '@nestjs/bullmq';
import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { Queue } from 'bullmq';

import { QUEUE_COORDINATE_UNLOCK } from '../../../queue/queue.constants';

/**
 * Registers the recurring coordinate unlock (Date 01, SHOWUP-193): once a minute, send
 * `coordinate_unlocked` for every date that reached meet − 1:30. The same shape as
 * `LikeExpiryScheduler`: a stable scheduler id keeps it idempotent across restarts, and a Redis
 * blip at boot is logged rather than fatal.
 */
@Injectable()
export class CoordinateUnlockScheduler implements OnModuleInit {
  private readonly logger = new Logger(CoordinateUnlockScheduler.name);

  constructor(
    @InjectQueue(QUEUE_COORDINATE_UNLOCK) private readonly queue: Queue,
  ) {}

  async onModuleInit(): Promise<void> {
    try {
      await this.queue.upsertJobScheduler(
        'coordinate-unlock-every-minute',
        { every: 60_000 },
        { name: 'unlock-due', data: {} },
      );
    } catch (error) {
      this.logger.warn(
        `Could not register the coordinate unlock schedule: ${(error as Error).message}`,
      );
    }
  }
}
