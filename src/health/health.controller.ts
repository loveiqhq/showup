import { Controller, Get } from '@nestjs/common';
import { ApiOkResponse, ApiTags } from '@nestjs/swagger';
import {
  HealthCheck,
  HealthCheckService,
  TypeOrmHealthIndicator,
} from '@nestjs/terminus';

import { Public } from '../modules/auth/decorators/public.decorator';

@ApiTags('health')
@Public()
@Controller('health')
export class HealthController {
  constructor(
    private readonly health: HealthCheckService,
    private readonly db: TypeOrmHealthIndicator,
  ) {}

  /** Liveness probe — returns 200 whenever the process is up. */
  @Get()
  @HealthCheck()
  @ApiOkResponse({ description: 'Service is alive' })
  liveness() {
    return this.health.check([]);
  }

  /** Readiness probe — also pings the database so orchestrators can gate traffic. */
  @Get('ready')
  @HealthCheck()
  @ApiOkResponse({
    description: 'Service and its dependencies (database) are ready',
  })
  readiness() {
    return this.health.check([() => this.db.pingCheck('database')]);
  }
}
