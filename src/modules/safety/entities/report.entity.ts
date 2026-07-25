import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

import {
  LatenessBucket,
  ReportContext,
  ReportReason,
  ReportStatus,
} from '../util/report';

/**
 * One person reporting another (SHOWUP-78). `context` is where it was made (active date vs. review),
 * `reason` is the picked choice, and `status` tracks progress from submitted through to resolved.
 * `dateId` is the date it relates to (no FK — dates land in Epic 7). Every report is a row here so it
 * can be traced later and worked through in the staff review list.
 */
@Entity('reports')
@Index('idx_reports_reported', ['reportedUserId'])
@Index('idx_reports_status', ['status'])
export class Report {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'reporter_id', type: 'uuid' })
  reporterId: string;

  @Column({ name: 'reported_user_id', type: 'uuid' })
  reportedUserId: string;

  @Column({
    type: 'enum',
    enum: ReportContext,
    enumName: 'report_context_enum',
  })
  context: ReportContext;

  @Column({
    type: 'enum',
    enum: ReportReason,
    enumName: 'report_reason_enum',
  })
  reason: ReportReason;

  /** Only set for the "showed up late" reason. */
  @Column({
    type: 'enum',
    enum: LatenessBucket,
    enumName: 'report_lateness_enum',
    nullable: true,
  })
  lateness: LatenessBucket | null;

  /** The date this report is about, once dates exist (Epic 7). No FK, so this stays independent. */
  @Column({ name: 'date_id', type: 'uuid', nullable: true })
  dateId: string | null;

  /** Optional free-text the reporter adds. */
  @Column({ type: 'varchar', length: 1000, nullable: true })
  note: string | null;

  @Column({
    type: 'enum',
    enum: ReportStatus,
    enumName: 'report_status_enum',
    default: ReportStatus.Submitted,
  })
  status: ReportStatus;

  /** The staff member who last acted on the report. */
  @Column({ name: 'handled_by_user_id', type: 'uuid', nullable: true })
  handledByUserId: string | null;

  @Column({
    name: 'resolution_note',
    type: 'varchar',
    length: 1000,
    nullable: true,
  })
  resolutionNote: string | null;

  @Column({ name: 'resolved_at', type: 'timestamptz', nullable: true })
  resolvedAt: Date | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
