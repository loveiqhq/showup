import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { JwtService } from '@nestjs/jwt';
import type { Request } from 'express';

import { User, UserStatus } from '../../users/entities/user.entity';
import { UsersService } from '../../users/users.service';
import { IS_PUBLIC_KEY } from '../decorators/public.decorator';

/**
 * Global guard. Verifies the Bearer access token, loads the user, and attaches it to the request.
 * Loading the user every request means suspended/deleted accounts are blocked immediately, even
 * with an otherwise-valid (unexpired) token. Routes marked `@Public()` bypass it.
 */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly jwt: JwtService,
    private readonly users: UsersService,
  ) {}

  async canActivate(ctx: ExecutionContext): Promise<boolean> {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      ctx.getHandler(),
      ctx.getClass(),
    ]);
    if (isPublic) return true;

    const req = ctx.switchToHttp().getRequest<Request & { user?: User }>();
    const [scheme, token] = (req.headers.authorization ?? '').split(' ');
    if (scheme !== 'Bearer' || !token) {
      throw new UnauthorizedException('Missing bearer token');
    }

    let payload: { sub?: string };
    try {
      payload = await this.jwt.verifyAsync<{ sub?: string }>(token);
    } catch {
      throw new UnauthorizedException('Invalid or expired token');
    }
    if (!payload.sub) throw new UnauthorizedException('Invalid token');

    const user = await this.users.findById(payload.sub);
    if (!user) throw new UnauthorizedException('User not found');
    if (
      user.status === UserStatus.Suspended ||
      user.status === UserStatus.Deleted
    ) {
      throw new ForbiddenException('Account is not active');
    }

    req.user = user;
    return true;
  }
}
