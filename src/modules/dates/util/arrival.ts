/**
 * The pure rules of the arrival check-in (Date 03, SHOWUP-195). No I/O: the service passes rows and
 * clocks in, the unit spec covers every branch.
 */

/** Where the person tapped "I'm here" (registry §41). */
export enum ArrivalEntry {
  DateScreen = 'date_screen',
  ChatChip = 'chat_chip',
  Report = 'report',
}

/**
 * Whole minutes from the latest agreed meet time to `at`: negative before it, truncated toward zero.
 * The inverse convention of `minutesBeforeStart`, so 90 s early is -1 and 59 s late is 0.
 */
export function minutesVsStart(meetAt: Date, at: Date): number {
  return Math.trunc((at.getTime() - meetAt.getTime()) / 60_000) || 0;
}

/** Whole seconds from `from` to `now`, never negative (a clock step back reads as 0). */
export function secondsSince(from: Date, now: Date): number {
  return Math.max(0, Math.floor((now.getTime() - from.getTime()) / 1000));
}

/**
 * The partner's arrival as the viewer's payload carries it: only a committed row, and only until the
 * viewer has read the chat at or after the commit (R3). Never a place, only the time.
 */
export function arrivalOf(
  partnerRow: { checkedInAt: Date; committedAt: Date | null } | null,
  viewerReadAt: Date | null,
): { checkedInAt: string } | null {
  if (!partnerRow || !partnerRow.committedAt) return null;
  if (
    viewerReadAt &&
    viewerReadAt.getTime() >= partnerRow.committedAt.getTime()
  ) {
    return null;
  }
  return { checkedInAt: partnerRow.checkedInAt.toISOString() };
}

/**
 * The instant one calendar year before `now` (R14, the retention sweep). 29 February clamps to 28
 * February: `setUTCFullYear` alone would roll to 1 March.
 */
export function retentionCutoff(now: Date): Date {
  const cutoff = new Date(now.getTime());
  const leapDay = cutoff.getUTCMonth() === 1 && cutoff.getUTCDate() === 29;
  if (leapDay) cutoff.setUTCDate(28);
  cutoff.setUTCFullYear(cutoff.getUTCFullYear() - 1);
  return cutoff;
}
