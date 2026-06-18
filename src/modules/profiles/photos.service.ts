import { randomUUID } from 'crypto';

import {
  BadRequestException,
  Inject,
  Injectable,
  NotFoundException,
  PayloadTooLargeException,
  UnsupportedMediaTypeException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import {
  PhotoModerationStatus,
  ProfilePhoto,
} from './entities/profile-photo.entity';
import { ProfilesService } from './profiles.service';
import { STORAGE, type StorageService } from './storage/storage.interface';

const ALLOWED_TYPES = new Map<string, string>([
  ['image/jpeg', 'jpg'],
  ['image/png', 'png'],
  ['image/webp', 'webp'],
]);
const MAX_BYTES = 8 * 1024 * 1024; // 8 MB
const MAX_PHOTOS = 6;

/** Minimal shape of an uploaded file (subset of Express.Multer.File). */
export interface UploadedImage {
  buffer: Buffer;
  mimetype: string;
  size: number;
}

@Injectable()
export class PhotosService {
  constructor(
    @InjectRepository(ProfilePhoto)
    private readonly photos: Repository<ProfilePhoto>,
    @Inject(STORAGE) private readonly storage: StorageService,
    private readonly profiles: ProfilesService,
  ) {}

  async upload(userId: string, file?: UploadedImage): Promise<ProfilePhoto> {
    if (!file) throw new BadRequestException('No file uploaded');

    const ext = ALLOWED_TYPES.get(file.mimetype);
    if (!ext) {
      throw new UnsupportedMediaTypeException(
        'Only JPEG, PNG, or WebP images are allowed',
      );
    }
    if (file.size > MAX_BYTES) {
      throw new PayloadTooLargeException('Image must be 8 MB or smaller');
    }

    const count = await this.photos.count({ where: { userId } });
    if (count >= MAX_PHOTOS) {
      throw new BadRequestException(
        `You can upload at most ${MAX_PHOTOS} photos`,
      );
    }

    const key = `${userId}/${randomUUID()}.${ext}`;
    await this.storage.save(key, file.buffer, file.mimetype);

    const photo = await this.photos.save(
      this.photos.create({
        userId,
        storageKey: key,
        contentType: file.mimetype,
        position: count,
        moderationStatus: PhotoModerationStatus.Pending,
      }),
    );
    await this.profiles.refreshCompletion(userId);
    return photo;
  }

  list(userId: string): Promise<ProfilePhoto[]> {
    return this.photos.find({
      where: { userId },
      order: { position: 'ASC', createdAt: 'ASC' },
    });
  }

  async remove(userId: string, photoId: string): Promise<void> {
    const photo = await this.photos.findOne({
      where: { id: photoId, userId },
    });
    if (!photo) throw new NotFoundException('Photo not found');
    await this.storage.delete(photo.storageKey);
    await this.photos.remove(photo);
    await this.profiles.refreshCompletion(userId);
  }

  /** Admin approves or rejects a photo. */
  async setModeration(
    photoId: string,
    status: PhotoModerationStatus.Approved | PhotoModerationStatus.Rejected,
  ): Promise<ProfilePhoto> {
    const photo = await this.photos.findOne({ where: { id: photoId } });
    if (!photo) throw new NotFoundException('Photo not found');
    photo.moderationStatus = status;
    return this.photos.save(photo);
  }

  url(photo: ProfilePhoto): string {
    return this.storage.publicUrl(photo.storageKey);
  }
}
