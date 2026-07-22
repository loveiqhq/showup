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
import {
  CancelDateDto,
  ChatMessageDto,
  ConfirmHappenedDto,
  DateDto,
  SendChatMessageDto,
} from './dto/date.dto';
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

  /** The pre-date chat messages on a date, oldest first (SHOWUP-115). */
  @Get('dates/:id/chat')
  @ApiOkResponse({ type: [ChatMessageDto] })
  async listChat(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<ChatMessageDto[]> {
    const messages = await this.dates.listChatMessages(id, user.id);
    return messages.map((m) => ChatMessageDto.from(m, user.id));
  }

  /** Send a pre-date chat message: pick a preset reason, optionally add a note (SHOWUP-115). */
  @Post('dates/:id/chat')
  @HttpCode(201)
  @ApiOkResponse({ type: ChatMessageDto })
  async sendChat(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: SendChatMessageDto,
  ): Promise<ChatMessageDto> {
    const message = await this.dates.sendChatMessage(
      id,
      user.id,
      dto.reason,
      dto.body,
    );
    return ChatMessageDto.from(message, user.id);
  }
}
