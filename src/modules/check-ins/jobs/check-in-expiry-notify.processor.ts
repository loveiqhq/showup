import { Processor, WorkerHost } from '@nestjs/bullmq';
import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import { NotificationDispatchService } from '../../notifications/services/notification-dispatch.service';
import { QUEUE_CHECK_IN_EXPIRY_NOTIFY } from '../../../queue/queue.constants';
import { CheckInsService } from '../check-ins.service';

/**
 * Reminds users whose check-in is about to end (SHOWUP-70). Scans for check-ins entering the
 * "expiring soon" window that have not yet been reminded, sends a discreet push (gated by the user's
 * preferences and account status in the dispatcher), and marks each as reminded so it never fires
 * twice — even if the push was suppressed. Cancelled/expired check-ins are excluded by the scan, so
 * they never generate a message. Idempotent: a re-run simply finds nothing new.
 */
@Processor(QUEUE_CHECK_IN_EXPIRY_NOTIFY)
export class CheckInExpiryNotifyProcessor extends WorkerHost {
  private readonly logger = new Logger(CheckInExpiryNotifyProcessor.name);

  constructor(
    private readonly checkIns: CheckInsService,
    private readonly dispatch: NotificationDispatchService,
    private readonly config: ConfigService,
  ) {
    super();
  }

  async process(): Promise<{ notified: number }> {
    const lead =
      this.config.get<number>('notifications.checkInExpiryLeadMinutes') ?? 10;
    const due = await this.checkIns.findExpiringSoon(lead);

    let notified = 0;
    for (const checkIn of due) {
      const res = await this.dispatch.sendPush(
        checkIn.userId,
        'check_in_expiry',
        {},
      );
      // Mark regardless of the send outcome so a suppressed reminder is not retried every minute.
      await this.checkIns.markExpiryReminderSent(checkIn.id);
      if (res.status === 'sent') notified++;
    }
    if (notified > 0) {
      this.logger.log(`Sent ${notified} check-in expiry reminder(s)`);
    }
    return { notified };
  }
}
