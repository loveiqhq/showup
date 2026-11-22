import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

import { EndEntry, EndReason } from '../util/date-end';
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

  /** When `coordinate_unlocked` was sent (meet − 1:30). Null until then; the job's once-only stamp. */
  @Column({
    name: 'coordinate_unlocked_at',
    type: 'timestamptz',
    nullable: true,
  })
  coordinateUnlockedAt: Date | null;

  /** Who ended the date from the chat, and when (Date 02); null unless `ended_in_chat`. */
  @Column({ name: 'ended_by_id', type: 'uuid', nullable: true })
  endedById: string | null;

  @Column({ name: 'ended_at', type: 'timestamptz', nullable: true })
  endedAt: Date | null;

  /** Date 06: how the date was ended and from where (registry e43 / e45); null unless `ended`. */
  @Column({
    name: 'end_reason',
    type: 'enum',
    enum: EndReason,
    enumName: 'date_end_reason_enum',
    nullable: true,
  })
  endReason: EndReason | null;

  @Column({
    name: 'end_entry',
    type: 'enum',
    enum: EndEntry,
    enumName: 'end_entry_enum',
    nullable: true,
  })
  endEntry: EndEntry | null;

  /**
   * Each person's COMMITTED check-in at the moment of an end or a cancel (Date 06 / 07). For a cancel
   * the canceller is the "ender". Null on a date that has neither.
   */
  @Column({ name: 'ender_checked_in', type: 'boolean', nullable: true })
  enderCheckedIn: boolean | null;

  @Column({ name: 'partner_checked_in', type: 'boolean', nullable: true })
  partnerCheckedIn: boolean | null;

  /** Date 07: the canceller had a suggestion from the cancel sheet on this date. */
  @Column({ name: 'cancel_change_requested', type: 'boolean', nullable: true })
  cancelChangeRequested: boolean | null;

  /** Date 07: hours from the cancel to the agreed time (negative after it), and the points it cost. */
  @Column({
    name: 'hours_notice',
    type: 'numeric',
    precision: 8,
    scale: 2,
    nullable: true,
    transformer: {
      to: (v: number | null) => v,
      from: (v: string | null) => (v === null ? null : Number(v)),
    },
  })
  hoursNotice: number | null;

  @Column({ name: 'penalty_points', type: 'int', nullable: true })
  penaltyPoints: number | null;

  /** When each person tapped "Open the chat" on this date's tutorial (Date 02). */
  @Column({ name: 'a_chat_tutorial_at', type: 'timestamptz', nullable: true })
  aChatTutorialAt: Date | null;

  @Column({ name: 'b_chat_tutorial_at', type: 'timestamptz', nullable: true })
  bChatTutorialAt: Date | null;

  /** When each person saw their outcome screen after the date ended in the chat (Date 02). */
  @Column({ name: 'a_end_seen_at', type: 'timestamptz', nullable: true })
  aEndSeenAt: Date | null;

  @Column({ name: 'b_end_seen_at', type: 'timestamptz', nullable: true })
  bEndSeenAt: Date | null;

  /** When each person last read this date's chat. A later message from the other side is unread. */
  @Column({ name: 'a_chat_read_at', type: 'timestamptz', nullable: true })
  aChatReadAt: Date | null;

  @Column({ name: 'b_chat_read_at', type: 'timestamptz', nullable: true })
  bChatReadAt: Date | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
