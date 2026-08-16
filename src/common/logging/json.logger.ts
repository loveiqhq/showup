import type { LoggerService } from '@nestjs/common';

import { redactForLog } from './redact';
import { getRequestId } from './request-context';

/** Conventional level names, so log tooling can filter on them (Nest's own names differ slightly). */
const LEVELS = {
  log: 'info',
  warn: 'warn',
  error: 'error',
  debug: 'debug',
  verbose: 'trace',
  fatal: 'fatal',
} as const;

export interface JsonLoggerOptions {
  /** Where a finished line goes. Defaults to stdout; injected in tests. */
  write?: (line: string) => void;
  /** Clock, injected in tests. */
  now?: () => Date;
}

/**
 * Writes every log line as a single-line JSON record instead of a sentence (Epic 16, SHOWUP-91).
 *
 * Each record carries the same named fields — level, time, msg, the part of the code it came from, and
 * the id of the request that produced it — so lines can be filtered by field and every line belonging
 * to one request can be gathered together. Metadata passed by the caller is merged in after being
 * stripped of prohibited values, and the reserved fields always win, so a stray `msg` or `level` in
 * metadata can never disguise what actually happened.
 *
 * Installed with `app.useLogger(...)`, so the existing `new Logger('Context')` call sites throughout
 * the codebase keep working unchanged — only the shape of the output changes.
 */
export class JsonLogger implements LoggerService {
  private readonly write: (line: string) => void;
  private readonly now: () => Date;

  constructor(options: JsonLoggerOptions = {}) {
    this.write =
      options.write ?? ((line: string) => process.stdout.write(`${line}\n`));
    this.now = options.now ?? (() => new Date());
  }

  log(message: unknown, ...params: unknown[]): void {
    this.emit(LEVELS.log, message, params);
  }

  warn(message: unknown, ...params: unknown[]): void {
    this.emit(LEVELS.warn, message, params);
  }

  error(message: unknown, ...params: unknown[]): void {
    this.emit(LEVELS.error, message, params);
  }

  debug(message: unknown, ...params: unknown[]): void {
    this.emit(LEVELS.debug, message, params);
  }

  verbose(message: unknown, ...params: unknown[]): void {
    this.emit(LEVELS.verbose, message, params);
  }

  fatal(message: unknown, ...params: unknown[]): void {
    this.emit(LEVELS.fatal, message, params);
  }

  private emit(level: string, message: unknown, params: unknown[]): void {
    const rest = [...params];

    // Nest's convention is that the trailing string argument is the logging context.
    let context: string | undefined;
    if (rest.length > 0 && typeof rest[rest.length - 1] === 'string') {
      context = rest.pop() as string;
    }

    let stack: string | undefined;
    const metas: Record<string, unknown>[] = [];
    for (const param of rest) {
      if (typeof param === 'string') stack = param;
      else if (param instanceof Error) stack = param.stack;
      else if (param !== null && typeof param === 'object') {
        metas.push(param as Record<string, unknown>);
      }
    }

    let msg: string;
    if (message instanceof Error) {
      msg = message.message;
      stack = stack ?? message.stack;
    } else if (typeof message === 'string') {
      msg = message;
    } else {
      msg = safeStringify(message);
    }

    const merged: Record<string, unknown> = {};
    for (const meta of metas) {
      Object.assign(merged, redactForLog(meta) as Record<string, unknown>);
    }

    // Reserved fields are applied last so metadata can never overwrite them.
    const record: Record<string, unknown> = {
      ...merged,
      level,
      time: this.now().toISOString(),
      msg,
      context,
      requestId: getRequestId(),
      stack,
    };

    for (const key of Object.keys(record)) {
      if (record[key] === undefined) delete record[key];
    }

    this.write(safeStringify(record));
  }
}

/** Stringify that survives circular references rather than throwing inside the logger. */
function safeStringify(value: unknown): string {
  const seen = new WeakSet<object>();
  try {
    return (
      // `inner` is typed as unknown rather than left implicitly `any`, so returning it is safe and
      // the two object casts below are no longer needed — the typeof check narrows it.
      JSON.stringify(value, (_key: string, inner: unknown) => {
        if (inner !== null && typeof inner === 'object') {
          if (seen.has(inner)) return '[circular]';
          seen.add(inner);
        }
        return inner;
      }) ?? String(value)
    );
  } catch {
    return String(value);
  }
}
