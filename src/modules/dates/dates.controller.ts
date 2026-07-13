import {
  Body,
  Controller,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Post,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { User } from '../users/entities/user.entity';
import { CancelDateDto, ConfirmHappenedDto, DateDto } from './dto/date.dto';
import { DatesService } from './dates.service';

@ApiTags('dates')
@ApiBearerAuth()
@Controller()
export class DatesController {
  constructor(private readonly dates: DatesService) {}

  /** The dates the user is part of. */
  @Get('me/dates')
  @ApiOkResponse({ type: [DateDto] })
  async list(@CurrentUser() user: User): Promise<DateDto[]> {
    const dates = await this.dates.listForUser(user.id);
    return dates.map((d) => DateDto.from(d, user.id));
  }

  /** Cancel a date (single cancellation type; always counts against the canceller). */
  @Post('dates/:id/cancel')
  @HttpCode(200)
  @ApiOkResponse({ type: DateDto })
  async cancel(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: CancelDateDto,
  ): Promise<DateDto> {
    const date = await this.dates.cancel(id, user.id, dto.reason);
    return DateDto.from(date, user.id);
  }

  /** Confirm the date happened and rate it. Completes once both people confirm. */
  @Post('dates/:id/confirm')
  @HttpCode(200)
  @ApiOkResponse({ type: DateDto })
  async confirm(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: ConfirmHappenedDto,
  ): Promise<DateDto> {
    const date = await this.dates.confirmHappened(id, user.id, dto.rating);
    return DateDto.from(date, user.id);
  }
}
