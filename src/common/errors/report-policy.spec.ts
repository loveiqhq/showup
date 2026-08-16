import {
  BadRequestException,
  ForbiddenException,
  InternalServerErrorException,
  NotFoundException,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';

import { shouldReportToSentry } from './report-policy';

describe('shouldReportToSentry', () => {
  it('reports unexpected failures, which is the whole point', () => {
    expect(shouldReportToSentry(new Error('boom'))).toBe(true);
    expect(
      shouldReportToSentry(new TypeError('undefined is not a function')),
    ).toBe(true);
  });

  it('reports server-side HTTP failures', () => {
    expect(shouldReportToSentry(new InternalServerErrorException())).toBe(true);
    expect(shouldReportToSentry(new ServiceUnavailableException())).toBe(true);
  });

  it('does NOT report ordinary client mistakes, which would drown the real failures', () => {
    expect(
      shouldReportToSentry(new BadRequestException('You must be 18')),
    ).toBe(false);
    expect(shouldReportToSentry(new UnauthorizedException())).toBe(false);
    expect(shouldReportToSentry(new ForbiddenException())).toBe(false);
    expect(shouldReportToSentry(new NotFoundException())).toBe(false);
  });

  it('reports a thrown non-error value, since it still means something broke', () => {
    expect(shouldReportToSentry('a bare string')).toBe(true);
    expect(shouldReportToSentry(undefined)).toBe(true);
  });
});
