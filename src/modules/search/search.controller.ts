import {
  Body,
  Controller,
  ForbiddenException,
  Get,
  HttpCode,
  NotFoundException,
  Post,
  Put,
  Query,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  ApiBearerAuth,
  ApiForbiddenResponse,
  ApiNoContentResponse,
  ApiOkResponse,
  ApiOperation,
  ApiQuery,
  ApiTags,
} from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { PlanTier, User } from '../users/entities/user.entity';
import {
  CreateSearchPassDto,
  LikesOpenCountDto,
  LikesReceivedDto,
  SearchAvailabilityListDto,
  SearchAvailabilityRequestDto,
  SearchGridDto,
  SearchQueueDto,
  SetPlanTierDto,
} from './dto/search.dto';
import { SearchService } from './search.service';

const TZ_QUERY = {
  name: 'tz',
  required: false,
  description:
    "The viewer's IANA time zone, for meetDay (today / tomorrow by the Check-In's 7am day)",
};

/**
 * The Premium surfaces show who liked the viewer, and the message they wrote -- "only a Premium
 * recipient may read it". The plan is the server's, read on every request, so a Freemium app that
 * asks anyway gets nothing.
 */
function requirePremium(user: User): void {
  if (user.planTier !== PlanTier.Premium) {
    throw new ForbiddenException('Premium only');
  }
}

/** Date search: story 01 (SHOWUP-184), the Premium grid (185) and the Likes tab (186). */
@ApiTags('search')
@ApiBearerAuth()
@Controller()
export class SearchController {
  constructor(private readonly search: SearchService) {}

  /** The queue: how many profiles it holds, and the next few in full. */
  @Get('search/queue')
  @ApiOperation({ operationId: 'getSearchQueue' })
  @ApiQuery(TZ_QUERY)
  @ApiOkResponse({ type: SearchQueueDto })
  queue(
    @CurrentUser() user: User,
    @Query('tz') tz?: string,
  ): Promise<SearchQueueDto> {
    return this.search.queue(user.id, tz);
  }

  /** The Premium grid (Search 02): the whole queue, each card with whether that person liked you. */
  @Get('search/grid')
  @ApiOperation({ operationId: 'getSearchGrid' })
  @ApiQuery(TZ_QUERY)
  @ApiOkResponse({ type: SearchGridDto })
  @ApiForbiddenResponse({ description: 'The account is not Premium' })
  grid(
    @CurrentUser() user: User,
    @Query('tz') tz?: string,
  ): Promise<SearchGridDto> {
    requirePremium(user);
    return this.search.grid(user.id, tz);
  }

  /**
   * The availability re-check (Search 02, 03): every card on screen in one call, on each return
   * from a profile. A read -- POST only because the list of ids is the body.
   */
  @Post('search/availability')
  @HttpCode(200)
  @ApiOperation({ operationId: 'getSearchAvailability' })
  @ApiQuery(TZ_QUERY)
  @ApiOkResponse({ type: SearchAvailabilityListDto })
  @ApiForbiddenResponse({ description: 'The account is not Premium' })
  async availability(
    @CurrentUser() user: User,
    @Body() dto: SearchAvailabilityRequestDto,
    @Query('tz') tz?: string,
  ): Promise<SearchAvailabilityListDto> {
    requirePremium(user);
    return {
      statuses: await this.search.availability(user.id, dto.userIds, tz),
    };
  }

  /** `Not now` or `Not interested`. Idempotent. `Not interested` on someone who liked you declines it. */
  @Post('search/passes')
  @HttpCode(204)
  @ApiOperation({ operationId: 'passSearchProfile' })
  @ApiNoContentResponse()
  async pass(
    @CurrentUser() user: User,
    @Body() dto: CreateSearchPassDto,
  ): Promise<void> {
    await this.search.pass(user.id, dto);
  }

  /** `Don't show again` on the actions coach. Nothing else sets it. */
  @Post('me/search-coach/hide')
  @HttpCode(204)
  @ApiOperation({ operationId: 'hideSearchCoach' })
  @ApiNoContentResponse()
  async hideCoach(@CurrentUser() user: User): Promise<void> {
    await this.search.hideCoach(user.id);
  }

  /** The open likes received -- the Likes tab badge and the likes chip. */
  @Get('me/likes/open-count')
  @ApiOperation({ operationId: 'getLikesOpenCount' })
  @ApiOkResponse({ type: LikesOpenCountDto })
  async likesOpenCount(@CurrentUser() user: User): Promise<LikesOpenCountDto> {
    return { count: await this.search.likesOpenCount(user.id) };
  }

  /**
   * The Likes tab (Search 03), once per visit: open likes, earliest meet time first, then the closed
   * ones not yet seen -- which this read marks seen.
   */
  @Get('me/likes/received')
  @ApiOperation({ operationId: 'getLikesReceived' })
  @ApiQuery(TZ_QUERY)
  @ApiOkResponse({ type: LikesReceivedDto })
  @ApiForbiddenResponse({ description: 'The account is not Premium' })
  likesReceived(
    @CurrentUser() user: User,
    @Query('tz') tz?: string,
  ): Promise<LikesReceivedDto> {
    requirePremium(user);
    return this.search.likesReceived(user.id, tz);
  }
}

/**
 * The dev menu's plan switch. Nothing sells Premium yet, so this is the only way to see the
 * Premium surfaces (Search 02, 03) before billing exists. 404 in production: it is not an
 * endpoint there, not a forbidden one.
 */
@ApiTags('dev')
@ApiBearerAuth()
@Controller('dev')
export class SearchDevController {
  constructor(
    private readonly search: SearchService,
    private readonly config: ConfigService,
  ) {}

  @Put('plan-tier')
  @HttpCode(204)
  @ApiOperation({ operationId: 'setDevPlanTier' })
  @ApiNoContentResponse()
  async setPlanTier(
    @CurrentUser() user: User,
    @Body() dto: SetPlanTierDto,
  ): Promise<void> {
    if (this.config.get<string>('app.env') === 'production') {
      throw new NotFoundException();
    }
    await this.search.setPlanTier(user.id, dto.planTier);
  }
}
