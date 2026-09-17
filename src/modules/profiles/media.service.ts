import { randomUUID } from 'crypto';

import {
  BadRequestException,
  Inject,
  Injectable,
  NotFoundException,
  PayloadTooLargeException,
  UnsupportedMediaTypeException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import {
  MEDIA_DURATION_TOLERANCE_MS,
  MEDIA_MAX_MS,
  type UploadMediaDto,
} from './dto/media.dto';
import {
  MediaModerationStatus,
  ProfileMedia,
} from './entities/profile-media.entity';
import { STORAGE, type StorageService } from './storage/storage.interface';
import {
  COLD_START_PREVIEW,
  isPreviewablePromptId,
  type MediaKind,
} from './util/media-prompts';

/**
 * What each slot will accept, and what extension it is stored under.
 *
 * SPLIT BY KIND rather than one list, because "a video in the voice slot" is a real client bug and
 * a shared list would store it happily. Android's MediaRecorder writes MPEG-4 for both media;
 * iOS writes QuickTime for video and M4A for audio. `audio/m4a` is a non-standard spelling some
 * clients send for the same container, accepted so a correct take is not refused over a synonym.
 */
const ALLOWED_TYPES: Readonly<Record<MediaKind, ReadonlyMap<string, string>>> =
  {
    video: new Map([
      ['video/mp4', 'mp4'],
      ['video/quicktime', 'mov'],
    ]),
    voice: new Map([
      ['audio/mp4', 'm4a'],
      ['audio/m4a', 'm4a'],
      ['audio/aac', 'aac'],
    ]),
  };

/** Ten seconds of video is small; the ceiling is here to stop an unbounded body, not to compress. */
const MAX_BYTES: Readonly<Record<MediaKind, number>> = {
  video: 32 * 1024 * 1024,
  voice: 8 * 1024 * 1024,
};

/** Minimal shape of an uploaded file (subset of Express.Multer.File). */
export interface UploadedMedia {
  buffer: Buffer;
  mimetype: string;
  size: number;
}

export interface MediaPreview {
  video: string;
  voice: string;
  source: 'ranked' | 'fallback';
}

@Injectable()
export class MediaService {
  constructor(
    @InjectRepository(ProfileMedia)
    private readonly media: Repository<ProfileMedia>,
    @Inject(STORAGE) private readonly storage: StorageService,
    private readonly config: ConfigService,
  ) {}

  /**
   * Stores one take, REPLACING whatever occupied that slot.
   *
   * Replace rather than append: the screen has two slots and a re-record is the user swapping what
   * is in one of them. The unique index enforces it, and the old object is deleted from storage
   * after the row is updated -- that order means a crash between the two leaves an orphaned object
   * (cheap, sweepable) rather than a row pointing at bytes that are gone (a broken card).
   */
  async upload(
    userId: string,
    dto: UploadMediaDto,
    file?: UploadedMedia,
  ): Promise<ProfileMedia> {
    if (!file) throw new BadRequestException('No file uploaded');

    const ext = ALLOWED_TYPES[dto.kind].get(file.mimetype);
    if (!ext) {
      throw new UnsupportedMediaTypeException(
        dto.kind === 'video'
          ? 'A video must be MP4 or QuickTime'
          : 'A voice note must be M4A or AAC',
      );
    }
    if (file.size > MAX_BYTES[dto.kind]) {
      throw new PayloadTooLargeException(
        `A ${dto.kind} recording must be ${MAX_BYTES[dto.kind] / (1024 * 1024)} MB or smaller`,
      );
    }

    // The client stops the take at the cap; this is the backstop, with room for the encoder to
    // land a frame or two past it. See MEDIA_DURATION_TOLERANCE_MS.
    const ceiling = MEDIA_MAX_MS[dto.kind] + MEDIA_DURATION_TOLERANCE_MS;
    if (dto.durationMs > ceiling) {
      throw new BadRequestException(
        `A ${dto.kind} recording may be at most ${MEDIA_MAX_MS[dto.kind] / 1000} seconds`,
      );
    }

    const existing = await this.media.findOne({
      where: { userId, kind: dto.kind },
    });
    const previousKey = existing?.storageKey;

    const key = `${userId}/media/${randomUUID()}.${ext}`;
    await this.storage.save(key, file.buffer, file.mimetype);

    const row = this.media.create({
      ...(existing ?? {}),
      userId,
      kind: dto.kind,
      storageKey: key,
      contentType: file.mimetype,
      durationMs: dto.durationMs,
      mediaPromptId: dto.mediaPromptId,
      // A replacement is a new artefact and has not been reviewed either.
      moderationStatus: MediaModerationStatus.Pending,
    });
    const saved = await this.media.save(row);

    if (previousKey && previousKey !== key) {
      await this.storage.delete(previousKey);
    }
    return saved;
  }

  list(userId: string): Promise<ProfileMedia[]> {
    return this.media.find({
      where: { userId },
      order: { createdAt: 'ASC' },
    });
  }

  async remove(userId: string, mediaId: string): Promise<void> {
    const row = await this.media.findOne({ where: { id: mediaId, userId } });
    if (!row) throw new NotFoundException('Recording not found');
    await this.media.remove(row);
    await this.storage.delete(row.storageKey);
  }

  /**
   * Which prompt each empty card previews.
   *
   * THE SERVER DECIDES AND THE CLIENT RENDERS -- decision 33. The ranking job publishes the
   * most-completed prompt per medium into these config values; until it has run, or until it has
   * a publishable answer, the section-20 cold-start prompt stands and the source is `fallback`.
   * There is no ranking logic here and none in the client.
   *
   * ONE `source` FOR TWO MEDIA, because that is the shape section 22 registers. It reads `ranked`
   * only when BOTH prompts came from the ranking. A view where one card was a fallback is not a
   * view the ranking can be credited or blamed for, and over-reporting `ranked` would corrupt the
   * exact comparison the field exists to make. Under-reporting only withholds attribution.
   * A per-medium source would be strictly better and is raised with the ticket.
   */
  preview(): MediaPreview {
    const video = this.published('video');
    const voice = this.published('voice');
    return {
      video: video ?? COLD_START_PREVIEW.video,
      voice: voice ?? COLD_START_PREVIEW.voice,
      source: video && voice ? 'ranked' : 'fallback',
    };
  }

  /**
   * A published ranking for one medium, or undefined.
   *
   * An unrecognised id is treated as nothing published rather than passed through: the config is
   * written by a job, and a job that publishes a prompt id we retired would otherwise put an empty
   * card on every install. `own_idea` is rejected here too -- it is storable but not suggestible.
   */
  private published(kind: MediaKind): string | undefined {
    const raw = this.config
      .get<string>(`MEDIA_PREVIEW_${kind.toUpperCase()}`)
      ?.trim();
    return raw && isPreviewablePromptId(raw) ? raw : undefined;
  }

  url(media: ProfileMedia): string {
    return this.storage.publicUrl(media.storageKey);
  }
}
