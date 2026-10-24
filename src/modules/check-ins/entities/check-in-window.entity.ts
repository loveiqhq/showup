import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

import {
  CHECK_IN_MOTIVATIONS,
  CHECK_IN_VIBES,
  type CheckInMotivation,
  type CheckInVibe,
} from '../util/check-in-flow';

/**
 * A Check-In window -- the `window_id` every Check-In event carries (story 01). One opens when
 * profile creation completes, unfinished and with no clock; the Check-In's 48 hours start at
 * `Find my date` (see `ensureCheckInWindow`). A COMMITTED Check-In is a window with `committedAt`
 * set: "one Check-In per window_id" is what makes the commit idempotent (story 06).
 * Its hours live in `check_ins`, keyed back here, so discovery and matching read them unchanged.
 */
@Entity('check_in_windows')
@Index(['userId', 'closesAt'])
export class CheckInWindow {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ name: 'opened_at', type: 'timestamptz', default: () => 'now()' })
  openedAt: Date;

  /** 48 hours after `committedAt`. Null while the Check-In is unfinished: nothing is counted yet. */
  @Column({ name: 'closes_at', type: 'timestamptz', nullable: true })
  closesAt: Date | null;

  /** Set once, by the commit on `Find my date`. Null on a window nobody has checked in to. */
  @Column({ name: 'committed_at', type: 'timestamptz', nullable: true })
  committedAt: Date | null;

  /** §30, set by the commit. Class 1. */
  @Column({
    type: 'enum',
    enum: CHECK_IN_MOTIVATIONS,
    enumName: 'check_in_motivation_enum',
    nullable: true,
  })
  motivation: CheckInMotivation | null;

  /** §31, one to five, in list order, set by the commit. */
  @Column({
    type: 'enum',
    enum: CHECK_IN_VIBES,
    enumName: 'check_in_vibe_enum',
    array: true,
    nullable: true,
  })
  vibes: CheckInVibe[] | null;

  /**
   * The reason to be picked (story 06): trimmed, at most 100 graphemes, never contact info. Null is
   * "no uplift" -- never stored as "". Shown on the profile while the Check-In is active; never in
   * a log line or a tracking payload (`prohibited-fields.ts`).
   */
  @Column({ type: 'text', nullable: true })
  uplift: string | null;

  /**
   * The neighbourhood Search names next to the distance (Search 01 hero, `~3 km · Kreuzberg`), as
   * the phone's own geocoder named it at `Find my date`. Never geocoded here: the server holds no
   * geocoding key and sends no coordinates anywhere. Null when the phone could not name one; the
   * hero then shows the distance alone.
   */
  @Column({ type: 'varchar', length: 80, nullable: true })
  district: string | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
