/**
 * Writes the OpenAPI contract to openapi.json, from the real NestJS application.
 *
 * WHY THIS EXISTS
 *
 * The mobile clients are generated from this file rather than hand-written. That only works if the
 * file is produced by the backend code itself: a hand-edited spec is a second source of truth, and
 * the drift between it and the server is silent until a user hits it.
 *
 * WHY PREVIEW MODE
 *
 * `preview: true` builds the module graph and reads every decorator without instantiating
 * providers or running lifecycle hooks. That matters because AppModule imports DatabaseModule and
 * QueueModule, which would otherwise open connections to Postgres and Redis. Swagger only needs
 * the metadata, so preview mode gives an identical document with no infrastructure -- which is
 * what lets this run in CI on a plain runner with no services attached.
 *
 * WHY THE KEYS ARE SORTED
 *
 * The document is committed and diffed in CI, so byte-stability matters more than key order.
 * Object key order in the emitted JSON is not semantically meaningful, but an unstable order
 * produces phantom diffs that make the drift check useless. Arrays are left alone -- their order
 * IS meaningful (parameters, enum members, `required`).
 */
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';

import { NestFactory } from '@nestjs/core';
import { SwaggerModule } from '@nestjs/swagger';

import { AppModule } from '../src/app.module';
import {
  applyStandardErrorResponses,
  buildOpenApiDocument,
} from '../src/openapi.config';
import { ApiErrorDto } from '../src/common/errors/api-error.dto';

/**
 * Where the contract is written. Both copies, always, in one command.
 *
 * The second exists because Apple's OpenAPI generator is a BUILD PLUGIN, and an SPM build plugin
 * resolves its inputs relative to the target directory -- it cannot read a file from outside the
 * package. So the iOS package needs its own copy.
 *
 * A second copy of a contract is exactly the kind of thing that drifts, so it is not left to
 * anybody to remember: this script writes both and CI diffs both. A stale iOS copy is therefore a
 * failed build rather than an app generated against an API the server no longer serves.
 *
 * Android needs no entry here -- the Gradle plugin takes an absolute path and reads the root file
 * directly. That path is repo-relative (`$rootDir/../../../openapi.json`), which couples the app
 * build to the repository layout; see docs/openapi-contract.md.
 */
const OUTPUTS = [
  join(__dirname, '..', 'openapi.json'),
  join(
    __dirname,
    '..',
    'mobile',
    'welcome-screen',
    'ios-app',
    'ShowUpAPI',
    'Sources',
    'ShowUpAPI',
    'openapi.json',
  ),
];

/** Recursively sort object keys; leave arrays in their declared order. */
function stable(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') {
    const source = value as Record<string, unknown>;
    return Object.keys(source)
      .sort()
      .reduce<Record<string, unknown>>((out, key) => {
        out[key] = stable(source[key]);
        return out;
      }, {});
  }
  return value;
}

async function main() {
  const app = await NestFactory.create(AppModule, {
    preview: true,
    logger: false,
  });

  const document = applyStandardErrorResponses(
    SwaggerModule.createDocument(app, buildOpenApiDocument(), {
      extraModels: [ApiErrorDto],
    }),
  );
  const serialised = `${JSON.stringify(stable(document), null, 2)}\n`;
  for (const target of OUTPUTS) writeFileSync(target, serialised, 'utf8');

  const paths = Object.keys(document.paths ?? {});
  const operations = paths.reduce(
    (n, path) =>
      n +
      Object.keys((document.paths as Record<string, object>)[path] ?? {})
        .length,
    0,
  );
  const schemas = Object.keys(document.components?.schemas ?? {}).length;

  // eslint-disable-next-line no-console
  console.log(
    `openapi.json written to ${OUTPUTS.length} locations: ` +
      `${paths.length} paths, ${operations} operations, ${schemas} schemas`,
  );

  await app.close();
}

main().catch((error) => {
  // eslint-disable-next-line no-console
  console.error(error);
  process.exit(1);
});
