import { Processor, WorkerHost } from '@nestjs/bullmq';

import { QUEUE_DATE_TIMEOUT } from '../../../queue/queue.constants';
import { DateTimeoutService } from '../date-timeout.service';

/** Worker for the date timeout queue: `closeDue`, safe to retry (each date is a conditional update). */
@Processor(QUEUE_DATE_TIMEOUT)
export class DateTimeoutProcessor extends WorkerHost {
  constructor(private readonly timeouts: DateTimeoutService) {
    super();
  }

  async process(): Promise<{ closed: number }> {
    return { closed: await this.timeouts.closeDue() };
  }
}
