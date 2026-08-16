import { AsyncLocalStorage } from 'async_hooks';

/**
 * Carries the current request's id alongside the work being done, so every log line written while
 * handling a request can be tagged with it — including lines written deep inside services that know
 * nothing about the request (Epic 16, SHOWUP-91).
 *
 * Uses Node's built-in AsyncLocalStorage, which follows the work across awaits, so nothing has to be
 * threaded through method signatures. Concurrent requests each get their own store.
 */

interface RequestStore {
  requestId: string;
}

const storage = new AsyncLocalStorage<RequestStore>();

/** Run `fn` (and everything it awaits) with `requestId` attached to the surrounding context. */
export function runWithRequestId<T>(requestId: string, fn: () => T): T {
  return storage.run({ requestId }, fn);
}

/** The current request's id, or undefined outside a request (for example in a background job). */
export function getRequestId(): string | undefined {
  return storage.getStore()?.requestId;
}
