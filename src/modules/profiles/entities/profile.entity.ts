import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

import { ModerationStanding } from '../../safety/util/safety';
import {
  DATING_LANGUAGES,
  EDUCATIONS,
  GENDERS,
  ORIENTATIONS,
  POLITICS,
  RELIGIONS,
} from '../util/profile-details';
import type {
  DatingLanguage,
  Education,
  Gender,
  Orientation,
  Politics,
  Religion,
} from '../util/profile-details';

/** Whether a profile/user has passed identity verification (trust & safety). */
export enum ProfileVerificationStatus {
  None = 'none',
  Pending = 'pending',
  Verified = 'verified',
  Rejected = 'rejected',
}

/**
 * A user's dating profile — the information shown in discovery. One profile per user.
 * Hidden from discovery when `isVisible` is false or the owning account is suspended/deleted
 * (the latter is enforced where profiles are surfaced, e.g. discovery in Epic 6).
 */
@Entity('profiles')
export class Profile {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index({ unique: true })
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ name: 'display_name', type: 'varchar', length: 80, nullable: true })
  displayName: string | null;

  // Stored as a date (YYYY-MM-DD); age is derived. Must be 18+ (enforced in the service).
  @Column({ name: 'date_of_birth', type: 'date', nullable: true })
  dateOfBirth: string | null;

  /**
   * §1 `gender` (Profile 15). An enum since SHOWUP-168, "so the column and the screen cannot drift";
   * it was free text before. Class 2.
   */
  @Column({
    type: 'enum',
    enum: GENDERS,
    enumName: 'profiles_gender_enum',
    nullable: true,
  })
  gender: Gender | null;

  /** Whole centimetres, 120-230 (Profile 14). The database enforces the range too. */
  @Column({ name: 'height_cm', type: 'smallint', nullable: true })
  heightCm: number | null;

  /** §1 `orientation` (Profile 16). Class 2. */
  @Column({
    type: 'enum',
    enum: ORIENTATIONS,
    enumName: 'profiles_orientation_enum',
    nullable: true,
  })
  orientation: Orientation | null;

  /**
   * §1 `dating_language` (Profile 17), stored in list order. NULL means never answered; an empty
   * set is refused by a CHECK, because "skipped" and "answered with nothing" are the same act.
   */
  @Column({
    name: 'dating_languages',
    type: 'enum',
    enum: DATING_LANGUAGES,
    enumName: 'profiles_dating_language_enum',
    array: true,
    nullable: true,
  })
  datingLanguages: DatingLanguage[] | null;

  /** §1 `education` (Profile 18). */
  @Column({
    type: 'enum',
    enum: EDUCATIONS,
    enumName: 'profiles_education_enum',
    nullable: true,
  })
  education: Education | null;

  /** §1 `religion` (Profile 19). Class 2, special-category data. */
  @Column({
    type: 'enum',
    enum: RELIGIONS,
    enumName: 'profiles_religion_enum',
    nullable: true,
  })
  religion: Religion | null;

  /** §1 `politics` (Profile 20). Class 2, special-category data. */
  @Column({
    type: 'enum',
    enum: POLITICS,
    enumName: 'profiles_politics_enum',
    nullable: true,
  })
  politics: Politics | null;

  /**
   * The furthest §2 step reached after prompts -- see `util/flow-position.ts`. Monotonic: going back
   * a screen never rewinds it.
   */
  @Column({
    name: 'flow_position',
    type: 'varchar',
    length: 32,
    nullable: true,
  })
  flowPosition: string | null;

  @Column({ name: 'looking_for', type: 'varchar', length: 40, nullable: true })
  lookingFor: string | null;

  @Column({ name: 'is_visible', type: 'boolean', default: true })
  isVisible: boolean;

  /**
   * Registry `field_id`s the user has chosen not to display on their profile.
   *
   * NOT a variant of [isVisible]. That flag governs whether this profile appears in discovery at
   * all; this set governs which values on a shown profile are rendered. Hiding a field never
   * affects discovery eligibility or matching — a hidden age is still passed to the matching
   * algorithm. See `util/hidden-fields.ts` for the allowed values and why this is one set rather
   * than a boolean per field.
   */
  @Column({
    name: 'hidden_fields',
    type: 'text',
    array: true,
    default: () => "'{}'",
  })
  hiddenFields: string[];

  @Column({ name: 'is_complete', type: 'boolean', default: false })
  isComplete: boolean;

  @Column({
    name: 'verification_status',
    type: 'enum',
    enum: ProfileVerificationStatus,
    enumName: 'profiles_verification_status_enum',
    default: ProfileVerificationStatus.None,
  })
  verificationStatus: ProfileVerificationStatus;

  @Column({
    name: 'verification_requested_at',
    type: 'timestamptz',
    nullable: true,
  })
  verificationRequestedAt: Date | null;

  @Column({ name: 'verified_at', type: 'timestamptz', nullable: true })
  verifiedAt: Date | null;

  /** Safety standing of the profile content (Epic 12, SHOWUP-79). */
  @Column({
    name: 'moderation_standing',
    type: 'enum',
    enum: ModerationStanding,
    enumName: 'moderation_standing_enum',
    default: ModerationStanding.Active,
  })
  moderationStanding: ModerationStanding;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
