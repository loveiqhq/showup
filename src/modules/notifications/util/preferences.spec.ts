import {
  categoryForType,
  DEFAULT_PREFERENCES,
  isCriticalType,
  NotificationPreferenceState,
  shouldSend,
} from './preferences';

const prefs = (
  over: Partial<NotificationPreferenceState> = {},
): NotificationPreferenceState => ({ ...DEFAULT_PREFERENCES, ...over });

describe('notification preferences (pure gating logic)', () => {
  describe('DEFAULT_PREFERENCES', () => {
    it('has marketing OFF and essential/engagement ON by default (SHOWUP-66)', () => {
      expect(DEFAULT_PREFERENCES.marketing).toBe(false);
      expect(DEFAULT_PREFERENCES.essential).toBe(true);
      expect(DEFAULT_PREFERENCES.engagement).toBe(true);
    });
  });

  describe('categoryForType', () => {
    it('maps reminders to engagement', () => {
      expect(categoryForType('date_reminder')).toBe('engagement');
      expect(categoryForType('check_in_expiry')).toBe('engagement');
      expect(categoryForType('new_match')).toBe('engagement');
    });
    it('maps account/safety messages to essential', () => {
      expect(categoryForType('password_reset')).toBe('essential');
      expect(categoryForType('email_verification')).toBe('essential');
      expect(categoryForType('safety_alert')).toBe('essential');
    });
    it('maps marketing_* to marketing', () => {
      expect(categoryForType('marketing_generic')).toBe('marketing');
    });
  });

  describe('isCriticalType', () => {
    it('treats account/safety messages as critical (always-send)', () => {
      expect(isCriticalType('password_reset')).toBe(true);
      expect(isCriticalType('email_verification')).toBe(true);
      expect(isCriticalType('account_closure')).toBe(true);
      expect(isCriticalType('safety_alert')).toBe(true);
      expect(isCriticalType('security_alert')).toBe(true);
    });
    it('treats reminders and marketing as non-critical', () => {
      expect(isCriticalType('date_reminder')).toBe(false);
      expect(isCriticalType('check_in_expiry')).toBe(false);
      expect(isCriticalType('marketing_generic')).toBe(false);
    });
  });

  describe('shouldSend', () => {
    it('sends engagement messages when the toggle is on (default)', () => {
      expect(shouldSend('date_reminder', prefs())).toBe(true);
    });
    it('suppresses engagement messages when the toggle is off', () => {
      expect(shouldSend('check_in_expiry', prefs({ engagement: false }))).toBe(
        false,
      );
    });
    it('never sends marketing without consent (default off)', () => {
      expect(shouldSend('marketing_generic', prefs())).toBe(false);
    });
    it('sends marketing once consent is given', () => {
      expect(shouldSend('marketing_generic', prefs({ marketing: true }))).toBe(
        true,
      );
    });
    it('ALWAYS sends critical messages, even if every toggle is off', () => {
      const allOff = prefs({
        essential: false,
        engagement: false,
        marketing: false,
      });
      expect(shouldSend('password_reset', allOff)).toBe(true);
      expect(shouldSend('safety_alert', allOff)).toBe(true);
    });
  });
});
