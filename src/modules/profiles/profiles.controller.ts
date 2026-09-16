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
import { ProfileProgressDto } from './dto/profile-progress.dto';
import { UpsertProfileDto } from './dto/upsert-profile.dto';
import { PhotosService } from './photos.service';
import { PromptsService } from './prompts.service';
import { ProfilesService } from './profiles.service';

@ApiTags('profiles')
@ApiBearerAuth()
@Controller('me/profile')
export class ProfilesController {
  constructor(
    private readonly profiles: ProfilesService,
    private readonly photos: PhotosService,
    private readonly prompts: PromptsService,
  ) {}

  @Get()
  @ApiOperation({ operationId: 'getProfile' })
  @ApiOkResponse({ type: ProfileDto })
  async get(@CurrentUser() user: User): Promise<ProfileDto> {
    return ProfileDto.from(await this.profiles.getOrCreate(user.id));
  }

  /**
   * Where a half-finished profile left off.
   *
   * FACTS, NOT A DECISION -- the client works out the step. See `ProfileProgressDto` for why, and
   * the profile flow README's rule 4a for the rule it serves: "on launch, an account with an
   * incomplete profile routes straight to its last incomplete step, with everything already
   * entered still present."
   *
   * One call rather than three, because this happens before the first frame on a cold launch.
   */
  @Get('progress')
  @ApiOperation({ operationId: 'getProfileProgress' })
  @ApiOkResponse({ type: ProfileProgressDto })
  async progress(@CurrentUser() user: User): Promise<ProfileProgressDto> {
    const profile = await this.profiles.getOrCreate(user.id);
    const [photos, promptCount] = await Promise.all([
      this.photos.list(user.id),
      this.prompts.count(user.id),
    ]);
    return {
      displayName: profile.displayName,
      email: user.email,
      // The column is a timestamp; the client only ever asks the yes/no question, and returning
      // the moment would be a date nothing renders.
      emailVerified: user.emailVerifiedAt !== null,
      // Whether, not what. The date of birth is the one value in the flow that cannot be changed
      // after it is set, and it has no reason to travel again once it has been stored.
      hasDateOfBirth: profile.dateOfBirth !== null,
      photoCount: photos.length,
      promptCount,
    };
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
