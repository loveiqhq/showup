import { Catch, type ArgumentsHost } from '@nestjs/common';
import { BaseExceptionFilter } from '@nestjs/core';
import * as Sentry from '@sentry/node';

import { getRequestId } from '../logging/request-context';
import { shouldReportToSentry } from './report-policy';

/**
 * Reports unhandled failures to the error monitor, then hands the request back to Nest's own handling
 * so the HTTP response the client receives is completely unchanged (Epic 16, SHOWUP-90).
 *
 * Extending BaseExceptionFilter is deliberate: this filter adds reporting and changes nothing about
 * status codes or response bodies. Only unexpected errors and 5xx failures are reported — see
 * `shouldReportToSentry` — and each report is tagged with the request id so it can be lined up with
 * the log lines for the same request.
 *
 * When Sentry is switched off, `captureException` is a no-op, so this filter is harmless.
 */
@Catch()
export class AllExceptionsFilter extends BaseExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost): void {
    if (shouldReportToSentry(exception)) {
      const requestId = getRequestId();
      Sentry.withScope((scope) => {
        if (requestId) scope.setTag('requestId', requestId);
        Sentry.captureException(exception);
      });
    }

    super.catch(exception, host);
  }
}
