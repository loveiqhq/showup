import { ForbiddenException } from '@nestjs/common';

import { FreshAuthGuard } from './fresh-auth.guard';

const nowSeconds = () => Math.floor(Date.now() / 1000);

function makeContext(authTime: number | undefined, maxAge: number | undefined) {
  const reflector = { getAllAndOverride: () => maxAge } as any;
  const context = {
    getHandler: () => undefined,
    getClass: () => undefined,
    switchToHttp: () => ({ getRequest: () => ({ authTime }) }),
  } as any;
  return { guard: new FreshAuthGuard(reflector), context };
}

describe('FreshAuthGuard', () => {
  it('allows routes that are not marked sensitive', () => {
    const { guard, context } = makeContext(undefined, undefined);
    expect(guard.canActivate(context)).toBe(true);
  });

  it('allows when authentication is recent', () => {
    const { guard, context } = makeContext(nowSeconds() - 10, 300);
    expect(guard.canActivate(context)).toBe(true);
  });

  it('blocks (step-up) when authentication is stale', () => {
    const { guard, context } = makeContext(nowSeconds() - 3600, 300);
    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });

  it('blocks when authTime is missing', () => {
    const { guard, context } = makeContext(undefined, 300);
    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });
});
