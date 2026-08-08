import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { APP_GUARD } from '@nestjs/core';
import { JwtModule } from '@nestjs/jwt';
import { TypeOrmModule } from '@nestjs/typeorm';

import { AnalyticsModule } from '../analytics/analytics.module';
import { UsersModule } from '../users/users.module';
import { AccountController } from './account.controller';
import { AccountService } from './account.service';
import { AuditService } from './audit.service';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { AuditLog } from './entities/audit-log.entity';
import { PhoneVerification } from './entities/phone-verification.entity';
import { RefreshToken } from './entities/refresh-token.entity';
import { FreshAuthGuard } from './guards/fresh-auth.guard';
import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { OtpService } from './otp.service';
import { LogSmsSender } from './sms/log-sms-sender';
import { SMS_SENDER } from './sms/sms-sender.interface';
import { AppleVerifier } from './social/apple-verifier';
import { GoogleVerifier } from './social/google-verifier';
import { TokenService } from './token.service';
import { parseDurationMs } from './util/duration';

@Module({
  imports: [
    UsersModule,
    ConfigModule,
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        secret: config.get<string>('auth.jwtSecret'),
        // expiresIn as seconds (number) avoids the `ms` StringValue typing on plain strings.
        signOptions: {
          expiresIn: Math.floor(
            parseDurationMs(config.get<string>('auth.accessTtl') ?? '15m') /
              1000,
          ),
        },
      }),
    }),
    TypeOrmModule.forFeature([RefreshToken, PhoneVerification, AuditLog]),
    AnalyticsModule,
  ],
  controllers: [AuthController, AccountController],
  providers: [
    AuthService,
    AccountService,
    TokenService,
    OtpService,
    AuditService,
    GoogleVerifier,
    AppleVerifier,
    { provide: SMS_SENDER, useClass: LogSmsSender },
    JwtAuthGuard,
    FreshAuthGuard,
    // Secure-by-default: every route requires a valid token unless marked @Public().
    { provide: APP_GUARD, useClass: JwtAuthGuard },
  ],
})
export class AuthModule {}
