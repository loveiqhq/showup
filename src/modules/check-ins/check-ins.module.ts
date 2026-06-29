import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { CheckInsController } from './check-ins.controller';
import { CheckInsService } from './check-ins.service';
import { CheckIn } from './entities/check-in.entity';

/** Check-in & availability (Epic 4): create / fetch-active / cancel, with lazy expiry. */
@Module({
  imports: [TypeOrmModule.forFeature([CheckIn])],
  controllers: [CheckInsController],
  providers: [CheckInsService],
  exports: [CheckInsService],
})
export class CheckInsModule {}
