import { Body, Controller, Get, HttpCode, Patch, Post } from '@nestjs/common';
import {
  ApiAcceptedResponse,
  ApiBearerAuth,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { User } from '../users/entities/user.entity';
import { ProfileDto } from './dto/profile.dto';
import { UpsertProfileDto } from './dto/upsert-profile.dto';
import { ProfilesService } from './profiles.service';

@ApiTags('profiles')
@ApiBearerAuth()
@Controller('me/profile')
export class ProfilesController {
  constructor(private readonly profiles: ProfilesService) {}

  @Get()
  @ApiOperation({ operationId: 'getProfile' })
  @ApiOkResponse({ type: ProfileDto })
  async get(@CurrentUser() user: User): Promise<ProfileDto> {
    return ProfileDto.from(await this.profiles.getOrCreate(user.id));
  }

  @Post()
  @ApiOperation({ operationId: 'createProfile' })
  @ApiOkResponse({ type: ProfileDto })
  async create(
    @CurrentUser() user: User,
    @Body() dto: UpsertProfileDto,
  ): Promise<ProfileDto> {
    return ProfileDto.from(await this.profiles.update(user.id, dto));
  }

  @Patch()
  @ApiOperation({ operationId: 'updateProfile' })
  @ApiOkResponse({ type: ProfileDto })
  async update(
    @CurrentUser() user: User,
    @Body() dto: UpsertProfileDto,
  ): Promise<ProfileDto> {
    return ProfileDto.from(await this.profiles.update(user.id, dto));
  }

  @Post('verification')
  @ApiOperation({ operationId: 'requestProfileVerification' })
  @HttpCode(202)
  @ApiAcceptedResponse({ type: ProfileDto })
  async requestVerification(@CurrentUser() user: User): Promise<ProfileDto> {
    return ProfileDto.from(await this.profiles.requestVerification(user.id));
  }
}
