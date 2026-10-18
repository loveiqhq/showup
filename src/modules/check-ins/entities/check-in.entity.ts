import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

import { CheckInStatus } from '../util/check-in';
import { SLOT_TIERS, type SlotTier } from '../util/check-in-flow';

export { CheckInStatus };

/**
 * A user's check-in: a declared window of availability for a real-world date. This is volatile,
 * high-churn data, so it lives in its own table (Epic 4), separate from the profile. Whether a
 * check-in is "active right now" is derived from status + the window (see `isActiveCheckIn`), so a
 * past-window check-in reads as inactive immediately — the expiry job (SHOWUP-40, needs Epic 17)
 * only tidies the stored status.
 */
@Entity('check_ins')
@Index(['status', 'availabilityEnd'])
export class CheckIn {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({
    type: 'enum',
    enum: CheckInStatus,
    enumName: 'check_ins_status_enum',
    default: CheckInStatus.Available,
  })
  status: CheckInStatus;

  @Column({ name: 'availability_start', type: 'timestamptz' })
  availabilityStart: Date;

  @Column({ name: 'availability_end', type: 'timestamptz' })
  availabilityEnd: Date;

  /**
   * How long the user says they need to get ready before heading out, in minutes. Subtracted
   * (with travel time) from the usable window when checking whether a date is feasible. Bounds are
   * enforced in the service via `preparationTimeError` (currently 15–60 min).
   */
  @Column({ name: 'preparation_minutes', type: 'smallint', default: 30 })
  preparationMinutes: number;

  /**
   * The Check-In window these hours were committed with (story 06), or null for a window created
   * directly through `POST /me/check-ins`. Lets a new Check-In retire the hours of the last one.
   */
  @Index()
  @Column({ name: 'check_in_window_id', type: 'uuid', nullable: true })
  windowId: string | null;

  /**
   * The 1st tap (`preferred`) or the 2nd tap (`available`) on these hours (story 02: "both tiers are
   * stored and used"). One row is one run of consecutive hours of one tier.
   */
  @Column({
    type: 'enum',
    enum: SLOT_TIERS,
    enumName: 'check_ins_tier_enum',
    default: 'preferred',
  })
  tier: SlotTier;

  /**
   * When the "your check-in is about to expire" reminder was sent (Epic 10, SHOWUP-70). Null until
   * the reminder scan handles this check-in; set once so the reminder never fires twice.
   */
  @Column({
    name: 'expiry_reminder_sent_at',
    type: 'timestamptz',
    nullable: true,
  })
  expiryReminderSentAt: Date | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
