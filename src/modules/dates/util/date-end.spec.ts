import { DateStatus } from './date-lifecycle';
import {
  CLIENT_END_ENTRIES,
  CLIENT_END_REASONS,
  EndEntry,
  EndReason,
  dateEndsEnabled,
  endRefusal,
  minutesVsStart,
  partnerScreenFor,
} from './date-end';

describe('dateEndsEnabled (R1)', () => {
  it('lets true and false win, whatever the environment', () => {
    expect(dateEndsEnabled({ flag: 'true', nodeEnv: 'production' })).toBe(true);
    expect(dateEndsEnabled({ flag: 'false', nodeEnv: 'development' })).toBe(
      false,
    );
  });
  it('reads the flag case-insensitively', () => {
    expect(dateEndsEnabled({ flag: 'TRUE', nodeEnv: 'production' })).toBe(true);
    expect(dateEndsEnabled({ flag: 'False', nodeEnv: 'staging' })).toBe(false);
  });
  it('is on everywhere when unset, production included (22 Nov)', () => {
    expect(dateEndsEnabled({ nodeEnv: 'production' })).toBe(true);
    expect(dateEndsEnabled({ nodeEnv: 'staging' })).toBe(true);
    expect(dateEndsEnabled({ nodeEnv: 'development' })).toBe(true);
    expect(dateEndsEnabled({})).toBe(true);
    expect(dateEndsEnabled({ flag: '', nodeEnv: 'production' })).toBe(true);
  });
});

describe('partnerScreenFor (end_reason alone, 17 Nov)', () => {
  it('maps each reason to the match screen', () => {
    expect(partnerScreenFor(EndReason.Met)).toBe('date_closed_positive');
    expect(partnerScreenFor(EndReason.PartnerNoShow)).toBe('you_no_showed');
    expect(partnerScreenFor(EndReason.Uncomfortable)).toBe(
      'date_ended_discomfort',
    );
  });
});

describe('Date 09 (timed out)', () => {
  it('maps a timed-out end to the Date 09 screen', () => {
    expect(partnerScreenFor(EndReason.TimedOut)).toBe('date_timed_out');
    expect(EndReason.TimedOut).toBe('timed_out');
    expect(EndEntry.AutoSwitch).toBe('auto_switch');
  });
  it('keeps the system values out of what a client may post', () => {
    expect([...CLIENT_END_REASONS]).toEqual([
      'met',
      'partner_no_show',
      'uncomfortable',
    ]);
    expect([...CLIENT_END_ENTRIES]).toEqual([
      'end_date_bar',
      'checkin_bar_link',
      'report',
    ]);
  });
});

describe('endRefusal (R7)', () => {
  const meetAt = new Date('2026-11-21T19:00:00Z');
  const before = new Date('2026-11-21T18:59:00Z');
  const at = new Date('2026-11-21T19:00:00Z');
  const base = {
    status: DateStatus.Confirmed,
    now: at,
    meetAt,
    reason: EndReason.Met,
    enderCheckedIn: false,
  };

  it('refuses a date that is not confirmed', () => {
    for (const status of [
      DateStatus.Cancelled,
      DateStatus.Ended,
      DateStatus.EndedInChat,
      DateStatus.Completed,
    ]) {
      expect(endRefusal({ ...base, status })).toBe('date_not_active');
    }
  });
  it('lets We met end with no check-in, before or after the time', () => {
    expect(endRefusal(base)).toBeNull();
    expect(endRefusal({ ...base, now: before })).toBeNull();
  });
  it('refuses a no-show end before the agreed time, then needs a check-in', () => {
    const noShow = { ...base, reason: EndReason.PartnerNoShow };
    expect(endRefusal({ ...noShow, now: before, enderCheckedIn: true })).toBe(
      'before_meet',
    );
    expect(endRefusal({ ...noShow, now: before })).toBe('before_meet');
    expect(endRefusal(noShow)).toBe('not_checked_in');
    expect(endRefusal({ ...noShow, enderCheckedIn: true })).toBeNull();
  });
  it('needs a check-in for an uncomfortable end, at any time', () => {
    const u = { ...base, reason: EndReason.Uncomfortable, now: before };
    expect(endRefusal(u)).toBe('not_checked_in');
    expect(endRefusal({ ...u, enderCheckedIn: true })).toBeNull();
  });
});

describe('minutesVsStart', () => {
  const meetAt = new Date('2026-11-21T19:00:00Z');
  it('is negative before the agreed time and positive after', () => {
    expect(minutesVsStart(meetAt, new Date('2026-11-21T18:30:00Z'))).toBe(-30);
    expect(minutesVsStart(meetAt, new Date('2026-11-21T19:10:30Z'))).toBe(10);
    expect(minutesVsStart(meetAt, meetAt)).toBe(0);
  });
});
