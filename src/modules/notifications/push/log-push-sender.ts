import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import {
  PushMessage,
  PushSender,
  PushSendResult,
} from './push-sender.interface';

/**
 * Development push stub: logs the notification instead of delivering it, so the whole flow is
 * testable without Firebase. Warns (does not log content) in production, where a real FCM adapter
 * should be configured instead. Selected automatically when no FCM project id is set.
 */
@Injectable()
export class LogPushSender implements PushSender {
  private readonly logger = new Logger('PushSender');

  constructor(private readonly config: ConfigService) {}

  send(message: PushMessage): Promise<PushSendResult> {
    if (this.config.get<string>('app.env') === 'production') {
      this.logger.warn(
        'No push provider configured (FCM) — notification not delivered',
      );
    } else {
      this.logger.log(
        `[DEV] push → ${message.token.slice(0, 12)}…: ${message.title} — ${message.body}`,
      );
    }
    return Promise.resolve({ success: true, messageId: 'dev-log' });
  }
}
