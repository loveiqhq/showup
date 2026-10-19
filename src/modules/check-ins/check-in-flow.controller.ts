import { Body, Controller, Get, HttpCode, Post, Put } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiNoContentResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
  ApiUnprocessableEntityResponse,
} from '@nestjs/swagger';

import { ApiErrorDto } from '../../common/errors/api-error.dto';
import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { User } from '../users/entities/user.entity';
import { CheckInFlowService } from './check-in-flow.service';
import {
  CheckInStateDto,
  CommitCheckInDto,
  FilterSettingsDto,
  SaveCheckInAnswersDto,
  SaveFilterSettingsDto,
} from './dto/check-in-flow.dto';

/**
 * The 48hrs Check-In flow (SHOWUP-177, stories 178 to 183). `me/check-in` (singular) is the flow;
 * `me/check-ins` (plural, Epic 4) stays the availability windows it writes.
 */
@ApiTags('check-in')
@ApiBearerAuth()
@Controller('me')
export class CheckInFlowController {
  constructor(private readonly flow: CheckInFlowService) {}

  /**
   * The Check-In state: the window, where the gate reopens, whether date search is unlocked and
   * what the steps pre-select. Read on every foreground (story 01). Opens a window if one is due.
   */
  @Get('check-in')
  @ApiOperation({ operationId: 'getCheckInState' })
  @ApiOkResponse({ type: CheckInStateDto })
  state(@CurrentUser() user: User): Promise<CheckInStateDto> {
    return this.flow.state(user.id);
  }

  /** The intro's CTA was tapped: the gate reopens at Today from now on. Idempotent. */
  @Post('check-in/intro-passed')
  @HttpCode(204)
  @ApiOperation({ operationId: 'markCheckInIntroPassed' })
  @ApiNoContentResponse()
  async introPassed(@CurrentUser() user: User): Promise<void> {
    await this.flow.markIntroPassed(user.id);
  }

  /** The last accepted Motivation and/or Vibe, saved on that step's Continue. */
  @Put('check-in/answers')
  @HttpCode(204)
  @ApiOperation({ operationId: 'saveCheckInAnswers' })
  @ApiNoContentResponse()
  async answers(
    @CurrentUser() user: User,
    @Body() dto: SaveCheckInAnswersDto,
  ): Promise<void> {
    await this.flow.saveAnswers(user.id, dto);
  }

  /** The user's filter settings -- the Filters step's Continue and Search's filter sheet. */
  @Put('filter-settings')
  @ApiOperation({ operationId: 'saveFilterSettings' })
  @ApiOkResponse({ type: FilterSettingsDto })
  filters(
    @CurrentUser() user: User,
    @Body() dto: SaveFilterSettingsDto,
  ): Promise<FilterSettingsDto> {
    return this.flow.saveFilters(user.id, dto);
  }

  /**
   * `Find my date`: commit the Check-In. Idempotent per window -- a retry answers with the same
   * state and creates nothing. 422 `uplift_contact_info` when the text carries contact info.
   */
  @Post('check-in/commit')
  @HttpCode(200)
  @ApiOperation({ operationId: 'commitCheckIn' })
  @ApiOkResponse({ type: CheckInStateDto })
  @ApiUnprocessableEntityResponse({
    type: ApiErrorDto,
    description:
      'error: uplift_contact_info -- the reason to be picked carries an email, a phone number or a link.',
  })
  async commit(
    @CurrentUser() user: User,
    @Body() dto: CommitCheckInDto,
  ): Promise<CheckInStateDto> {
    return (await this.flow.commit(user.id, dto)).state;
  }
}
