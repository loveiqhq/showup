import * as Joi from 'joi';

/**
 * Joi schema validating environment variables at boot. The app fails fast (and reports every
 * problem, not just the first — abortEarly:false in AppModule) when a required variable is
 * missing or malformed. Required vars are limited to what the backend cannot run without (the
 * database); integration credentials are optional so each environment can enable them as needed.
 */
export const validationSchema = Joi.object({
  // Application
  NODE_ENV: Joi.string()
    .valid('local', 'development', 'staging', 'production', 'test')
    .default('local'),
  PORT: Joi.number().port().default(3000),
  APP_NAME: Joi.string().default('showup-backend'),
  CORS_ORIGINS: Joi.string().default('*'),

  // Database (required)
  DB_HOST: Joi.string().required(),
  DB_PORT: Joi.number().port().default(5432),
  DB_USER: Joi.string().required(),
  DB_PASSWORD: Joi.string().required(),
  DB_NAME: Joi.string().required(),
  DB_SSL: Joi.boolean().default(false),
  DB_MIGRATIONS_RUN: Joi.boolean().default(false),
  DB_LOGGING: Joi.boolean().default(false),

  // Redis (used by BullMQ — Epic 17)
  REDIS_HOST: Joi.string().default('localhost'),
  REDIS_PORT: Joi.number().port().default(6379),
  REDIS_PASSWORD: Joi.string().allow('').optional(),

  // API docs
  SWAGGER_ENABLED: Joi.boolean().default(true),
  SWAGGER_PATH: Joi.string().default('docs'),
  SWAGGER_USER: Joi.string().allow('').optional(),
  SWAGGER_PASSWORD: Joi.string().allow('').optional(),

  // RevenueCat (Epic 9)
  REVENUECAT_API_KEY: Joi.string().allow('').optional(),
  REVENUECAT_WEBHOOK_SECRET: Joi.string().allow('').optional(),

  // Firebase Cloud Messaging (Epic 10)
  FCM_PROJECT_ID: Joi.string().allow('').optional(),
  FCM_CLIENT_EMAIL: Joi.string().allow('').optional(),
  FCM_PRIVATE_KEY: Joi.string().allow('').optional(),

  // PostHog EU (Epic 11)
  POSTHOG_ENABLED: Joi.boolean().default(false),
  POSTHOG_API_KEY: Joi.string().allow('').optional(),
  POSTHOG_HOST: Joi.string().uri().default('https://eu.i.posthog.com'),

  // Sentry (Epic 16)
  SENTRY_ENABLED: Joi.boolean().default(false),
  SENTRY_DSN: Joi.string().allow('').optional(),
  SENTRY_ENVIRONMENT: Joi.string().allow('').optional(),

  // CMS (Epic 13)
  CMS_BASE_URL: Joi.string().uri().allow('').optional(),
  CMS_API_KEY: Joi.string().allow('').optional(),
});
