import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';

import { User, UserRole } from '../../users/entities/user.entity';

/**
 * Allows the request only if the authenticated user has the admin role. Runs after the global
 * JwtAuthGuard (which loads the user, including their role, onto the request). Minimal admin
 * support that the broader admin epic (Epic 19) builds on.
 */
@Injectable()
export class AdminGuard implements CanActivate {
  canActivate(ctx: ExecutionContext): boolean {
    const req = ctx.switchToHttp().getRequest<{ user?: User }>();
    if (req.user?.role !== UserRole.Admin) {
      throw new ForbiddenException('Admin access required');
    }
    return true;
  }
}
