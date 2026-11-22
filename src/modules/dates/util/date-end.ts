import { DateStatus } from './date-lifecycle';

/** How one person ended the date from a Date 06 path (registry §43 `date_end_reason`). */
export enum EndReason {
  Met = 'met',
  PartnerNoShow = 'partner_no_show',
  Uncomfortable = 'uncomfortable',
}

/** Where an end started (registry §45 `end_entry`). */
export enum EndEntry {
  EndDateBar = 'end_date_bar',
  CheckinBarLink = 'checkin_bar_link',
  Report = 'report',
}

/** The Date 08 screen the match lands on after a Date 06 end. */
export type PartnerScreen =
  | 'date_closed_positive'
  | 'you_no_showed'
  | 'date_ended_discomfort';

/** Why an end is refused; each is answered as a 409 with this body `code`. */
export type RefusalCode =
  | 'date_not_active'
  | 'before_meet'
  | 'not_checked_in'
  | 'checked_in';

/**
 * Whether Date 06's end and discomfort routes and Date 07's cancel are on (R1). `flag` is the
 * `DATE_ENDS_ENABLED` variable: `true` / `false` win when set; unset means on everywhere, production
 * included (Esma, 22 Nov 2026: shown before flow 10 and Date 08 exist, an end lands on Search until
 * then). `false` is the one way to hide them.
 */
export function dateEndsEnabled(env: {
  flag?: string;
  nodeEnv?: string;
}): boolean {
  const flag = env.flag?.trim().toLowerCase();
  if (flag === 'true') return true;
  if (flag === 'false') return false;
  return true;
}

/**
 * The match's Date 08 screen, from `end_reason` ALONE (17 Nov 2026): a no-show end is the ender's
 * claim, so it shows `you_no_showed` whether or not the match checked in.
 */
export function partnerScreenFor(reason: EndReason): PartnerScreen {
  switch (reason) {
    case EndReason.Met:
      return 'date_closed_positive';
    case EndReason.PartnerNoShow:
      return 'you_no_showed';
    case EndReason.Uncomfortable:
      return 'date_ended_discomfort';
  }
}

/**
 * The first rule an end breaks, or null. The date must still be confirmed; a no-show end waits for
 * the agreed meet time; a no-show or uncomfortable end needs the ender's committed check-in. `We met`
 * needs neither (two people who both confirm in the review count as met).
 */
export function endRefusal(input: {
  status: DateStatus;
  now: Date;
  meetAt: Date;
  reason: EndReason;
  enderCheckedIn: boolean;
}): RefusalCode | null {
  if (input.status !== DateStatus.Confirmed) return 'date_not_active';
  if (
    input.reason === EndReason.PartnerNoShow &&
    input.now.getTime() < input.meetAt.getTime()
  ) {
    return 'before_meet';
  }
  if (input.reason !== EndReason.Met && !input.enderCheckedIn) {
    return 'not_checked_in';
  }
  return null;
}

/**
 * Whole minutes from the latest agreed meet time to `now`, rounded toward zero: negative before it
 * (family N's `minutes_vs_start`).
 */
export function minutesVsStart(meetAt: Date, now: Date): number {
  return Math.trunc((now.getTime() - meetAt.getTime()) / 60_000) || 0;
}
