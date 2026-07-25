import { Provider } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import { EMAIL_SENDER } from './email-sender.interface';
import { LogEmailSender } from './log-email-sender';
import { SesEmailSender } from './ses-email-sender';

/**
 * Config-driven selection of the email sender: real AWS SES when EMAIL_PROVIDER='ses', otherwise the
 * dev log stub. Same seam pattern as the push sender, so the app boots with no email provider set.
 */
export const emailSenderProvider: Provider = {
  provide: EMAIL_SENDER,
  inject: [ConfigService],
  useFactory: (config: ConfigService) => {
    const provider = config.get<string>('email.provider');
    return provider === 'ses'
      ? new SesEmailSender(config)
      : new LogEmailSender(config);
  },
};
