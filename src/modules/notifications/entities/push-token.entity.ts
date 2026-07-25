import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

/** Which kind of device a push token belongs to (FCM handles the platform differences). */
export enum PushPlatform {
  Ios = 'ios',
  Android = 'android',
}

/**
 * A device's push registration token (SHOWUP-67). `token` is unique, so a device re-submitting its
 * token upserts rather than duplicating, and a token can move between users (re-pointed to whoever
 * last registered it). Rows cascade-delete with the user on a hard purge; soft-deleted users are
 * excluded at send time by the dispatcher's status check.
 */
@Entity('push_tokens')
export class PushToken {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ type: 'text', unique: true })
  token: string;

  @Column({
    type: 'enum',
    enum: PushPlatform,
    enumName: 'push_tokens_platform_enum',
  })
  platform: PushPlatform;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
