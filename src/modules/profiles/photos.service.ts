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
import { In, Repository } from 'typeorm';

import {
  PhotoModerationStatus,
  ProfilePhoto,
} from './entities/profile-photo.entity';
import { ProfilesService } from './profiles.service';
import { STORAGE, type StorageService } from './storage/storage.interface';
import { VISIBLE_PHOTO_STATUSES } from './util/photo-visibility';

const ALLOWED_TYPES = new Map<string, string>([
  ['image/jpeg', 'jpg'],
  ['image/png', 'png'],
  ['image/webp', 'webp'],
]);
const MAX_BYTES = 8 * 1024 * 1024; // 8 MB
/** The most photos one account may hold. Six, matching the grid the client draws. */
export const MAX_PHOTOS = 6;

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

  /**
   * Photos to show to OTHER users — excludes rejected. Any path that serves a user's photos to
   * someone other than the owner MUST use this. `list()` stays the owner's own full view (it keeps
   * rejected photos so the owner can see the rejection and replace them).
   */
  listVisible(userId: string): Promise<ProfilePhoto[]> {
    return this.photos.find({
      where: { userId, moderationStatus: In(VISIBLE_PHOTO_STATUSES) },
      order: { position: 'ASC', createdAt: 'ASC' },
    });
  }

  /**
   * Stores the order the user dragged the grid into.
   *
   * TAKES THE WHOLE ORDER AND REQUIRES IT TO BE COMPLETE. `ids` must be exactly this account's
   * photos, each once — no missing, no extra, no duplicates. A partial list is refused rather than
   * applied, because "position these three and leave the rest" has no answer the caller and the
   * server would agree on: whatever the server invented for the others would be an order the user
   * did not choose.
   *
   * Refusing is also what makes a stale client safe. A grid that was built before another device
   * added a photo sends a list one short, and the 400 sends it back to `list()` rather than
   * quietly renumbering around a photo it has never seen.
   *
   * MODERATION IS NOT CONSULTED, deliberately. This app post-moderates: `isPhotoVisibleToOthers`
   * shows pending and approved and hides only rejected, and `listVisible` is what every path
   * serving photos to somebody else already uses. So which photo a stranger sees first is decided
   * where photos are READ, not here — and refusing to move a photo that is still in review would
   * be a drag that silently springs back, with nothing on screen to explain it.
   */
  async reorder(userId: string, ids: string[]): Promise<ProfilePhoto[]> {
    const mine = await this.list(userId);

    const unique = new Set(ids);
    if (unique.size !== ids.length) {
      throw new BadRequestException('The same photo appears more than once.');
    }
    if (ids.length !== mine.length || !mine.every((p) => unique.has(p.id))) {
      // One message for all three shapes of wrong -- missing, extra, or somebody else's id --
      // because the caller's only recovery is the same in each case: re-read and try again. Naming
      // which id was unrecognised would also tell a caller whether an id exists on another account.
      throw new BadRequestException(
        'The order must list this account’s photos exactly once each.',
      );
    }

    const byId = new Map(mine.map((photo) => [photo.id, photo]));
    const reordered = ids.map((id, index) => {
      const photo = byId.get(id)!;
      photo.position = index;
      return photo;
    });
    await this.photos.save(reordered);
    return reordered;
  }

  /**
   * Deletes one photo and closes the gap it left.
   *
   * RENUMBERING IS NOT TIDINESS. `upload` gives a new photo `position = count`, which is only the
   * end of the list while positions are dense. Leave holes and it stops being: delete three of
   * five photos and the next upload takes position 2, ahead of the two survivors at 3 and 4 --
   * a photo the user just added silently becoming their main photo.
   *
   * Six rows at most, so this is one extra write and no query worth optimising.
   */
  async remove(userId: string, photoId: string): Promise<void> {
    const photo = await this.photos.findOne({
      where: { id: photoId, userId },
    });
    if (!photo) throw new NotFoundException('Photo not found');
    await this.storage.delete(photo.storageKey);
    await this.photos.remove(photo);

    const remaining = await this.list(userId);
    const moved = remaining.filter((p, index) => {
      if (p.position === index) return false;
      p.position = index;
      return true;
    });
    if (moved.length > 0) await this.photos.save(moved);

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
    const saved = await this.photos.save(photo);
    // A rejection can drop the profile below the photo minimum, so recompute completeness.
    await this.profiles.refreshCompletion(photo.userId);
    return saved;
  }

  url(photo: ProfilePhoto): string {
    return this.storage.publicUrl(photo.storageKey);
  }
}
