import {
  Body,
  Controller,
  DefaultValuePipe,
  Get,
  ParseIntPipe,
  Post,
  Query,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { User } from '../users/entities/user.entity';
import { CreateLikeDto } from './dto/create-like.dto';
import {
  DiscoveryProfileDto,
  LikeResultDto,
  MatchDto,
} from './dto/matching.dto';
import { MatchingService } from './matching.service';

@ApiTags('matching')
@ApiBearerAuth()
@Controller()
export class MatchingController {
  constructor(private readonly matching: MatchingService) {}

  /** The discovery feed: nearby, available, eligible profiles the user hasn't liked yet. */
  @Get('discovery')
  @ApiOperation({ operationId: 'listDiscoveryProfiles' })
  @ApiOkResponse({ type: [DiscoveryProfileDto] })
  async discovery(
    @CurrentUser() user: User,
    @Query('radiusMeters', new DefaultValuePipe(50000), ParseIntPipe)
    radiusMeters: number,
  ): Promise<DiscoveryProfileDto[]> {
    const results = await this.matching.getDiscovery(user.id, radiusMeters);
    return results.map((r) =>
      DiscoveryProfileDto.from(r.profile, r.distanceMeters),
    );
  }

  /** Like a profile. Creates a match automatically if the like is mutual and both are available. */
  @Post('likes')
  @ApiOperation({ operationId: 'likeProfile' })
  @ApiOkResponse({ type: LikeResultDto })
  async like(
    @CurrentUser() user: User,
    @Body() dto: CreateLikeDto,
  ): Promise<LikeResultDto> {
    const { like, match } = await this.matching.sendLike(user.id, dto);
    return LikeResultDto.from(like, match);
  }

  /** The user's active matches. */
  @Get('me/matches')
  @ApiOperation({ operationId: 'listMatches' })
  @ApiOkResponse({ type: [MatchDto] })
  async matches(@CurrentUser() user: User): Promise<MatchDto[]> {
    const matches = await this.matching.listMatches(user.id);
    return matches.map((m) => MatchDto.from(m, user.id));
  }
}
