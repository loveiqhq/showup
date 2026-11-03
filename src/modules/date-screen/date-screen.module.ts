import { Module } from '@nestjs/common';

import { SearchModule } from '../search/search.module';
import { DateScreenController } from './date-screen.controller';
import { DateScreenService } from './date-screen.service';

/**
 * The Date screen (Date 01, SHOWUP-193). Its own module because it reads both a date and Search's
 * profile builder, and Search already imports Dates -- folding this into either would close a loop.
 */
@Module({
  imports: [SearchModule],
  controllers: [DateScreenController],
  providers: [DateScreenService],
  exports: [DateScreenService],
})
export class DateScreenModule {}
