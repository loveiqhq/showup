import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { Profile } from '../profiles/entities/profile.entity';
import { ProfilePhoto } from '../profiles/entities/profile-photo.entity';
import { User } from '../users/entities/user.entity';
import { Block } from './entities/block.entity';
import { ModerationStatusChange } from './entities/moderation-status-change.entity';
import { Report } from './entities/report.entity';
import { VerificationAttempt } from './entities/verification-attempt.entity';
import { ModerationService } from './moderation.service';
import { ReportsService } from './reports.service';
import { SafetyAdminController } from './safety-admin.controller';
import { SafetyController } from './safety.controller';
import { SafetyService } from './safety.service';
import { VerificationService } from './verification.service';
import {
  StubVerificationProvider,
  VERIFICATION_PROVIDER,
} from './verification/verification.provider';

/**
 * Safety, moderation & admin (Epic 12). Blocking (SHOWUP-77), moderation standing (SHOWUP-79),
 * staff moderation tools (SHOWUP-80), and selfie-verification scaffolding (SHOWUP-110).
 * Exports SafetyService/ModerationService so matching can filter blocked & hidden users.
 */
@Module({
  imports: [
    TypeOrmModule.forFeature([
      Block,
      ModerationStatusChange,
      Report,
      VerificationAttempt,
      User,
      Profile,
      ProfilePhoto,
    ]),
  ],
  controllers: [SafetyController, SafetyAdminController],
  providers: [
    SafetyService,
    ModerationService,
    ReportsService,
    VerificationService,
    { provide: VERIFICATION_PROVIDER, useClass: StubVerificationProvider },
  ],
  exports: [SafetyService, ModerationService],
})
export class SafetyModule {}
