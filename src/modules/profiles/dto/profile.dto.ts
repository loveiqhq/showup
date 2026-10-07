import { ApiProperty } from '@nestjs/swagger';

import { Profile, ProfileVerificationStatus } from '../entities/profile.entity';
import { ageFromDateOfBirth } from '../util/age';
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

  static fromOwn(profile: Profile): OwnProfileDto {
    return {
      ...ProfileDto.from(profile),
      heightCm: profile.heightCm,
      orientation: profile.orientation,
      datingLanguages: profile.datingLanguages,
      education: profile.education,
      religion: profile.religion,
      politics: profile.politics,
    };
  }
}
