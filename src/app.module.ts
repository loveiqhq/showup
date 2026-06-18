import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { APP_GUARD } from '@nestjs/core';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';

import configuration from './config/configuration';
import { validationSchema } from './config/env.validation';
import { DatabaseModule } from './database/database.module';
import { HealthModule } from './health/health.module';
import { LocationModule } from './modules/location/location.module';

// Domain module placeholders — wired now so future epics drop straight in.
import { AuthModule } from './modules/auth/auth.module';
import { UsersModule } from './modules/users/users.module';
import { ProfilesModule } from './modules/profiles/profiles.module';
import { CheckInsModule } from './modules/check-ins/check-ins.module';
import { MatchingModule } from './modules/matching/matching.module';
import { DatesModule } from './modules/dates/dates.module';
import { PaymentsModule } from './modules/payments/payments.module';
import { NotificationsModule } from './modules/notifications/notifications.module';
import { AnalyticsModule } from './modules/analytics/analytics.module';
import { SafetyModule } from './modules/safety/safety.module';
import { AdminModule } from './modules/admin/admin.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      load: [configuration],
      validationSchema,
      validationOptions: { abortEarly: false },
      envFilePath: ['.env'],
    }),
    ThrottlerModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        throttlers: [
          {
            ttl: config.get<number>('auth.throttleTtlMs') ?? 60_000,
            limit: config.get<number>('auth.throttleLimit') ?? 60,
          },
        ],
      }),
    }),
    DatabaseModule,
    HealthModule,
    LocationModule,
    // Domain placeholders (Epics 2+)
    AuthModule,
    UsersModule,
    ProfilesModule,
    CheckInsModule,
    MatchingModule,
    DatesModule,
    PaymentsModule,
    NotificationsModule,
    AnalyticsModule,
    SafetyModule,
    AdminModule,
  ],
  providers: [
    // Global rate limiting (Epic 2). Stricter per-route limits via @Throttle on auth endpoints.
    { provide: APP_GUARD, useClass: ThrottlerGuard },
  ],
})
export class AppModule {}
