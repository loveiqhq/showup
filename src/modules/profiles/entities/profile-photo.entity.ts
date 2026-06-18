import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

/** Moderation state of an uploaded photo (reviewed for safety before it's shown). */
export enum PhotoModerationStatus {
  Pending = 'pending',
  Approved = 'approved',
  Rejected = 'rejected',
}

/**
 * A profile photo. The image bytes live in object storage (see StorageService); only metadata is
 * kept here — the storage key, content type, ordering, and moderation status. EXIF/metadata that
 * could leak location is stripped before storing.
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

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
