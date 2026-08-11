import { JsonLogger } from './json.logger';
import { REDACTED } from './redact';
import { runWithRequestId } from './request-context';

describe('JsonLogger', () => {
  let lines: string[];
  const make = () =>
    new JsonLogger({
      write: (line) => lines.push(line),
      now: () => new Date('2026-08-11T10:00:00.000Z'),
    });

  const parsed = () => lines.map((l) => JSON.parse(l));

  beforeEach(() => {
    lines = [];
  });

  it('writes one single-line JSON record per log call', () => {
    const logger = make();
    logger.log('check-in created', 'CheckInsService');

    expect(lines).toHaveLength(1);
    expect(lines[0]).not.toContain('\n');
    expect(parsed()[0]).toMatchObject({
      level: 'info',
      msg: 'check-in created',
      context: 'CheckInsService',
      time: '2026-08-11T10:00:00.000Z',
    });
  });

  it('maps each severity to a conventional level name', () => {
    const logger = make();
    logger.log('a');
    logger.warn('b');
    logger.error('c');
    logger.debug('d');
    logger.verbose('e');

    expect(parsed().map((r) => r.level)).toEqual([
      'info',
      'warn',
      'error',
      'debug',
      'trace',
    ]);
  });

  it('attaches the request id when logging inside a request', () => {
    const logger = make();
    runWithRequestId('req-9', () => logger.log('liked', 'MatchingService'));

    expect(parsed()[0].requestId).toBe('req-9');
  });

  it('omits the request id outside a request, rather than inventing one', () => {
    const logger = make();
    logger.log('scheduler tick', 'CheckInExpiryScheduler');

    expect(parsed()[0].requestId).toBeUndefined();
  });

  it('merges structured metadata into the record', () => {
    const logger = make();
    logger.log('notification dispatched', { channel: 'push', delivered: 2 }, 'Dispatch');

    expect(parsed()[0]).toMatchObject({
      msg: 'notification dispatched',
      context: 'Dispatch',
      channel: 'push',
      delivered: 2,
    });
  });

  it('redacts prohibited fields in metadata', () => {
    const logger = make();
    logger.log('otp sent', { phone: '+491701234567', attempts: 1 }, 'OtpService');

    const record = parsed()[0];
    expect(record.phone).toBe(REDACTED);
    expect(record.attempts).toBe(1);
  });

  it('never lets metadata overwrite the reserved fields', () => {
    const logger = make();
    logger.log('real message', { msg: 'spoofed', level: 'debug' }, 'Ctx');

    expect(parsed()[0].msg).toBe('real message');
    expect(parsed()[0].level).toBe('info');
  });

  it('records an error stack when given one', () => {
    const logger = make();
    const err = new Error('boom');
    logger.error('dispatch failed', err.stack, 'Dispatch');

    const record = parsed()[0];
    expect(record.level).toBe('error');
    expect(record.msg).toBe('dispatch failed');
    expect(String(record.stack)).toContain('boom');
  });

  it('serialises an Error passed as the message', () => {
    const logger = make();
    logger.error(new Error('kaboom'));

    const record = parsed()[0];
    expect(record.msg).toContain('kaboom');
    expect(String(record.stack)).toContain('kaboom');
  });
});
