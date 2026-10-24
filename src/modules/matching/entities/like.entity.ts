import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  Unique,
  UpdateDateColumn,
} from 'typeorm';

import { LikeCloseReason, LikeStatus } from '../util/match';

export { LikeCloseReason, LikeStatus };

/**
 * A like: one user's interest in another (Epic 6). The optional `message` is a premium extra.
 * A unique (sender, receiver) constraint makes duplicate likes impossible.
 */
@Entity('likes')
@Unique('uq_likes_sender_receiver', ['senderId', 'receiverId'])
@Index(['receiverId', 'status'])
export class Like {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'sender_id', type: 'uuid' })
  senderId: string;

  @Column({ name: 'receiver_id', type: 'uuid' })
  receiverId: string;

  @Column({
    type: 'enum',
    enum: LikeStatus,
    enumName: 'likes_status_enum',
    default: LikeStatus.Active,
  })
  status: LikeStatus;

  /**
   * The optional message (Search 01: at most 100 characters, trimmed, an empty one stored as no
   * message). ANY sender may write one; only a Premium RECIPIENT may read it (Search 03). The
   * column is wider than the rule because the rule is the DTO's -- a narrower column would turn a
   * future limit change into a migration. Never in a log line or a tracking payload.
   */
  @Column({ type: 'varchar', length: 500, nullable: true })
  message: string | null;

  /** Set with `closedAt` when the like closes without a decision (status `withdrawn`). */
  @Column({
    name: 'closed_reason',
    type: 'enum',
    enum: LikeCloseReason,
    enumName: 'likes_closed_reason_enum',
    nullable: true,
  })
  closedReason: LikeCloseReason | null;

  @Column({ name: 'closed_at', type: 'timestamptz', nullable: true })
  closedAt: Date | null;

  /** The match that closed this like (reason `matched`), or the match it made (status `matched`). */
  @Column({ name: 'match_id', type: 'uuid', nullable: true })
  matchId: string | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
