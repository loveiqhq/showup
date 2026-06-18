import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';

import { FRESH_AUTH_KEY } from '../decorators/fresh-auth.decorator';

/**
 * Step-up guard for sensitive actions. Runs after the global JwtAuthGuard, which sets
 * `req.authTime` (epoch seconds of the user's last real authentication, preserved across token
 * refreshes). If the route is marked `@RequiresFreshAuth()` and the last authentication is older
 * than the allowed window, the request is rejected with `step_up_required` so the client can
 * prompt the user to re-verify (which mints a token with a fresh auth time).
 */
@Injectable()
export class FreshAuthGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(ctx: ExecutionContext): boolean {
    const maxAgeSeconds = this.reflector.getAllAndOverride<number>(
      FRESH_AUTH_KEY,
      [ctx.getHandler(), ctx.getClass()],
    );
    if (maxAgeSeconds == null) return true; // route is not marked sensitive

    const req = ctx.switchToHttp().getRequest<{ authTime?: number }>();
    const authTime = req.authTime;
    const nowSeconds = Math.floor(Date.now() / 1000);

    if (typeof authTime !== 'number' || nowSeconds - authTime > maxAgeSeconds) {
      throw new ForbiddenException({
        statusCode: 403,
        error: 'step_up_required',
        message: 'Please re-verify your identity to continue.',
      });
    }
    return true;
  }
}
