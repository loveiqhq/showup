# OpenAPI gaps: what has to change before client generation

1 September 2026 · generated from the controllers, not written by hand

Companion to `client-architecture-spike.md` area 2. Generating clients from an
under-typed spec produces an under-typed client, which then gets patched by hand -- the
exact problem the generation is meant to remove. So these come first.

## The numbers

| | |
|---|---|
| Routes | 52 |
| Routes with a typed response | 13 |
| **Routes with NO typed response** | **39** |
| Routes declaring an `operationId` | **0** |
| A step that writes `openapi.json` | **none** |

## 1 · Emit the spec

`SwaggerModule.createDocument` runs at startup to serve the interactive docs, but nothing
writes the document to a file, so no build tool can read it. Add a script, an
`npm run openapi:emit`, and a CI step that fails if the committed file differs from the
generated one -- otherwise the spec drifts from the code and a stale spec is worse than
none, because it is trusted.

## 2 · Give every route an operationId

None of the 52 routes declares one. NestJS then invents it from the controller and method
name, so a generated client offers `authControllerVerifyPhone(...)` rather than
`verifyPhoneOtp(...)`. One decorator each:

```ts
@ApiOperation({ operationId: 'verifyPhoneOtp' })
```

## 3 · Type the 39 responses below

Each of these emits `void` or an untyped blob into both clients, losing every field.

### account.controller.ts  (2)

- `account.controller.ts:17` — `GET /auth/me`
- `account.controller.ts:33` — `POST /account/delete-request/cancel`

### auth.controller.ts  (6)

- `auth.controller.ts:33` — `POST /phone/start`
- `auth.controller.ts:53` — `POST /apple`
- `auth.controller.ts:64` — `POST /google`
- `auth.controller.ts:75` — `POST /refresh`
- `auth.controller.ts:86` — `POST /logout`
- `auth.controller.ts:96` — `POST /email/start`

### check-ins.controller.ts  (2)

- `check-ins.controller.ts:25` — `POST /`
- `check-ins.controller.ts:35` — `GET /active`

### dates.controller.ts  (5)

- `dates.controller.ts:30` — `GET /me/dates`
- `dates.controller.ts:51` — `POST /dates/:id/confirm`
- `dates.controller.ts:64` — `POST /dates/:id/no-show`
- `dates.controller.ts:76` — `GET /dates/:id/chat`
- `dates.controller.ts:87` — `POST /dates/:id/chat`

### health.controller.ts  (1)

- `health.controller.ts:23` — `GET /`

### matching.controller.ts  (3)

- `matching.controller.ts:29` — `GET /discovery`
- `matching.controller.ts:43` — `POST /likes`
- `matching.controller.ts:54` — `GET /me/matches`

### notification-preferences.controller.ts  (1)

- `notification-preferences.controller.ts:19` — `GET /`

### photos.controller.ts  (2)

- `photos.controller.ts:32` — `POST /`
- `photos.controller.ts:44` — `GET /`

### profile-admin.controller.ts  (2)

- `profile-admin.controller.ts:30` — `PATCH /profiles/:userId/verification`
- `profile-admin.controller.ts:41` — `PATCH /photos/:id/moderation`

### profiles.controller.ts  (1)

- `profiles.controller.ts:16` — `GET /`

### push-tokens.controller.ts  (2)

- `push-tokens.controller.ts:25` — `POST /`
- `push-tokens.controller.ts:35` — `DELETE /`

### safety-admin.controller.ts  (7)

- `safety-admin.controller.ts:50` — `GET /users`
- `safety-admin.controller.ts:61` — `GET /users/:id/safety`
- `safety-admin.controller.ts:85` — `PATCH /users/:id/standing`
- `safety-admin.controller.ts:103` — `PATCH /profiles/:id/standing`
- `safety-admin.controller.ts:121` — `PATCH /photos/:id/standing`
- `safety-admin.controller.ts:139` — `GET /reports`
- `safety-admin.controller.ts:150` — `GET /reports/:id`

### safety.controller.ts  (5)

- `safety.controller.ts:39` — `POST /blocks`
- `safety.controller.ts:49` — `DELETE /blocks/:targetUserId`
- `safety.controller.ts:59` — `GET /me/blocks`
- `safety.controller.ts:77` — `POST /me/verification`
- `safety.controller.ts:89` — `GET /me/verification`

## Priority

The app depends on `auth` and `matching` directly, so those come first. `safety-admin` is
internal tooling and can follow.
