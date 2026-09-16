import { Body, Controller, Get, Patch } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';

import { CurrentUser } from '../../auth/decorators/current-user.decorator';
import { User } from '../../users/entities/user.entity';
import {
  NotificationPreferencesDto,
  UpdateNotificationPreferencesDto,
} from '../dto/notification-preferences.dto';
import { NotificationPreferencesService } from '../services/notification-preferences.service';

@ApiTags('notifications')
@ApiBearerAuth()
@Controller('me/notification-preferences')
export class NotificationPreferencesController {
  constructor(private readonly prefs: NotificationPreferencesService) {}

  /** The signed-in user's current notification preferences (created with defaults on first read). */
  @Get()
  @ApiOperation({ operationId: 'getNotificationPreferences' })
  @ApiOkResponse({ type: NotificationPreferencesDto })
  async get(@CurrentUser() user: User): Promise<NotificationPreferencesDto> {
    return NotificationPreferencesDto.from(await this.prefs.ensure(user.id));
  }

  /** Change which categories of message the user receives. */
  @Patch()
  @ApiOperation({ operationId: 'updateNotificationPreferences' })
  @ApiOkResponse({ type: NotificationPreferencesDto })
  async update(
    @CurrentUser() user: User,
    @Body() dto: UpdateNotificationPreferencesDto,
  ): Promise<NotificationPreferencesDto> {
    return NotificationPreferencesDto.from(
      await this.prefs.update(user.id, dto),
    );
  }
}
