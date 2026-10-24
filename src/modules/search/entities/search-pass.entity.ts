import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

/** §34 `search_pass_type` -- the `pass_type` on `pass_sent`. */
export enum SearchPassType {
  NotNow = 'not_now',
  NotInterested = 'not_interested',
}

/**
 * `Not now` or `Not interested` on a Search profile (SHOWUP-184).
 *
 * - `not_now` is stored against the viewer's Check-In window: the profile is hidden until the
 *   viewer's NEXT Check-In, which is a new window, so no expiry job is needed.
 * - `not_interested` has no window: a permanent, one-way exclusion from the viewer's pool. It never
 *   touches the other person's pool.
 *
 * The database holds that shape (`search_passes_window_iff_not_now`) and one row per decision
 * (partial unique indexes), so a retried tap lands on the same row.
 */
@Entity('search_passes')
export class SearchPass {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'viewer_id', type: 'uuid' })
  viewerId: string;

  @Column({ name: 'target_id', type: 'uuid' })
  targetId: string;

  @Column({
    name: 'pass_type',
    type: 'enum',
    enum: SearchPassType,
    enumName: 'search_pass_type_enum',
  })
  passType: SearchPassType;

  @Column({ name: 'window_id', type: 'uuid', nullable: true })
  windowId: string | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
