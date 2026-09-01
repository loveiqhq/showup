import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

import { ModerationStanding } from '../../safety/util/safety';

/** Moderation state of an uploaded photo (reviewed for safety before it's shown). */
export enum PhotoModerationStatus {
  Pending = 'pending',
  Approved = 'approved',
  Rejected = 'rejected',
}

/**
 * A profile photo. The image bytes live in object storage (see StorageService); only metadata is
 * kept here — the storage key, content type, ordering, and moderation status.
 *
 * NOT YET STRIPPED: uploads are stored exactly as received, so a photo still carries whatever EXIF
 * the phone embedded — including GPS coordinates when location services were on. SHOWUP-34 required
 * this ("Photo metadata does not expose unsafe information") but nothing implements it. Stripping
 * falls out of re-encoding, so it arrives with the image-resizing work on SHOWUP-119.
 */
@Entity('profile_photos')
export class ProfilePhoto {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ name: 'storage_key', type: 'varchar', length: 512 })
  storageKey: string;

  @Column({ name: 'content_type', type: 'varchar', length: 100 })
  contentType: string;

  @Column({ type: 'int', default: 0 })
  position: number;

  @Column({
    name: 'moderation_status',
    type: 'enum',
    enum: PhotoModerationStatus,
    enumName: 'photos_moderation_status_enum',
    default: PhotoModerationStatus.Pending,
  })
  moderationStatus: PhotoModerationStatus;

  /** Safety standing of the photo (Epic 12, SHOWUP-79) — separate from the pre-show review above. */
  @Column({
    name: 'moderation_standing',
    type: 'enum',
    enum: ModerationStanding,
    enumName: 'moderation_standing_enum',
    default: ModerationStanding.Active,
  })
  moderationStanding: ModerationStanding;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
