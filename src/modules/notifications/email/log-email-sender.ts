import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import {
  EmailMessage,
  EmailSender,
  EmailSendResult,
} from './email-sender.interface';

/**
 * Development email stub: logs the email instead of sending it, so account flows are testable
 * without a provider. Warns (no content) in production, where AWS SES should be configured instead.
 * Selected when EMAIL_PROVIDER is not 'ses'.
 */
@Injectable()
export class LogEmailSender implements EmailSender {
  private readonly logger = new Logger('EmailSender');

  constructor(private readonly config: ConfigService) {}

  send(message: EmailMessage): Promise<EmailSendResult> {
    if (this.config.get<string>('app.env') === 'production') {
      this.logger.warn(
        'No email provider configured (SES) — email not delivered',
      );
    } else {
      this.logger.log(`[DEV] email → ${message.to}: ${message.subject}`);
    }
    return Promise.resolve({ success: true, messageId: 'dev-log' });
  }
}
