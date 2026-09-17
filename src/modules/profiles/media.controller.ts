import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Post,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import {
  ApiBearerAuth,
  ApiBody,
  ApiConsumes,
  ApiCreatedResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { User } from '../users/entities/user.entity';
import { MediaDto, MediaStateDto, UploadMediaDto } from './dto/media.dto';
import { MediaService } from './media.service';
import { MEDIA_PROMPTS } from './util/media-prompts';

@ApiTags('profiles')
@ApiBearerAuth()
@Controller('me/media')
export class MediaController {
  constructor(private readonly media: MediaService) {}

  /**
   * Stores one take (SHOWUP-161).
   *
   * POST with no id in the path, because the caller is not choosing where it goes -- `kind` does,
   * and there is exactly one slot per kind. A second POST for the same kind REPLACES rather than
   * adding, which is what `Retake` means on this screen.
   *
   * The explicit @ApiBody is not decoration. @ApiConsumes describes the content type alone, so
   * without this the emitted operation carries no requestBody and the generated client exposes an
   * upload with no file and no fields -- the exact defect that left SHOWUP-156 with a finished
   * screen and no call that could carry a photo. The field name must stay `file` to match
   * FileInterceptor above.
   */
  @Post()
  @ApiOperation({ operationId: 'uploadMedia' })
  @UseInterceptors(FileInterceptor('file'))
  @ApiConsumes('multipart/form-data')
  @ApiBody({
    required: true,
    schema: {
      type: 'object',
      required: ['file', 'kind', 'mediaPromptId', 'durationMs'],
      properties: {
        file: {
          type: 'string',
          format: 'binary',
          description:
            'One recording. MP4/QuickTime for video, M4A/AAC for voice.',
        },
        kind: { type: 'string', enum: ['video', 'voice'] },
        mediaPromptId: {
          type: 'string',
          enum: MEDIA_PROMPTS.map((p) => p.id),
          description: 'The section-20 id of the prompt answered.',
        },
        durationMs: {
          type: 'integer',
          description: 'The take’s real length in milliseconds.',
        },
      },
    },
  })
  @ApiCreatedResponse({ type: MediaDto })
  async upload(
    @CurrentUser() user: User,
    @Body() dto: UploadMediaDto,
    @UploadedFile() file: Express.Multer.File,
  ): Promise<MediaDto> {
    const media = await this.media.upload(user.id, dto, file);
    return MediaDto.from(media, this.media.url(media));
  }

  /**
   * The whole screen's state in one read -- both artefacts and both previewed prompts.
   *
   * See MediaStateDto for why the previews travel with the artefacts rather than from a second
   * endpoint.
   */
  @Get()
  @ApiOperation({ operationId: 'getMedia' })
  @ApiOkResponse({ type: MediaStateDto })
  async state(@CurrentUser() user: User): Promise<MediaStateDto> {
    const items = await this.media.list(user.id);
    const preview = this.media.preview();
    return {
      items: items.map((m) => MediaDto.from(m, this.media.url(m))),
      previewVideo: preview.video,
      previewVoice: preview.voice,
      previewSource: preview.source,
    };
  }

  @Delete(':id')
  @ApiOperation({ operationId: 'deleteMedia' })
  @HttpCode(204)
  remove(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    return this.media.remove(user.id, id);
  }
}
