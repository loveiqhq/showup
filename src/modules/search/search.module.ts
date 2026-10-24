import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { DatesModule } from '../dates/dates.module';
import { ProfilesModule } from '../profiles/profiles.module';
import { SafetyModule } from '../safety/safety.module';
import { SearchPass } from './entities/search-pass.entity';
import { SearchController, SearchDevController } from './search.controller';
import { SearchService } from './search.service';

/** Date search (epic SHOWUP-187): the queue, the profile payload, passes and the coach flag. */
@Module({
  imports: [
    TypeOrmModule.forFeature([SearchPass]),
    ProfilesModule,
    SafetyModule,
    DatesModule,
  ],
  controllers: [SearchController, SearchDevController],
  providers: [SearchService],
  exports: [SearchService],
})
export class SearchModule {}
