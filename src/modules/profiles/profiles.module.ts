import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { AdminGuard } from '../auth/guards/admin.guard';
import { ProfilePhoto } from './entities/profile-photo.entity';
import { Profile } from './entities/profile.entity';
import { PhotosController } from './photos.controller';
import { PhotosService } from './photos.service';
import { ProfileAdminController } from './profile-admin.controller';
import { ProfilesController } from './profiles.controller';
import { ProfilesService } from './profiles.service';
import { LocalDiskStorage } from './storage/local-disk.storage';
import { STORAGE } from './storage/storage.interface';

@Module({
  imports: [TypeOrmModule.forFeature([Profile, ProfilePhoto])],
  controllers: [ProfilesController, PhotosController, ProfileAdminController],
  providers: [
    ProfilesService,
    PhotosService,
    AdminGuard,
    { provide: STORAGE, useClass: LocalDiskStorage },
  ],
})
export class ProfilesModule {}
