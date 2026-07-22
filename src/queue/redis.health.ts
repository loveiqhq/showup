import { Inject, Injectable } from '@nestjs/common';
import {
  HealthIndicatorResult,
  HealthIndicatorService,
} from '@nestjs/terminus';
import type { Redis } from 'ioredis';

import { REDIS_CLIENT } from './queue.constants';

/**
 * Readiness check for Redis (SHOWUP-93). Pings the shared client; reported as `up`/`down` so the
 * readiness probe can gate traffic. Reused by the health endpoint's `/ready` route.
 */
@Injectable()
export class RedisHealthIndicator {
  constructor(
    private readonly health: HealthIndicatorService,
    @Inject(REDIS_CLIENT) private readonly redis: Redis,
  ) {}

  async isHealthy(key = 'redis'): Promise<HealthIndicatorResult> {
    const indicator = this.health.check(key);
    try {
      const pong = await this.redis.ping();
      return pong === 'PONG'
        ? indicator.up()
        : indicator.down({ response: pong });
    } catch (error) {
      return indicator.down({ message: (error as Error).message });
    }
  }
}
