import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryColumn,
  UpdateDateColumn,
} from 'typeorm';

import { MEET_GENDERS, type MeetGender } from '../util/check-in-flow';

/**
 * Who a user wants to see -- ONE record per user, shared by the Check-In's Filters step and Search's
 * filter sheet (story 05: "one store with Search's filter sheet"). Written on each accepted Continue
 * there and on save in Search; never on a tap, a drag or a close.
 *
 * `meetGender` is Class 2 and never leaves the server in a tracking payload. `maxAge` null is 65+,
 * "no upper limit".
 */
@Entity('filter_settings')
export class FilterSettings {
  @PrimaryColumn({ name: 'user_id', type: 'uuid' })
  userId: string;

  /** §10 `meet_gender` -- the profile's own gender type, so the two can never drift. */
  @Column({
    name: 'meet_gender',
    type: 'enum',
    enum: MEET_GENDERS,
    enumName: 'profiles_gender_enum',
    array: true,
  })
  meetGender: MeetGender[];

  @Column({ name: 'min_age', type: 'smallint', default: 18 })
  minAge: number;

  @Column({ name: 'max_age', type: 'smallint', nullable: true })
  maxAge: number | null;

  @Column({ name: 'distance_km', type: 'smallint', default: 10 })
  distanceKm: number;

  /**
   * §10 `age_dealbreaker` (Check-In 05b): true, nobody outside the age range is ever shown; false,
   * the range is a preference matching may widen. Default false for everyone.
   */
  @Column({ name: 'age_dealbreaker', type: 'boolean', default: false })
  ageDealbreaker: boolean;

  /** §10 `distance_dealbreaker` (Check-In 05b): as `ageDealbreaker`, for the distance. */
  @Column({ name: 'distance_dealbreaker', type: 'boolean', default: false })
  distanceDealbreaker: boolean;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
