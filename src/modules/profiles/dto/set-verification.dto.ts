import { ApiProperty } from '@nestjs/swagger';
import { IsIn } from 'class-validator';

import { ProfileVerificationStatus } from '../entities/profile.entity';

/** Admin sets a profile's verification result. */
export class SetVerificationDto {
  @ApiProperty({
    enum: [
      ProfileVerificationStatus.Verified,
      ProfileVerificationStatus.Rejected,
    ],
  })
  @IsIn([
    ProfileVerificationStatus.Verified,
    ProfileVerificationStatus.Rejected,
  ])
  status:
    | ProfileVerificationStatus.Verified
    | ProfileVerificationStatus.Rejected;
}
