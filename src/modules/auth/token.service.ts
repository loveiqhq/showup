import { createHash, randomBytes } from 'crypto';

import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';

import { User, UserStatus } from '../users/entities/user.entity';
import { UsersService } from '../users/users.service';
import { RefreshToken } from './entities/refresh-token.entity';
import { parseDurationMs } from './util/duration';

export interface DeviceMeta {
  deviceId?: string | null;
  userAgent?: string | null;
}

export interface IssuedTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number; // access-token lifetime, seconds
}

@Injectable()
export class TokenService {
  constructor(
    @InjectRepository(RefreshToken)
    private readonly refreshTokens: Repository<RefreshToken>,
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
    private readonly users: UsersService,
  ) {}

  private hashToken(raw: string): string {
    return createHash('sha256').update(raw).digest('hex');
  }

  async issueTokens(user: User, meta: DeviceMeta = {}): Promise<IssuedTokens> {
    const accessToken = await this.jwt.signAsync({ sub: user.id });
    const accessTtl = this.config.get<string>('auth.accessTtl') ?? '15m';
    const refreshTtl = this.config.get<string>('auth.refreshTtl') ?? '30d';
    const raw = randomBytes(32).toString('base64url');

    await this.refreshTokens.save(
      this.refreshTokens.create({
        userId: user.id,
        tokenHash: this.hashToken(raw),
        deviceId: meta.deviceId ?? null,
        userAgent: meta.userAgent ?? null,
        expiresAt: new Date(Date.now() + parseDurationMs(refreshTtl)),
      }),
    );

    return {
      accessToken,
      refreshToken: raw,
      expiresIn: Math.floor(parseDurationMs(accessTtl) / 1000),
    };
  }

  /** Exchange a refresh token for a new pair, rotating (revoking) the old one. */
  async rotate(
    rawRefresh: string,
    meta: DeviceMeta = {},
  ): Promise<{ tokens: IssuedTokens; user: User }> {
    const existing = await this.refreshTokens.findOne({
      where: { tokenHash: this.hashToken(rawRefresh) },
    });
    if (!existing) throw new UnauthorizedException('Invalid refresh token');

    if (existing.revokedAt) {
      // Reuse of an already-rotated token ⇒ likely theft: revoke the whole family.
      await this.revokeAllForUser(existing.userId);
      throw new UnauthorizedException('Refresh token already used');
    }
    if (existing.expiresAt.getTime() < Date.now()) {
      existing.revokedAt = new Date();
      await this.refreshTokens.save(existing);
      throw new UnauthorizedException('Refresh token expired');
    }

    const user = await this.users.findById(existing.userId);
    if (
      !user ||
      user.status === UserStatus.Suspended ||
      user.status === UserStatus.Deleted
    ) {
      throw new UnauthorizedException('Account not active');
    }

    const tokens = await this.issueTokens(user, {
      deviceId: existing.deviceId,
      userAgent: meta.userAgent ?? existing.userAgent,
    });
    const successor = await this.refreshTokens.findOne({
      where: { tokenHash: this.hashToken(tokens.refreshToken) },
    });
    existing.revokedAt = new Date();
    existing.rotatedToId = successor?.id ?? null;
    await this.refreshTokens.save(existing);

    return { tokens, user };
  }

  async revoke(rawRefresh: string): Promise<void> {
    const existing = await this.refreshTokens.findOne({
      where: { tokenHash: this.hashToken(rawRefresh) },
    });
    if (existing && !existing.revokedAt) {
      existing.revokedAt = new Date();
      await this.refreshTokens.save(existing);
    }
  }

  async revokeAllForUser(userId: string): Promise<void> {
    await this.refreshTokens.update(
      { userId, revokedAt: IsNull() },
      { revokedAt: new Date() },
    );
  }
}
