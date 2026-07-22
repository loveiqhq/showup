import {
  CHAT_OPENS_MINUTES_BEFORE,
  ChatReason,
  chatOpensAt,
  chatWindowError,
  isChatOpen,
} from './pre-date-chat';

describe('pre-date chat rules (SHOWUP-115)', () => {
  const scheduledAt = new Date('2026-07-14T20:00:00.000Z');

  describe('chatOpensAt', () => {
    it('opens ~1.5 hours (90 min) before the date', () => {
      expect(chatOpensAt(scheduledAt).toISOString()).toBe(
        '2026-07-14T18:30:00.000Z',
      );
    });

    it('derives the offset from the tunable constant', () => {
      const opens = chatOpensAt(scheduledAt).getTime();
      expect(scheduledAt.getTime() - opens).toBe(
        CHAT_OPENS_MINUTES_BEFORE * 60_000,
      );
    });
  });

  describe('isChatOpen', () => {
    it('is closed well before the window', () => {
      expect(isChatOpen(scheduledAt, new Date('2026-07-14T17:00:00Z'))).toBe(
        false,
      );
    });

    it('is closed one minute before it opens', () => {
      expect(isChatOpen(scheduledAt, new Date('2026-07-14T18:29:00Z'))).toBe(
        false,
      );
    });

    it('is open exactly at the opening moment', () => {
      expect(isChatOpen(scheduledAt, new Date('2026-07-14T18:30:00Z'))).toBe(
        true,
      );
    });

    it('stays open right up to the date', () => {
      expect(isChatOpen(scheduledAt, new Date('2026-07-14T19:59:00Z'))).toBe(
        true,
      );
    });

    it('stays open during/after the scheduled time (status ends it, not the clock)', () => {
      expect(isChatOpen(scheduledAt, new Date('2026-07-14T20:30:00Z'))).toBe(
        true,
      );
    });
  });

  describe('chatWindowError', () => {
    it('returns null once the chat is open', () => {
      expect(
        chatWindowError(scheduledAt, new Date('2026-07-14T18:45:00Z')),
      ).toBeNull();
    });

    it('explains when the chat will open if it is too early', () => {
      const error = chatWindowError(
        scheduledAt,
        new Date('2026-07-14T12:00:00Z'),
      );
      expect(error).toContain('opens about 90 minutes before');
      expect(error).toContain('2026-07-14T18:30:00.000Z');
    });
  });

  describe('ChatReason', () => {
    it('has exactly the six preset reasons from the design', () => {
      expect(Object.values(ChatReason).sort()).toEqual(
        [
          'cant_find_you',
          'cant_make_it_today',
          'change_location',
          'change_meet_time',
          'on_my_way',
          'running_late',
        ].sort(),
      );
    });
  });
});
