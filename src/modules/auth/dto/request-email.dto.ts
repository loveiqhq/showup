import { ApiProperty } from '@nestjs/swagger';
import { IsEmail } from 'class-validator';

/** Start email verification: the address to send a 6-digit code to. */
export class RequestEmailDto {
  @ApiProperty({ example: 'leo@example.com' })
  @IsEmail()
  email: string;
}
