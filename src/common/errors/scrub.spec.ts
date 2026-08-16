import { REDACTED } from '../logging/redact';
import { scrubSentryEvent } from './scrub';

describe('scrubSentryEvent', () => {
  it('drops credentials carried in request headers', () => {
    const event: any = scrubSentryEvent({
      request: {
        headers: {
          authorization: 'Bearer secret-token',
          cookie: 'session=abc',
          'user-agent': 'ShowUp/1.0',
        },
      },
    });

    expect(event.request.headers.authorization).toBeUndefined();
    expect(event.request.headers.cookie).toBeUndefined();
    expect(event.request.headers['user-agent']).toBe('ShowUp/1.0');
  });

  it('drops cookies entirely', () => {
    const event: any = scrubSentryEvent({ request: { cookies: { session: 'abc' } } });
    expect(event.request.cookies).toBeUndefined();
  });

  it('redacts prohibited fields in the request body', () => {
    const event: any = scrubSentryEvent({
      request: { data: { phone: '+491701234567', code: '123456', deviceId: 'd1' } },
    });

    expect(event.request.data.phone).toBe(REDACTED);
    expect(event.request.data.code).toBe(REDACTED);
    expect(event.request.data.deviceId).toBe('d1');
  });

  it('keeps the user id but never the email or IP address', () => {
    const event: any = scrubSentryEvent({
      user: { id: 'user-1', email: 'leo@example.com', ip_address: '1.2.3.4' },
    });

    expect(event.user.id).toBe('user-1');
    expect(event.user.email).toBeUndefined();
    expect(event.user.ip_address).toBeUndefined();
  });

  it('redacts the query string, which can carry a token', () => {
    const event: any = scrubSentryEvent({
      request: { query_string: { token: 'abc', page: '2' } },
    });

    expect(event.request.query_string.token).toBe(REDACTED);
    expect(event.request.query_string.page).toBe('2');
  });

  it('leaves an event with nothing sensitive untouched', () => {
    const event: any = scrubSentryEvent({
      request: { url: '/dates/123/cancel', method: 'POST' },
      tags: { requestId: 'req-1' },
    });

    expect(event.request.url).toBe('/dates/123/cancel');
    expect(event.request.method).toBe('POST');
    expect(event.tags.requestId).toBe('req-1');
  });

  it('copes with an empty event', () => {
    expect(() => scrubSentryEvent({})).not.toThrow();
  });
});
