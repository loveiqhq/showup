/** DI token for the SMS sender implementation. */
export const SMS_SENDER = Symbol('SMS_SENDER');

/**
 * Sends transactional SMS. Implemented now by a dev stub that logs the code; Epic 10 swaps in a
 * real provider (e.g. Twilio) with no changes to the auth flow.
 */
export interface SmsSender {
  sendVerificationCode(phone: string, code: string): Promise<void>;
}
