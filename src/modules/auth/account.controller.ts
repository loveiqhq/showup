import { Controller, Get, HttpCode, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';

import { UserDto } from '../users/dto/user.dto';
import { User } from '../users/entities/user.entity';
import { AccountService } from './account.service';
import { CurrentUser } from './decorators/current-user.decorator';
import { RequiresFreshAuth } from './decorators/fresh-auth.decorator';
import { FreshAuthGuard } from './guards/fresh-auth.guard';

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

  // Sensitive action: requires recent authentication (step-up). A stale session gets a
  // `step_up_required` error and must re-verify before deleting.
  @Post('account/delete-request')
  @HttpCode(202)
  @RequiresFreshAuth()
  @UseGuards(FreshAuthGuard)
  requestDeletion(@CurrentUser() user: User): Promise<void> {
    return this.account.requestDeletion(user);
  }

  @Post('account/delete-request/cancel')
  @HttpCode(200)
  cancelDeletion(@CurrentUser() user: User): Promise<void> {
    return this.account.cancelDeletion(user);
  }
}
