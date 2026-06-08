# ShowUp Backend

Backend API for **ShowUp** — a real-world dating app. Built with **NestJS** (Node.js + TypeScript)
and **PostgreSQL + PostGIS**. This service is the source of truth for users, profiles, check-ins,
matching, dates, payments, notifications, analytics, safety and admin logic; the native iOS and
Android apps integrate through its HTTP API.

> The full multi-month roadmap (20 epics) lives in `docs/planning/esma_1.docx` — **local-only, not
> committed** (see `.gitignore`). The canonical copy is in Jira.

## Tech stack

| Concern        | Choice                                   |
| -------------- | ---------------------------------------- |
| Framework      | NestJS 11 (Express)                      |
| Language       | TypeScript                               |
| Database       | PostgreSQL 16 + PostGIS 3.4              |
| ORM / migrations | TypeORM                                |
| API docs       | OpenAPI / Swagger (`@nestjs/swagger`)    |
| Health checks  | `@nestjs/terminus`                       |
| Queues (later) | Redis + BullMQ (Epic 17 — not yet wired) |
| Tests          | Jest                                     |

## Prerequisites

- Node.js (LTS recommended) and npm
- Docker Desktop (for local Postgres/PostGIS + Redis)

## Quick start

```bash
# 1. Install dependencies
npm install

# 2. Create your local env file
cp .env.example .env          # values already match docker-compose defaults

# 3. Start Postgres/PostGIS + Redis
npm run db:up

# 4. Apply migrations (enables PostGIS, creates demo venues table)
npm run migration:run

# 5. (optional) Seed sample venues for location queries
npm run seed

# 6. Run the API
npm run start:dev
```

The API listens on `http://localhost:3000` by default.

- Liveness:  `GET /health`
- Readiness: `GET /health/ready` (includes a DB ping)
- API docs:  `http://localhost:3000/docs` (and `/docs-json`)

## Environments (Story 1.2)

`NODE_ENV` selects the environment: `local` | `development` | `staging` | `production`. All
configuration comes from environment variables (validated at boot by a Joi schema in
`src/config/env.validation.ts`); the app fails fast if a required variable is missing. Config is
grouped into sections in `src/config/configuration.ts`: `app`, `database`, `redis`, `swagger`,
`revenuecat`, `fcm`, `posthog`, `sentry`, `cms`. **No secrets are committed** — `.env` is
git-ignored; `.env.example` documents every variable.

## Database & migrations (Story 1.3)

- Connection is configured in `src/database/database.module.ts` (`synchronize` is always off).
- The TypeORM CLI uses the standalone DataSource in `src/database/data-source.ts`.

```bash
npm run migration:run        # apply pending migrations
npm run migration:revert     # roll back the last migration
npm run migration:generate -- src/database/migrations/MyChange   # generate from entity diffs
npm run seed                 # insert sample data
npm run db:down              # stop containers
```

## PostGIS / location (Story 1.4)

The first migration enables the `postgis` extension. Location data uses `geography(Point, 4326)`
columns with a GIST index (see the `venues` demo table). `LocationService`
(`src/modules/location/`) provides:

- `estimateDistanceMeters(a, b)` — pure haversine (DB-free, unit-tested)
- `distanceMeters(a, b)` — authoritative PostGIS `ST_Distance` on the WGS84 spheroid
- `isWithin(a, b, radius)` — `ST_DWithin` proximity check

Verify PostGIS end-to-end against the running DB:

```bash
npm run verify:postgis
```

**Privacy rule (from the roadmap):** locations are stored only for active check-in / proximity
logic. There is **no continuous live tracking**, raw coordinates are never returned to other users,
and they are never sent to analytics — only derived values (distances, within-radius booleans).

## API documentation (Story 1.5)

Swagger/OpenAPI is served at `/docs`. It is enabled in local/development, can be protected behind
HTTP Basic auth in staging (`SWAGGER_USER` / `SWAGGER_PASSWORD`), and disabled in production
(`SWAGGER_ENABLED=false`). Endpoints are grouped by module tag; bearer auth is documented for
protected routes.

## Project structure

```
src/
  config/         # configuration factory + env validation
  database/       # TypeORM module, CLI data source, migrations, seeds, scripts
  health/         # liveness/readiness endpoints
  modules/
    location/     # PostGIS proximity logic (live)
    auth, users, profiles, check-ins, matching, dates, payments,
    notifications, analytics, safety, admin   # placeholders for Epics 2+
  main.ts         # bootstrap: validation pipe, CORS, Swagger
  app.module.ts   # root module
```

## Tests

```bash
npm test           # unit tests (e.g. haversine) — no DB required
npm run test:e2e   # health e2e — requires the Docker DB to be up
npm run lint       # ESLint + Prettier
```

## Current status

Sprint 1 — **Epic 1: Backend Foundation & Architecture** (SHOWUP-21 … 25): NestJS project,
environments/config, PostgreSQL connection, PostGIS, and OpenAPI docs. Domain modules for later
epics exist as wired placeholders.
