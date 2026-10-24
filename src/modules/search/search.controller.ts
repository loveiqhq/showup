import {
  Body,
  Controller,
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
  ApiNoContentResponse,
  ApiOkResponse,
  ApiOperation,
  ApiQuery,
  ApiTags,
} from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { User } from '../users/entities/user.entity';
import {
  CreateSearchPassDto,
  LikesOpenCountDto,
  SearchQueueDto,
  SetPlanTierDto,
} from './dto/search.dto';
import { SearchService } from './search.service';

/** Date search, story 01 (SHOWUP-184). */
@ApiTags('search')
@ApiBearerAuth()
@Controller()
export class SearchController {
  constructor(private readonly search: SearchService) {}

  /** The queue: how many profiles it holds, and the next few in full. */
  @Get('search/queue')
  @ApiOperation({ operationId: 'getSearchQueue' })
  @ApiQuery({
    name: 'tz',
    required: false,
    description:
      "The viewer's IANA time zone, for meetDay (today / tomorrow by the Check-In's 7am day)",
  })
  @ApiOkResponse({ type: SearchQueueDto })
  queue(
    @CurrentUser() user: User,
    @Query('tz') tz?: string,
  ): Promise<SearchQueueDto> {
    return this.search.queue(user.id, tz);
  }

  /** `Not now` or `Not interested`. Idempotent; no undo on this screen. */
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
