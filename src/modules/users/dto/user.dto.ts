import { ApiProperty } from '@nestjs/swagger';

import { PlanTier, User, UserStatus } from '../entities/user.entity';

/** Public-safe view of a user — never exposes internal/auth fields. */
export class UserDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ type: String, nullable: true })
  phone: string | null;

  @ApiProperty({ type: String, nullable: true })
  email: string | null;

  @ApiProperty({ type: String, nullable: true })
  displayName: string | null;

  @ApiProperty({ enum: UserStatus })
  status: UserStatus;

  @ApiProperty()
  phoneVerified: boolean;

  @ApiProperty()
  emailVerified: boolean;

  @ApiProperty()
  createdAt: Date;

  /**
   * Picks the Search surface (Date search epic rule 2). A STRING read tolerantly, not a response
   * enum: Epic 9 may add tiers, and an installed app must not fail to decode its own session.
   */
  @ApiProperty({
    type: String,
    description:
      'freemium or premium. Read tolerantly: a newer server may add values (treat as freemium).',
  })
  planTier: PlanTier;

  /** The Search actions coach's `Don't show again` (Search 01): true means never show it. */
  @ApiProperty()
  searchCoachHidden: boolean;

  static from(user: User): UserDto {
    return {
      id: user.id,
      phone: user.phone,
      email: user.email,
      displayName: user.displayName,
      status: user.status,
      phoneVerified: user.phoneVerifiedAt != null,
      emailVerified: user.emailVerifiedAt != null,
      createdAt: user.createdAt,
      planTier: user.planTier,
      searchCoachHidden: user.searchCoachHidden,
    };
  }
}
