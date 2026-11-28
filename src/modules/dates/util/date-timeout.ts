/** Date 09, the +2 h switch (SHOWUP-200): a confirmed date nobody ended closes two hours after its meet time. */
export const TIMEOUT_AFTER_MS = 2 * 60 * 60 * 1000;

/** The most dates one sweep closes; a backlog drains over the next runs, once a minute. */
export const TIMEOUT_SWEEP_LIMIT = 100;

/** Whether the date is due: exactly two hours after the latest agreed meet time counts. */
export function timeoutDue(scheduledAt: Date, now: Date): boolean {
  return now.getTime() - scheduledAt.getTime() >= TIMEOUT_AFTER_MS;
}

/** The `DATE_TIMEOUT_ENABLED` switch: on only for `true` (any case), off by default until flow 10 exists. */
export function dateTimeoutEnabled(flag: string | undefined): boolean {
  return flag?.trim().toLowerCase() === 'true';
}
