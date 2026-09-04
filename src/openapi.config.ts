import { DocumentBuilder } from '@nestjs/swagger';
import type { OpenAPIObject } from '@nestjs/swagger';

// Derived from the public OpenAPIObject rather than imported from a dist/ path: @nestjs/swagger's
// package exports map does not expose its internal interface files, so a deep import compiles on
// some resolution modes and not others.
type PathItem = NonNullable<OpenAPIObject['paths']>[string];
type Operation = NonNullable<PathItem['get']>;
type ResponseValue = NonNullable<Operation['responses']>[string];

/**
 * The OpenAPI document definition, in one place.
 *
 * WHY THIS IS NOT INLINE IN main.ts ANY MORE
 *
 * Two things build this document: the running server, which serves the interactive docs, and
 * `scripts/emit-openapi.ts`, which writes `openapi.json` for the mobile clients to generate from.
 * If each held its own DocumentBuilder they would drift, and the drift would be silent -- the
 * generated iOS and Android clients would be built from a contract that no longer matches the one
 * the server documents. Sharing the definition makes that impossible rather than merely unlikely.
 */
export function buildOpenApiDocument() {
  return new DocumentBuilder()
    .setTitle('ShowUp Backend API')
    .setDescription(
      'Backend API for ShowUp — real-world dating. Source of truth for users, profiles, ' +
        'check-ins, matching, dates, payments, notifications, analytics, safety and admin.',
    )
    .setVersion('0.1.0')
    .addBearerAuth()
    .addTag('health', 'Liveness & readiness probes')
    .addTag('auth', 'Registration, login, sessions (Epic 2)')
    .addTag('profiles', 'User profiles & media (Epic 3)')
    .addTag('check-ins', 'Check-in & availability (Epic 4)')
    .addTag('location', 'Proximity & PostGIS logic (Epic 5)')
    .addTag('matching', 'Discovery, likes & matches (Epic 6)')
    .addTag('dates', 'Date scheduling & lifecycle (Epic 7)')
    .addTag('payments', 'Entitlements & RevenueCat (Epic 9)')
    .addTag('notifications', 'Push & email (Epic 10)')
    .addTag('safety', 'Reports, blocks & moderation (Epic 12)')
    .addTag('admin', 'Internal operations (Epic 19)')
    .build();
}

/**
 * Attaches the standard error responses to every operation that can produce them.
 *
 * WHY THIS IS DERIVED RATHER THAN DECLARED PER ROUTE
 *
 * Writing `@ApiResponse` for 400/401/500 on all 52 routes is 150 decorators that say the same
 * three things, and the first one anybody forgets creates a silent hole in the contract. These
 * rules are read off the document itself, so they cannot drift from it:
 *
 *   401 -- the operation declares `security`, so a missing or expired bearer token always fails
 *          this way. Nothing else is assumed about authorisation.
 *   400 -- the operation takes a request body or parameters, so the global ValidationPipe
 *          (`whitelist`, `forbidNonWhitelisted`) can reject it.
 *   500 -- true of every operation: an unhandled error reaches BaseExceptionFilter.
 *
 * 403 and 404 are deliberately NOT added. Whether a specific route can forbid or not-find depends
 * on its guards and its lookups, and inferring that would be guessing -- the one thing a generated
 * contract must never do. Routes that need them should declare them explicitly.
 *
 * Existing responses are never overwritten.
 */
export function applyStandardErrorResponses(
  document: OpenAPIObject,
): OpenAPIObject {
  const errorBody = (description: string): ResponseValue => ({
    description,
    content: {
      'application/json': {
        schema: { $ref: '#/components/schemas/ApiErrorDto' },
      },
    },
  });

  const VERBS = [
    'get',
    'put',
    'post',
    'patch',
    'delete',
    'options',
    'head',
  ] as const;

  for (const pathItem of Object.values(document.paths ?? {})) {
    for (const verb of VERBS) {
      const operation: Operation | undefined = pathItem[verb];
      if (!operation) continue;

      const responses = (operation.responses ??= {});

      if (operation.requestBody || (operation.parameters?.length ?? 0) > 0) {
        responses['400'] ??= errorBody('Request validation failed.');
      }
      if (operation.security) {
        responses['401'] ??= errorBody(
          'Missing, malformed or expired bearer token.',
        );
      }
      responses['500'] ??= errorBody('Unexpected server error.');
    }
  }

  return document;
}
