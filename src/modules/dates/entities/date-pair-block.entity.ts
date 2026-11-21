import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
  Unique,
} from 'typeorm';

/** Why a pair block was written (Date 07). */
export const PAIR_BLOCK_RED_LINK_CANCEL = 'red_link_cancel';

/**
 * The permanent "never match again" block a red-link cancel writes (Date 07, R4). Unlike `blocks` it
 * is not directional, has no blocker, cannot be lifted, and carries the date. One row per pair, stored
 * with the ids in order (`user_low` < `user_high`); nothing deletes it. `SafetyService.blockedUserIdsFor`
 * unions it, so search, Likes received and matching exclude the pair both ways.
 */
@Entity('date_pair_blocks')
@Unique('UQ_date_pair_blocks_pair', ['userLow', 'userHigh'])
export class DatePairBlock {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'user_low', type: 'uuid' })
  userLow: string;

  @Column({ name: 'user_high', type: 'uuid' })
  userHigh: string;

  @Column({ name: 'date_id', type: 'uuid' })
  dateId: string;

  @Column({ type: 'varchar', length: 32 })
  reason: string;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
