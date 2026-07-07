import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

import {
  Profile,
  ProfileVerificationStatus,
} from '../../profiles/entities/profile.entity';
import { Like } from '../entities/like.entity';
import { Match } from '../entities/match.entity';
import { LikeStatus, MatchStatus } from '../util/match';

/** Result of sending a like: the like itself, and whether it produced a match. */
export class LikeResultDto {
  @ApiProperty()
  likeId: string;

  @ApiProperty({ enum: LikeStatus })
  status: LikeStatus;

  @ApiProperty({ description: 'True if this like completed a mutual match' })
  matched: boolean;

  @ApiPropertyOptional({
    description: 'The match id, when a match was created',
  })
  matchId?: string;

  static from(like: Like, match?: Match): LikeResultDto {
    return {
      likeId: like.id,
      status: like.status,
      matched: Boolean(match),
      matchId: match?.id,
    };
  }
}

/** A profile card shown in discovery — safe fields only, plus a derived distance. */
export class DiscoveryProfileDto {
  @ApiProperty()
  userId: string;

  @ApiProperty({ nullable: true })
  displayName: string | null;

  @ApiProperty({ nullable: true })
  bio: string | null;

  @ApiProperty({ nullable: true })
  gender: string | null;

  @ApiProperty({ nullable: true })
  lookingFor: string | null;

  @ApiProperty({ description: 'Whether the profile is identity-verified' })
  verified: boolean;

  @ApiProperty({
    description: 'Distance from the viewer, rounded (never exact coordinates)',
  })
  distanceMeters: number;

  static from(profile: Profile, distanceMeters: number): DiscoveryProfileDto {
    return {
      userId: profile.userId,
      displayName: profile.displayName,
      bio: profile.bio,
      gender: profile.gender,
      lookingFor: profile.lookingFor,
      verified:
        profile.verificationStatus === ProfileVerificationStatus.Verified,
      distanceMeters,
    };
  }
}

/** A match as seen by one of its participants. */
export class MatchDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ description: 'The other person in the match' })
  otherUserId: string;

  @ApiProperty({ enum: MatchStatus })
  status: MatchStatus;

  @ApiProperty()
  createdAt: Date;

  static from(match: Match, viewerUserId: string): MatchDto {
    const otherUserId =
      match.userAId === viewerUserId ? match.userBId : match.userAId;
    return {
      id: match.id,
      otherUserId,
      status: match.status,
      createdAt: match.createdAt,
    };
  }
}
