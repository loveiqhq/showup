import { redactForLog } from '../logging/redact';

/**
 * Removes personal information from an error report before it leaves the backend (Epic 16, SHOWUP-90).
 *
 * Failures tend to happen while handling exactly the data we are most careful about, so the report is
 * stripped rather than trusted: credentials in headers and cookies are dropped outright, and the body
 * and query string are run through the same redaction the logs use. The user id is kept, because
 * knowing which account hit a failure is what makes it fixable, but the email address and IP are not.
 *
 * Typed loosely on purpose so this stays a plain function that can be tested without the Sentry SDK.
 */
export function scrubSentryEvent(
  event: Record<string, any>,
): Record<string, any> {
  const request = event.request as Record<string, any> | undefined;
  if (request) {
    if (request.headers && typeof request.headers === 'object') {
      const headers = { ...(request.headers as Record<string, unknown>) };
      delete headers.authorization;
      delete headers.Authorization;
      delete headers.cookie;
      delete headers.Cookie;
      request.headers = headers;
    }

    // Cookies carry the session; there is never a reason to ship them with a crash report.
    delete request.cookies;

    if (request.data !== undefined) {
      request.data = redactForLog(request.data);
    }
    if (request.query_string !== undefined) {
      request.query_string = redactForLog(request.query_string);
    }
  }

  const user = event.user as Record<string, any> | undefined;
  if (user) {
    delete user.email;
    delete user.ip_address;
    delete user.username;
  }

  return event;
}
