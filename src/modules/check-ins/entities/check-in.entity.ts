import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

import { CheckInStatus } from '../util/check-in';

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

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
