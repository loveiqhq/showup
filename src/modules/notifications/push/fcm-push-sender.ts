import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { App, cert, getApps, initializeApp } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';

import {
  PushMessage,
  PushSender,
  PushSendResult,
} from './push-sender.interface';

const FIREBASE_APP_NAME = 'showup-notifications';

/**
 * FCM error codes that mean the token is permanently unusable and should be pruned (SHOWUP-68).
 * Transient/other errors are retried by the queue and must NOT trigger a prune.
 */
const INVALID_TOKEN_CODES: ReadonlySet<string> = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);

/** Pure classifier: does this FCM error code mean "drop this token"? Exported for unit testing. */
export function isInvalidTokenErrorCode(code: string | undefined): boolean {
  return code != null && INVALID_TOKEN_CODES.has(code);
}

/**
 * Real push adapter backed by Firebase Cloud Messaging (SHOWUP-68). Selected only when Firebase
 * service-account credentials are configured; otherwise the log stub is used. Reuses a single named
 * Firebase app so repeated construction never re-initialises.
 */
@Injectable()
export class FcmPushSender implements PushSender {
  private readonly logger = new Logger('PushSender');
  private readonly app: App;

  constructor(config: ConfigService) {
    const projectId = config.get<string>('fcm.projectId');
    const clientEmail = config.get<string>('fcm.clientEmail');
    // Env-encoded private keys carry literal "\n"; restore real newlines for the PEM parser.
    const privateKey = config
      .get<string>('fcm.privateKey')
      ?.replace(/\\n/g, '\n');

    const existing = getApps().find((a) => a.name === FIREBASE_APP_NAME);
    this.app =
      existing ??
      initializeApp(
        { credential: cert({ projectId, clientEmail, privateKey }) },
        FIREBASE_APP_NAME,
      );
  }

  async send(message: PushMessage): Promise<PushSendResult> {
    try {
      const messageId = await getMessaging(this.app).send({
        token: message.token,
        notification: { title: message.title, body: message.body },
        data: message.data,
      });
      return { success: true, messageId };
    } catch (error) {
      const code = (error as { code?: string }).code;
      const invalidToken = isInvalidTokenErrorCode(code);
      if (!invalidToken) {
        this.logger.warn(`FCM send failed (${code ?? 'unknown'})`);
      }
      return {
        success: false,
        invalidToken,
        error: code ?? (error as Error).message,
      };
    }
  }
}
