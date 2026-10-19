import { Body, Controller, Get, HttpCode, Put } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiNoContentResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';

import { CurrentUser } from '../../auth/decorators/current-user.decorator';
import { User } from '../../users/entities/user.entity';
import {
  PermissionStatusDto,
  SavePermissionStatusesDto,
} from '../dto/permission-status.dto';
import { PermissionStatusesService } from '../services/permission-statuses.service';

@ApiTags('permissions')
@ApiBearerAuth()
@Controller('me/permissions')
export class PermissionStatusesController {
  constructor(private readonly permissions: PermissionStatusesService) {}

  /** The phone's permissions as just read -- sent by the apps whenever one moves. */
  @Put()
  @ApiOperation({ operationId: 'savePermissionStatuses' })
  @HttpCode(204)
  @ApiNoContentResponse()
  async save(
    @CurrentUser() user: User,
    @Body() dto: SavePermissionStatusesDto,
  ): Promise<void> {
    await this.permissions.save(user.id, dto);
  }

  /** What the server last heard about each of the user's permissions. */
  @Get()
  @ApiOperation({ operationId: 'getPermissionStatuses' })
  @ApiOkResponse({ type: PermissionStatusDto, isArray: true })
  async list(@CurrentUser() user: User): Promise<PermissionStatusDto[]> {
    return (await this.permissions.list(user.id)).map((entry) =>
      PermissionStatusDto.from(entry),
    );
  }
}
