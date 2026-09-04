import {
  Body,
  Controller,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Post,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiExtraModels,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
  getSchemaPath,
} from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { User } from '../users/entities/user.entity';
import { CheckInsService } from './check-ins.service';
import { CheckInDto } from './dto/check-in.dto';
import { CreateCheckInDto } from './dto/create-check-in.dto';

@ApiTags('check-ins')
@ApiBearerAuth()
@Controller('me/check-ins')
export class CheckInsController {
  constructor(private readonly checkIns: CheckInsService) {}

  /** Check in: become available for matching for the given window. */
  @Post()
  @ApiOperation({ operationId: 'createCheckIn' })
  @ApiOkResponse({ type: CheckInDto })
  async create(
    @CurrentUser() user: User,
    @Body() dto: CreateCheckInDto,
  ): Promise<CheckInDto> {
    return CheckInDto.from(await this.checkIns.create(user.id, dto));
  }

  /** Fetch the user's currently-active check-in (or null if they are not checked in). */
  @Get('active')
  @ApiOperation({ operationId: 'getActiveCheckIn' })
  @ApiExtraModels(CheckInDto)
  @ApiOkResponse({
    description: 'The active check-in, or null if the user has none.',
    schema: { allOf: [{ $ref: getSchemaPath(CheckInDto) }], nullable: true },
  })
  async active(@CurrentUser() user: User): Promise<CheckInDto | null> {
    const checkIn = await this.checkIns.findActive(user.id);
    return checkIn ? CheckInDto.from(checkIn) : null;
  }

  /** Cancel one of the user's check-ins. */
  @Post(':id/cancel')
  @ApiOperation({ operationId: 'cancelCheckIn' })
  @HttpCode(200)
  @ApiOkResponse({ type: CheckInDto })
  async cancel(
    @CurrentUser() user: User,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<CheckInDto> {
    return CheckInDto.from(await this.checkIns.cancel(user.id, id));
  }
}
