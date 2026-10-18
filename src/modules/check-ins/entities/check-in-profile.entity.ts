import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryColumn,
  UpdateDateColumn,
} from 'typeorm';

import {
  CHECK_IN_MOTIVATIONS,
  CHECK_IN_VIBES,
  type CheckInMotivation,
  type CheckInVibe,
} from '../util/check-in-flow';

/**
 * What the Check-In remembers about a user between Check-Ins (one row each):
 *
 * - whether the intro has been passed -- set by its CTA, read by the gate, which reopens at Today
 *   once it has (story 01). Server-side, so it survives a reinstall.
 * - the last ACCEPTED Motivation and Vibe -- written on each accepted Continue, whether the flow
 *   was later closed or committed, and used only to pre-select those steps next time (epic rule 3).
 *   Never written on a tap or a close.
 */
@Entity('check_in_profiles')
export class CheckInProfile {
  @PrimaryColumn({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ name: 'intro_passed_at', type: 'timestamptz', nullable: true })
  introPassedAt: Date | null;

  @Column({
    name: 'last_motivation',
    type: 'enum',
    enum: CHECK_IN_MOTIVATIONS,
    enumName: 'check_in_motivation_enum',
    nullable: true,
  })
  lastMotivation: CheckInMotivation | null;

  @Column({
    name: 'last_vibes',
    type: 'enum',
    enum: CHECK_IN_VIBES,
    enumName: 'check_in_vibe_enum',
    array: true,
    nullable: true,
  })
  lastVibes: CheckInVibe[] | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
