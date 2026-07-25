import { Provider } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import { FcmPushSender } from './fcm-push-sender';
import { LogPushSender } from './log-push-sender';
import { PUSH_SENDER } from './push-sender.interface';

/**
 * Config-driven selection of the push sender (same idea as `createTravelEstimator`): the real FCM
 * adapter is used only when full Firebase credentials are present; otherwise the dev log stub. This
 * lets the app boot and the whole notification flow work with no Firebase project configured.
 */
export const pushSenderProvider: Provider = {
  provide: PUSH_SENDER,
  inject: [ConfigService],
  useFactory: (config: ConfigService) => {
    const configured =
      !!config.get<string>('fcm.projectId') &&
      !!config.get<string>('fcm.clientEmail') &&
      !!config.get<string>('fcm.privateKey');
    return configured ? new FcmPushSender(config) : new LogPushSender(config);
  },
};
