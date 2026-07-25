import { Injectable } from '@nestjs/common';

/**
 * Selfie-verification provider seam (SHOWUP-110).
 *
 * The actual biometric check (active liveness + face match to profile photos) is done by an
 * external vendor — e.g. AWS Rekognition Face Liveness in the Ireland region, or another provider.
 * That decision is deferred, so this is a swappable seam: the rest of the app depends only on this
 * interface, and a real provider drops in later without touching the service or controller.
 */
export interface VerificationProviderResult {
  /** The live check confirmed a real, present human (not a photo or replay). */
  livenessPassed: boolean;
  /** The live selfie matched the person's own profile photos. */
  faceMatched: boolean;
  /** Face-comparison similarity score, 0–100, if the provider returns one. */
  matchScore?: number;
  /** A one-way, non-reversible face summary — never the raw image. */
  templateHash?: string;
}

export interface VerificationProviderInput {
  userId: string;
  /**
   * A reference to the captured live selfie/challenge that the provider retrieves and checks.
   * The raw bytes never enter this backend; the provider discards them after checking.
   */
  captureRef?: string;
}

export interface VerificationProvider {
  readonly name: string;
  verify(input: VerificationProviderInput): Promise<VerificationProviderResult>;
}

/** Injection token for the active verification provider. */
export const VERIFICATION_PROVIDER = Symbol('VERIFICATION_PROVIDER');

/**
 * Placeholder provider used until a real biometric vendor is wired. It performs no biometric check;
 * it stands in so the verification flow, records, consent handling, and raw-deletion can be built
 * and tested end-to-end. A real provider replaces this binding.
 */
@Injectable()
export class StubVerificationProvider implements VerificationProvider {
  readonly name = 'stub';

  verify(): Promise<VerificationProviderResult> {
    // No real biometric check — the stub always "passes" so the flow can be exercised. A real
    // provider would return a genuine liveness/match result and a one-way template hash.
    return Promise.resolve({
      livenessPassed: true,
      faceMatched: true,
      matchScore: 99.9,
    });
  }
}
