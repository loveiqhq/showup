import { BullModule } from '@nestjs/bullmq';
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { QUEUE_DATE_REMINDERS } from '../../queue/queue.constants';
import { User } from '../users/entities/user.entity';
import { NotificationPreferencesController } from './controllers/notification-preferences.controller';
import { PushTokensController } from './controllers/push-tokens.controller';
import { emailSenderProvider } from './email/email-sender.provider';
import { NotificationLogEntry } from './entities/notification-log.entity';
import { NotificationPreference } from './entities/notification-preference.entity';
import { PushToken } from './entities/push-token.entity';
import { DateReminderProcessor } from './jobs/date-reminder.processor';
import { pushSenderProvider } from './push/push-sender.provider';
import { EmailService } from './services/email.service';
import { NotificationDispatchService } from './services/notification-dispatch.service';
import { NotificationPreferencesService } from './services/notification-preferences.service';
import { NotificationsScheduler } from './services/notifications.scheduler';
import { PushTokensService } from './services/push-tokens.service';

/**
 * Notifications & email (Epic 10). Owns the send seams (push/email), the single gated dispatch
 * path, user preferences, device tokens, and date reminders. Check-in expiry reminders live in the
 * check-ins module (which imports this one for the dispatcher), keeping the dependency one-way.
 */
@Module({
  imports: [
    TypeOrmModule.forFeature([
      NotificationPreference,
      PushToken,
      NotificationLogEntry,
      User,
    ]),
    BullModule.registerQueue({ name: QUEUE_DATE_REMINDERS }),
  ],
  controllers: [NotificationPreferencesController, PushTokensController],
  providers: [
    NotificationPreferencesService,
    PushTokensService,
    NotificationDispatchService,
    EmailService,
    NotificationsScheduler,
    DateReminderProcessor,
    pushSenderProvider,
    emailSenderProvider,
  ],
  exports: [
    NotificationDispatchService,
    EmailService,
    NotificationsScheduler,
    NotificationPreferencesService,
  ],
})
export class NotificationsModule {}
