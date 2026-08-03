import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

export enum NotificationChannel {
  Push = 'push',
  Email = 'email',
}

/** `skipped` = suppressed by the user's preferences or account status; not an error. */
export enum NotificationLogStatus {
  Sent = 'sent',
  Failed = 'failed',
  Skipped = 'skipped',
}

/**
 * An append-only record of every notification the app tried to send (SHOWUP-68/71). Kept for
 * observability and troubleshooting. `user_id` is `ON DELETE SET NULL` so the log survives a user
 * purge (like `audit_logs`). `type`/`category` are free-form strings mirroring `util/preferences.ts`.
 */
@Entity('notification_log')
@Index(['userId', 'createdAt'])
export class NotificationLogEntry {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'user_id', type: 'uuid', nullable: true })
  userId: string | null;

  @Column({
    type: 'enum',
    enum: NotificationChannel,
    enumName: 'notification_channel_enum',
  })
  channel: NotificationChannel;

  @Column({ type: 'varchar', length: 64 })
  type: string;

  @Column({ type: 'varchar', length: 16 })
  category: string;

  @Column({ type: 'varchar', length: 8 })
  locale: string;

  @Column({
    type: 'enum',
    enum: NotificationLogStatus,
    enumName: 'notification_status_enum',
  })
  status: NotificationLogStatus;

  @Column({
    name: 'provider_message_id',
    type: 'varchar',
    length: 255,
    nullable: true,
  })
  providerMessageId: string | null;

  @Column({ type: 'text', nullable: true })
  error: string | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
