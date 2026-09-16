import { ApiProperty } from '@nestjs/swagger';

import {
  PhotoModerationStatus,
  ProfilePhoto,
} from '../entities/profile-photo.entity';

export class PhotoDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  url: string;

  @ApiProperty({ enum: PhotoModerationStatus })
  moderationStatus: PhotoModerationStatus;

  @ApiProperty({
    type: 'integer',
    description: 'Ordering within the user’s photos, from 0.',
  })
  position: number;

  @ApiProperty()
  createdAt: Date;

  static from(photo: ProfilePhoto, url: string): PhotoDto {
    return {
      id: photo.id,
      url,
      moderationStatus: photo.moderationStatus,
      position: photo.position,
      createdAt: photo.createdAt,
    };
  }
}
