import { ApiProperty } from '@nestjs/swagger';

import { Profile, ProfileVerificationStatus } from '../entities/profile.entity';
import { ageFromDateOfBirth } from '../util/age';

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

  @ApiProperty({ type: String, nullable: true })
  gender: string | null;

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
