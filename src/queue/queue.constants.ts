import type { DefaultJobOptions } from 'bullmq';

/** Injection token for the shared ioredis client (used for health checks and ad-hoc Redis access). */
export const REDIS_CLIENT = Symbol('REDIS_CLIENT');

/** Queue names. One per kind of background chore. */
export const QUEUE_CHECK_IN_EXPIRY = 'check-in-expiry';

/**
 * Default options applied to every job (SHOWUP-94/95):
 *  - `attempts` + exponential `backoff`: a failed job is retried a few times, spaced further apart.
 *  - `removeOnComplete`: completed jobs are pruned so Redis does not fill up.
 *  - `removeOnFail`: failed jobs are kept (longer) so problems can be reviewed, then eventually pruned.
 */
export const DEFAULT_JOB_OPTIONS: DefaultJobOptions = {
  attempts: 3,
  backoff: { type: 'exponential', delay: 5000 },
  removeOnComplete: { count: 100, age: 86_400 }, // keep ~last 100 for a day
  removeOnFail: { count: 1_000, age: 604_800 }, // keep failures for a week
};
