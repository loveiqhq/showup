import { ForbiddenException, Inject, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import {
  Profile,
  ProfileVerificationStatus,
} from '../profiles/entities/profile.entity';
import { VerificationAttempt } from './entities/verification-attempt.entity';
import { evaluateVerification, VerificationOutcome } from './util/safety';
import { VERIFICATION_PROVIDER } from './verification/verification.provider';
import type { VerificationProvider } from './verification/verification.provider';

/**
 * Selfie verification (SHOWUP-110) — backend scaffolding.
 *
 * The real biometric check is delegated to a swappable {@link VerificationProvider} (currently a
 * stub). This service owns the parts that do not depend on the vendor: recording consent, running
 * the provider, deciding the outcome, discarding the raw capture, keeping only a one-way template,
 * and — on success — marking the profile verified.
 */
@Injectable()
export class VerificationService {
  constructor(
    @Inject(VERIFICATION_PROVIDER)
    private readonly provider: VerificationProvider,
    @InjectRepository(VerificationAttempt)
    private readonly attempts: Repository<VerificationAttempt>,
    @InjectRepository(Profile) private readonly profiles: Repository<Profile>,
  ) {}

  /**
   * Run one verification attempt. Consent must already be given (enforced at the DTO). The raw
   * selfie never reaches this backend and is discarded by the provider after checking; we record
   * that discard time and keep only the non-reversible template.
   */
  async submit(userId: string, consent: boolean): Promise<VerificationAttempt> {
    // Biometric (face) data is GDPR Art. 9 special-category — never process it without consent.
    if (!consent) {
      throw new ForbiddenException('Consent is required to verify');
    }
    const consentGivenAt = new Date();
    const result = await this.provider.verify({ userId });
    const outcome = evaluateVerification({
      livenessPassed: result.livenessPassed,
      faceMatched: result.faceMatched,
    });

    const attempt = await this.attempts.save(
      this.attempts.create({
        userId,
        provider: this.provider.name,
        livenessPassed: result.livenessPassed,
        faceMatched: result.faceMatched,
        outcome,
        matchScore:
          result.matchScore != null ? String(result.matchScore) : null,
        templateHash: result.templateHash ?? null,
        consentGivenAt,
        // Raw capture is discarded immediately after the provider checks it.
        rawDeletedAt: new Date(),
      }),
    );

    if (outcome === VerificationOutcome.Verified) {
      await this.markProfileVerified(userId);
    }
    return attempt;
  }

  /** The user's most recent verification attempt, or null if they have never tried. */
  async latest(userId: string): Promise<VerificationAttempt | null> {
    return this.attempts.findOne({
      where: { userId },
      order: { createdAt: 'DESC' },
    });
  }

  private async markProfileVerified(userId: string): Promise<void> {
    const profile = await this.profiles.findOne({ where: { userId } });
    if (!profile) return;
    profile.verificationStatus = ProfileVerificationStatus.Verified;
    profile.verifiedAt = new Date();
    await this.profiles.save(profile);
  }
}
