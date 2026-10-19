import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  Unique,
} from 'typeorm';

import { ModerationStanding } from '../../safety/util/safety';
import type { MediaKind } from '../util/media-prompts';

/** Moderation state of a recording, mirroring photos. Nothing reviews these yet -- see below. */
export enum MediaModerationStatus {
  Pending = 'pending',
  Approved = 'approved',
  Rejected = 'rejected',
}

/**
 * One recorded answer to a media prompt -- a 10-second video or a 15-second voice note.
 *
 * ONE PER KIND PER USER, enforced by the unique index rather than by the service. The screen draws
 * exactly two slots, so a second video is not a thing a user can ask for; a re-record REPLACES.
 * Putting that in the database means a retried upload over a flaky connection cannot leave an
 * account holding two videos, which is the failure the photo grid actually had.
 *
 * The bytes live in object storage (see StorageService); only metadata is here. There is no
 * `uploadStatus` column on purpose: queued / in flight / failed are states of an upload the CLIENT
 * is holding, and the server never observes them. If a row exists, the bytes are stored -- which is
 * the only status the server can honestly report.
 *
 * NOT MODERATED. `moderationStatus` exists and defaults to Pending, and nothing reads or advances
 * it. SHOWUP-161 puts moderation out of scope while stating that it must exist before media is
 * shown to other users; the column is here so the profile-view work has to look at it rather than
 * discover the gap. Do not render another user's media while this is Pending.
 */
@Entity('profile_media')
@Unique('profile_media_user_kind_key', ['userId', 'kind'])
export class ProfileMedia {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({
    type: 'enum',
    enum: ['video', 'voice'],
    enumName: 'profile_media_kind_enum',
  })
  kind: MediaKind;

  @Column({ name: 'storage_key', type: 'varchar', length: 512 })
  storageKey: string;

  @Column({ name: 'content_type', type: 'varchar', length: 100 })
  contentType: string;

  /**
   * The take's real length in milliseconds.
   *
   * Milliseconds rather than seconds because the caps are 10 and 15 and a take rounded to a whole
   * second cannot be told apart from one that hit the cap -- and `stop_reason: max_length`
   * dominating is the measurement the ticket asks for on whether the caps are right.
   */
  @Column({ name: 'duration_ms', type: 'int' })
  durationMs: number;

  /**
   * The section-20 id of the prompt this answers. Never the display string: the prompts are copy
   * and will be edited, and an edit must not orphan the recordings made under them.
   */
  @Column({ name: 'media_prompt_id', type: 'varchar', length: 64 })
  mediaPromptId: string;

  @Column({
    name: 'moderation_status',
    type: 'enum',
    enum: MediaModerationStatus,
    enumName: 'media_moderation_status_enum',
    default: MediaModerationStatus.Pending,
  })
  moderationStatus: MediaModerationStatus;

  /** Safety standing, shared with photos (Epic 12). Separate from the pre-show review above. */
  @Column({
    name: 'moderation_standing',
    type: 'enum',
    enum: ModerationStanding,
    enumName: 'moderation_standing_enum',
    default: ModerationStanding.Active,
  })
  moderationStanding: ModerationStanding;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
