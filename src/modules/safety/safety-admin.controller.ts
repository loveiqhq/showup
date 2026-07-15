import {
  Body,
  Controller,
  Get,
  Param,
  ParseEnumPipe,
  ParseUUIDPipe,
  Patch,
  Query,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { AdminGuard } from '../auth/guards/admin.guard';
import { ProfileVerificationStatus } from '../profiles/entities/profile.entity';
import { User } from '../users/entities/user.entity';
import { SetStandingDto } from './dto/set-standing.dto';
import {
  AdminUserRowDto,
  StandingChangeDto,
  UserSafetyContextDto,
} from './dto/safety.dto';
import { ModerationService, StandingChangeResult } from './moderation.service';
import { SafetyService } from './safety.service';
import { ModerationStanding } from './util/safety';

/**
 * Staff-only moderation tools (SHOWUP-80). Locked behind {@link AdminGuard}, so ordinary users
 * cannot reach them. Every standing change is written to the append-only history by ModerationService.
 *
 * The report-review endpoints (list/open/update reports) are intentionally NOT here yet: reports
 * (SHOWUP-78) are on hold pending product decisions. They will slot in alongside these once the
 * report model exists — this controller is the seam for them.
 */
@ApiTags('admin')
@ApiBearerAuth()
@UseGuards(AdminGuard)
@Controller('admin')
export class SafetyAdminController {
  constructor(
    private readonly moderation: ModerationService,
    private readonly safety: SafetyService,
  ) {}

  /** List users currently in a given moderation standing (the staff queue). */
  @Get('users')
  @ApiOkResponse({ type: [AdminUserRowDto] })
  async listUsers(
    @Query('standing', new ParseEnumPipe(ModerationStanding))
    standing: ModerationStanding,
  ): Promise<AdminUserRowDto[]> {
    const users = await this.moderation.listUsersByStanding(standing);
    return users.map((u) => AdminUserRowDto.from(u));
  }

  /** The full safety picture of one user: standing, verification, and blocks in both directions. */
  @Get('users/:id/safety')
  @ApiOkResponse({ type: UserSafetyContextDto })
  async userSafety(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<UserSafetyContextDto> {
    const [user, profile, blocks, blockedBy] = await Promise.all([
      this.moderation.getUser(id),
      this.moderation.getUserProfile(id),
      this.safety.listBlocks(id),
      this.safety.blockedBy(id),
    ]);
    return {
      userId: user.id,
      accountStatus: user.status,
      role: user.role,
      moderationStanding: user.moderationStanding,
      verificationStatus:
        profile?.verificationStatus ?? ProfileVerificationStatus.None,
      blockedUsers: blocks.map((b) => b.blockedId),
      blockedBy,
    };
  }

  /** Set a user's standing (limit / ban / reinstate). Audited. */
  @Patch('users/:id/standing')
  @ApiOkResponse({ type: StandingChangeDto })
  async setUserStanding(
    @CurrentUser() admin: User,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: SetStandingDto,
  ): Promise<StandingChangeDto> {
    return toDto(
      await this.moderation.setUserStanding(
        id,
        dto.standing,
        admin.id,
        dto.reason,
      ),
    );
  }

  /** Set a profile's standing. Audited. */
  @Patch('profiles/:id/standing')
  @ApiOkResponse({ type: StandingChangeDto })
  async setProfileStanding(
    @CurrentUser() admin: User,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: SetStandingDto,
  ): Promise<StandingChangeDto> {
    return toDto(
      await this.moderation.setProfileStanding(
        id,
        dto.standing,
        admin.id,
        dto.reason,
      ),
    );
  }

  /** Set a photo's standing. Audited. */
  @Patch('photos/:id/standing')
  @ApiOkResponse({ type: StandingChangeDto })
  async setPhotoStanding(
    @CurrentUser() admin: User,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: SetStandingDto,
  ): Promise<StandingChangeDto> {
    return toDto(
      await this.moderation.setPhotoStanding(
        id,
        dto.standing,
        admin.id,
        dto.reason,
      ),
    );
  }
}

function toDto(r: StandingChangeResult): StandingChangeDto {
  return {
    subjectType: r.subjectType,
    subjectId: r.subjectId,
    from: r.from ?? undefined,
    to: r.to,
  };
}
