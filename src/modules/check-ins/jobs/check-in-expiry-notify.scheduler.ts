import { InjectQueue } from '@nestjs/bullmq';
import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { Queue } from 'bullmq';

import { QUEUE_CHECK_IN_EXPIRY_NOTIFY } from '../../../queue/queue.constants';

/**
 * Registers the recurring check-in expiry reminder scan (SHOWUP-70): roughly once a minute, look for
 * check-ins nearing their end and remind those users. Uses `upsertJobScheduler` with a stable id so
 * restarts never stack duplicate schedules (SHOWUP-95); a Redis blip at boot is logged, not fatal.
 */
@Injectable()
export class CheckInExpiryNotifyScheduler implements OnModuleInit {
  private readonly logger = new Logger(CheckInExpiryNotifyScheduler.name);

  constructor(
    @InjectQueue(QUEUE_CHECK_IN_EXPIRY_NOTIFY) private readonly queue: Queue,
  ) {}

  async onModuleInit(): Promise<void> {
    try {
      await this.queue.upsertJobScheduler(
        'check-in-expiry-notify-every-minute',
        { every: 60_000 },
        { name: 'remind-expiring', data: {} },
      );
    } catch (error) {
      this.logger.warn(
        `Could not register the check-in expiry reminder schedule: ${(error as Error).message}`,
      );
    }
  }
}
