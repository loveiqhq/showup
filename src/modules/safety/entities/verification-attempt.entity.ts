import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

import { VerificationOutcome } from '../util/safety';

/**
 * A record of one selfie-verification attempt (SHOWUP-110).
 *
 * Privacy (GDPR Art. 9 — biometric data): the raw selfie/video is never stored here. It is checked
 * by the provider and then discarded; `rawDeletedAt` records when. Only a scrambled, one-way
 * `templateHash` (which cannot be turned back into an image) may be kept, and only after the
 * person's explicit consent, recorded in `consentGivenAt`.
 */
@Entity('verification_attempts')
@Index('idx_verification_attempts_user', ['userId'])
export class VerificationAttempt {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  /** Which provider ran the check (e.g. 'stub', 'aws-rekognition'). */
  @Column({ type: 'varchar', length: 60 })
  provider: string;

  @Column({ name: 'liveness_passed', type: 'boolean' })
  livenessPassed: boolean;

  @Column({ name: 'face_matched', type: 'boolean' })
  faceMatched: boolean;

  @Column({
    type: 'enum',
    enum: VerificationOutcome,
    enumName: 'verification_outcome_enum',
  })
  outcome: VerificationOutcome;

  /** Similarity score from the face comparison, if the provider returns one (0–100). */
  @Column({
    name: 'match_score',
    type: 'numeric',
    precision: 5,
    scale: 2,
    nullable: true,
  })
  matchScore: string | null;

  /** One-way, non-reversible face summary — never the image itself. */
  @Column({
    name: 'template_hash',
    type: 'varchar',
    length: 255,
    nullable: true,
  })
  templateHash: string | null;

  @Column({ name: 'consent_given_at', type: 'timestamptz' })
  consentGivenAt: Date;

  /** When the raw selfie/video was discarded after checking. */
  @Column({ name: 'raw_deleted_at', type: 'timestamptz', nullable: true })
  rawDeletedAt: Date | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
