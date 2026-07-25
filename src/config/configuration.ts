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
  email: {
    // 'log' = dev stub that logs instead of sending; 'ses' = real AWS SES (eu-west-1).
    provider: process.env.EMAIL_PROVIDER ?? 'log',
    from: process.env.EMAIL_FROM ?? 'no-reply@showup.app',
    replyTo: process.env.EMAIL_REPLY_TO || undefined,
    ses: {
      region: process.env.AWS_SES_REGION ?? 'eu-west-1',
      // Left undefined → the AWS SDK default credential chain (IAM role / shared config) is used.
      accessKeyId: process.env.AWS_ACCESS_KEY_ID || undefined,
      secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || undefined,
    },
  },
  notifications: {
    // Reminders fire this many minutes before the moment they concern.
    dateReminderLeadMinutes: parseInt(
      process.env.DATE_REMINDER_LEAD_MINUTES ?? '60',
      10,
    ),
    checkInExpiryLeadMinutes: parseInt(
      process.env.CHECK_IN_EXPIRY_LEAD_MINUTES ?? '10',
      10,
    ),
    // Fallback language when a user has none set (background jobs have no request locale).
    defaultLocale: process.env.DEFAULT_LOCALE ?? 'en',
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
  auth: {
    jwtSecret: process.env.JWT_ACCESS_SECRET ?? 'dev-insecure-change-me-please',
    accessTtl: process.env.JWT_ACCESS_TTL ?? '15m',
    refreshTtl: process.env.JWT_REFRESH_TTL ?? '30d',
    otpLength: parseInt(process.env.OTP_LENGTH ?? '4', 10),
    otpTtlSeconds: parseInt(process.env.OTP_TTL ?? '300', 10),
    otpMaxAttempts: parseInt(process.env.OTP_MAX_ATTEMPTS ?? '5', 10),
    otpResendCooldownSeconds: parseInt(
      process.env.OTP_RESEND_COOLDOWN ?? '60',
      10,
    ),
    // Returns the OTP code in the API response for testing. Defaults on outside production.
    exposeOtp:
      (process.env.AUTH_EXPOSE_OTP ??
        (process.env.NODE_ENV === 'production' ? 'false' : 'true')) === 'true',
    throttleTtlMs: parseInt(process.env.THROTTLE_TTL_MS ?? '60000', 10),
    throttleLimit: parseInt(process.env.THROTTLE_LIMIT ?? '60', 10),
  },
  social: {
    googleClientIds: (process.env.GOOGLE_CLIENT_IDS ?? '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean),
    appleClientIds: (process.env.APPLE_CLIENT_IDS ?? '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean),
    appleIssuer: process.env.APPLE_ISSUER ?? 'https://appleid.apple.com',
  },
});
