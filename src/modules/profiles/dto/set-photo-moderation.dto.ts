import { ApiProperty } from '@nestjs/swagger';
import { IsIn } from 'class-validator';

import { PhotoModerationStatus } from '../entities/profile-photo.entity';

/** Admin approves or rejects a photo. */
export class SetPhotoModerationDto {
  @ApiProperty({
    enum: [PhotoModerationStatus.Approved, PhotoModerationStatus.Rejected],
  })
  @IsIn([PhotoModerationStatus.Approved, PhotoModerationStatus.Rejected])
  status: PhotoModerationStatus.Approved | PhotoModerationStatus.Rejected;
}
