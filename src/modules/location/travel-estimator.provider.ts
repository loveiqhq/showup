import { ConfigService } from '@nestjs/config';
import { Provider } from '@nestjs/common';

import { createTravelEstimator, TravelTimeEstimator } from './util/travel';

/**
 * DI token for the travel-time source (same seam pattern as the SMS, email, push and analytics
 * senders): one interface, a free default, and a real provider chosen by configuration.
 */
export const TRAVEL_TIME_ESTIMATOR = Symbol('TRAVEL_TIME_ESTIMATOR');

/**
 * Config-driven selection of the travel-time source. With `GOOGLE_MAPS_API_KEY` set, real routes are
 * used; without it, the free rough estimate (distance × a fixed rate). Tests therefore never make a
 * paid lookup, and the estimate stays proportional to distance until a key is added.
 */
export const travelTimeEstimatorProvider: Provider = {
  provide: TRAVEL_TIME_ESTIMATOR,
  inject: [ConfigService],
  useFactory: (config: ConfigService): TravelTimeEstimator =>
    createTravelEstimator({
      apiKey: config.get<string>('maps.googleApiKey') || undefined,
    }),
};
