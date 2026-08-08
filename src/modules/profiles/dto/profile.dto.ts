import { ApiProperty } from '@nestjs/swagger';

import { Profile, ProfileVerificationStatus } from '../entities/profile.entity';
import { ageFromDateOfBirth } from '../util/age';

/** Public-safe view of a profile. Exposes derived age, not the raw date of birth. */
export class ProfileDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ nullable: true })
  displayName: string | null;

  @ApiProperty({ nullable: true })
  age: number | null;

  @ApiProperty({ nullable: true })
  gender: string | null;

  @ApiProperty({ nullable: true })
  lookingFor: string | null;

  @ApiProperty()
  isVisible: boolean;

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
      isComplete: profile.isComplete,
      verificationStatus: profile.verificationStatus,
    };
  }
}
