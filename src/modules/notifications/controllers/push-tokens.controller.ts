import { Body, Controller, Delete, HttpCode, Post } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiCreatedResponse,
  ApiNoContentResponse,
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

  /**
   * Register this device's push token (idempotent — re-submitting the same token updates it).
   *
   * 201, Nest's default for a POST, and the contract now says so. It used to declare 200 while the
   * route answered 201, and the iOS client, which matches responses to the contract exactly, read
   * every successful registration as a failure.
   */
  @Post()
  @ApiOperation({ operationId: 'registerPushToken' })
  @ApiCreatedResponse({ type: PushTokenDto })
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
