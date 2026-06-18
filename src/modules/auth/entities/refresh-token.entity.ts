import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

/**
 * A refresh token (stored only as a SHA-256 hash, never in plaintext). Rotated on every use:
 * the old row is revoked and `rotatedToId` points at its successor, so a replayed/stolen token
 * that was already rotated is detected and the whole chain can be revoked.
 */
@Entity('refresh_tokens')
export class RefreshToken {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ name: 'token_hash', type: 'varchar', length: 64, unique: true })
  tokenHash: string;

  @Column({ name: 'device_id', type: 'varchar', length: 255, nullable: true })
  deviceId: string | null;

  @Column({ name: 'user_agent', type: 'varchar', length: 512, nullable: true })
  userAgent: string | null;

  @Column({ name: 'expires_at', type: 'timestamptz' })
  expiresAt: Date;

  @Column({ name: 'revoked_at', type: 'timestamptz', nullable: true })
  revokedAt: Date | null;

  @Column({ name: 'rotated_to_id', type: 'uuid', nullable: true })
  rotatedToId: string | null;

  // When the user last truly authenticated (phone OTP / social). Carried across rotation so that
  // refreshing a session does NOT count as re-authentication — used for step-up on sensitive ops.
  @Column({ name: 'auth_time', type: 'timestamptz' })
  authTime: Date;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
