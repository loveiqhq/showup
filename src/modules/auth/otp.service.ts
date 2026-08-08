import { createHmac, randomInt, timingSafeEqual } from 'crypto';

import {
  HttpException,
  HttpStatus,
  Inject,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';

import { PhoneVerification } from './entities/phone-verification.entity';
import { SMS_SENDER, type SmsSender } from './sms/sms-sender.interface';

export interface OtpChallengeResult {
  expiresAt: Date;
  resendAvailableAt: Date;
  /** Present only when AUTH_EXPOSE_OTP is on (non-prod) — for local/e2e testing. */
  devCode?: string;
}

@Injectable()
export class OtpService {
  constructor(
    @InjectRepository(PhoneVerification)
    private readonly repo: Repository<PhoneVerification>,
    private readonly config: ConfigService,
    @Inject(SMS_SENDER) private readonly sms: SmsSender,
  ) {}

  /** HMAC the code with the server secret so a DB leak alone can't reveal codes. */
  private hash(phone: string, code: string): string {
    const secret = this.config.get<string>('auth.jwtSecret') ?? '';
    return createHmac('sha256', secret)
      .update(`${phone}:${code}`)
      .digest('hex');
  }

  async request(phone: string): Promise<OtpChallengeResult> {
    const cooldownMs =
      (this.config.get<number>('auth.otpResendCooldownSeconds') ?? 60) * 1000;
    const latest = await this.repo.findOne({
      where: { phone },
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
    // Supersede any prior unconsumed challenge for this phone.
    await this.repo.delete({ phone, consumedAt: IsNull() });

    const length = this.config.get<number>('auth.otpLength') ?? 6;
    const code = randomInt(0, 10 ** length)
      .toString()
      .padStart(length, '0');
    const ttlMs = (this.config.get<number>('auth.otpTtlSeconds') ?? 300) * 1000;
    const now = Date.now();
    const expiresAt = new Date(now + ttlMs);

    await this.repo.save(
      this.repo.create({
        phone,
        codeHash: this.hash(phone, code),
        expiresAt,
        attempts: 0,
      }),
    );
    await this.sms.sendVerificationCode(phone, code);

    return {
      expiresAt,
      resendAvailableAt: new Date(now + cooldownMs),
      devCode:
        this.config.get<boolean>('auth.exposeOtp') === true ? code : undefined,
    };
  }

  async verify(phone: string, code: string): Promise<void> {
    const challenge = await this.repo.findOne({
      where: { phone, consumedAt: IsNull() },
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
    const actual = Buffer.from(this.hash(phone, code));
    const matches =
      expected.length === actual.length && timingSafeEqual(expected, actual);
    if (!matches) {
      await this.repo.save(challenge);
      throw new UnauthorizedException('Invalid or expired code');
    }

    challenge.consumedAt = new Date();
    await this.repo.save(challenge);
  }
}
