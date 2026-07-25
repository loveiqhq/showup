/** DI token for the push-notification sender implementation. */
export const PUSH_SENDER = Symbol('PUSH_SENDER');

/** A single push notification, already localised and addressed to one device token. */
export interface PushMessage {
  token: string;
  title: string;
  body: string;
  /** Optional key/value payload the client app can act on (e.g. a deep link). */
  data?: Record<string, string>;
}

export interface PushSendResult {
  success: boolean;
  messageId?: string;
  /**
   * True when the provider reports the token is permanently unusable (unregistered / invalid), so
   * the caller should prune it. Distinct from a transient failure, which should NOT prune.
   */
  invalidToken?: boolean;
  error?: string;
}

/**
 * Sends push notifications. Implemented now by a dev stub that logs; a real FCM adapter is swapped
 * in via config with no changes to callers — same seam pattern as the SMS sender and object storage.
 */
export interface PushSender {
  send(message: PushMessage): Promise<PushSendResult>;
}
