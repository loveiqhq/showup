import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

import { ModerationStanding, ModerationSubjectType } from '../util/safety';

/**
 * Append-only history of every moderation standing change (SHOWUP-79). One row is written each time
 * a user's, profile's, or photo's standing is set, so there is always a full trail of what was
 * decided, by whom, and when.
 */
@Entity('moderation_status_changes')
@Index('idx_mod_changes_subject', ['subjectType', 'subjectId'])
export class ModerationStatusChange {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({
    name: 'subject_type',
    type: 'enum',
    enum: ModerationSubjectType,
    enumName: 'moderation_subject_enum',
  })
  subjectType: ModerationSubjectType;

  @Column({ name: 'subject_id', type: 'uuid' })
  subjectId: string;

  /** Null on the very first standing set for a subject. */
  @Column({
    name: 'from_standing',
    type: 'enum',
    enum: ModerationStanding,
    enumName: 'moderation_standing_enum',
    nullable: true,
  })
  fromStanding: ModerationStanding | null;

  @Column({
    name: 'to_standing',
    type: 'enum',
    enum: ModerationStanding,
    enumName: 'moderation_standing_enum',
  })
  toStanding: ModerationStanding;

  /** The staff member who made the change; null when the system set it. */
  @Column({ name: 'changed_by_user_id', type: 'uuid', nullable: true })
  changedByUserId: string | null;

  @Column({ type: 'varchar', length: 500, nullable: true })
  reason: string | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
