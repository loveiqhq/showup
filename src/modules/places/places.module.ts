import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import { choosePlacesProvider } from './places.binding';
import { PLACES_PROVIDER } from './places.provider';
import { PlacesService } from './places.service';

/** Meeting-place lookup for Date 05: the provider seam plus the per-date service. */
@Module({
  providers: [
    {
      provide: PLACES_PROVIDER,
      inject: [ConfigService],
      useFactory: (config: ConfigService) =>
        choosePlacesProvider({
          key: config.get<string>('GOOGLE_PLACES_API_KEY'),
          nodeEnv: config.get<string>('NODE_ENV'),
        }),
    },
    PlacesService,
  ],
  exports: [PlacesService, PLACES_PROVIDER],
})
export class PlacesModule {}
