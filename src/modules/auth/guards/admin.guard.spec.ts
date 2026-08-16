import { ForbiddenException } from '@nestjs/common';
import type { ExecutionContext } from '@nestjs/common';

import { UserRole } from '../../users/entities/user.entity';
import { AdminGuard } from './admin.guard';

/**
 * The guard reads the user that the global JwtAuthGuard has already put on the request, so a
 * context here is just that request. `role: undefined` models the case where the guard somehow runs
 * without an authenticated user — it must fail closed rather than let the request through.
 */
function makeContext(role: UserRole | undefined) {
  const context = {
    switchToHttp: () => ({
      getRequest: () => (role === undefined ? {} : { user: { role } }),
    }),
  } as unknown as ExecutionContext;
  return { guard: new AdminGuard(), context };
}

describe('AdminGuard', () => {
  it('allows an admin', () => {
    const { guard, context } = makeContext(UserRole.Admin);
    expect(guard.canActivate(context)).toBe(true);
  });

  it('refuses a normal user', () => {
    const { guard, context } = makeContext(UserRole.User);
    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });

  it('fails closed when there is no authenticated user on the request', () => {
    const { guard, context } = makeContext(undefined);
    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });
});
