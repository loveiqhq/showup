import { ChatReason } from './pre-date-chat';

/**
 * Who said "I can't make it today", seen from the person ending the date in the chat (Date 02's
 * `cant_make_it_from`): `self` when they sent the most recent `cant_make_it` message, `match` when the
 * other person did, null when the thread has none -- and then the date cannot be ended from the chat.
 * `messages` oldest first, as the thread is stored.
 */
export function cantMakeItFrom(
  messages: ReadonlyArray<{ senderId: string; reason: ChatReason | null }>,
  enderId: string,
): 'self' | 'match' | null {
  for (let i = messages.length - 1; i >= 0; i--) {
    if (messages[i].reason === ChatReason.CantMakeIt) {
      return messages[i].senderId === enderId ? 'self' : 'match';
    }
  }
  return null;
}
