import {
  Body,
  Controller,
  Param,
  ParseUUIDPipe,
  Patch,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';

import { AdminGuard } from '../auth/guards/admin.guard';
import { PhotoDto } from './dto/photo.dto';
import { ProfileDto } from './dto/profile.dto';
import { SetPhotoModerationDto } from './dto/set-photo-moderation.dto';
import { SetVerificationDto } from './dto/set-verification.dto';
import { PhotosService } from './photos.service';
import { ProfilesService } from './profiles.service';

/** Admin-only moderation endpoints (protected by AdminGuard + the global auth guard). */
@ApiTags('admin')
@ApiBearerAuth()
@UseGuards(AdminGuard)
@Controller('admin')
export class ProfileAdminController {
  constructor(
    private readonly profiles: ProfilesService,
    private readonly photos: PhotosService,
  ) {}

  @Patch('profiles/:userId/verification')
  @ApiOkResponse({ type: ProfileDto })
  async setVerification(
    @Param('userId', ParseUUIDPipe) userId: string,
    @Body() dto: SetVerificationDto,
  ): Promise<ProfileDto> {
    return ProfileDto.from(
      await this.profiles.setVerification(userId, dto.status),
    );
  }

  @Patch('photos/:id/moderation')
  @ApiOkResponse({ type: PhotoDto })
  async setPhotoModeration(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: SetPhotoModerationDto,
  ): Promise<PhotoDto> {
    const photo = await this.photos.setModeration(id, dto.status);
    return PhotoDto.from(photo, this.photos.url(photo));
  }
}
