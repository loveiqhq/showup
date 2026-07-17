import {
  DEFAULT_JOB_OPTIONS,
  QUEUE_CHECK_IN_EXPIRY,
  REDIS_CLIENT,
} from './queue.constants';

describe('queue constants', () => {
  it('names the check-in expiry queue', () => {
    expect(QUEUE_CHECK_IN_EXPIRY).toBe('check-in-expiry');
  });

  it('exposes a Redis client injection token', () => {
    expect(typeof REDIS_CLIENT).toBe('symbol');
  });
});

describe('DEFAULT_JOB_OPTIONS', () => {
  it('retries failed jobs with exponential backoff', () => {
    expect(DEFAULT_JOB_OPTIONS.attempts).toBe(3);
    expect(DEFAULT_JOB_OPTIONS.backoff).toEqual({
      type: 'exponential',
      delay: 5000,
    });
  });

  it('trims completed jobs but keeps failed ones for review', () => {
    // Completed jobs are pruned so Redis does not fill up...
    expect(DEFAULT_JOB_OPTIONS.removeOnComplete).toEqual({
      count: 100,
      age: 86_400,
    });
    // ...while failed jobs are retained (longer) so problems can be spotted.
    expect(DEFAULT_JOB_OPTIONS.removeOnFail).toEqual({
      count: 1_000,
      age: 604_800,
    });
  });
});
