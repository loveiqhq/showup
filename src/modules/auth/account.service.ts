import { BadRequestException, Injectable } from '@nestjs/common';

import { User, UserStatus } from '../users/entities/user.entity';
import { UsersService } from '../users/users.service';
import { AuditService } from './audit.service';
import { TokenService } from './token.service';

@Injectable()
export class AccountService {
  constructor(
    private readonly users: UsersService,
    private readonly tokens: TokenService,
    private readonly audit: AuditService,
  ) {}

  /**
   * Story 2.6: move the account to `deletion_pending`, log out everywhere, and audit. The timed
   * final purge (anonymize) runs later via a scheduled job (Epic 17); `UsersService.anonymize`
   * already implements the scrub.
   */
  async requestDeletion(user: User): Promise<void> {
    await this.users.requestDeletion(user);
    await this.tokens.revokeAllForUser(user.id);
    await this.audit.record('account.delete_requested', user.id);
  }

  async cancelDeletion(user: User): Promise<void> {
    if (user.status !== UserStatus.DeletionPending) {
      throw new BadRequestException('No pending deletion to cancel');
    }
    await this.users.cancelDeletion(user);
    await this.audit.record('account.delete_cancelled', user.id);
  }
}
