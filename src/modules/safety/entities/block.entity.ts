import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  Unique,
} from 'typeorm';

/**
 * One person blocking another (SHOWUP-77). Directional: `blockerId` blocked `blockedId`.
 * Soft-unblock — `unblockedAt` is set instead of deleting the row, so the history stays traceable.
 * One row per (blocker, blocked) pair; re-blocking reactivates the existing row.
 */
@Entity('blocks')
@Unique('uq_blocks_pair', ['blockerId', 'blockedId'])
@Index('idx_blocks_blocker', ['blockerId'])
@Index('idx_blocks_blocked', ['blockedId'])
export class Block {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'blocker_id', type: 'uuid' })
  blockerId: string;

  @Column({ name: 'blocked_id', type: 'uuid' })
  blockedId: string;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  /** Null while the block is active; set when the blocker lifts it. */
  @Column({ name: 'unblocked_at', type: 'timestamptz', nullable: true })
  unblockedAt: Date | null;
}
