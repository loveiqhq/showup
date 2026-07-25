import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { CheckInsModule } from '../check-ins/check-ins.module';
import { DatesModule } from '../dates/dates.module';
import { Profile } from '../profiles/entities/profile.entity';
import { SafetyModule } from '../safety/safety.module';
import { User } from '../users/entities/user.entity';
import { Like } from './entities/like.entity';
import { Match } from './entities/match.entity';
import { MatchingController } from './matching.controller';
import { MatchingService } from './matching.service';

/** Discovery, likes & matching (Epic 6). */
@Module({
  imports: [
    TypeOrmModule.forFeature([Like, Match, Profile, User]),
    CheckInsModule,
    SafetyModule,
    DatesModule,
  ],
  controllers: [MatchingController],
  providers: [MatchingService],
  exports: [MatchingService],
})
export class MatchingModule {}
