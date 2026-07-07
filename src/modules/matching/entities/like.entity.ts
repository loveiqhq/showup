import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  Unique,
  UpdateDateColumn,
} from 'typeorm';

import { LikeStatus } from '../util/match';

export { LikeStatus };

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

  /** Optional short message attached to the like — only premium users may send one. */
  @Column({ type: 'varchar', length: 500, nullable: true })
  message: string | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
