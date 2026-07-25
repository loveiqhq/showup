import { Injectable } from '@nestjs/common';

import {
  DispatchResult,
  NotificationDispatchService,
} from './notification-dispatch.service';

/**
 * Transactional email API (SHOWUP-71). Thin, well-named methods over the dispatch send-path so that
 * account flows (Epic 2) and safety (Epic 12) call `emailService.sendPasswordReset(...)` rather than
 * hand-assembling message types. All gating, localisation, and logging happen in the dispatcher.
 */
@Injectable()
export class EmailService {
  constructor(private readonly dispatch: NotificationDispatchService) {}

  sendEmailVerification(userId: string, code: string): Promise<DispatchResult> {
    return this.dispatch.sendEmail(userId, 'email_verification', { code });
  }

  sendPasswordReset(userId: string, code: string): Promise<DispatchResult> {
    return this.dispatch.sendEmail(userId, 'password_reset', { code });
  }

  sendAccountClosure(userId: string): Promise<DispatchResult> {
    return this.dispatch.sendEmail(userId, 'account_closure', {});
  }

  sendSafetyAlert(userId: string, detail?: string): Promise<DispatchResult> {
    return this.dispatch.sendEmail(
      userId,
      'safety_alert',
      detail ? { detail } : {},
    );
  }
}
