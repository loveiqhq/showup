import { ApiProperty } from '@nestjs/swagger';
import { IsString, Length } from 'class-validator';

/** Confirm email ownership with the 6-digit code that was emailed. */
export class VerifyEmailDto {
  @ApiProperty({ example: '123456', description: 'The 6-digit code sent by email' })
  @IsString()
  @Length(6, 6)
  code: string;
}
