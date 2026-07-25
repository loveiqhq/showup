import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryColumn,
  UpdateDateColumn,
} from 'typeorm';

/**
 * A user's notification choices (SHOWUP-66), one row per user (the `user_id` is the primary key).
 * Three groups: `essential` (account & dates), `engagement` (reminders/nudges), `marketing`.
 * Marketing is off by default and only sent with explicit consent. The gating policy lives in
 * `util/preferences.ts`; this is just its storage.
 */
@Entity('notification_preferences')
export class NotificationPreference {
  @PrimaryColumn({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ type: 'boolean', default: true })
  essential: boolean;

  @Column({ type: 'boolean', default: true })
  engagement: boolean;

  @Column({ type: 'boolean', default: false })
  marketing: boolean;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
