import { Controller, Get, NotFoundException, Query } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOkResponse,
  ApiOperation,
  ApiQuery,
  ApiTags,
} from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { SearchProfileDto } from '../search/dto/search.dto';
import { User } from '../users/entities/user.entity';
import { DateScreenService } from './date-screen.service';
import { ActiveDateResponseDto } from './dto/active-date.dto';

const TZ_QUERY = {
  name: 'tz',
  required: false,
  description:
    "The viewer's IANA time zone, for meetDay (today / tomorrow by the Check-In's 7am day)",
};

/** The Date screen's reads (Date 01, SHOWUP-193). Tag `dates`, beside the rest of a date's calls. */
@ApiTags('dates')
@ApiBearerAuth()
@Controller('me/date')
export class DateScreenController {
  constructor(private readonly dates: DateScreenService) {}

  /**
   * Everything the Date screen renders; `date` is null when there is no active date, and then `ended`
   * carries an ended-in-chat outcome the viewer has still to see (Date 02).
   */
  @Get()
  @ApiOperation({ operationId: 'getActiveDate' })
  @ApiQuery(TZ_QUERY)
  @ApiOkResponse({ type: ActiveDateResponseDto })
  async activeDate(
    @CurrentUser() user: User,
    @Query('tz') tz?: string,
  ): Promise<ActiveDateResponseDto> {
    const date = await this.dates.activeDate(user.id, tz);
    return {
      date,
      ended: date ? null : await this.dates.endedOutcome(user.id),
    };
  }

  /** "See {name}'s full profile": the match, read-only, built as Search builds a profile. */
  @Get('profile')
  @ApiOperation({ operationId: 'getActiveDateProfile' })
  @ApiQuery(TZ_QUERY)
  @ApiOkResponse({ type: SearchProfileDto })
  async partnerProfile(
    @CurrentUser() user: User,
    @Query('tz') tz?: string,
  ): Promise<SearchProfileDto> {
    const profile = await this.dates.partnerProfile(user.id, tz);
    if (!profile) throw new NotFoundException('No active date');
    return profile;
  }
}
