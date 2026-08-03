import { SendEmailCommand, SESv2Client } from '@aws-sdk/client-sesv2';
import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import {
  EmailMessage,
  EmailSender,
  EmailSendResult,
} from './email-sender.interface';

/**
 * Real transactional-email adapter backed by AWS SES v2 in eu-west-1 (SHOWUP-71) — same region as
 * the face tools, so recipient data stays in the EEA. Selected only when EMAIL_PROVIDER='ses'.
 * Credentials come from the config, or (when omitted) the AWS SDK's default credential chain.
 */
@Injectable()
export class SesEmailSender implements EmailSender {
  private readonly logger = new Logger('EmailSender');
  private readonly client: SESv2Client;
  private readonly from: string;
  private readonly replyTo?: string;

  constructor(config: ConfigService) {
    const accessKeyId = config.get<string>('email.ses.accessKeyId');
    const secretAccessKey = config.get<string>('email.ses.secretAccessKey');
    this.client = new SESv2Client({
      region: config.get<string>('email.ses.region') ?? 'eu-west-1',
      ...(accessKeyId && secretAccessKey
        ? { credentials: { accessKeyId, secretAccessKey } }
        : {}),
    });
    this.from = config.get<string>('email.from') ?? 'no-reply@showup.app';
    this.replyTo = config.get<string>('email.replyTo') ?? undefined;
  }

  async send(message: EmailMessage): Promise<EmailSendResult> {
    try {
      const out = await this.client.send(
        new SendEmailCommand({
          FromEmailAddress: this.from,
          Destination: { ToAddresses: [message.to] },
          ...(this.replyTo ? { ReplyToAddresses: [this.replyTo] } : {}),
          Content: {
            Simple: {
              Subject: { Data: message.subject, Charset: 'UTF-8' },
              Body: { Text: { Data: message.body, Charset: 'UTF-8' } },
            },
          },
        }),
      );
      return { success: true, messageId: out.MessageId };
    } catch (error) {
      this.logger.warn(`SES send failed: ${(error as Error).message}`);
      return { success: false, error: (error as Error).message };
    }
  }
}
