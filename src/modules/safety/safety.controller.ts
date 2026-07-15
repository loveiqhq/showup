import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Post,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { User } from '../users/entities/user.entity';
import { CreateBlockDto } from './dto/create-block.dto';
import { RequestVerificationDto } from './dto/request-verification.dto';
import { BlockDto, VerificationDto } from './dto/safety.dto';
import { SafetyService } from './safety.service';
import { VerificationService } from './verification.service';

/** User-facing safety actions: blocking (SHOWUP-77) and selfie verification (SHOWUP-110). */
@ApiTags('safety')
@ApiBearerAuth()
@Controller()
export class SafetyController {
  constructor(
    private readonly safety: SafetyService,
    private readonly verification: VerificationService,
  ) {}

  /** Block another user. Idempotent; you cannot block yourself. */
  @Post('blocks')
  @ApiOkResponse({ type: BlockDto })
  async block(
    @CurrentUser() user: User,
    @Body() dto: CreateBlockDto,
  ): Promise<BlockDto> {
    return BlockDto.from(await this.safety.block(user.id, dto.targetUserId));
  }

  /** Lift a block. Idempotent — a no-op if there is no active block. */
  @Delete('blocks/:targetUserId')
  @HttpCode(204)
  async unblock(
    @CurrentUser() user: User,
    @Param('targetUserId', ParseUUIDPipe) targetUserId: string,
  ): Promise<void> {
    await this.safety.unblock(user.id, targetUserId);
  }

  /** The active blocks the user has in place. */
  @Get('me/blocks')
  @ApiOkResponse({ type: [BlockDto] })
  async myBlocks(@CurrentUser() user: User): Promise<BlockDto[]> {
    const blocks = await this.safety.listBlocks(user.id);
    return blocks.map((b) => BlockDto.from(b));
  }

  /** Run a selfie-verification attempt (consent required). */
  @Post('me/verification')
  @ApiOkResponse({ type: VerificationDto })
  async verify(
    @CurrentUser() user: User,
    @Body() dto: RequestVerificationDto,
  ): Promise<VerificationDto> {
    return VerificationDto.from(
      await this.verification.submit(user.id, dto.consent),
    );
  }

  /** The user's most recent verification result, or null if they have never tried. */
  @Get('me/verification')
  @ApiOkResponse({ type: VerificationDto })
  async myVerification(
    @CurrentUser() user: User,
  ): Promise<VerificationDto | null> {
    const latest = await this.verification.latest(user.id);
    return latest ? VerificationDto.from(latest) : null;
  }
}
