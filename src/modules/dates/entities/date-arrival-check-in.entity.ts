import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

import { ArrivalEntry } from '../util/arrival';

export { ArrivalEntry };

/** A GeoJSON point, longitude first. */
export interface GeoPoint {
  type: 'Point';
  coordinates: [number, number];
}

/**
 * One person's "I'm here" on one date (Date 03, SHOWUP-195). At most one row per person per date
 * (migration 1717000035000). `committedAt` is set by the Close step: until then the row can be undone
 * or replaced, and the partner sees nothing. Coordinates and accuracy stay in this table: never in a
 * log, an event, a push or a response. Deleted after a year by the arrival-retention job.
 */
@Entity('date_arrival_check_ins')
@Index('UQ_date_arrival_check_ins_date_user', ['dateId', 'userId'], {
  unique: true,
})
export class DateArrivalCheckIn {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'date_id', type: 'uuid' })
  dateId: string;

  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({
    type: 'geography',
    spatialFeatureType: 'Point',
    srid: 4326,
    select: false,
  })
  location: GeoPoint;

  @Column({ name: 'accuracy_m', type: 'double precision', nullable: true })
  accuracyM: number | null;

  @Column({
    type: 'enum',
    enum: ArrivalEntry,
    enumName: 'arrival_entry',
  })
  entry: ArrivalEntry;

  // The id the phone gave this tap, kept for a resend of the same tap.
  @Column({ name: 'client_id', type: 'uuid' })
  clientId: string;

  @Index('idx_date_arrival_check_ins_checked_in_at')
  @CreateDateColumn({ name: 'checked_in_at', type: 'timestamptz' })
  checkedInAt: Date;

  @Column({ name: 'committed_at', type: 'timestamptz', nullable: true })
  committedAt: Date | null;
}
