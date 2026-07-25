import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

import { ProfileVerificationStatus } from '../../profiles/entities/profile.entity';
import { User, UserRole, UserStatus } from '../../users/entities/user.entity';
import { Block } from '../entities/block.entity';
import { VerificationAttempt } from '../entities/verification-attempt.entity';
import {
  ModerationStanding,
  ModerationSubjectType,
  VerificationOutcome,
} from '../util/safety';

/** A block the viewer has in place. */
export class BlockDto {
  @ApiProperty() id: string;
  @ApiProperty() blockedUserId: string;
  @ApiProperty() createdAt: Date;

  static from(b: Block): BlockDto {
    return { id: b.id, blockedUserId: b.blockedId, createdAt: b.createdAt };
  }
}

/** The outcome of a verification attempt. */
export class VerificationDto {
  @ApiProperty() id: string;
  @ApiProperty({ enum: VerificationOutcome }) outcome: VerificationOutcome;
  @ApiProperty() verified: boolean;
  @ApiPropertyOptional() matchScore?: number;
  @ApiProperty() createdAt: Date;

  static from(a: VerificationAttempt): VerificationDto {
    return {
      id: a.id,
      outcome: a.outcome,
      verified: a.outcome === VerificationOutcome.Verified,
      matchScore: a.matchScore != null ? Number(a.matchScore) : undefined,
      createdAt: a.createdAt,
    };
  }
}

/** Result of a staff standing change. */
export class StandingChangeDto {
  @ApiProperty({ enum: ModerationSubjectType })
  subjectType: ModerationSubjectType;
  @ApiProperty() subjectId: string;
  @ApiPropertyOptional({ enum: ModerationStanding }) from?: ModerationStanding;
  @ApiProperty({ enum: ModerationStanding }) to: ModerationStanding;
}

/** A row in the staff list of users filtered by standing (SHOWUP-80). */
export class AdminUserRowDto {
  @ApiProperty() userId: string;
  @ApiProperty({ enum: ModerationStanding })
  moderationStanding: ModerationStanding;
  @ApiProperty({ enum: UserStatus }) accountStatus: UserStatus;
  @ApiProperty() createdAt: Date;

  static from(u: User): AdminUserRowDto {
    return {
      userId: u.id,
      moderationStanding: u.moderationStanding,
      accountStatus: u.status,
      createdAt: u.createdAt,
    };
  }
}

/** The safety picture of one user, assembled for staff review (SHOWUP-80). */
export class UserSafetyContextDto {
  @ApiProperty() userId: string;
  @ApiProperty({ enum: UserStatus }) accountStatus: UserStatus;
  @ApiProperty({ enum: UserRole }) role: UserRole;
  @ApiProperty({ enum: ModerationStanding })
  moderationStanding: ModerationStanding;
  @ApiProperty({ enum: ProfileVerificationStatus })
  verificationStatus: ProfileVerificationStatus;
  @ApiProperty({ description: 'Ids of users this person has blocked.' })
  blockedUsers: string[];
  @ApiProperty({ description: 'Ids of users who have blocked this person.' })
  blockedBy: string[];
}
