import { ApiProperty } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsIn, IsInt, IsString, Max, Min } from 'class-validator';

import {
  MediaModerationStatus,
  ProfileMedia,
} from '../entities/profile-media.entity';
import { MEDIA_PROMPTS, type MediaKind } from '../util/media-prompts';

/** Every id the registry knows, for validation and for the generated client's enum. */
const MEDIA_PROMPT_IDS = MEDIA_PROMPTS.map((p) => p.id);

/**
 * The hard caps, in milliseconds. 10 seconds of video, 15 of voice (SHOWUP-161).
 *
 * `TOLERANCE_MS` exists because the client stops the take at the cap and the ENCODER decides where
 * the last frame lands -- a recorder asked to stop at exactly 10.000s routinely writes 10.04s.
 * Rejecting that would fail the one take the user did everything right on, so the server accepts a
 * small overshoot and the client is still the thing enforcing the cap.
 */
export const MEDIA_MAX_MS: Readonly<Record<MediaKind, number>> = {
  video: 10_000,
  voice: 15_000,
};
export const MEDIA_DURATION_TOLERANCE_MS = 500;

/** The body of `POST /me/media`, alongside the file. Multipart, so every field arrives a string. */
export class UploadMediaDto {
  @ApiProperty({ enum: ['video', 'voice'] })
  @IsIn(['video', 'voice'])
  kind: MediaKind;

  @ApiProperty({
    enum: MEDIA_PROMPT_IDS,
    description:
      'The section-20 id of the prompt this answers. Never the display string.',
  })
  @IsString()
  @IsIn(MEDIA_PROMPT_IDS)
  mediaPromptId: string;

  @ApiProperty({
    type: 'integer',
    description: 'The take’s real length in milliseconds.',
  })
  // Multipart fields arrive as strings; without this the Min/Max below compare a string.
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(MEDIA_MAX_MS.voice + MEDIA_DURATION_TOLERANCE_MS)
  durationMs: number;
}

export class MediaDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ enum: ['video', 'voice'] })
  kind: MediaKind;

  @ApiProperty()
  url: string;

  @ApiProperty()
  mediaPromptId: string;

  @ApiProperty({ type: 'integer' })
  durationMs: number;

  @ApiProperty({ enum: MediaModerationStatus })
  moderationStatus: MediaModerationStatus;

  @ApiProperty()
  createdAt: Date;

  static from(media: ProfileMedia, url: string): MediaDto {
    return {
      id: media.id,
      kind: media.kind,
      url,
      mediaPromptId: media.mediaPromptId,
      durationMs: media.durationMs,
      moderationStatus: media.moderationStatus,
      createdAt: media.createdAt,
    };
  }
}

/**
 * Everything the media screen needs in one read.
 *
 * THE PREVIEWS TRAVEL WITH THE ARTEFACTS, and that is deliberate. `media_screen_viewed` fires on
 * every mount carrying has_video, has_voice, both preview ids and preview_source together; served
 * from two endpoints those five facts can disagree, and a view that names a preview it had not yet
 * loaded is exactly the attribution hole the tracking spec calls out. One call, one snapshot.
 */
export class MediaStateDto {
  @ApiProperty({ type: [MediaDto] })
  items: MediaDto[];

  @ApiProperty({
    enum: MEDIA_PROMPT_IDS,
    description: 'The prompt previewed on an empty video card.',
  })
  previewVideo: string;

  @ApiProperty({
    enum: MEDIA_PROMPT_IDS,
    description: 'The prompt previewed on an empty voice card.',
  })
  previewVoice: string;

  @ApiProperty({
    enum: ['ranked', 'fallback'],
    description:
      'Section 22. `ranked` came from the completion ranking; `fallback` means the ranking had ' +
      'no publishable answer and the section-20 cold-start prompt was used.',
  })
  previewSource: 'ranked' | 'fallback';
}
