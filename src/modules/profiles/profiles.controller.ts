import { Body, Controller, Get, HttpCode, Patch, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';

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
  @ApiOkResponse({ type: ProfileDto })
  async get(@CurrentUser() user: User): Promise<ProfileDto> {
    return ProfileDto.from(await this.profiles.getOrCreate(user.id));
  }

  @Post()
  @ApiOkResponse({ type: ProfileDto })
  async create(
    @CurrentUser() user: User,
    @Body() dto: UpsertProfileDto,
  ): Promise<ProfileDto> {
    return ProfileDto.from(await this.profiles.update(user.id, dto));
  }

  @Patch()
  @ApiOkResponse({ type: ProfileDto })
  async update(
    @CurrentUser() user: User,
    @Body() dto: UpsertProfileDto,
  ): Promise<ProfileDto> {
    return ProfileDto.from(await this.profiles.update(user.id, dto));
  }

  @Post('verification')
  @HttpCode(202)
  @ApiOkResponse({ type: ProfileDto })
  async requestVerification(@CurrentUser() user: User): Promise<ProfileDto> {
    return ProfileDto.from(await this.profiles.requestVerification(user.id));
  }
}
