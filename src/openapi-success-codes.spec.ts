import { RequestMethod } from '@nestjs/common';
import { HTTP_CODE_METADATA, METHOD_METADATA } from '@nestjs/common/constants';
import { ModulesContainer, NestFactory } from '@nestjs/core';

import { AppModule } from './app.module';

/**
 * Where `@ApiOkResponse` and friends store their codes: `DECORATORS.API_RESPONSE` in
 * @nestjs/swagger, which its package `exports` map does not let us import. If a swagger upgrade
 * renames it, every route reads as documenting nothing, and the `documented` count below fails.
 */
const API_RESPONSE_METADATA = 'swagger/apiResponse';

/**
 * Every route's documented success code is the code it really answers with.
 *
 * WHY THIS EXISTS
 *
 * The apps are generated from openapi.json, and the iOS client matches a response to the codes the
 * contract lists, exactly. Nest answers a POST with 201 unless the handler says `@HttpCode(...)`,
 * but `@ApiOkResponse` documents 200 -- so a POST with the one and not the other answers a code
 * the contract never mentions, and iOS reads every success as a failure. Android checks
 * `isSuccessful` and never notices, so nothing on that side fails either.
 *
 * It shipped that way on seven routes. `registerPushToken` was found in October 2026 by an e2e
 * test written for something else; six more (`createCheckIn`, `likeProfile`, `createProfile`,
 * `blockUser`, `submitSelfieVerification`, `reportUser`) were found by looking for the rest, before
 * any app called them. This makes the next one a failed unit test instead of a bug report.
 *
 * HOW
 *
 * The same preview-mode module graph `scripts/emit-openapi.ts` builds: every decorator is read and
 * no provider is instantiated, so no database or Redis is needed. For each route, the code Nest
 * will really send (`@HttpCode`, or 201 for a POST and 200 for anything else) must be among the 2xx
 * codes its `@Api...Response` decorators declare.
 */
describe('OpenAPI success codes', () => {
  it('documents the status code each route really answers with', async () => {
    const app = await NestFactory.create(AppModule, {
      preview: true,
      logger: false,
    });
    const mismatches: string[] = [];
    let routes = 0;
    let documented = 0;
    try {
      for (const module of app.get(ModulesContainer).values()) {
        for (const wrapper of module.controllers.values()) {
          const controller = wrapper.metatype as
            | (new (...args: unknown[]) => unknown)
            | null;
          if (!controller) continue;
          const prototype = controller.prototype as Record<string, unknown>;
          for (const name of Object.getOwnPropertyNames(prototype)) {
            const handler = prototype[name];
            if (typeof handler !== 'function') continue;
            const method = Reflect.getMetadata(METHOD_METADATA, handler) as
              | RequestMethod
              | undefined;
            if (method === undefined) continue;
            routes += 1;
            const real =
              (Reflect.getMetadata(HTTP_CODE_METADATA, handler) as
                | number
                | undefined) ?? (method === RequestMethod.POST ? 201 : 200);
            const declared = Object.keys(
              (Reflect.getMetadata(API_RESPONSE_METADATA, handler) as
                | Record<string, unknown>
                | undefined) ?? {},
            ).filter((code) => code.startsWith('2'));
            if (declared.length > 0) documented += 1;
            if (declared.length > 0 && !declared.includes(String(real))) {
              mismatches.push(
                `${controller.name}.${name}: answers ${real}, documents ${declared.join(' / ')}`,
              );
            }
          }
        }
      }
    } finally {
      await app.close();
    }
    expect(routes).toBeGreaterThan(50);
    // Most routes document their success; if this collapses, the metadata key above has moved.
    expect(documented).toBeGreaterThan(routes / 2);
    expect(mismatches).toEqual([]);
  });
});
