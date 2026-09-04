# The OpenAPI contract

4 September 2026 · replaces `openapi-gaps.md`, whose numbers were wrong

`openapi.json` at the repository root is the contract the iOS and Android clients will be generated
from. It is produced by the backend code and committed, and CI fails if the two disagree.

## Using it

```bash
npm run openapi:emit      # regenerate openapi.json from the NestJS app
```

Run it whenever a controller, DTO or response type changes, and commit the result. CI regenerates
and runs `git diff --exit-code openapi.json`, so a stale spec fails the build rather than silently
misleading both apps.

**The file is committed deliberately.** Three alternatives were considered — generated only in CI,
downloaded during mobile builds, published as an artifact. Committing wins here because backend and
both apps live in one repository, so it is one file rather than a distribution problem, and because
a client build must never require a running server or a network to succeed. The drift check is what
makes committing safe: the committed copy cannot diverge from the code.

**It never needs a database.** The emit script uses Nest's `preview: true` mode, which builds the
module graph and reads every decorator without instantiating providers, so `DatabaseModule` and
`QueueModule` never open a connection.

**Output is byte-stable.** Object keys are sorted before writing (arrays are left alone — their
order is meaningful). Without that, key reordering between runs would produce phantom diffs and the
drift check would be noise.

## Current state

| | |
|---|---|
| Operations | **52** across 44 paths |
| Explicit `operationId` | **52 of 52** |
| NestJS-generated names remaining | **0** |
| Success responses typed or explicitly bodyless | **52 of 52** |
| Schemas | 45 |
| Enum properties | 36 |
| Nullable properties | 12 |
| Untyped schema properties | **0** |
| Security scheme | `bearer`, HTTP, JWT |
| Operations requiring auth | 42 |
| Public operations | 10 — the eight auth routes plus two health probes |

## What the correction was

An earlier document reported **13 typed and 39 untyped responses**. That was wrong. It came from a
pattern that looked for `@ApiResponse({ ... type: ... })` and did not match `@ApiOkResponse({ type:
... })`, which is the form this codebase uses everywhere. The real figure before any work was 43
typed, 5 explicit `204`, and 4 genuine problems.

It also reported **26 `: any` occurrences as untyped holes in the generated client**. All 26 are in
`.spec.ts` test doubles. None is in a DTO or a controller, and the emitted schemas contain zero
untyped properties. They were left alone.

The lesson is the one already in the spike: a measurement is only as good as the pattern behind it,
and this one was never checked against the emitted document. Every figure in this file comes from
`openapi.json` itself.

## What was actually fixed

**Four real contract bugs**, all of which would have produced broken generated clients:

| | Route | Problem |
|---|---|---|
| Status mismatch | `POST /dates/{id}/chat` | `@HttpCode(201)` documented as `200`, so a generated client treats the real response as undocumented |
| Status mismatch | `POST /me/profile/verification` | `@HttpCode(202)` documented as `200` |
| Nullable | `GET /me/check-ins/active` | Returns `CheckInDto \| null`; the contract said non-null, so Swift would generate a non-optional and fail to decode when a user has no active check-in |
| Nullable | `GET /me/verification` | Returns `VerificationDto \| null`, same problem |

**Two routes made explicit** — `POST /account/delete-request` (202) and `.../cancel` (200) both
return `Promise<void>` and had no declared response. They are now declared as bodyless with a
description. No DTO was invented for them, because there is no body to describe.

**Fifty-two operationIds**, replacing names NestJS derived from the controller and method. A
generated client now offers `verifyPhone(...)` rather than `AuthController_verifyPhone(...)`.

**An error schema.** Before this, the document declared no error responses at all, so a generated
client had no type for a failure and every screen would have parsed the body by hand. `ApiErrorDto`
describes what the backend already returns — `{ statusCode, message, error }` — including the fact
that `message` is a string array when request validation fails.

## How the error responses are attached

Not by 150 decorators. `applyStandardErrorResponses` in `src/openapi.config.ts` reads the rules off
the document itself, so they cannot drift from it:

| Code | Added when | Because |
|---|---|---|
| `400` | the operation has a request body or parameters | the global `ValidationPipe` can reject it |
| `401` | the operation declares `security` | a missing or expired bearer always fails this way |
| `500` | always | an unhandled error reaches `BaseExceptionFilter` |

**`403` and `404` are deliberately not added.** Whether a route can forbid or not-find depends on
its guards and its lookups, and inferring that would be guessing. Routes that return them should
declare them explicitly.

`src/openapi.config.ts` is shared by `main.ts` and the emit script, so the interactive docs and the
committed contract are always the same document.

## Naming convention

`verbNoun`, unique across the API, no controller prefix. Where a name was ambiguous it was resolved
by reading the implementation rather than the route:

| Route | operationId | Why |
|---|---|---|
| `POST /me/verification` | `submitSelfieVerification` | runs a selfie-verification attempt |
| `GET /me/verification` | `getSelfieVerification` | the latest attempt, nullable |
| `POST /me/profile/verification` | `requestProfileVerification` | a different concept — asks for profile verification |
| `PATCH /admin/profiles/{userId}/verification` | `setProfileVerificationStatus` | the admin action on that request |

Four routes across three controllers all called something "verification". That is worth knowing
before adding a fifth.

## Remaining gaps

| Gap | Severity | Note |
|---|---|---|
| No pagination model | **Medium** | No list endpoint pages yet. Define one shared DTO before the first one does, or each will invent its own |
| `403` / `404` undeclared | Low | Deliberate. Add per route where a guard or lookup genuinely produces them |
| `message` is `string \| string[]` | Low | Real, and declared honestly. Both clients must handle the union; narrowing it would break decoding on exactly the errors users hit most |
| No `@ApiProperty` audit | Low | 150 are present and no untyped properties reach the schema, so this is polish |

None of these blocks client generation.

## Next: generating the clients

The contract is ready. Neither generator is installed yet — that is deliberately the next task, not
this one.

**Android first**, because it proves the pipeline end to end on the platform with the faster build:
the OpenAPI Generator Gradle plugin, `kotlin` generator, `jvm-retrofit2` library,
`kotlinx_serialization`, output into `build/` and never committed.

**Then iOS**: `apple/swift-openapi-generator` as a build plugin, so generation happens during the
build and nothing generated is committed or editable.

**Then the auth layer on both** — Keychain and `EncryptedSharedPreferences`, with a single-flight
refresh so a burst of concurrent 401s produces one refresh rather than ten.

Details for all three are in `mobile-client-architecture-spike.md`, part 2.
