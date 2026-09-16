import { ApiProperty } from '@nestjs/swagger';

import { UserDto } from '../../users/dto/user.dto';

export class AuthResponseDto {
  @ApiProperty()
  accessToken: string;

  @ApiProperty()
  refreshToken: string;

  @ApiProperty({ example: 'Bearer' })
  tokenType: string;

  @ApiProperty({
    type: 'integer',
    description: 'Access-token lifetime in seconds',
  })
  expiresIn: number;

  @ApiProperty({ type: UserDto })
  user: UserDto;
}

export class OtpChallengeResponseDto {
  @ApiProperty()
  expiresAt: Date;

  @ApiProperty()
  resendAvailableAt: Date;

  @ApiProperty({
    required: false,
    description:
      'Dev/testing only — present when AUTH_EXPOSE_OTP is enabled (non-production).',
  })
  devCode?: string;
}
