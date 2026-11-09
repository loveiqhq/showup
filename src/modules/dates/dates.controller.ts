import {
  Body,
  Controller,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Post,
} from '@nestjs/common';
import { EndedInChatDto } from '../date-screen/dto/active-date.dto';
import {
  ApiBearerAuth,
  ApiCreatedResponse,
  ApiNoContentResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';

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
  @ApiOperation({ operationId: 'listDates' })
  @ApiOkResponse({ type: [DateDto] })
  async list(@CurrentUser() user: User): Promise<DateDto[]> {
    const dates = await this.dates.listForUser(user.id);
    return dates.map((d) => DateDto.from(d, user.id));
  }

  /** Cancel a date (single cancellation type; always counts against the canceller). */
  @Post('dates/:id/cancel')
  @ApiOperation({ operationId: 'cancelDate' })
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
  @ApiOperation({ operationId: 'confirmDate' })
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

  /** Report that the other person did not show up. A date outcome — separate from reporting a problem. */
  @Post('dates/:id/no-show')
  @ApiOperation({ operationId: 'reportDateNoShow' })
  @HttpCode(200)
  @ApiOkResponse({ type: DateDto })
  async noShow(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<DateDto> {
    const date = await this.dates.reportNoShow(id, user.id);
    return DateDto.from(date, user.id);
  }

  /** The chat messages on a date, oldest first, with read receipts on your own (SHOWUP-115, Date 02). */
  @Get('dates/:id/chat')
  @ApiOperation({ operationId: 'listDateChatMessages' })
  @ApiOkResponse({ type: [ChatMessageDto] })
  async listChat(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<ChatMessageDto[]> {
    const { messages, otherReadAt } = await this.dates.listChatMessages(
      id,
      user.id,
    );
    return messages.map((m) => ChatMessageDto.from(m, user.id, otherReadAt));
  }

  /**
   * The caller has read the date's chat (Date 01, SHOWUP-193). Messages from the other person
   * after this are unread -- what moves the Date screen's Coordinate card into state C. Date 02's
   * chat calls it when the thread opens. Idempotent.
   */
  @Post('dates/:id/chat/read')
  @ApiOperation({ operationId: 'markDateChatRead' })
  @HttpCode(204)
  @ApiNoContentResponse()
  async markChatRead(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    await this.dates.markChatRead(id, user.id);
  }

  /** The caller tapped "Open the chat" on this date's tutorial (Date 02, frame A). Idempotent. */
  @Post('dates/:id/chat/tutorial')
  @ApiOperation({ operationId: 'markChatTutorialSeen' })
  @HttpCode(204)
  @ApiNoContentResponse()
  async markChatTutorialSeen(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    await this.dates.markChatTutorialSeen(id, user.id);
  }

  /**
   * Ends the date for both people from the chat (Date 02, frame F). 409 unless the date is still on and
   * an "I can't make it today" message is in its thread. The caller then sees G.
   */
  @Post('dates/:id/end-in-chat')
  @ApiOperation({ operationId: 'endDateInChat' })
  @HttpCode(200)
  @ApiOkResponse({ type: EndedInChatDto })
  async endInChat(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<EndedInChatDto> {
    return this.dates.endInChat(id, user.id);
  }

  /** The caller has seen their outcome screen, G or H, for a date ended in the chat (Date 02). */
  @Post('dates/:id/ended/seen')
  @ApiOperation({ operationId: 'markDateEndSeen' })
  @HttpCode(204)
  @ApiNoContentResponse()
  async markEndSeen(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    await this.dates.markEndSeen(id, user.id);
  }

  /** Send a chat message: the text, with the selected chip's reason or none (Date 02). */
  @Post('dates/:id/chat')
  @ApiOperation({ operationId: 'sendDateChatMessage' })
  @HttpCode(201)
  @ApiCreatedResponse({ type: ChatMessageDto })
  async sendChat(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: SendChatMessageDto,
  ): Promise<ChatMessageDto> {
    const message = await this.dates.sendChatMessage(
      id,
      user.id,
      dto.reason ?? null,
      dto.body,
      dto.clientId ?? null,
    );
    // Just sent: the other person cannot have read it yet.
    return ChatMessageDto.from(message, user.id, null);
  }
}
