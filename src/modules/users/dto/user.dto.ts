import { ApiProperty } from '@nestjs/swagger';

import { User, UserStatus } from '../entities/user.entity';

/** Public-safe view of a user — never exposes internal/auth fields. */
export class UserDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ nullable: true })
  phone: string | null;

  @ApiProperty({ nullable: true })
  email: string | null;

  @ApiProperty({ nullable: true })
  displayName: string | null;

  @ApiProperty({ enum: UserStatus })
  status: UserStatus;

  @ApiProperty()
  phoneVerified: boolean;

  @ApiProperty()
  emailVerified: boolean;

  @ApiProperty()
  createdAt: Date;

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
    };
  }
}
