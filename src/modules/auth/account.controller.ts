import { Controller, Get, HttpCode, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';

import { UserDto } from '../users/dto/user.dto';
import { User } from '../users/entities/user.entity';
import { AccountService } from './account.service';
import { CurrentUser } from './decorators/current-user.decorator';

@ApiTags('account')
@ApiBearerAuth()
@Controller()
export class AccountController {
  constructor(private readonly account: AccountService) {}

  @Get('auth/me')
  @ApiOkResponse({ type: UserDto })
  me(@CurrentUser() user: User): UserDto {
    return UserDto.from(user);
  }

  @Post('account/delete-request')
  @HttpCode(202)
  requestDeletion(@CurrentUser() user: User): Promise<void> {
    return this.account.requestDeletion(user);
  }

  @Post('account/delete-request/cancel')
  @HttpCode(200)
  cancelDeletion(@CurrentUser() user: User): Promise<void> {
    return this.account.cancelDeletion(user);
  }
}
