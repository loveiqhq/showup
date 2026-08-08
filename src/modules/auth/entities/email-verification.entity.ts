import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

/**
 * A one-time email verification challenge. Mirrors PhoneVerification, but keyed by the logged-in
 * user (email verification is an authenticated "confirm my email" step, not a way to sign in). The
 * code is stored only as an HMAC hash; one active (unconsumed, unexpired) challenge per user — a new
 * request supersedes the old.
 */
@Entity('email_verifications')
export class EmailVerification {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ type: 'varchar', length: 320 })
  email: string;

  @Column({ name: 'code_hash', type: 'varchar', length: 128 })
  codeHash: string;

  @Column({ name: 'expires_at', type: 'timestamptz' })
  expiresAt: Date;

  @Column({ type: 'int', default: 0 })
  attempts: number;

  @Column({ name: 'consumed_at', type: 'timestamptz', nullable: true })
  consumedAt: Date | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
