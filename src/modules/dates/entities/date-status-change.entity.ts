import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

/**
 * A permanent, append-only history of every date stage change (Epic 7). One row per transition, so
 * there is a full audit trail of how each date evolved. `fromStatus` is null for the initial
 * creation; `changedByUserId` is null when the system made the change.
 */
@Entity('date_status_changes')
export class DateStatusChange {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'date_id', type: 'uuid' })
  dateId: string;

  @Column({ name: 'from_status', type: 'varchar', length: 32, nullable: true })
  fromStatus: string | null;

  @Column({ name: 'to_status', type: 'varchar', length: 32 })
  toStatus: string;

  @Column({ name: 'changed_by_user_id', type: 'uuid', nullable: true })
  changedByUserId: string | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
