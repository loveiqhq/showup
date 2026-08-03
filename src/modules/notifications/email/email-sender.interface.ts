/** DI token for the transactional-email sender implementation. */
export const EMAIL_SENDER = Symbol('EMAIL_SENDER');

/** A single email, already localised and addressed. `body` is plain text. */
export interface EmailMessage {
  to: string;
  subject: string;
  body: string;
}

export interface EmailSendResult {
  success: boolean;
  messageId?: string;
  error?: string;
}

/**
 * Sends transactional email (SHOWUP-71). Implemented now by a dev stub that logs; the real AWS SES
 * adapter (eu-west-1) is swapped in via config with no changes to callers.
 */
export interface EmailSender {
  send(message: EmailMessage): Promise<EmailSendResult>;
}
