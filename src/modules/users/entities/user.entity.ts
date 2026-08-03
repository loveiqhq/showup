import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

import { ModerationStanding } from '../../safety/util/safety';

/**
 * Account lifecycle states (Story 2.1). `deletion_pending` and `deleted` drive the deletion flow
 * (Story 2.6); `suspended`/`deleted` users are rejected at login and by the auth guard.
 */
export enum UserStatus {
  Registered = 'registered',
  Verified = 'verified',
  Active = 'active',
  Restricted = 'restricted',
  Suspended = 'suspended',
  DeletionPending = 'deletion_pending',
  Deleted = 'deleted',
}

/** Authorization role. Minimal admin support (seeds Epic 19). */
export enum UserRole {
  User = 'user',
  Admin = 'admin',
}

@Entity('users')
export class User {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  // Passwordless: identity is a verified phone and/or a linked Apple/Google account.
  // Nullable + UNIQUE — Postgres allows many NULLs, so social-only users (no phone) are fine.
  @Column({ type: 'varchar', length: 32, unique: true, nullable: true })
  phone: string | null;

  @Column({ name: 'phone_verified_at', type: 'timestamptz', nullable: true })
  phoneVerifiedAt: Date | null;

  @Column({ type: 'varchar', length: 320, unique: true, nullable: true })
  email: string | null;

  @Column({ name: 'email_verified_at', type: 'timestamptz', nullable: true })
  emailVerifiedAt: Date | null;

  @Column({
    name: 'apple_user_id',
    type: 'varchar',
    length: 255,
    unique: true,
    nullable: true,
  })
  appleUserId: string | null;

  @Column({
    name: 'google_user_id',
    type: 'varchar',
    length: 255,
    unique: true,
    nullable: true,
  })
  googleUserId: string | null;

  @Column({
    name: 'display_name',
    type: 'varchar',
    length: 120,
    nullable: true,
  })
  displayName: string | null;

  @Column({
    type: 'enum',
    enum: UserStatus,
    enumName: 'users_status_enum',
    default: UserStatus.Registered,
  })
  status: UserStatus;

  @Column({
    type: 'enum',
    enum: UserRole,
    enumName: 'users_role_enum',
    default: UserRole.User,
  })
  role: UserRole;

  /**
   * Safety standing (Epic 12, SHOWUP-79). Distinct from `status` (account lifecycle): a `limited`
   * or `banned` standing hides the user from discovery and matching without touching login.
   */
  @Column({
    name: 'moderation_standing',
    type: 'enum',
    enum: ModerationStanding,
    enumName: 'moderation_standing_enum',
    default: ModerationStanding.Active,
  })
  moderationStanding: ModerationStanding;

  @Column({ name: 'last_login_at', type: 'timestamptz', nullable: true })
  lastLoginAt: Date | null;

  /**
   * Preferred language for notifications/emails (Epic 10). Stored (not derived from a request
   * header) so background jobs — which have no HTTP request — can localise messages. Free-form
   * varchar with an 'en' default; the message catalog falls back to English for anything it lacks.
   */
  @Column({ type: 'varchar', length: 8, default: 'en' })
  locale: string;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;

  @Column({
    name: 'deletion_requested_at',
    type: 'timestamptz',
    nullable: true,
  })
  deletionRequestedAt: Date | null;

  @Column({ name: 'deleted_at', type: 'timestamptz', nullable: true })
  deletedAt: Date | null;
}
