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
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
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

export class DevDateMessageDto {
  @ApiProperty({ enum: ChatReason, enumName: 'ChatReason' })
  @IsEnum(ChatReason)
  reason: ChatReason;

  @ApiPropertyOptional({ maxLength: CHAT_MESSAGE_MAX_LENGTH })
  @IsOptional()
  @IsString()
  @MaxLength(CHAT_MESSAGE_MAX_LENGTH)
  body?: string;
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
    await this.dev.message(user.id, dto.reason, dto.body);
  }

  @Post('end')
  @HttpCode(204)
  @ApiOperation({ operationId: 'endDevDate' })
  @ApiNoContentResponse()
  async end(@CurrentUser() user: User): Promise<void> {
    this.refuseInProduction();
    await this.dev.endDate(user.id);
  }

  private refuseInProduction(): void {
    if (this.config.get<string>('app.env') === 'production') {
      throw new NotFoundException();
    }
  }
}
