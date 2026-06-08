import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';

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
})
export class AppModule {}
