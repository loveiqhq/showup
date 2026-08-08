import { DateStatus } from './date-lifecycle';

/**
 * The date fields needed to decide whether a person is "locked" out of matching. A person is locked
 * from the moment they are matched (a confirmed date exists) until they submit their OWN post-date
 * review — there is no timed auto-release. Cancelled and completed dates never lock.
 */
export interface DateLockRow {
  status: DateStatus;
  userAId: string;
  userBId: string;
  aConfirmedHappened: boolean;
  bConfirmedHappened: boolean;
}

/** Whether this single date locks `userId` out of matching (still confirmed, their side unreviewed). */
export function dateLocksUser(d: DateLockRow, userId: string): boolean {
  if (d.status !== DateStatus.Confirmed) return false;
  if (d.userAId === userId) return !d.aConfirmedHappened;
  if (d.userBId === userId) return !d.bConfirmedHappened;
  return false;
}

/** Whether any of the user's dates lock them out of matching. Pure so it can be unit-tested. */
export function isLockedFromMatching(
  dates: DateLockRow[],
  userId: string,
): boolean {
  return dates.some((d) => dateLocksUser(d, userId));
}
