import { Column, Entity, Index, PrimaryGeneratedColumn } from 'typeorm';

import type { PlaceType } from '../util/change-rules';
import type { GeoPoint } from './date-arrival-check-in.entity';

export enum ChangeKind {
  Time = 'time',
  Place = 'place',
}

export enum ChangeStatus {
  Pending = 'pending',
  Agreed = 'agreed',
  Replaced = 'replaced',
  Expired = 'expired',
}

/** Where the suggestion was made from (analytics `entry`). */
export enum ChangeEntry {
  CoordinateTile = 'coordinate_tile',
  ChatChip = 'chat_chip',
  CounterCard = 'counter_card',
  CounterThread = 'counter_thread',
  CancelSheet = 'cancel_sheet',
}

/**
 * A suggestion to move a date's meet time or meet place (Date 05, SHOWUP-196; migration
 * 1717000036000). A place is stored as a snapshot, never read back from Google: name, address and
 * point are personal data, so they stay in this table and out of logs, events and pushes.
 */
@Entity('date_change_suggestions')
@Index('UQ_date_change_suggestions_sender_client', ['senderId', 'clientId'], {
  unique: true,
})
@Index('idx_date_change_suggestions_status_deadline', ['status', 'deadline'])
export class DateChangeSuggestion {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'date_id', type: 'uuid' })
  dateId: string;

  @Column({ name: 'sender_id', type: 'uuid' })
  senderId: string;

  @Column({ type: 'enum', enum: ChangeKind, enumName: 'change_kind' })
  kind: ChangeKind;

  @Column({ name: 'value_time', type: 'timestamptz', nullable: true })
  valueTime: Date | null;

  @Column({ name: 'place_id', type: 'varchar', length: 255, nullable: true })
  placeId: string | null;

  @Column({ name: 'place_name', type: 'varchar', length: 200, nullable: true })
  placeName: string | null;

  @Column({ name: 'place_type', type: 'varchar', length: 16, nullable: true })
  placeType: PlaceType | null;

  @Column({
    name: 'place_address',
    type: 'varchar',
    length: 300,
    nullable: true,
  })
  placeAddress: string | null;

  @Column({
    name: 'place_location',
    type: 'geography',
    spatialFeatureType: 'Point',
    srid: 4326,
    nullable: true,
    select: false,
  })
  placeLocation: GeoPoint | null;

  /** The meet time in force when this was suggested; the first agreed one feeds `Changed - was`. */
  @Column({ name: 'was_time', type: 'timestamptz' })
  wasTime: Date;

  @Column({ name: 'was_venue_id', type: 'uuid', nullable: true })
  wasVenueId: string | null;

  @Column({
    name: 'was_place_name',
    type: 'varchar',
    length: 200,
    nullable: true,
  })
  wasPlaceName: string | null;

  @Column({
    type: 'enum',
    enum: ChangeStatus,
    enumName: 'change_status',
    default: ChangeStatus.Pending,
  })
  status: ChangeStatus;

  /** Meet - 0:30 when the suggestion was made. */
  @Column({ type: 'timestamptz' })
  deadline: Date;

  @Column({ type: 'enum', enum: ChangeEntry, enumName: 'change_entry' })
  entry: ChangeEntry;

  @Column({ name: 'from_search', type: 'boolean', nullable: true })
  fromSearch: boolean | null;

  /** The phone's id for this Suggest tap, so a resend is the same row. */
  @Column({ name: 'client_id', type: 'uuid' })
  clientId: string;

  @Column({ name: 'expired_binding_time', type: 'timestamptz', nullable: true })
  expiredBindingTime: Date | null;

  @Column({
    name: 'expired_binding_place_name',
    type: 'varchar',
    length: 200,
    nullable: true,
  })
  expiredBindingPlaceName: string | null;

  @Column({
    name: 'created_at',
    type: 'timestamptz',
    default: () => 'now()',
  })
  createdAt: Date;

  @Column({ name: 'answered_at', type: 'timestamptz', nullable: true })
  answeredAt: Date | null;

  @Column({ name: 'replaced_at', type: 'timestamptz', nullable: true })
  replacedAt: Date | null;

  @Column({ name: 'expired_at', type: 'timestamptz', nullable: true })
  expiredAt: Date | null;
}
