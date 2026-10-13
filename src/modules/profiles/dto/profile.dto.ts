import { ApiProperty } from '@nestjs/swagger';

import { Profile, ProfileVerificationStatus } from '../entities/profile.entity';
import { ageFromDateOfBirth } from '../util/age';
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

/**
 * How a closed answer set is described in a RESPONSE: as a string, with today's values listed.
 *
 * NOT AN ENUM ON THE WAY OUT. The request DTO validates against the exact §1 list, which is right
 * -- the server must refuse what it cannot store. But a response enum is a promise to every app
 * already installed that no other value will ever arrive, and both generated clients hold it to
 * that: Kotlin's decoder throws on an unknown enum constant, and so does Swift's. The first new
 * value added to any of these sets would make every older app fail to load its own profile. A
 * string decodes whatever comes; the app maps the values it knows and treats the rest as unknown.
 */
function answerSet(values: readonly string[]): string {
  return `One of: ${values.join(', ')}. Read tolerantly: a newer server may add values.`;
}

/** Public-safe view of a profile. Exposes derived age, not the raw date of birth. */
export class ProfileDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ type: String, nullable: true })
  displayName: string | null;

  // 'integer', not Number: OpenAPI's `number` is an arbitrary-precision decimal, which the
  // Kotlin generator maps to BigDecimal. An age is a whole number and every caller would have to
  // convert it.
  @ApiProperty({ type: 'integer', nullable: true })
  age: number | null;

  @ApiProperty({
    type: String,
    nullable: true,
    description: answerSet(GENDERS),
  })
  gender: Gender | null;

  @ApiProperty({ type: String, nullable: true })
  lookingFor: string | null;

  @ApiProperty()
  isVisible: boolean;

  /**
   * Returned so a consumer rendering this profile knows which values to omit. Without it the age
   * is on the wire and every renderer would have to guess.
   */
  @ApiProperty({
    type: [String],
    example: ['age'],
    description:
      'Registry field_ids the user has hidden from their profile. Presentation only — ' +
      'a hidden field does not affect discovery visibility or matching.',
  })
  hiddenFields: string[];

  @ApiProperty()
  isComplete: boolean;

  @ApiProperty({ enum: ProfileVerificationStatus })
  verificationStatus: ProfileVerificationStatus;

  static from(profile: Profile): ProfileDto {
    return {
      id: profile.id,
      displayName: profile.displayName,
      age: ageFromDateOfBirth(profile.dateOfBirth),
      gender: profile.gender,
      lookingFor: profile.lookingFor,
      isVisible: profile.isVisible,
      hiddenFields: profile.hiddenFields ?? [],
      isComplete: profile.isComplete,
      verificationStatus: profile.verificationStatus,
    };
  }
}

/**
 * The account holder's own profile: everything in [ProfileDto] plus the "Share some details"
 * answers, so a resumed detail screen can pre-fill what was saved (Profile 14: "a relaunch onto
 * height pre-fills the saved value").
 *
 * A SEPARATE TYPE, AND THE SEPARATION IS THE POINT. [ProfileDto] is also returned by the admin
 * verification endpoint, and orientation, religion and politics are Class 2 special-category data
 * that verifying an identity has no use for. Adding them to the shared type would hand them to
 * every caller of it; putting them here gives them to exactly one reader -- the person they
 * describe. Hidden fields are still returned, because hiding governs what OTHERS see, never what
 * the owner sees of themselves.
 */
export class OwnProfileDto extends ProfileDto {
  @ApiProperty({ type: 'integer', nullable: true })
  heightCm: number | null;

  @ApiProperty({
    type: String,
    nullable: true,
    description: answerSet(ORIENTATIONS),
  })
  orientation: Orientation | null;

  @ApiProperty({
    type: [String],
    nullable: true,
    description: `In list order; null when never answered. Each: ${answerSet(DATING_LANGUAGES)}`,
  })
  datingLanguages: DatingLanguage[] | null;

  @ApiProperty({
    type: String,
    nullable: true,
    description: answerSet(EDUCATIONS),
  })
  education: Education | null;

  @ApiProperty({
    type: String,
    nullable: true,
    description: answerSet(RELIGIONS),
  })
  religion: Religion | null;

  @ApiProperty({
    type: String,
    nullable: true,
    description: answerSet(POLITICS),
  })
  politics: Politics | null;

  // Interests (Profile 21). Shown publicly once a profile view exists, but that view is its own
  // ticket; until then they are the owner's alone, like everything else here. Alcohol and smoking
  // are Class 2.

  @ApiProperty({
    type: String,
    nullable: true,
    description: answerSet(ACTIVITIES),
  })
  activity: Activity | null;

  @ApiProperty({ type: String, nullable: true, description: answerSet(DIETS) })
  diet: Diet | null;

  @ApiProperty({
    type: String,
    nullable: true,
    description: answerSet(ALCOHOL),
  })
  alcohol: Alcohol | null;

  @ApiProperty({
    type: String,
    nullable: true,
    description: answerSet(SMOKING),
  })
  smoking: Smoking | null;

  @ApiProperty({
    type: String,
    nullable: true,
    description: answerSet(RHYTHMS),
  })
  rhythm: Rhythm | null;

  @ApiProperty({
    type: String,
    nullable: true,
    description: answerSet(SOCIAL_BATTERIES),
  })
  social: SocialBattery | null;

  @ApiProperty({
    type: [String],
    nullable: true,
    description: `Up to ten, in list order; null when never answered. Each: ${answerSet(INTEREST_TAGS)}`,
  })
  interests: InterestTag[] | null;

  // Life (Profile 22) and Future (Profile 23). MATCHING ONLY: "never shown on the profile", and
  // "none of the three may be exposed by the public profile endpoint". This type is the owner's
  // own read and nothing else; `ProfileDto` and the discovery card never carry them, and both are
  // guarded by tests. Null when the screen was never answered; empty when walked past.

  @ApiProperty({
    type: [String],
    nullable: true,
    description: `Class 2. Each: ${answerSet(LIFE_PARTNERSHIP)}`,
  })
  partnership: LifePartnership[] | null;

  @ApiProperty({
    type: [String],
    nullable: true,
    description: `Each: ${answerSet(LIFE_FAMILY)}`,
  })
  family: LifeFamily[] | null;

  @ApiProperty({
    type: [String],
    nullable: true,
    description: `Each: ${answerSet(LIFE_LIVING)}`,
  })
  living: LifeLiving[] | null;

  @ApiProperty({
    type: [String],
    nullable: true,
    description: `At most one. Each: ${answerSet(FUTURE_FAMILY)}`,
  })
  familyAhead: FutureFamily[] | null;

  @ApiProperty({
    type: [String],
    nullable: true,
    description: `Class 2. Each: ${answerSet(FUTURE_PARTNERSHIP)}`,
  })
  partnershipAhead: FuturePartnership[] | null;

  @ApiProperty({
    type: [String],
    nullable: true,
    description: `Each: ${answerSet(FUTURE_LIVING)}`,
  })
  livingAhead: FutureLiving[] | null;

  static fromOwn(profile: Profile): OwnProfileDto {
    return {
      ...ProfileDto.from(profile),
      heightCm: profile.heightCm,
      orientation: profile.orientation,
      datingLanguages: profile.datingLanguages,
      education: profile.education,
      religion: profile.religion,
      politics: profile.politics,
      activity: profile.activity,
      diet: profile.diet,
      alcohol: profile.alcohol,
      smoking: profile.smoking,
      rhythm: profile.rhythm,
      social: profile.social,
      interests: profile.interests,
      partnership: profile.partnership,
      family: profile.family,
      living: profile.living,
      familyAhead: profile.familyAhead,
      partnershipAhead: profile.partnershipAhead,
      livingAhead: profile.livingAhead,
    };
  }
}
