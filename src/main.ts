import { Logger, ValidationPipe } from '@nestjs/common';
import type { INestApplication } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import type { NextFunction, Request, Response } from 'express';

import { AppModule } from './app.module';

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

  const doc = new DocumentBuilder()
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

  const document = SwaggerModule.createDocument(app, doc);
  SwaggerModule.setup(path, app, document, {
    swaggerOptions: { persistAuthorization: true },
  });
}

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  const config = app.get(ConfigService);
  const logger = new Logger('Bootstrap');

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      transform: true,
      forbidNonWhitelisted: true,
    }),
  );

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
  );
  if (docsExposed) {
    logger.log(
      `API docs: http://localhost:${port}/${config.get('swagger.path')}`,
    );
  }
}

void bootstrap();
