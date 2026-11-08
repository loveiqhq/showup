import { Module } from '@nestjs/common';

import { CheckInsModule } from '../check-ins/check-ins.module';
import { DatesModule } from '../dates/dates.module';
import { SearchModule } from '../search/search.module';
import { DateDevController } from './date-dev.controller';
import { DateDevService } from './date-dev.service';
import { DateScreenController } from './date-screen.controller';
import { DateScreenService } from './date-screen.service';

/**
 * The Date screen (Date 01, SHOWUP-193). Its own module because it reads both a date and Search's
 * profile builder, and Search already imports Dates -- folding this into either would close a loop.
 */
@Module({
  imports: [SearchModule, DatesModule, CheckInsModule],
  controllers: [DateScreenController, DateDevController],
  providers: [DateScreenService, DateDevService],
  exports: [DateScreenService],
})
export class DateScreenModule {}
