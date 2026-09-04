import {
  BadRequestException,
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
import {
  ApiBearerAuth,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { AdminGuard } from '../auth/guards/admin.guard';
import { ProfileVerificationStatus } from '../profiles/entities/profile.entity';
import { User } from '../users/entities/user.entity';
import { StaffReportDto } from './dto/report.dto';
import { SetStandingDto } from './dto/set-standing.dto';
import {
  AdminUserRowDto,
  StandingChangeDto,
  UserSafetyContextDto,
} from './dto/safety.dto';
import { UpdateReportDto } from './dto/update-report.dto';
import { ModerationService, StandingChangeResult } from './moderation.service';
import { ReportsService } from './reports.service';
import { SafetyService } from './safety.service';
import { ReportStatus } from './util/report';
import { ModerationStanding } from './util/safety';

/**
 * Staff-only moderation tools (SHOWUP-80). Locked behind {@link AdminGuard}, so ordinary users
 * cannot reach them. Covers the report review list (SHOWUP-78) and moderation standing (SHOWUP-79):
 * every standing change is written to the append-only history by ModerationService.
 */
@ApiTags('admin')
@ApiBearerAuth()
@UseGuards(AdminGuard)
@Controller('admin')
export class SafetyAdminController {
  constructor(
    private readonly moderation: ModerationService,
    private readonly safety: SafetyService,
    private readonly reports: ReportsService,
  ) {}

  /** List users currently in a given moderation standing (the staff queue). */
  @Get('users')
  @ApiOperation({ operationId: 'listAdminUsers' })
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
  @ApiOperation({ operationId: 'getUserSafetyContext' })
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
  @ApiOperation({ operationId: 'setUserStanding' })
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
  @ApiOperation({ operationId: 'setProfileStanding' })
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
  @ApiOperation({ operationId: 'setPhotoStanding' })
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

  /** The report review list (SHOWUP-78), optionally filtered by status. */
  @Get('reports')
  @ApiOperation({ operationId: 'listReports' })
  @ApiOkResponse({ type: [StaffReportDto] })
  async listReports(
    @Query('status') statusRaw?: string,
  ): Promise<StaffReportDto[]> {
    const status = parseReportStatus(statusRaw);
    const reports = await this.reports.listForStaff(status);
    return reports.map((r) => StaffReportDto.fromReport(r));
  }

  /** Open one report. */
  @Get('reports/:id')
  @ApiOperation({ operationId: 'getReport' })
  @ApiOkResponse({ type: StaffReportDto })
  async getReport(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<StaffReportDto> {
    return StaffReportDto.fromReport(await this.reports.getOne(id));
  }

  /** Move a report along (reviewing / resolved / dismissed). Records who handled it. */
  @Patch('reports/:id')
  @ApiOperation({ operationId: 'updateReport' })
  @ApiOkResponse({ type: StaffReportDto })
  async updateReport(
    @CurrentUser() admin: User,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateReportDto,
  ): Promise<StaffReportDto> {
    return StaffReportDto.fromReport(
      await this.reports.updateStatus(id, admin.id, dto),
    );
  }
}

/** Validate the optional ?status= filter against the enum (undefined = all reports). */
function parseReportStatus(raw?: string): ReportStatus | undefined {
  if (raw === undefined || raw === '') return undefined;
  if (!Object.values(ReportStatus).includes(raw as ReportStatus)) {
    throw new BadRequestException('Invalid report status filter');
  }
  return raw as ReportStatus;
}

function toDto(r: StandingChangeResult): StandingChangeDto {
  return {
    subjectType: r.subjectType,
    subjectId: r.subjectId,
    from: r.from ?? undefined,
    to: r.to,
  };
}
