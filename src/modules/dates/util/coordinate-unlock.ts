import { DateStatus } from './date-lifecycle';
import { chatOpensAt } from './pre-date-chat';

/** The fields "is this date's Coordinate due to unlock" needs. */
export interface CoordinateUnlockRow {
  id: string;
  status: DateStatus;
  scheduledAt: Date;
  coordinateUnlockedAt: Date | null;
}

/**
 * The dates whose Coordinate card unlocks now (Date 01, SHOWUP-193): confirmed, meet − 1:30
 * reached, the meet time not yet passed, and never sent before.
 *
 * NOT AFTER THE MEET TIME. A date that started while the job was down, or every date that existed
 * before this job did, would otherwise report an "unlock" hours late -- an event about a moment
 * that never happened on anyone's screen.
 */
export function dueForUnlock(
  rows: readonly CoordinateUnlockRow[],
  now: Date,
): string[] {
  return rows
    .filter(
      (d) =>
        d.status === DateStatus.Confirmed &&
        d.coordinateUnlockedAt === null &&
        chatOpensAt(d.scheduledAt).getTime() <= now.getTime() &&
        d.scheduledAt.getTime() > now.getTime(),
    )
    .map((d) => d.id);
}

/**
 * Whole minutes from `now` to the latest agreed meet time, negative after it, rounded toward zero
 * -- the `minutes_before_start` every 07a event carries.
 */
export function minutesBeforeStart(meetAt: Date, now: Date): number {
  return Math.trunc((meetAt.getTime() - now.getTime()) / 60_000) || 0;
}
