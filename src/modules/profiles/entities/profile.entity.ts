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
  ACTIVITIES,
  ALCOHOL,
  DATING_LANGUAGES,
  DIETS,
  EDUCATIONS,
  FUTURE_FAMILY,
  FUTURE_LIVING,
  FUTURE_PARTNERSHIP,
  GENDERS,
  INTEREST_TAGS,
  LIFE_FAMILY,
  LIFE_LIVING,
  LIFE_PARTNERSHIP,
  ORIENTATIONS,
  POLITICS,
  RELIGIONS,
  RHYTHMS,
  SMOKING,
  SOCIAL_BATTERIES,
} from '../util/profile-details';
import type {
  Activity,
  Alcohol,
  DatingLanguage,
  Diet,
  Education,
  FutureFamily,
  FutureLiving,
  FuturePartnership,
  Gender,
  InterestTag,
  LifeFamily,
  LifeLiving,
  LifePartnership,
  Orientation,
  Politics,
  Religion,
  Rhythm,
  Smoking,
  SocialBattery,
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

  // ── Interests (Profile 21): "How I live", one §1 value each or NULL ──────────────────────────

  /** §1 `activity`. */
  @Column({
    type: 'enum',
    enum: ACTIVITIES,
    enumName: 'profiles_activity_enum',
    nullable: true,
  })
  activity: Activity | null;

  /** §1 `diet`. */
  @Column({
    type: 'enum',
    enum: DIETS,
    enumName: 'profiles_diet_enum',
    nullable: true,
  })
  diet: Diet | null;

  /** §1 `alcohol`. Class 2, special-category data (substance use). */
  @Column({
    type: 'enum',
    enum: ALCOHOL,
    enumName: 'profiles_alcohol_enum',
    nullable: true,
  })
  alcohol: Alcohol | null;

  /** §1 `smoking`. Class 2, special-category data (substance use). */
  @Column({
    type: 'enum',
    enum: SMOKING,
    enumName: 'profiles_smoking_enum',
    nullable: true,
  })
  smoking: Smoking | null;

  /** §1 `rhythm`. */
  @Column({
    type: 'enum',
    enum: RHYTHMS,
    enumName: 'profiles_rhythm_enum',
    nullable: true,
  })
  rhythm: Rhythm | null;

  /** §1 `social`, the "Social battery" group. */
  @Column({
    type: 'enum',
    enum: SOCIAL_BATTERIES,
    enumName: 'profiles_social_enum',
    nullable: true,
  })
  social: SocialBattery | null;

  /**
   * "What I'm into": up to 10 §3 tag_ids, in list order. NULL means never answered; an empty set is
   * refused by a CHECK, as for dating languages -- Continue with nothing picked is a Skip.
   */
  @Column({
    type: 'enum',
    enum: INTEREST_TAGS,
    enumName: 'profiles_interest_enum',
    array: true,
    nullable: true,
  })
  interests: InterestTag[] | null;

  // ── Life (Profile 22) and Future (Profile 23): matching only, never on another user's response ──
  //
  // Each is a set of §4 values in list order. NULL means the screen was never answered; an EMPTY
  // set is a real answer -- Continue "saves an unanswered question as empty". A CHECK refuses an
  // exclusive value combined with anything else.

  /** §4 `partnership`. Class 2. */
  @Column({
    type: 'enum',
    enum: LIFE_PARTNERSHIP,
    enumName: 'profiles_life_partnership_enum',
    array: true,
    nullable: true,
  })
  partnership: LifePartnership[] | null;

  /** §4 `family`. `no_children` is exclusive. */
  @Column({
    type: 'enum',
    enum: LIFE_FAMILY,
    enumName: 'profiles_life_family_enum',
    array: true,
    nullable: true,
  })
  family: LifeFamily[] | null;

  /** §4 `living`. */
  @Column({
    type: 'enum',
    enum: LIFE_LIVING,
    enumName: 'profiles_life_living_enum',
    array: true,
    nullable: true,
  })
  living: LifeLiving[] | null;

  /** §4 `family_ahead`. One value at most. */
  @Column({
    name: 'family_ahead',
    type: 'enum',
    enum: FUTURE_FAMILY,
    enumName: 'profiles_future_family_enum',
    array: true,
    nullable: true,
  })
  familyAhead: FutureFamily[] | null;

  /** §4 `partnership_ahead`. Class 2. `find_out_together` is exclusive. */
  @Column({
    name: 'partnership_ahead',
    type: 'enum',
    enum: FUTURE_PARTNERSHIP,
    enumName: 'profiles_future_partnership_enum',
    array: true,
    nullable: true,
  })
  partnershipAhead: FuturePartnership[] | null;

  /** §4 `living_ahead`. `find_out_together` is exclusive. */
  @Column({
    name: 'living_ahead',
    type: 'enum',
    enum: FUTURE_LIVING,
    enumName: 'profiles_future_living_enum',
    array: true,
    nullable: true,
  })
  livingAhead: FutureLiving[] | null;

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
