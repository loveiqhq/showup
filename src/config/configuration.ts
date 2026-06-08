/**
 * Typed configuration factory. Reads validated environment variables (see env.validation.ts)
 * into structured sections so each integration (db, redis, payments, push, analytics, etc.)
 * has a clear home. Loaded globally via ConfigModule in AppModule.
 */
export default () => ({
  app: {
    name: process.env.APP_NAME ?? 'showup-backend',
    env: process.env.NODE_ENV ?? 'local',
    port: parseInt(process.env.PORT ?? '3000', 10),
    corsOrigins: process.env.CORS_ORIGINS ?? '*',
  },
  database: {
    host: process.env.DB_HOST ?? 'localhost',
    port: parseInt(process.env.DB_PORT ?? '5432', 10),
    user: process.env.DB_USER ?? 'showup',
    password: process.env.DB_PASSWORD ?? 'showup',
    name: process.env.DB_NAME ?? 'showup',
    ssl: process.env.DB_SSL === 'true',
    migrationsRun: process.env.DB_MIGRATIONS_RUN === 'true',
    logging: process.env.DB_LOGGING === 'true',
  },
  redis: {
    host: process.env.REDIS_HOST ?? 'localhost',
    port: parseInt(process.env.REDIS_PORT ?? '6379', 10),
    password: process.env.REDIS_PASSWORD || undefined,
  },
  swagger: {
    enabled: process.env.SWAGGER_ENABLED !== 'false',
    path: process.env.SWAGGER_PATH ?? 'docs',
    user: process.env.SWAGGER_USER || undefined,
    password: process.env.SWAGGER_PASSWORD || undefined,
  },
  revenuecat: {
    apiKey: process.env.REVENUECAT_API_KEY || undefined,
    webhookSecret: process.env.REVENUECAT_WEBHOOK_SECRET || undefined,
  },
  fcm: {
    projectId: process.env.FCM_PROJECT_ID || undefined,
    clientEmail: process.env.FCM_CLIENT_EMAIL || undefined,
    privateKey: process.env.FCM_PRIVATE_KEY || undefined,
  },
  posthog: {
    enabled: process.env.POSTHOG_ENABLED === 'true',
    apiKey: process.env.POSTHOG_API_KEY || undefined,
    host: process.env.POSTHOG_HOST ?? 'https://eu.i.posthog.com',
  },
  sentry: {
    enabled: process.env.SENTRY_ENABLED === 'true',
    dsn: process.env.SENTRY_DSN || undefined,
    environment:
      process.env.SENTRY_ENVIRONMENT ?? process.env.NODE_ENV ?? 'local',
  },
  cms: {
    baseUrl: process.env.CMS_BASE_URL || undefined,
    apiKey: process.env.CMS_API_KEY || undefined,
  },
});
