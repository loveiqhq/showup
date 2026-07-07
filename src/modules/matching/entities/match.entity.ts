import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  Unique,
  UpdateDateColumn,
} from 'typeorm';

import { MatchStatus } from '../util/match';

export { MatchStatus };

/**
 * A match: the shared connection two users get once they have both liked each other (Epic 6).
 * The pair is stored canonically (`userAId` < `userBId`) with a uniqueness constraint, so a mutual
 * like from either direction always maps to the same row — duplicate matches are impossible.
 */
@Entity('matches')
@Unique('uq_matches_pair', ['userAId', 'userBId'])
export class Match {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_a_id', type: 'uuid' })
  userAId: string;

  @Index()
  @Column({ name: 'user_b_id', type: 'uuid' })
  userBId: string;

  @Column({
    type: 'enum',
    enum: MatchStatus,
    enumName: 'matches_status_enum',
    default: MatchStatus.Active,
  })
  status: MatchStatus;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
