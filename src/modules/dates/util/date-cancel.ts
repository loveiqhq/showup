import { type RefusalCode } from './date-end';
import { DateStatus } from './date-lifecycle';

/**
 * Hours from `now` to the latest agreed meet time, two decimals; negative after it (Date 07, stored
 * as `hours_notice`). Stored for analysis only: no rule reads it, the penalty is the same at any time.
 */
export function hoursNotice(now: Date, meetAt: Date): number {
  const hours = (meetAt.getTime() - now.getTime()) / 3_600_000;
  return Math.round(hours * 100) / 100 || 0;
}

/**
 * The first rule a cancel breaks, or null (R7). Only a `confirmed` date can be cancelled, so a second
 * or simultaneous cancel (or an end that won) is `date_not_active`; a person who has a committed
 * check-in ends the date through Date 06 instead, so is `checked_in`.
 */
export function cancelRefusal(input: {
  status: DateStatus;
  cancellerCheckedIn: boolean;
}): RefusalCode | null {
  if (input.status !== DateStatus.Confirmed) return 'date_not_active';
  if (input.cancellerCheckedIn) return 'checked_in';
  return null;
}
