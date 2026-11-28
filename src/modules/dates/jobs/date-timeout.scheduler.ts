import { InjectQueue } from '@nestjs/bullmq';
import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { Queue } from 'bullmq';

import { QUEUE_DATE_TIMEOUT } from '../../../queue/queue.constants';

/**
 * Registers the recurring +2 h sweep (Date 09, SHOWUP-200): once a minute. Registered whatever the
 * switch says; `closeDue` reads `DATE_TIMEOUT_ENABLED` per run and does nothing while it is off. A
 * stable scheduler id keeps it idempotent across restarts, and a Redis blip at boot is logged.
 */
@Injectable()
export class DateTimeoutScheduler implements OnModuleInit {
  private readonly logger = new Logger(DateTimeoutScheduler.name);

  constructor(@InjectQueue(QUEUE_DATE_TIMEOUT) private readonly queue: Queue) {}

  async onModuleInit(): Promise<void> {
    try {
      await this.queue.upsertJobScheduler(
        'date-timeout-every-minute',
        { every: 60_000 },
        { name: 'close-due', data: {} },
      );
    } catch (error) {
      this.logger.warn(
        `Could not register the date timeout schedule: ${(error as Error).message}`,
      );
    }
  }
}
