import {
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
  ApiConsumes,
  ApiCreatedResponse,
  ApiOkResponse,
  ApiTags,
} from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { User } from '../users/entities/user.entity';
import { PhotoDto } from './dto/photo.dto';
import { PhotosService } from './photos.service';

@ApiTags('profiles')
@ApiBearerAuth()
@Controller('me/photos')
export class PhotosController {
  constructor(private readonly photos: PhotosService) {}

  @Post()
  @UseInterceptors(FileInterceptor('file'))
  @ApiConsumes('multipart/form-data')
  @ApiCreatedResponse({ type: PhotoDto })
  async upload(
    @CurrentUser() user: User,
    @UploadedFile() file: Express.Multer.File,
  ): Promise<PhotoDto> {
    const photo = await this.photos.upload(user.id, file);
    return PhotoDto.from(photo, this.photos.url(photo));
  }

  @Get()
  @ApiOkResponse({ type: [PhotoDto] })
  async list(@CurrentUser() user: User): Promise<PhotoDto[]> {
    const photos = await this.photos.list(user.id);
    return photos.map((photo) => PhotoDto.from(photo, this.photos.url(photo)));
  }

  @Delete(':id')
  @HttpCode(204)
  remove(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    return this.photos.remove(user.id, id);
  }
}
