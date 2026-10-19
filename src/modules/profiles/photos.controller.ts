import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Patch,
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
import { PhotoDto } from './dto/photo.dto';
import { ReorderPhotosDto } from './dto/reorder-photos.dto';
import { PhotosService } from './photos.service';

@ApiTags('profiles')
@ApiBearerAuth()
@Controller('me/photos')
export class PhotosController {
  constructor(private readonly photos: PhotosService) {}

  @Post()
  @ApiOperation({ operationId: 'uploadPhoto' })
  @UseInterceptors(FileInterceptor('file'))
  @ApiConsumes('multipart/form-data')
  // @ApiConsumes alone describes the CONTENT TYPE and nothing else, so without this the emitted
  // operation carries no requestBody at all -- and a generated client therefore exposes
  // `uploadPhoto()` with no file parameter, which is what both mobile clients had until
  // 15 September 2026. Found while wiring SHOWUP-156: the screen was finished and there was no
  // generated call that could carry a photo. The field name must stay `file`, because that is
  // what FileInterceptor above is listening for.
  @ApiBody({
    required: true,
    schema: {
      type: 'object',
      required: ['file'],
      properties: {
        file: {
          type: 'string',
          format: 'binary',
          description: 'One image. JPEG, PNG or HEIC.',
        },
      },
    },
  })
  @ApiCreatedResponse({ type: PhotoDto })
  async upload(
    @CurrentUser() user: User,
    @UploadedFile() file: Express.Multer.File,
  ): Promise<PhotoDto> {
    const photo = await this.photos.upload(user.id, file);
    return PhotoDto.from(photo, this.photos.url(photo));
  }

  @Get()
  @ApiOperation({ operationId: 'listPhotos' })
  @ApiOkResponse({ type: [PhotoDto] })
  async list(@CurrentUser() user: User): Promise<PhotoDto[]> {
    const photos = await this.photos.list(user.id);
    return photos.map((photo) => PhotoDto.from(photo, this.photos.url(photo)));
  }

  /**
   * Stores the order the user dragged the grid into (SHOWUP-156).
   *
   * PATCH rather than PUT on the collection: this changes one property of the photos that are
   * already there and creates or deletes nothing. The body carries the WHOLE order rather than a
   * from/to pair -- see `ReorderPhotosDto` for why -- which also makes it idempotent, so a retry
   * after a dropped connection is safe.
   */
  @Patch('order')
  @ApiOperation({ operationId: 'reorderPhotos' })
  @ApiOkResponse({ type: [PhotoDto] })
  async reorder(
    @CurrentUser() user: User,
    @Body() dto: ReorderPhotosDto,
  ): Promise<PhotoDto[]> {
    const photos = await this.photos.reorder(user.id, dto.ids);
    return photos.map((photo) => PhotoDto.from(photo, this.photos.url(photo)));
  }

  @Delete(':id')
  @ApiOperation({ operationId: 'deletePhoto' })
  @HttpCode(204)
  remove(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    return this.photos.remove(user.id, id);
  }
}
