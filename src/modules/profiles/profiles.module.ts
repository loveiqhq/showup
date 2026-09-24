import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { AdminGuard } from '../auth/guards/admin.guard';
import { ProfileMedia } from './entities/profile-media.entity';
import { ProfilePhoto } from './entities/profile-photo.entity';
import { ProfilePrompt } from './entities/profile-prompt.entity';
import { Profile } from './entities/profile.entity';
import { MediaController } from './media.controller';
import { MediaService } from './media.service';
import { PhotosController } from './photos.controller';
import { PhotosService } from './photos.service';
import { ProfileAdminController } from './profile-admin.controller';
import { PromptsController } from './prompts.controller';
import { PromptsService } from './prompts.service';
import { ProfilesController } from './profiles.controller';
import { ProfilesService } from './profiles.service';
import { LocalDiskStorage } from './storage/local-disk.storage';
import { STORAGE } from './storage/storage.interface';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      Profile,
      ProfilePhoto,
      ProfilePrompt,
      ProfileMedia,
    ]),
  ],
  controllers: [
    ProfilesController,
    PhotosController,
    PromptsController,
    MediaController,
    ProfileAdminController,
  ],
  providers: [
    ProfilesService,
    PhotosService,
    PromptsService,
    MediaService,
    AdminGuard,
    { provide: STORAGE, useClass: LocalDiskStorage },
  ],
})
export class ProfilesModule {}
