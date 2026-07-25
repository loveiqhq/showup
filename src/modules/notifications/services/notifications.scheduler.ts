import { InjectQueue } from '@nestjs/bullmq';
import { Injectable } from '@nestjs/common';
import { Queue } from 'bullmq';

import { QUEUE_DATE_REMINDERS } from '../../../queue/queue.constants';

/**
 * Everything the reminder needs, carried on the job itself — so no `dates` table is required. Epic 7
 * calls `scheduleDateReminder` the moment a date is arranged, and `cancelDateReminder` if it is
 * called off.
 */
export interface DateReminderPayload {
  dateId: string;
  participantUserIds: string[];
  time?: string;
  venue?: string;
}

/**
 * Schedules date reminders (SHOWUP-69) as delayed BullMQ jobs. The job id is derived from the date
 * id, so scheduling is idempotent (a duplicate schedule is ignored) and a reminder can be cancelled
 * by that same id — no lookup table needed. The Epic 7 date domain drives this later; for now it is
 * a ready public API with no trigger wired.
 *
 * Note: BullMQ forbids ':' in a custom job id (it is its Redis key separator), so the id uses '-'.
 */
@Injectable()
export class NotificationsScheduler {
  constructor(
    @InjectQueue(QUEUE_DATE_REMINDERS) private readonly dateQueue: Queue,
  ) {}

  private jobId(dateId: string): string {
    return `date-reminder-${dateId}`;
  }

  async scheduleDateReminder(
    payload: DateReminderPayload,
    sendAt: Date,
  ): Promise<void> {
    const delay = Math.max(0, sendAt.getTime() - Date.now());
    await this.dateQueue.add('send', payload, {
      jobId: this.jobId(payload.dateId),
      delay,
    });
  }

  async cancelDateReminder(dateId: string): Promise<void> {
    const job = await this.dateQueue.getJob(this.jobId(dateId));
    if (job) await job.remove();
  }
}
