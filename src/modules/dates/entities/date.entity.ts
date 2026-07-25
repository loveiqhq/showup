import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

import { DateStatus } from '../util/date-lifecycle';

export { DateStatus };

/**
 * A real-world date between two matched people (Epic 7). Created already "confirmed" when a match
 * is made (the app proposes the time + venue). The two participants are stored as a canonical
 * ordered pair (userAId < userBId); each participant's "did it happen" confirmation and rating are
 * tracked so completion requires both. Named `DateEntity` to avoid clashing with the JS `Date`.
 */
@Entity('dates')
export class DateEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_a_id', type: 'uuid' })
  userAId: string;

  @Index()
  @Column({ name: 'user_b_id', type: 'uuid' })
  userBId: string;

  @Column({ name: 'match_id', type: 'uuid', nullable: true })
  matchId: string | null;

  @Column({ name: 'scheduled_at', type: 'timestamptz' })
  scheduledAt: Date;

  @Column({ name: 'venue_id', type: 'uuid', nullable: true })
  venueId: string | null;

  @Column({
    type: 'enum',
    enum: DateStatus,
    enumName: 'dates_status_enum',
    default: DateStatus.Confirmed,
  })
  status: DateStatus;

  // Completion needs both participants to confirm the date happened; each also leaves a rating.
  @Column({ name: 'a_confirmed_happened', type: 'boolean', default: false })
  aConfirmedHappened: boolean;

  @Column({ name: 'b_confirmed_happened', type: 'boolean', default: false })
  bConfirmedHappened: boolean;

  @Column({ name: 'a_rating', type: 'smallint', nullable: true })
  aRating: number | null;

  @Column({ name: 'b_rating', type: 'smallint', nullable: true })
  bRating: number | null;

  // Cancellation is a single type and always penalises the canceller (score handled in Epic 8).
  @Column({ name: 'cancelled_by_id', type: 'uuid', nullable: true })
  cancelledById: string | null;

  @Column({
    name: 'cancel_reason',
    type: 'varchar',
    length: 500,
    nullable: true,
  })
  cancelReason: string | null;

  @Column({ name: 'cancelled_at', type: 'timestamptz', nullable: true })
  cancelledAt: Date | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
