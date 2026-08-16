import { HealthController } from './health.controller';

/**
 * The load-bearing property of these two endpoints is the DIFFERENCE between them: liveness must not
 * touch any dependency (so a brief database blip never causes the platform to restart the service),
 * while readiness must actually contact the database and Redis (so traffic is only sent to an instance
 * that can serve it). These tests lock that difference in.
 *
 * The fake `check` runs the indicator callbacks it is given, which is what Terminus does, so a failing
 * dependency surfaces here exactly as it would at runtime.
 */
describe('HealthController', () => {
  let health: { check: jest.Mock };
  let db: { pingCheck: jest.Mock };
  let redis: { isHealthy: jest.Mock };

  const make = () =>
    new HealthController(health as any, db as any, redis as any);

  beforeEach(() => {
    health = {
      check: jest.fn(async (checks: Array<() => Promise<unknown>>) => {
        const results = await Promise.all(checks.map((c) => c()));
        return { status: 'ok', details: Object.assign({}, ...results) };
      }),
    };
    db = {
      pingCheck: jest.fn().mockResolvedValue({ database: { status: 'up' } }),
    };
    redis = {
      isHealthy: jest.fn().mockResolvedValue({ redis: { status: 'up' } }),
    };
  });

  describe('liveness (GET /health)', () => {
    it('reports healthy without contacting any dependency', async () => {
      const res: any = await make().liveness();

      expect(res.status).toBe('ok');
      expect(health.check).toHaveBeenCalledWith([]);
      expect(db.pingCheck).not.toHaveBeenCalled();
      expect(redis.isHealthy).not.toHaveBeenCalled();
    });

    it('stays healthy even while the database is unreachable', async () => {
      db.pingCheck.mockRejectedValue(new Error('database down'));

      const res: any = await make().liveness();

      expect(res.status).toBe('ok');
    });
  });

  describe('readiness (GET /health/ready)', () => {
    it('pings both the database and Redis', async () => {
      const res: any = await make().readiness();

      expect(db.pingCheck).toHaveBeenCalledWith('database');
      expect(redis.isHealthy).toHaveBeenCalledWith('redis');
      expect(res.status).toBe('ok');
    });

    it('fails when the database is unreachable', async () => {
      db.pingCheck.mockRejectedValue(new Error('database down'));

      await expect(make().readiness()).rejects.toThrow(/database down/);
    });

    it('fails when Redis is unreachable', async () => {
      redis.isHealthy.mockRejectedValue(new Error('redis down'));

      await expect(make().readiness()).rejects.toThrow(/redis down/);
    });
  });
});
