import { Logger, ValidationPipe } from '@nestjs/common';
import type { INestApplication } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { HttpAdapterHost, NestFactory } from '@nestjs/core';
import { SwaggerModule } from '@nestjs/swagger';
import type { NextFunction, Request, Response } from 'express';

import { AppModule } from './app.module';
import { AllExceptionsFilter } from './common/errors/all-exceptions.filter';
import { initSentry } from './common/errors/sentry.setup';
import { JsonLogger } from './common/logging/json.logger';
import {
  applyStandardErrorResponses,
  buildOpenApiDocument,
} from './openapi.config';
import { ApiErrorDto } from './common/errors/api-error.dto';

/** Minimal HTTP Basic auth gate used to protect API docs in staging. */
function basicAuth(user: string, pass: string) {
  return (req: Request, res: Response, next: NextFunction) => {
    const header = req.headers.authorization ?? '';
    const [scheme, encoded] = header.split(' ');
    if (scheme === 'Basic' && encoded) {
      const [u, p] = Buffer.from(encoded, 'base64').toString().split(':');
      if (u === user && p === pass) return next();
    }
    res.setHeader('WWW-Authenticate', 'Basic realm="ShowUp API Docs"');
    res.status(401).send('Authentication required');
  };
}

function setupSwagger(app: INestApplication, config: ConfigService) {
  const path = config.get<string>('swagger.path') ?? 'docs';
  const env = config.get<string>('app.env');
  const user = config.get<string>('swagger.user');
  const pass = config.get<string>('swagger.password');

  // In staging, require basic auth in front of the docs if credentials are configured.
  if (env === 'staging' && user && pass) {
    app.use(`/${path}`, basicAuth(user, pass));
    app.use(`/${path}-json`, basicAuth(user, pass));
  }

  // Shared with scripts/emit-openapi.ts so the served docs and the committed openapi.json
  // can never describe different APIs. See src/openapi.config.ts.
  const document = applyStandardErrorResponses(
    SwaggerModule.createDocument(app, buildOpenApiDocument(), {
      extraModels: [ApiErrorDto],
    }),
  );
  SwaggerModule.setup(path, app, document, {
    swaggerOptions: { persistAuthorization: true },
  });
}

async function bootstrap() {
  const app = await NestFactory.create(AppModule, { bufferLogs: true });

  // Structured logging (Epic 16, SHOWUP-91): every existing `new Logger('Context')` call site now
  // produces a single-line JSON record carrying the request id, without any of them changing.
  app.useLogger(new JsonLogger());

  const config = app.get(ConfigService);
  const logger = new Logger('Bootstrap');

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      transform: true,
      forbidNonWhitelisted: true,
    }),
  );

  // Error monitoring (Epic 16, SHOWUP-90). No-ops unless SENTRY_ENABLED is on with a DSN set, so the
  // filter below is harmless when it is off.
  const sentryStarted = initSentry({
    enabled: config.get<boolean>('sentry.enabled') ?? false,
    dsn: config.get<string>('sentry.dsn'),
    environment: config.get<string>('sentry.environment') ?? 'local',
  });
  const { httpAdapter } = app.get(HttpAdapterHost);
  app.useGlobalFilters(new AllExceptionsFilter(httpAdapter));

  const corsOrigins = config.get<string>('app.corsOrigins') ?? '*';
  app.enableCors({
    origin:
      corsOrigins === '*' ? true : corsOrigins.split(',').map((o) => o.trim()),
  });

  const env = config.get<string>('app.env');
  const swaggerEnabled = config.get<boolean>('swagger.enabled');
  const docsExposed = swaggerEnabled && env !== 'production';
  if (docsExposed) setupSwagger(app, config);

  const port = config.get<number>('app.port') ?? 3000;
  await app.listen(port);

  logger.log(
    `ShowUp backend listening on http://localhost:${port} (env=${env})`,
    { sentry: sentryStarted ? 'on' : 'off' },
    'Bootstrap',
  );
  if (docsExposed) {
    logger.log(
      `API docs: http://localhost:${port}/${config.get('swagger.path')}`,
    );
  }
}

void bootstrap();
