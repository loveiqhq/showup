import { BullModule } from '@nestjs/bullmq';
import {
  Global,
  Inject,
  Logger,
  Module,
  OnModuleDestroy,
  Provider,
} from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TerminusModule } from '@nestjs/terminus';
import { Redis } from 'ioredis';

import { DEFAULT_JOB_OPTIONS, REDIS_CLIENT } from './queue.constants';
import { RedisHealthIndicator } from './redis.health';

const logger = new Logger('Redis');

/**
 * Shared ioredis client (SHOWUP-93). Used for the health check and any ad-hoc Redis access. Errors
 * are logged rather than thrown, and ioredis reconnects on its own, so a Redis blip never crashes
 * the app. `maxRetriesPerRequest: null` matches what BullMQ requires of its connections.
 */
const redisClientProvider: Provider = {
  provide: REDIS_CLIENT,
  inject: [ConfigService],
  useFactory: (config: ConfigService) => {
    const client = new Redis({
      host: config.get<string>('redis.host'),
      port: config.get<number>('redis.port'),
      password: config.get<string>('redis.password'),
      maxRetriesPerRequest: null,
      retryStrategy: (times) => Math.min(times * 200, 2000),
    });
    client.on('error', (err) =>
      logger.warn(`connection error: ${err.message}`),
    );
    return client;
  },
};

/**
 * Redis + BullMQ infrastructure (Epic 17). Global so any feature can register a queue with
 * `BullModule.registerQueue({ name })` and inject the shared Redis client / health indicator.
 */
@Global()
@Module({
  imports: [
    TerminusModule,
    BullModule.forRootAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        connection: {
          host: config.get<string>('redis.host'),
          port: config.get<number>('redis.port'),
          password: config.get<string>('redis.password'),
        },
        defaultJobOptions: DEFAULT_JOB_OPTIONS,
      }),
    }),
  ],
  providers: [redisClientProvider, RedisHealthIndicator],
  exports: [BullModule, REDIS_CLIENT, RedisHealthIndicator],
})
export class QueueModule implements OnModuleDestroy {
  constructor(@Inject(REDIS_CLIENT) private readonly redis: Redis) {}

  /** Close the client cleanly on shutdown so tests and deploys don't leave a dangling connection. */
  async onModuleDestroy(): Promise<void> {
    try {
      await this.redis.quit();
    } catch {
      // Already disconnected — nothing to do.
    }
  }
}
