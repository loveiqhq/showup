import { randomUUID } from 'crypto';

import { Injectable, type NestMiddleware } from '@nestjs/common';
import type { NextFunction, Request, Response } from 'express';

import { runWithRequestId } from './request-context';

/** Header used to carry the id, both inbound and outbound. */
export const REQUEST_ID_HEADER = 'x-request-id';

/**
 * Gives every request an id and runs the rest of the request inside it, so every log line produced
 * while handling it carries the same id and one action can be followed end to end (Epic 16, SHOWUP-91).
 *
 * An incoming `x-request-id` is honoured, so a trace started by the app or a proxy continues rather
 * than being broken in half. The id is returned on the response, which means a person reporting a
 * problem can quote it and the exact request can be found.
 */
@Injectable()
export class RequestIdMiddleware implements NestMiddleware {
  use(req: Request, res: Response, next: NextFunction): void {
    const incoming = req.headers[REQUEST_ID_HEADER];
    const supplied = Array.isArray(incoming) ? incoming[0] : incoming;
    const requestId =
      typeof supplied === 'string' && supplied.trim().length > 0
        ? supplied.trim()
        : randomUUID();

    res.setHeader(REQUEST_ID_HEADER, requestId);
    runWithRequestId(requestId, () => next());
  }
}
