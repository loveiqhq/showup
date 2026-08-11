import { HttpException } from '@nestjs/common';

/**
 * Decides whether a failure is worth reporting to the error monitor (Epic 16, SHOWUP-90).
 *
 * Expected client mistakes — a validation failure, a wrong one-time code, a missing record, a rate
 * limit — are a normal part of running an API and are already visible in the logs. Reporting them
 * would bury the failures that actually need attention, so only unexpected errors and server-side
 * failures (5xx) are sent.
 */
export function shouldReportToSentry(exception: unknown): boolean {
  if (exception instanceof HttpException) {
    return exception.getStatus() >= 500;
  }
  // Anything that is not a deliberate HTTP response means something broke unexpectedly.
  return true;
}
