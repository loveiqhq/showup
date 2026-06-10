import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import { SmsSender } from './sms-sender.interface';

/**
 * Development SMS stub: logs the verification code so the flow is testable without a provider.
 * Never logs the code in production (no provider wired yet → warns instead). Replaced in Epic 10.
 */
@Injectable()
export class LogSmsSender implements SmsSender {
  private readonly logger = new Logger('SmsSender');

  constructor(private readonly config: ConfigService) {}

  sendVerificationCode(phone: string, code: string): Promise<void> {
    if (this.config.get<string>('app.env') === 'production') {
      this.logger.warn(
        `No SMS provider configured (Epic 10) — cannot deliver code to ${phone}`,
      );
    } else {
      this.logger.log(`[DEV] SMS to ${phone}: your ShowUp code is ${code}`);
    }
    return Promise.resolve();
  }
}
