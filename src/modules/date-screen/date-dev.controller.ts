import {
  Body,
  Controller,
  HttpCode,
  NotFoundException,
  Post,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  ApiBearerAuth,
  ApiNoContentResponse,
  ApiOperation,
  ApiProperty,
  ApiPropertyOptional,
  ApiTags,
} from '@nestjs/swagger';
import {
  IsEnum,
  IsInt,
  IsNotEmpty,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { ChangeKind } from '../dates/entities/date-change-suggestion.entity';
import { EndReason } from '../dates/util/date-end';
import {
  CHAT_MESSAGE_MAX_LENGTH,
  ChatReason,
} from '../dates/util/pre-date-chat';
import { User } from '../users/entities/user.entity';
import { DateDevService } from './date-dev.service';

export class CreateDevDateDto {
  @ApiProperty({
    type: 'integer',
    minimum: 1,
    maximum: 2880,
    description:
      'Meet time, minutes from now. Over 90 is state A, 90 or under is B',
  })
  @IsInt()
  @Min(1)
  @Max(2880)
  minutesFromNow: number;
}

/** The test match's message, as the real send takes it (Date 02): text, and a reason or none. */
export class DevDateMessageDto {
  @ApiPropertyOptional({ enum: ChatReason, enumName: 'ChatReason' })
  @IsOptional()
  @IsEnum(ChatReason)
  reason?: ChatReason;

  @ApiProperty({ minLength: 1, maxLength: CHAT_MESSAGE_MAX_LENGTH })
  @IsString()
  @IsNotEmpty()
  @MaxLength(CHAT_MESSAGE_MAX_LENGTH)
  body: string;
}

/** The test match's change suggestion (Date 05). */
export class DevDateChangeDto {
  @ApiProperty({ enum: ChangeKind, enumName: 'ChangeKind' })
  @IsEnum(ChangeKind)
  kind: ChangeKind;

  @ApiPropertyOptional({
    type: 'integer',
    minimum: 1,
    maximum: 720,
    default: 30,
    description:
      'kind `time`: minutes after the meet time, moved to a listed slot when there is one',
  })
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(720)
  minutesLater?: number;
}

/** How the test match ends the caller's date (Date 06). */
export class DevDatePartnerEndsDto {
  @ApiProperty({ enum: EndReason, enumName: 'DateEndReason' })
  @IsEnum(EndReason)
  reason: EndReason;
}

/**
 * Walking the Date screen on an emulator (Date 01, SHOWUP-193). Debug builds only -- the apps'
 * dev menu calls these -- and refused in production, as `PUT /dev/plan-tier` is.
 */
@ApiTags('dev')
@ApiBearerAuth()
@Controller('dev/date')
export class DateDevController {
  constructor(
    private readonly dev: DateDevService,
    private readonly config: ConfigService,
  ) {}

  @Post()
  @HttpCode(204)
  @ApiOperation({ operationId: 'createDevDate' })
  @ApiNoContentResponse()
  async create(
    @CurrentUser() user: User,
    @Body() dto: CreateDevDateDto,
  ): Promise<void> {
    this.refuseInProduction();
    await this.dev.createDate(user.id, dto.minutesFromNow);
  }

  @Post('message')
  @HttpCode(204)
  @ApiOperation({ operationId: 'sendDevDateMessage' })
  @ApiNoContentResponse()
  async message(
    @CurrentUser() user: User,
    @Body() dto: DevDateMessageDto,
  ): Promise<void> {
    this.refuseInProduction();
    await this.dev.message(user.id, dto.reason ?? null, dto.body);
  }

  @Post('end')
  @HttpCode(204)
  @ApiOperation({ operationId: 'endDevDate' })
  @ApiNoContentResponse()
  async end(@CurrentUser() user: User): Promise<void> {
    this.refuseInProduction();
    await this.dev.endDate(user.id);
  }

  /**
   * The test match ends the caller's date in the chat (Date 02), through the real path -- so it needs
   * an "I can't make it today" message in the thread, and the caller lands on H.
   */
  @Post('end-in-chat')
  @HttpCode(204)
  @ApiOperation({ operationId: 'endDevDateInChat' })
  @ApiNoContentResponse()
  async endInChat(@CurrentUser() user: User): Promise<void> {
    this.refuseInProduction();
    await this.dev.endInChat(user.id);
  }

  /**
   * The test match ends the caller's date from Date 06 (we met, no-show or uncomfortable), through
   * the real path: the caller sees the match's side of it.
   */
  @Post('partner-ends')
  @HttpCode(204)
  @ApiOperation({ operationId: 'endDevDateAsPartner' })
  @ApiNoContentResponse()
  async partnerEnds(
    @CurrentUser() user: User,
    @Body() dto: DevDatePartnerEndsDto,
  ): Promise<void> {
    this.refuseInProduction();
    await this.dev.partnerEnds(user.id, dto.reason);
  }

  /**
   * The test match taps "I'm here" and commits it (Date 03), through the real service, so the event
   * and the push to the caller fire exactly as in production.
   */
  @Post('arrival')
  @HttpCode(204)
  @ApiOperation({ operationId: 'devPartnerCheckIn' })
  @ApiNoContentResponse()
  async arrival(@CurrentUser() user: User): Promise<void> {
    this.refuseInProduction();
    await this.dev.partnerArrives(user.id);
  }

  /**
   * The test match suggests a new time or place for the caller's test date (Date 05), through the real
   * path, so the push, the event and the replacement of an open one behave as in production.
   */
  @Post('change')
  @HttpCode(204)
  @ApiOperation({ operationId: 'createDevDateChange' })
  @ApiNoContentResponse()
  async change(
    @CurrentUser() user: User,
    @Body() dto: DevDateChangeDto,
  ): Promise<void> {
    this.refuseInProduction();
    await this.dev.suggestChange(user.id, dto.kind, dto.minutesLater ?? 30);
  }

  /** The test match agrees to the caller's open suggestion (Date 05). 404 when there is none. */
  @Post('change/agree')
  @HttpCode(204)
  @ApiOperation({ operationId: 'agreeDevDateChange' })
  @ApiNoContentResponse()
  async agreeChange(@CurrentUser() user: User): Promise<void> {
    this.refuseInProduction();
    await this.dev.agreeChange(user.id);
  }

  /** Makes the test date's open suggestions due now and runs the expiry once (Date 05). */
  @Post('change/expire')
  @HttpCode(204)
  @ApiOperation({ operationId: 'expireDevDateChange' })
  @ApiNoContentResponse()
  async expireChange(@CurrentUser() user: User): Promise<void> {
    this.refuseInProduction();
    await this.dev.expireChanges(user.id);
  }

  private refuseInProduction(): void {
    if (this.config.get<string>('app.env') === 'production') {
      throw new NotFoundException();
    }
  }
}
