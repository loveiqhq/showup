import { ForbiddenException, Injectable } from '@nestjs/common';

import { AnalyticsService } from '../analytics/analytics.service';
import { accountCreatedEvent } from '../analytics/events/server-events';
import { UserDto } from '../users/dto/user.dto';
import { User, UserStatus } from '../users/entities/user.entity';
import { UsersService } from '../users/users.service';
import { AuditService } from './audit.service';
import { AppleLoginDto } from './dto/apple-login.dto';
import {
  AuthResponseDto,
  OtpChallengeResponseDto,
} from './dto/auth-response.dto';
import { GoogleLoginDto } from './dto/google-login.dto';
import { RefreshDto } from './dto/refresh.dto';
import { RequestOtpDto } from './dto/request-otp.dto';
import { VerifyOtpDto } from './dto/verify-otp.dto';
import { OtpService } from './otp.service';
import { AppleVerifier } from './social/apple-verifier';
import { GoogleVerifier } from './social/google-verifier';
import { SocialIdentity } from './social/social-identity.interface';
import { DeviceMeta, IssuedTokens, TokenService } from './token.service';
import { normalizePhone } from './util/phone';

@Injectable()
export class AuthService {
  constructor(
    private readonly users: UsersService,
    private readonly otp: OtpService,
    private readonly tokens: TokenService,
    private readonly audit: AuditService,
    private readonly google: GoogleVerifier,
    private readonly apple: AppleVerifier,
    private readonly analytics: AnalyticsService,
  ) {}

  // ── Phone OTP ─────────────────────────────────────────────────────────────
  requestPhoneOtp(dto: RequestOtpDto): Promise<OtpChallengeResponseDto> {
    return this.otp.request(normalizePhone(dto.phone));
  }

  async verifyPhoneOtp(
    dto: VerifyOtpDto,
    meta: DeviceMeta,
  ): Promise<AuthResponseDto> {
    const phone = normalizePhone(dto.phone);
    await this.otp.verify(phone, dto.code);

    const existing = await this.users.findByPhone(phone);
    const user = existing
      ? await this.users.markPhoneVerified(existing)
      : await this.users.createFromPhone(phone);
    if (!existing) {
      void this.analytics.trackServerEvent({
        userId: user.id,
        ...accountCreatedEvent('phone'),
      });
    }

    this.ensureUsable(user);
    await this.users.touchLastLogin(user);
    const tokens = await this.tokens.issueTokens(user, {
      deviceId: dto.deviceId,
      userAgent: meta.userAgent,
    });
    await this.audit.record('auth.phone.login', user.id);
    return this.authResponse(tokens, user);
  }

  // ── Social ────────────────────────────────────────────────────────────────
  async loginWithApple(
    dto: AppleLoginDto,
    meta: DeviceMeta,
  ): Promise<AuthResponseDto> {
    const identity = await this.apple.verify(dto.identityToken);
    const user = await this.resolveSocialUser(identity, dto.fullName);
    return this.finishSocialLogin(user, identity, dto.deviceId, meta);
  }

  async loginWithGoogle(
    dto: GoogleLoginDto,
    meta: DeviceMeta,
  ): Promise<AuthResponseDto> {
    const identity = await this.google.verify(dto.idToken);
    const user = await this.resolveSocialUser(identity);
    return this.finishSocialLogin(user, identity, dto.deviceId, meta);
  }

  /** Match by provider id, else link by verified email, else create a new account. */
  private async resolveSocialUser(
    identity: SocialIdentity,
    name?: string,
  ): Promise<User> {
    const existing = await this.users.findByProvider(
      identity.provider,
      identity.providerId,
    );
    if (existing) return existing;

    if (identity.email && identity.emailVerified) {
      const byEmail = await this.users.findByEmail(identity.email);
      if (byEmail) return this.users.linkSocial(byEmail, identity);
    }
    const created = await this.users.createFromSocial(identity, name);
    void this.analytics.trackServerEvent({
      userId: created.id,
      ...accountCreatedEvent(identity.provider),
    });
    return created;
  }

  private async finishSocialLogin(
    user: User,
    identity: SocialIdentity,
    deviceId: string | undefined,
    meta: DeviceMeta,
  ): Promise<AuthResponseDto> {
    this.ensureUsable(user);
    await this.users.touchLastLogin(user);
    const tokens = await this.tokens.issueTokens(user, {
      deviceId,
      userAgent: meta.userAgent,
    });
    await this.audit.record(`auth.${identity.provider}.login`, user.id);
    return this.authResponse(tokens, user);
  }

  // ── Session lifecycle ───────────────────────────────────────────────────────
  async refresh(dto: RefreshDto, meta: DeviceMeta): Promise<AuthResponseDto> {
    const { tokens, user } = await this.tokens.rotate(dto.refreshToken, {
      userAgent: meta.userAgent,
    });
    await this.audit.record('auth.refresh', user.id);
    return this.authResponse(tokens, user);
  }

  logout(refreshToken: string): Promise<void> {
    return this.tokens.revoke(refreshToken);
  }

  private ensureUsable(user: User): void {
    if (
      user.status === UserStatus.Suspended ||
      user.status === UserStatus.Deleted
    ) {
      throw new ForbiddenException('Account is not active');
    }
  }

  private authResponse(tokens: IssuedTokens, user: User): AuthResponseDto {
    return { ...tokens, tokenType: 'Bearer', user: UserDto.from(user) };
  }
}
