import { BullModule } from '@nestjs/bullmq';
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { AnalyticsModule } from '../analytics/analytics.module';
import { CheckInsModule } from '../check-ins/check-ins.module';
import { DatesModule } from '../dates/dates.module';
import { Profile } from '../profiles/entities/profile.entity';
import { QUEUE_LIKE_EXPIRY } from '../../queue/queue.constants';
import { SafetyModule } from '../safety/safety.module';
import { SearchModule } from '../search/search.module';
import { User } from '../users/entities/user.entity';
import { Like } from './entities/like.entity';
import { Match } from './entities/match.entity';
import { LikeExpiryProcessor } from './jobs/like-expiry.processor';
import { LikeExpiryScheduler } from './jobs/like-expiry.scheduler';
import { MatchingController } from './matching.controller';
import { MatchingService } from './matching.service';

/** Discovery, likes & matching (Epic 6). */
@Module({
  imports: [
    TypeOrmModule.forFeature([Like, Match, Profile, User]),
    CheckInsModule,
    SafetyModule,
    DatesModule,
    AnalyticsModule,
    SearchModule,
    BullModule.registerQueue({ name: QUEUE_LIKE_EXPIRY }),
  ],
  controllers: [MatchingController],
  providers: [MatchingService, LikeExpiryScheduler, LikeExpiryProcessor],
  exports: [MatchingService],
})
export class MatchingModule {}
