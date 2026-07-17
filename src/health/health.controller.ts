import { Controller, Get } from '@nestjs/common';
import { ApiOkResponse, ApiTags } from '@nestjs/swagger';
import {
  HealthCheck,
  HealthCheckService,
  TypeOrmHealthIndicator,
} from '@nestjs/terminus';

import { Public } from '../modules/auth/decorators/public.decorator';
import { RedisHealthIndicator } from '../queue/redis.health';

@ApiTags('health')
@Public()
@Controller('health')
export class HealthController {
  constructor(
    private readonly health: HealthCheckService,
    private readonly db: TypeOrmHealthIndicator,
    private readonly redis: RedisHealthIndicator,
  ) {}

  /** Liveness probe — returns 200 whenever the process is up. */
  @Get()
  @HealthCheck()
  @ApiOkResponse({ description: 'Service is alive' })
  liveness() {
    return this.health.check([]);
  }

  /** Readiness probe — pings the database and Redis so orchestrators can gate traffic. */
  @Get('ready')
  @HealthCheck()
  @ApiOkResponse({
    description: 'Service and its dependencies (database, Redis) are ready',
  })
  readiness() {
    return this.health.check([
      () => this.db.pingCheck('database'),
      () => this.redis.isHealthy('redis'),
    ]);
  }
}
