import { Processor, WorkerHost } from '@nestjs/bullmq';
import { Job } from 'bullmq';

import { QUEUE_DATE_REMINDERS } from '../../../queue/queue.constants';
import { NotificationDispatchService } from '../services/notification-dispatch.service';
import { DateReminderPayload } from '../services/notifications.scheduler';

/**
 * Fires a date reminder to exactly the two participants of one date (SHOWUP-69). Each recipient is
 * re-checked at send time by the dispatcher (status + preferences), so a user who left or opted out
 * between scheduling and firing is skipped. A cancelled date has had its job removed, so it never
 * runs at all.
 */
@Processor(QUEUE_DATE_REMINDERS)
export class DateReminderProcessor extends WorkerHost {
  constructor(private readonly dispatch: NotificationDispatchService) {
    super();
  }

  async process(job: Job<DateReminderPayload>): Promise<{ notified: number }> {
    const { participantUserIds, time, venue } = job.data;
    let notified = 0;
    for (const userId of participantUserIds) {
      const res = await this.dispatch.sendPush(userId, 'date_reminder', {
        time: time ?? '',
        venue: venue ?? '',
      });
      if (res.status === 'sent') notified++;
    }
    return { notified };
  }
}
