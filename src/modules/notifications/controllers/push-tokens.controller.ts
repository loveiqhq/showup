import { Body, Controller, Delete, HttpCode, Post } from '@nestjs/common';
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
  DeletePushTokenDto,
  PushTokenDto,
  RegisterPushTokenDto,
} from '../dto/push-token.dto';
import { PushTokensService } from '../services/push-tokens.service';

@ApiTags('notifications')
@ApiBearerAuth()
@Controller('me/push-tokens')
export class PushTokensController {
  constructor(private readonly pushTokens: PushTokensService) {}

  /** Register this device's push token (idempotent — re-submitting the same token updates it). */
  @Post()
  @ApiOperation({ operationId: 'registerPushToken' })
  @ApiOkResponse({ type: PushTokenDto })
  async register(
    @CurrentUser() user: User,
    @Body() dto: RegisterPushTokenDto,
  ): Promise<PushTokenDto> {
    return PushTokenDto.from(await this.pushTokens.register(user.id, dto));
  }

  /** Remove this device's push token so it stops receiving alerts (e.g. on logout). */
  @Delete()
  @ApiOperation({ operationId: 'deregisterPushToken' })
  @HttpCode(204)
  @ApiNoContentResponse()
  async deregister(
    @CurrentUser() user: User,
    @Body() dto: DeletePushTokenDto,
  ): Promise<void> {
    await this.pushTokens.deregister(user.id, dto.token);
  }
}
