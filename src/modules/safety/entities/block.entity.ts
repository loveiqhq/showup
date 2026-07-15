import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  Unique,
} from 'typeorm';

import { BlockSource } from '../util/safety';

/**
 * One person blocking another (SHOWUP-77). Directional: `blockerId` blocked `blockedId`, but because
 * the block filter is bidirectional this single row hides the two from each other everywhere ("blocked
 * for one another"). `source` records how the block arose (direct tap vs. a date/search flow) so it
 * stays traceable. Soft-unblock — `unblockedAt` is set instead of deleting the row, keeping history.
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

  /** How this block came to be — see {@link BlockSource}. */
  @Column({
    type: 'enum',
    enum: BlockSource,
    enumName: 'block_source_enum',
    default: BlockSource.Manual,
  })
  source: BlockSource;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  /** Null while the block is active; set when the blocker lifts it. */
  @Column({ name: 'unblocked_at', type: 'timestamptz', nullable: true })
  unblockedAt: Date | null;
}
