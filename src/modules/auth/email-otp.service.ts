import { createHmac, randomInt, timingSafeEqual } from 'crypto';

import {
  BadRequestException,
  HttpException,
  HttpStatus,
  Inject,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';

import {
  EMAIL_SENDER,
  type EmailSender,
} from '../notifications/email/email-sender.interface';
import { User } from '../users/entities/user.entity';
import { EmailVerification } from './entities/email-verification.entity';

/**
 * Email codes are always six digits (design build item 08), independent of the phone SMS OTP length.
 */
const EMAIL_OTP_LENGTH = 6;

export interface EmailChallengeResult {
  expiresAt: Date;
  resendAvailableAt: Date;
  /** Present only when AUTH_EXPOSE_OTP is on (non-prod) — for local/e2e testing. */
  devCode?: string;
}

/**
 * Verifies that a user owns an email address, via a 6-digit code (support/contact only — email is
 * NOT a sign-in or recovery credential). Mirrors OtpService; the code is HMAC-hashed, single-use,
 * expiring, attempt-limited and rate-limited. On success the address is written to the user with an
 * `emailVerifiedAt` stamp.
 */
@Injectable()
export class EmailOtpService {
  constructor(
    @InjectRepository(EmailVerification)
    private readonly repo: Repository<EmailVerification>,
    @InjectRepository(User)
    private readonly users: Repository<User>,
    private readonly config: ConfigService,
    @Inject(EMAIL_SENDER) private readonly email: EmailSender,
  ) {}

  /** HMAC the code with the server secret so a DB leak alone can't reveal codes. */
  private hash(userId: string, code: string): string {
    const secret = this.config.get<string>('auth.jwtSecret') ?? '';
    return createHmac('sha256', secret)
      .update(`${userId}:${code}`)
      .digest('hex');
  }

  async request(userId: string, email: string): Promise<EmailChallengeResult> {
    const normalized = email.trim().toLowerCase();

    // An email may belong to at most one account (it is unique on users).
    const existing = await this.users.findOne({ where: { email: normalized } });
    if (existing && existing.id !== userId) {
      throw new BadRequestException('That email is already in use');
    }

    const cooldownMs =
      (this.config.get<number>('auth.otpResendCooldownSeconds') ?? 60) * 1000;
    const latest = await this.repo.findOne({
      where: { userId },
      order: { createdAt: 'DESC' },
    });
    if (latest && !latest.consumedAt) {
      const elapsed = Date.now() - latest.createdAt.getTime();
      if (elapsed < cooldownMs) {
        const wait = Math.ceil((cooldownMs - elapsed) / 1000);
        throw new HttpException(
          `Please wait ${wait}s before requesting another code`,
          HttpStatus.TOO_MANY_REQUESTS,
        );
      }
    }
    // Supersede any prior unconsumed challenge for this user.
    await this.repo.delete({ userId, consumedAt: IsNull() });

    const code = randomInt(0, 10 ** EMAIL_OTP_LENGTH)
      .toString()
      .padStart(EMAIL_OTP_LENGTH, '0');
    const ttlMs = (this.config.get<number>('auth.otpTtlSeconds') ?? 300) * 1000;
    const now = Date.now();
    const expiresAt = new Date(now + ttlMs);

    await this.repo.save(
      this.repo.create({
        userId,
        email: normalized,
        codeHash: this.hash(userId, code),
        expiresAt,
        attempts: 0,
      }),
    );
    await this.email.send({
      to: normalized,
      subject: 'Your Show Up verification code',
      body: `Your Show Up email verification code is ${code}. It expires in 5 minutes.`,
    });

    return {
      expiresAt,
      resendAvailableAt: new Date(now + cooldownMs),
      devCode:
        this.config.get<boolean>('auth.exposeOtp') === true ? code : undefined,
    };
  }

  async verify(userId: string, code: string): Promise<void> {
    const challenge = await this.repo.findOne({
      where: { userId, consumedAt: IsNull() },
      order: { createdAt: 'DESC' },
    });
    if (!challenge) throw new UnauthorizedException('Invalid or expired code');

    if (challenge.expiresAt.getTime() < Date.now()) {
      await this.repo.delete({ id: challenge.id });
      throw new UnauthorizedException('Invalid or expired code');
    }

    const maxAttempts = this.config.get<number>('auth.otpMaxAttempts') ?? 5;
    if (challenge.attempts >= maxAttempts) {
      throw new UnauthorizedException('Too many attempts; request a new code');
    }
    challenge.attempts += 1;

    const expected = Buffer.from(challenge.codeHash);
    const actual = Buffer.from(this.hash(userId, code));
    const matches =
      expected.length === actual.length && timingSafeEqual(expected, actual);
    if (!matches) {
      await this.repo.save(challenge);
      throw new UnauthorizedException('Invalid or expired code');
    }

    challenge.consumedAt = new Date();
    await this.repo.save(challenge);

    // Record the verified email on the user (support/contact — not a sign-in credential).
    await this.users.update(
      { id: userId },
      { email: challenge.email, emailVerifiedAt: new Date() },
    );
  }
}
