import { Module } from '@nestjs/common';

import { LocationService } from './location.service';
import {
  TRAVEL_TIME_ESTIMATOR,
  travelTimeEstimatorProvider,
} from './travel-estimator.provider';

@Module({
  providers: [LocationService, travelTimeEstimatorProvider],
  exports: [LocationService, TRAVEL_TIME_ESTIMATOR],
})
export class LocationModule {}
